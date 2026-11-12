package com.atom.quantum

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val Violet = Color(0xFF7668EF)
private val Teal = Color(0xFF29CAB7)
private val DarkBlue = Color(0xFF0C1021)
private val Pale = Color(0xFFF7F8FD)

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
            val ar = if (language == "system") {
                LocalContext.current.resources.configuration.locales[0].language == "ar"
            } else language == "ar"
            SideEffect {
                window.statusBarColor = if (dark) DarkBlue.toArgb() else Pale.toArgb()
                window.navigationBarColor = if (dark) DarkBlue.toArgb() else Pale.toArgb()
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = !dark
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightNavigationBars = !dark
            }
            val scheme = if (dark) darkColorScheme(
                primary = Color(0xFFB1A8FF),
                onPrimary = DarkBlue,
                primaryContainer = Color(0xFF353065),
                onPrimaryContainer = Color.White,
                background = DarkBlue,
                surface = Color(0xFF141A30),
                surfaceVariant = Color(0xFF222941),
                onSurface = Color(0xFFE9EAF6),
                onSurfaceVariant = Color(0xFFD5D9EE),
                secondary = Teal
            ) else lightColorScheme(
                primary = Color(0xFF5546CE),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFEAE7FF),
                onPrimaryContainer = Color(0xFF1D1A43),
                background = Pale,
                surface = Color.White,
                surfaceVariant = Color(0xFFF0F2FA),
                onSurface = Color(0xFF182038),
                onSurfaceVariant = Color(0xFF48506C),
                secondary = Color(0xFF087A72)
            )
            CompositionLocalProvider(LocalLayoutDirection provides
                (if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
                MaterialTheme(colorScheme = scheme) {
                    AtomApp(vm, ar)
                }
            }
        }
    }
}

