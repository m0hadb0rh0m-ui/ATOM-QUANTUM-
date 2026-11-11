package com.atom.quantum

import android.content.ClipData
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AtomViewModel = viewModel()
            val theme by vm.theme.collectAsState()
            val language by vm.language.collectAsState()
            val dark = when (theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val locale = LocalContext.current.resources.configuration.locales[0].language
            val ar = if (language == "system") locale == "ar" else language == "ar"
            CompositionLocalProvider(LocalLayoutDirection provides
                if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    AtomApp(vm, ar)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AtomApp(vm: AtomViewModel, ar: Boolean) {
    val chats by vm.chats.collectAsState()
    val messages by vm.messages.collectAsState()
    val activeId by vm.selectedChat.collectAsState()
    val running by vm.streaming.collectAsState()
    val live by vm.streamingText.collectAsState()
    val liveId by vm.streamingMessageId.collectAsState()
    val mode by vm.mode.collectAsState()
    val error by vm.error.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val snack = remember { SnackbarHostState() }
    var draft by rememberSaveable { mutableStateOf("") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var renaming by remember { mutableStateOf<ChatEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val tts = remember { TextToSpeech(context) {} }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val result = it.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!result.isNullOrBlank()) draft = (draft + " " + result).trim()
    }
    LaunchedEffect(error) {
        if (error != null) {
            snack.showSnackbar(error ?: "")
            vm.dismissError()
        }
    }

    if (renaming != null) {
        AlertDialog(onDismissRequest = { renaming = null }, title = { Text(if (ar) "تسمية المحادثة" else "Rename chat") },
            text = { OutlinedTextField(renameText, { renameText = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                renaming?.let { vm.renameChat(it.id, renameText) }
                renaming = null
            }) { Text(if (ar) "حفظ" else "Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(if (ar) "إلغاء" else "Cancel") } })
    }
    if (confirmClear) {
        AlertDialog(onDismissRequest = { confirmClear = false },
            title = { Text(if (ar) "حذف جميع المحادثات؟" else "Delete all chats?") },
            text = { Text(if (ar) "سيُحذف السجل المحلي نهائيًا." else "All local history will be deleted.") },
            confirmButton = { TextButton(onClick = { vm.clearAll(); confirmClear = false }) {
                Text(if (ar) "حذف" else "Delete")
            } }, dismissButton = { TextButton(onClick = { confirmClear = false }) {
                Text(if (ar) "إلغاء" else "Cancel")
            } })
    }

    if (settings) {
        SettingsPage(vm, ar, onBack = { settings = false }, onClear = { confirmClear = true })
        return
    }

    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet {
            Text("⚛ ATOM AI X", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
            Button(onClick = {
                vm.newChat()
                scope.launch { drawer.close() }
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (ar) "محادثة جديدة" else "New chat")
            }
            OutlinedTextField(search, { search = it }, modifier = Modifier
                .fillMaxWidth().padding(12.dp), singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(if (ar) "بحث في المحادثات" else "Search chats") })
            HorizontalDivider()
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(chats.filter { it.title.contains(search, ignoreCase = true) }, key = { it.id }) { chat ->
                    var openMenu by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            vm.openChat(chat.id)
                            scope.launch { drawer.close() }
                        }.padding(start = 12.dp, end = 4.dp, top = 5.dp, bottom = 5.dp)) {
                        Icon(if (chat.pinned) Icons.Default.PushPin else Icons.Default.ChatBubbleOutline,
                            contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(chat.title, modifier = Modifier.weight(1f).padding(10.dp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box {
                            IconButton(onClick = { openMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Chat options")
                            }
                            DropdownMenu(expanded = openMenu, onDismissRequest = { openMenu = false }) {
                                DropdownMenuItem(text = { Text(if (ar) "إعادة تسمية" else "Rename") },
                                    onClick = {
                                        renameText = chat.title; renaming = chat; openMenu = false
                                    })
                                DropdownMenuItem(text = { Text(if (chat.pinned) "Unpin" else "Pin") },
                                    onClick = { vm.pinChat(chat.id, !chat.pinned); openMenu = false })
                                DropdownMenuItem(text = { Text(if (ar) "حذف" else "Delete") },
                                    onClick = { vm.deleteChat(chat.id); openMenu = false })
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            NavigationDrawerItem(label = { Text(if (ar) "الإعدادات" else "Settings") },
                selected = false, icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                onClick = { settings = true; scope.launch { drawer.close() } },
                modifier = Modifier.padding(12.dp))
        }
    }) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                TopAppBar(title = {
                    Column {
                        Text("ATOM AI X", fontWeight = FontWeight.Bold)
                        Text(if (ar) "ATOM QUANTUM INTELLIGENCE" else "ATOM QUANTUM INTELLIGENCE",
                            style = MaterialTheme.typography.labelSmall)
                    }
                }, navigationIcon = {
                    IconButton(onClick = { scope.launch { drawer.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }, actions = {
                    IconButton(onClick = { vm.newChat() }) {
                        Icon(Icons.Default.AddComment, contentDescription = "New chat")
                    }
                    IconButton(onClick = { settings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                })
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("fast", "study", "coding", "deep").forEach { key ->
                        FilterChip(selected = mode == key, onClick = { vm.setMode(key) },
                            label = { Text(modeTitle(key, ar)) })
                    }
                }
                HorizontalDivider()
                val state = rememberLazyListState()
                LaunchedEffect(activeId, messages.size, live.length) {
                    if (messages.isNotEmpty()) state.animateScrollToItem(messages.lastIndex)
                }
                LazyColumn(state = state, modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (messages.isEmpty()) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 38.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("⚛", style = MaterialTheme.typography.displayLarge)
                                Text(if (ar) "كيف أستطيع مساعدتك؟" else "How can I help?",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold)
                                Text(if (ar) "اسأل، تعلّم، اكتب، وبرمج." else "Ask, learn, write and build.",
                                    style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(26.dp))
                                listOf(
                                    if (ar) "اشرح لي مفهومًا علميًا ببساطة" else "Explain a scientific concept",
                                    if (ar) "ساعدني في كتابة كود Kotlin" else "Help me write Kotlin code",
                                    if (ar) "ترجم فقرة للعربية والإنجليزية" else "Translate Arabic and English"
                                ).forEach { suggestion ->
                                    OutlinedButton(onClick = { draft = suggestion },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text(suggestion)
                                    }
                                }
                            }
                        }
                    }
                    items(messages, key = { it.id }) { message ->
                        val display = if (message.id == liveId && running) live else message.content
                        MessageBubble(message.role, display, ar,
                            onSpeak = {
                                tts.language = if (ar) Locale("ar") else Locale.ENGLISH
                                tts.speak(display, TextToSpeech.QUEUE_FLUSH, null, "atom-answer")
                            },
                            onCopy = {
                                val manager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                manager.setPrimaryClip(ClipData.newPlainText("ATOM AI X", display))
                            },
                            onShare = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, display)
                                }
                                context.startActivity(Intent.createChooser(intent, "ATOM AI X"))
                            })
                    }
                }
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.Bottom) {
                    IconButton(onClick = {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                                    if (ar) "ar" else "en")
                            }
                            speech.launch(intent)
                        } catch (_: Exception) {
                            scope.launch { snack.showSnackbar(if (ar) "التعرف الصوتي غير متاح"
                                else "Speech recognition unavailable") }
                        }
                    }) { Icon(Icons.Default.Mic, contentDescription = "Speak") }
                    OutlinedTextField(value = draft, onValueChange = { draft = it },
                        modifier = Modifier.weight(1f), maxLines = 6,
                        placeholder = { Text(if (ar) "اكتب رسالتك..." else "Message ATOM...") },
                        shape = RoundedCornerShape(20.dp))
                    if (running) {
                        IconButton(onClick = { vm.stop() }) {
                            Icon(Icons.Default.StopCircle, contentDescription = "Stop")
                        }
                    } else {
                        IconButton(onClick = { if (vm.send(draft)) draft = "" },
                            enabled = draft.isNotBlank()) {
                            Icon(Icons.Default.Send, contentDescription = "Send")
                        }
                    }
                }
                if (BuildConfig.API_BASE_URL.isBlank()) {
                    Text(if (ar) "الخدمة السحابية غير مهيأة. يلزم نشر Backend أولًا."
                    else "Cloud backend not deployed. Configure it in CI.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(role: String, markdown: String, ar: Boolean,
                          onCopy: () -> Unit, onSpeak: () -> Unit, onShare: () -> Unit) {
    val user = role == "user"
    val context = LocalContext.current
    val foreground = if (user) MaterialTheme.colorScheme.onPrimaryContainer.toArgb()
                     else MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val markwon = remember(context) {
        Markwon.builder(context).usePlugin(TablePlugin.create(context)).build()
    }
    Column(modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
        Text(if (user) (if (ar) "أنت" else "You") else "⚛ ATOM",
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp))
        Surface(color = if (user) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(if (user) 0.90f else 1f)) {
            if (markdown.isBlank()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(if (ar) "جارٍ التوليد..." else "Generating...")
                }
            } else {
                AndroidView(factory = {
                    TextView(it).apply {
                        setTextIsSelectable(true)
                        setPadding(24, 20, 24, 20)
                        setTextColor(foreground)
                        textSize = 16f
                    }
                }, update = { it.setTextColor(foreground); markwon.setMarkdown(it, markdown) },
                    modifier = Modifier.fillMaxWidth())
            }
        }
        if (!user && markdown.isNotBlank()) {
            Row {
                IconButton(onClick = onCopy, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onSpeak, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Read aloud", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onShare, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun modeTitle(value: String, ar: Boolean): String = when (value) {
    "study" -> if (ar) "دراسة" else "Study"
    "coding" -> if (ar) "برمجة" else "Coding"
    "deep" -> if (ar) "تحليل" else "Analysis"
    else -> if (ar) "سريع" else "Fast"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPage(vm: AtomViewModel, ar: Boolean, onBack: () -> Unit,
                         onClear: () -> Unit) {
    val theme by vm.theme.collectAsState()
    val language by vm.language.collectAsState()
    val mode by vm.mode.collectAsState()
    Scaffold(topBar = {
        TopAppBar(title = { Text(if (ar) "الإعدادات" else "Settings") },
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            } })
    }) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (ar) "المظهر" else "Appearance", style = MaterialTheme.typography.titleLarge)
            OptionRow(listOf("system", "light", "dark"), theme,
                labels = if (ar) listOf("تلقائي", "فاتح", "داكن") else listOf("System", "Light", "Dark"),
                onChange = vm::setTheme)
            HorizontalDivider()
            Text(if (ar) "اللغة" else "Language", style = MaterialTheme.typography.titleLarge)
            OptionRow(listOf("system", "ar", "en"), language,
                labels = if (ar) listOf("الجهاز", "العربية", "English") else listOf("Device", "Arabic", "English"),
                onChange = vm::setLanguage)
            HorizontalDivider()
            Text(if (ar) "أسلوب المساعدة" else "Assistant mode", style = MaterialTheme.typography.titleLarge)
            OptionRow(listOf("fast", "study", "coding", "deep"), mode,
                labels = listOf(modeTitle("fast", ar), modeTitle("study", ar),
                    modeTitle("coding", ar), modeTitle("deep", ar)), onChange = vm::setMode)
            HorizontalDivider()
            Text(if (ar) "الخصوصية والسجل" else "Privacy and history",
                style = MaterialTheme.typography.titleLarge)
            Text(if (ar) "تُحفظ المحادثات محليًا في قاعدة Room. تُرسل الرسائل إلى الخادم عند الطلب."
                 else "Chats are saved locally in Room. Messages are sent to the configured server for inference.")
            OutlinedButton(onClick = onClear) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (ar) "حذف جميع المحادثات" else "Delete all chats")
            }
            HorizontalDivider()
            Text(if (ar) "الخدمة السحابية" else "Cloud service", style = MaterialTheme.typography.titleLarge)
            Text(if (BuildConfig.API_BASE_URL.isBlank())
                (if (ar) "غير منشورة أو غير مهيأة — لا توجد استجابات تجريبية."
                 else "Unconfigured: no fake AI responses.")
                else (if (ar) "عنوان الخادم محدد أثناء بناء التطبيق."
                      else "Backend URL configured at build time."))
            Text(if (ar) "البحث المباشر وتحليل الصور والملفات وتوليدها غير مفعّلة في هذا الإصدار."
                 else "Web search, image generation, image and document analysis are not enabled in this build.",
                style = MaterialTheme.typography.bodySmall)
            Text("ATOM AI X • v0.2.0", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun OptionRow(values: List<String>, selected: String, labels: List<String>,
                      onChange: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEachIndexed { i, value ->
            FilterChip(selected = value == selected, onClick = { onChange(value) },
                label = { Text(labels[i]) })
        }
    }
}
