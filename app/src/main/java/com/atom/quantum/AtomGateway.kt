package com.atom.quantum

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** SSE protocol: {"type":"delta","text":"..."} / {"type":"done"} / {"type":"error","message":"..."} */
class AtomGateway {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .build()
    @Volatile private var call: Call? = null

    fun stop() { call?.cancel() }

    suspend fun checkHealth(): String = withContext(Dispatchers.IO) {
        val base = BuildConfig.API_BASE_URL.trim().trimEnd('/')
        if (!base.startsWith("https://")) return@withContext "unconfigured"
        val request = Request.Builder().url(base + "/health").get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext "offline"
                val body = response.body?.string() ?: return@withContext "offline"
                if (JSONObject(body).optString("status") == "configured") "gateway" else "unconfigured"
            }
        } catch (_: IOException) { "offline" }
        catch (_: Exception) { "offline" }
    }

    suspend fun stream(history: List<MessageEntity>, mode: String, onDelta: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            val base = BuildConfig.API_BASE_URL.trim().trimEnd('/')
            if (!base.startsWith("https://")) throw IOException("Cloud endpoint is not configured with HTTPS")
            val messages = JSONArray()
            history.filter { it.content.isNotBlank() }.takeLast(24).forEach {
                messages.put(JSONObject().put("role", it.role).put("content", it.content))
            }
            val payload = JSONObject().put("messages", messages).put("mode", mode)
            val request = Request.Builder()
                .url(base + "/v1/chat/stream")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .header("Accept", "text/event-stream")
                .build()
            val activeCall = client.newCall(request)
            call = activeCall
            try {
                activeCall.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Cloud HTTP " + response.code)
                    }
                    val streamBody = response.body ?: throw IOException("Cloud returned an empty response")
                    var completed = false
                    streamBody.charStream().buffered().useLines { lines ->
                        for (line in lines) {
                            if (!currentCoroutineContext().isActive) throw CancellationException()
                            if (!line.startsWith("data: ")) continue
                            val packet = try { JSONObject(line.removePrefix("data: ")) }
                                catch (_: Exception) { continue }
                            when (packet.optString("type")) {
                                "delta" -> onDelta(packet.optString("text"))
                                "done" -> { completed = true; break }
                                "error" -> throw IOException(packet.optString("message", "Cloud inference failed"))
                            }
                        }
                    }
                    if (!completed) throw IOException("Cloud stream ended before completion")
                }
            } finally {
                call = null
            }
        }
    }
}