@Composable
private fun BrandLogo(modifier: Modifier = Modifier, size: Int = 43) {
    Box(modifier = modifier.size(size.dp)
        .background(Brush.linearGradient(listOf(Violet, Color(0xFF4A4EC2), Teal)),
            RoundedCornerShape((size * .30f).dp)),
        contentAlignment = Alignment.Center) {
        Text("A", color = Color.White, fontSize = (size * .53f).sp,
            fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AtomApp(vm: AtomViewModel, ar: Boolean) {
    val chats by vm.chats.collectAsState()
    val messages by vm.messages.collectAsState()
    val activeId by vm.selectedChat.collectAsState()
    val running by vm.streaming.collectAsState()
    val streamingText by vm.streamingText.collectAsState()
    val streamingId by vm.streamingMessageId.collectAsState()
    val mode by vm.mode.collectAsState()
    val service by vm.serviceState.collectAsState()
    val error by vm.error.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    var input by rememberSaveable { mutableStateOf("") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var renameItem by remember { mutableStateOf<ChatEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var clearConfirmation by remember { mutableStateOf(false) }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val words = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!words.isNullOrBlank()) input = (input + " " + words).trim()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val extracted = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                            val buffer = CharArray(16000)
                            val length = reader.read(buffer)
                            if (length > 0) String(buffer, 0, length) else ""
                        }
                    } catch (_: Exception) { null }
                }
                if (extracted.isNullOrBlank()) {
                    snackbar.showSnackbar(if (ar) "تعذر قراءة الملف النصي" else "Cannot read this text file")
                } else {
                    input = (input + "\n\n" + (if (ar) "نص الملف:\n" else "Document text:\n") +
                        extracted + "\n\n").trim()
                    snackbar.showSnackbar(if (ar) "أُضيف النص إلى الرسالة" else "Document text added")
                }
            }
        }
    }
    val tts = remember { TextToSpeech(context) {} }
    DisposableEffect(tts) { onDispose { tts.stop(); tts.shutdown() } }

    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) {
            snackbar.showSnackbar(error.orEmpty())
            vm.dismissError()
        }
    }
    if (renameItem != null) {
        AlertDialog(onDismissRequest = { renameItem = null },
            icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
            title = { Text(if (ar) "إعادة تسمية المحادثة" else "Rename chat") },
            text = { OutlinedTextField(renameInput, { renameInput = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                renameItem?.let { vm.renameChat(it.id, renameInput) }
                renameItem = null
            }) { Text(if (ar) "حفظ" else "Save") } },
            dismissButton = { TextButton(onClick = { renameItem = null }) {
                Text(if (ar) "إلغاء" else "Cancel")
            } })
    }
    if (clearConfirmation) {
        AlertDialog(onDismissRequest = { clearConfirmation = false },
            title = { Text(if (ar) "حذف جميع المحادثات؟" else "Delete all chats?") },
            text = { Text(if (ar) "سيُحذف سجل المحادثات المحفوظ على هذا الهاتف ولا يمكن استعادته."
                           else "Local chat history will be permanently deleted.") },
            confirmButton = { TextButton(onClick = {
                vm.clearAll()
                clearConfirmation = false
            }) { Text(if (ar) "حذف" else "Delete") } },
            dismissButton = { TextButton(onClick = { clearConfirmation = false }) {
                Text(if (ar) "إلغاء" else "Cancel")
            } })
    }
    if (settings) {
        AtomSettings(vm, ar, onBack = { settings = false },
            onClear = { clearConfirmation = true })
        return
    }

    ModalNavigationDrawer(drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrandLogo(size = 45)
                    Column {
                        Text("ATOM AI X", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold)
                        Text(if (ar) "سجل المحادثات" else "Your conversations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(onClick = { vm.newChat(); scope.launch { drawer.close() } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (ar) "محادثة جديدة" else "New conversation")
                }
                OutlinedTextField(query, { query = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    placeholder = { Text(if (ar) "بحث بعناوين المحادثات" else "Search chat titles") })
                HorizontalDivider()
                LazyColumn(modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 9.dp)) {
                    items(chats.filter { it.title.contains(query, ignoreCase = true) },
                        key = { it.id }) { chat ->
                        var menu by remember { mutableStateOf(false) }
                        val active = activeId == chat.id
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (active) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent)
                            .clickable {
                                vm.openChat(chat.id)
                                scope.launch { drawer.close() }
                            }.padding(start = 9.dp, end = 3.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (chat.pinned) Icons.Default.PushPin else Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null, modifier = Modifier.size(18.dp),
                                tint = if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(chat.title, modifier = Modifier.weight(1f).padding(11.dp),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Box {
                                IconButton(onClick = { menu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text(if (ar) "تغيير الاسم" else "Rename") },
                                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                        onClick = {
                                            renameInput = chat.title; renameItem = chat; menu = false
                                        })
                                    DropdownMenuItem(text = { Text(if (chat.pinned)
                                        (if (ar) "إلغاء التثبيت" else "Unpin")
                                        else (if (ar) "تثبيت" else "Pin")) },
                                        leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                                        onClick = { vm.pinChat(chat.id, !chat.pinned); menu = false })
                                    DropdownMenuItem(text = { Text(if (ar) "حذف" else "Delete") },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                        onClick = { vm.deleteChat(chat.id); menu = false })
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                NavigationDrawerItem(label = { Text(if (ar) "الإعدادات والخصوصية" else "Settings & privacy") },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    selected = false, onClick = {
                        settings = true; scope.launch { drawer.close() }
                    }, modifier = Modifier.padding(10.dp))
            }
        }) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BrandLogo(size = 40)
                            Column {
                                Text("ATOM AI X", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                                Text("QUANTUM INTELLIGENCE", fontSize = 9.sp,
                                    letterSpacing = 1.2.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) {
                            Icon(Icons.Outlined.Menu, contentDescription = if (ar) "القائمة" else "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.newChat() }) {
                            Icon(Icons.Outlined.EditSquare, contentDescription =
                                if (ar) "محادثة جديدة" else "New chat")
                        }
                        IconButton(onClick = { settings = true }) {
                            Icon(Icons.Outlined.Tune, contentDescription =
                                if (ar) "الإعدادات" else "Settings")
                        }
                    }
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(8.dp).background(
                            if (service == "gateway") Teal else if (service == "checking") Violet
                            else Color(0xFFEF8E4C), CircleShape))
                        Text(serviceLabel(service, ar), fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { vm.checkService() }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh service",
                                modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(if (ar) "مساعدك الذكي" else "Your AI assistant",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("fast", "study", "coding", "deep").forEach { choice ->
                        FilterChip(selected = mode == choice, onClick = { vm.setMode(choice) },
                            label = { Text(modeLabel(choice, ar), fontSize = 12.sp) },
                            leadingIcon = if (mode == choice) {
                                { Icon(Icons.Default.Check, contentDescription = null,
                                    modifier = Modifier.size(14.dp)) }
                            } else null,
                            shape = RoundedCornerShape(12.dp))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
                val listState = rememberLazyListState()
                LaunchedEffect(activeId, messages.size, streamingText.length) {
                    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
                }
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (messages.isEmpty()) {
                        item {
                            WelcomeHome(ar, service, onSuggest = { prompt, chosen ->
                                vm.setMode(chosen)
                                input = prompt
                            }, onSettings = { settings = true })
                        }
                    }
                    items(messages, key = { it.id }) { message ->
                        val shown = if (running && message.id == streamingId)
                            streamingText else message.content
                        AtomMessage(
                            user = message.role == "user",
                            markdown = shown, ar = ar,
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("ATOM AI X", shown))
                            },
                            onSpeak = {
                                tts.language = if (ar) Locale("ar") else Locale.ENGLISH
                                tts.speak(shown, TextToSpeech.QUEUE_FLUSH, null, "atom-x")
                            },
                            onShare = {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shown)
                                }, "ATOM AI X"))
                            }
                        )
                    }
                }
                Composer(
                    value = input, onValue = { input = it }, running = running, ar = ar,
                    onSend = { if (vm.send(input)) input = "" },
                    onStop = { vm.stop() },
                    onVoice = {
                        try {
                            speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                                    if (ar) "ar" else "en")
                            })
                        } catch (_: Exception) {
                            scope.launch {
                                snackbar.showSnackbar(if (ar) "التعرف على الصوت غير متاح على الجهاز"
                                    else "Speech recognition is unavailable")
                            }
                        }
                    },
                    onFile = { picker.launch(arrayOf("text/plain", "text/csv", "text/markdown")) }
                )
            }
        }
    }
}

