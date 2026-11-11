package com.atom.quantum

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AtomViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = Room.databaseBuilder(application, AtomDatabase::class.java, "atom_x.db").build().dao()
    private val prefs = application.getSharedPreferences("atom_preferences", Context.MODE_PRIVATE)
    private val gateway = AtomGateway()
    private var task: Job? = null

    val chats: StateFlow<List<ChatEntity>> = dao.observeChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val selectedChat = MutableStateFlow<String?>(null)
    val messages: StateFlow<List<MessageEntity>> = selectedChat.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else dao.observeMessages(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val streaming = MutableStateFlow(false)
    val streamingText = MutableStateFlow("")
    val streamingMessageId = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)
    val theme = MutableStateFlow(prefs.getString("theme", "system") ?: "system")
    val language = MutableStateFlow(prefs.getString("language", "system") ?: "system")
    val mode = MutableStateFlow(prefs.getString("mode", "fast") ?: "fast")

    init {
        viewModelScope.launch {
            selectedChat.value = dao.mostRecentId()
            if (selectedChat.value == null) createChat()
        }
    }

    private suspend fun createChat(): String {
        val id = UUID.randomUUID().toString()
        dao.saveChat(ChatEntity(id = id, title = "New chat"))
        selectedChat.value = id
        return id
    }

    fun newChat() {
        stop()
        viewModelScope.launch { createChat() }
    }

    fun openChat(id: String) {
        stop()
        selectedChat.value = id
        error.value = null
    }

    fun renameChat(id: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { dao.renameChat(id, title.trim().take(90)) }
    }

    fun pinChat(id: String, pinned: Boolean) {
        viewModelScope.launch { dao.pinChat(id, pinned) }
    }

    fun deleteChat(id: String) {
        stop()
        viewModelScope.launch {
            dao.deleteChat(id)
            if (selectedChat.value == id) {
                selectedChat.value = dao.mostRecentId()
                if (selectedChat.value == null) createChat()
            }
        }
    }

    fun clearAll() {
        stop()
        viewModelScope.launch { dao.deleteAllChats(); createChat() }
    }

    fun setTheme(value: String) {
        theme.value = value
        prefs.edit().putString("theme", value).apply()
    }

    fun setLanguage(value: String) {
        language.value = value
        prefs.edit().putString("language", value).apply()
    }

    fun setMode(value: String) {
        mode.value = value
        prefs.edit().putString("mode", value).apply()
    }

    fun dismissError() { error.value = null }

    fun stop() {
        task?.cancel()
        gateway.stop()
    }

    fun send(input: String): Boolean {
        val text = input.trim()
        if (text.isEmpty() || streaming.value) return false
        if (!BuildConfig.API_BASE_URL.startsWith("https://")) {
            error.value = "Cloud service is not deployed. Set ATOM_BACKEND_URL in GitHub Actions."
            return false
        }
        val chatId = selectedChat.value ?: return false
        val answerId = UUID.randomUUID().toString()
        streaming.value = true
        streamingText.value = ""
        streamingMessageId.value = answerId
        error.value = null
        task = viewModelScope.launch {
            val result = StringBuilder()
            try {
                dao.saveMessage(MessageEntity(UUID.randomUUID().toString(), chatId, "user", text))
                dao.saveMessage(MessageEntity(answerId, chatId, "assistant", ""))
                val history = dao.history(chatId).asReversed().filter { it.content.isNotBlank() }
                if (history.size == 1) dao.renameChat(chatId, text.take(48))
                dao.touchChat(chatId)
                gateway.stream(history, mode.value) { delta ->
                    result.append(delta)
                    streamingText.value = result.toString()
                }
                dao.updateMessage(answerId, result.toString())
            } catch (_: CancellationException) {
                dao.updateMessage(answerId, result.toString())
                throw CancellationException()
            } catch (err: Exception) {
                if (result.isNotEmpty()) dao.updateMessage(answerId, result.toString())
                else dao.updateMessage(answerId, "")
                error.value = err.message ?: "Request failed"
            } finally {
                streaming.value = false
                streamingMessageId.value = null
                streamingText.value = ""
            }
        }
        return true
    }
}