@Composable
private fun WelcomeHome(
    ar: Boolean, service: String,
    onSuggest: (String, String) -> Unit,
    onSettings: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(80.dp).background(
            Brush.linearGradient(listOf(Violet, Color(0xFF4B59D0), Teal)),
            RoundedCornerShape(25.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null,
                modifier = Modifier.size(43.dp), tint = Color.White)
        }
        Spacer(Modifier.height(20.dp))
        Text(if (ar) "أفكارك تبدأ من هنا" else "A space for your next idea",
            fontSize = 26.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(7.dp))
        Text(if (ar) "اسأل، تعلّم، اكتب، وابتكر مع ATOM"
            else "Ask, learn, write and create with ATOM",
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile(Modifier.weight(1f), Icons.Outlined.School,
                if (ar) "افهم درسًا" else "Learn",
                if (ar) "شرح خطوة بخطوة" else "Explain clearly",
                onClick = {
                    onSuggest(if (ar) "اشرح لي مفهوم التهجين في الكيمياء من الصفر مع أمثلة"
                        else "Explain chemical hybridization from basics with examples", "study")
                })
            QuickTile(Modifier.weight(1f), Icons.Outlined.Code,
                if (ar) "اكتب كودًا" else "Build",
                if (ar) "برمجة وحلول" else "Code & debug",
                onClick = {
                    onSuggest(if (ar) "ساعدني في بناء تطبيق Android بلغة Kotlin"
                        else "Help me build an Android app with Kotlin", "coding")
                })
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile(Modifier.weight(1f), Icons.Outlined.Translate,
                if (ar) "ترجمة" else "Translate",
                if (ar) "عربي وإنجليزي" else "Arabic & English",
                onClick = {
                    onSuggest(if (ar) "ترجم النص التالي إلى الإنجليزية ووضح المصطلحات: "
                        else "Translate the following into Arabic and explain key terms: ", "fast")
                })
            QuickTile(Modifier.weight(1f), Icons.Outlined.Lightbulb,
                if (ar) "فكّر بعمق" else "Reason",
                if (ar) "حلّل فكرة أو مسألة" else "Analyze an idea",
                onClick = {
                    onSuggest(if (ar) "حلّل هذه المسألة خطوة بخطوة: "
                        else "Analyze this problem step by step: ", "deep")
                })
        }
        if (service != "gateway") {
            Spacer(Modifier.height(24.dp))
            Surface(shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CloudOff, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (ar) "الذكاء الاصطناعي غير متصل" else "AI is not connected",
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(if (ar) "الواجهة تعمل، لكن المحادثة تحتاج خدمة سحابية منشورة."
                            else "Chat requires a deployed inference service.",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onSettings) {
                        Text(if (ar) "تفاصيل" else "Details", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTile(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector,
                      title: String, subtitle: String, onClick: () -> Unit) {
    Surface(modifier = modifier.height(113.dp).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(19.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .38f)),
        tonalElevation = 1.dp) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp))
            Column {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Composer(value: String, onValue: (String) -> Unit,
                     running: Boolean, ar: Boolean, onSend: () -> Unit,
                     onStop: () -> Unit, onVoice: () -> Unit, onFile: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 7.dp,
        color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 9.dp)) {
            Row(verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                IconButton(onClick = onFile, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Outlined.AttachFile, contentDescription =
                        if (ar) "إضافة ملف نصي" else "Attach text file")
                }
                IconButton(onClick = onVoice, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Outlined.Mic, contentDescription =
                        if (ar) "إدخال صوتي" else "Voice input")
                }
                OutlinedTextField(value, onValue, modifier = Modifier.weight(1f),
                    minLines = 1, maxLines = 5,
                    placeholder = { Text(if (ar) "اكتب سؤالك هنا..." else "Ask anything...",
                        fontSize = 13.sp) },
                    shape = RoundedCornerShape(20.dp),
                    textStyle = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(2.dp))
                FilledIconButton(onClick = if (running) onStop else onSend,
                    enabled = running || value.isNotBlank(),
                    modifier = Modifier.size(48.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(if (running) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (running) "Stop" else "Send")
                }
            }
            Text(if (ar) "الملفات المدعومة حاليًا: TXT وCSV فقط"
                 else "Current file support: TXT and CSV only",
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp, start = 6.dp, end = 6.dp))
        }
    }
}

@Composable
private fun AtomMessage(user: Boolean, markdown: String, ar: Boolean,
                        onCopy: () -> Unit, onSpeak: () -> Unit, onShare: () -> Unit) {
    val context = LocalContext.current
    val textColor = if (user) MaterialTheme.colorScheme.onPrimaryContainer.toArgb()
        else MaterialTheme.colorScheme.onSurface.toArgb()
    val markwon = remember(context) {
        Markwon.builder(context).usePlugin(TablePlugin.create(context)).build()
    }
    Column(Modifier.fillMaxWidth(),
        horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.padding(bottom = 7.dp)) {
            if (!user) BrandLogo(size = 23)
            Text(if (user) (if (ar) "أنت" else "You") else "ATOM",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Surface(shape = RoundedCornerShape(18.dp),
            color = if (user) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(if (user) .91f else 1f),
            tonalElevation = if (user) 0.dp else 1.dp) {
            if (markdown.isBlank()) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(if (ar) "جارٍ إعداد الرد..." else "Generating response...")
                }
            } else {
                AndroidView(factory = {
                    TextView(it).apply {
                        setTextIsSelectable(true)
                        setPadding(20, 20, 20, 20)
                        textSize = 16f
                    }
                }, update = { textView ->
                    textView.setTextColor(textColor)
                    markwon.setMarkdown(textView, markdown)
                }, modifier = Modifier.fillMaxWidth())
            }
        }
        if (!user && markdown.isNotEmpty()) {
            Row(Modifier.padding(top = 3.dp)) {
                IconButton(onClick = onCopy, modifier = Modifier.size(37.dp)) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy",
                        modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onSpeak, modifier = Modifier.size(37.dp)) {
                    Icon(Icons.Outlined.VolumeUp, contentDescription = "Read aloud",
                        modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = onShare, modifier = Modifier.size(37.dp)) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share",
                        modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun modeLabel(mode: String, ar: Boolean): String = when (mode) {
    "study" -> if (ar) "التعلّم" else "Study"
    "coding" -> if (ar) "البرمجة" else "Coding"
    "deep" -> if (ar) "التحليل" else "Analysis"
    else -> if (ar) "سريع" else "Fast"
}

private fun serviceLabel(state: String, ar: Boolean): String = when (state) {
    "gateway" -> if (ar) "الخادم يستجيب" else "Gateway reachable"
    "checking" -> if (ar) "فحص الاتصال..." else "Checking connection..."
    "offline" -> if (ar) "الخادم لا يستجيب" else "Gateway offline"
    else -> if (ar) "المحرك غير مُفعّل" else "AI not configured"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AtomSettings(vm: AtomViewModel, ar: Boolean,
                         onBack: () -> Unit, onClear: () -> Unit) {
    val theme by vm.theme.collectAsState()
    val language by vm.language.collectAsState()
    val mode by vm.mode.collectAsState()
    val service by vm.serviceState.collectAsState()
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text(if (ar) "الإعدادات" else "Settings") },
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (ar) "رجوع" else "Back")
            } }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            SettingsTitle(Icons.Outlined.Palette, if (ar) "المظهر" else "Appearance")
            ChoiceRow(listOf("system", "light", "dark"),
                if (ar) listOf("تلقائي", "فاتح", "داكن")
                else listOf("System", "Light", "Dark"), theme, vm::setTheme)
            HorizontalDivider()
            SettingsTitle(Icons.Outlined.Language, if (ar) "اللغة" else "Language")
            ChoiceRow(listOf("system", "ar", "en"),
                if (ar) listOf("الجهاز", "العربية", "English")
                else listOf("Device", "Arabic", "English"), language, vm::setLanguage)
            HorizontalDivider()
            SettingsTitle(Icons.Outlined.AutoAwesome, if (ar) "أسلوب المساعدة" else "Assistant mode")
            ChoiceRow(listOf("fast", "study", "coding", "deep"),
                listOf(modeLabel("fast", ar), modeLabel("study", ar),
                    modeLabel("coding", ar), modeLabel("deep", ar)), mode, vm::setMode)
            HorizontalDivider()
            SettingsTitle(Icons.Outlined.Cloud, if (ar) "اتصال الذكاء الاصطناعي" else "AI connection")
            Text(serviceLabel(service, ar), fontWeight = FontWeight.SemiBold)
            Text(if (ar) "يجب أن يجهّز مالك التطبيق خدمة استدلال آمنة قبل عمل المحادثة. "
                + "لا يحتاج مستخدم التطبيق إلى مفاتيح أو إعدادات خوادم."
                else "The app operator must deploy an inference gateway. "
                + "End users do not enter API keys or server addresses.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { vm.checkService() }) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (ar) "فحص الاتصال" else "Check connection")
            }
            HorizontalDivider()
            SettingsTitle(Icons.Outlined.Lock, if (ar) "الخصوصية والسجل" else "Privacy & history")
            Text(if (ar) "المحادثات محفوظة على هذا الهاتف. عند إرسال رسالة "
                + "يُنقل سياقها إلى خدمة الاستدلال المحددة."
                else "Chats are stored on this phone. When you send a message, "
                + "its context is transferred to the configured inference provider.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onClear) {
                Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (ar) "حذف سجل المحادثات" else "Delete chat history")
            }
            HorizontalDivider()
            Text(if (ar) "الميزات غير المتاحة في هذا الإصدار: تحليل PDF والصور، "
                + "البحث المباشر وتوليد الصور."
                else "Not included yet: PDF and image analysis, live web search and image generation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("ATOM AI X  ·  0.3.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ChoiceRow(options: List<String>, labels: List<String>,
                      selection: String, set: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, option ->
            FilterChip(selected = option == selection, onClick = { set(option) },
                label = { Text(labels[i], fontSize = 12.sp) },
                shape = RoundedCornerShape(12.dp))
        }
    }
}
