package com.example.ui

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.Translations
import com.example.gemini.GeminiClient
import com.example.viewmodel.AdbViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke

data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val modelId: String? = null,
    val attachedUris: List<String> = emptyList(),
    val generatedMedia: com.example.media.GeneratedMediaItem? = null
)

sealed class MessageContentBlock {
    data class TextBlock(val text: String) : MessageContentBlock()
    data class CodeBlock(val language: String, val code: String) : MessageContentBlock()
}

fun parseMessageContent(text: String): List<MessageContentBlock> {
    val blocks = mutableListOf<MessageContentBlock>()
    val parts = text.split("```")
    for (i in parts.indices) {
        val part = parts[i]
        if (i % 2 == 1) {
            val lines = part.split("\n", limit = 2)
            val firstLine = lines.getOrNull(0)?.trim() ?: ""
            val knownLanguages = listOf(
                "bash", "sh", "shell", "python", "py", "javascript", "js", "typescript", "ts",
                "kotlin", "kt", "java", "html", "css", "json", "xml", "yaml", "yml", "sql", "c", "cpp"
            )
            val hasLanguage = knownLanguages.contains(firstLine.lowercase()) || (firstLine.isNotEmpty() && !firstLine.contains(" ") && firstLine.length < 15)
            val language = if (hasLanguage) firstLine else ""
            val code = if (hasLanguage && lines.size > 1) lines[1] else part
            
            blocks.add(MessageContentBlock.CodeBlock(language, code.trim()))
        } else {
            if (part.isNotEmpty()) {
                blocks.add(MessageContentBlock.TextBlock(part))
            }
        }
    }
    return blocks
}

@Composable
fun getAnnotatedCode(code: String, language: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        val trimmedLang = language.lowercase()
        append(code)

        val keywordColor = Color(0xFFC678DD) // Purple for actual keywords
        val typeColor = Color(0xFFE5C07B) // Yellowish/Gold for classes / types
        val functionColor = Color(0xFF61AFEF) // Blue for functions
        val stringColor = Color(0xFF98C379) // Green for strings
        val numberColor = Color(0xFFD19A66) // Orange for numbers
        val commentColor = Color(0xFF5C6370) // Gray for comments

        val keywords = when (trimmedLang) {
            "kotlin", "kt", "java" -> listOf("package", "import", "class", "interface", "fun", "val", "var", "return", "if", "else", "when", "for", "while", "null", "true", "false", "private", "public", "protected", "override", "this", "super", "try", "catch", "throw")
            "python", "py" -> listOf("def", "import", "from", "class", "return", "if", "elif", "else", "for", "while", "in", "is", "not", "and", "or", "try", "except", "lambda", "None", "True", "False", "as", "with")
            "bash", "sh", "shell" -> listOf("sudo", "echo", "if", "then", "else", "fi", "for", "in", "do", "done", "exit", "return", "alias", "export", "local", "adb", "shell", "cat", "grep", "ls", "cd", "python3")
            "javascript", "js", "typescript", "ts" -> listOf("const", "let", "var", "function", "return", "if", "else", "for", "while", "class", "export", "import", "from", "null", "undefined", "true", "false", "this", "async", "await", "new")
            else -> listOf("adb", "shell", "sudo", "git", "npm", "gradle")
        }

        // 1. Strings
        val stringRegex = "\"[^\"]*\"|'[^']*'".toRegex()
        stringRegex.findAll(code).forEach { match ->
            addStyle(
                androidx.compose.ui.text.SpanStyle(color = stringColor),
                match.range.first,
                match.range.last + 1
            )
        }

        // 2. Numbers
        val numberRegex = "\\b\\d+(\\.\\d+)?\\b".toRegex()
        numberRegex.findAll(code).forEach { match ->
            addStyle(
                androidx.compose.ui.text.SpanStyle(color = numberColor),
                match.range.first,
                match.range.last + 1
            )
        }

        // 3. Types / Capitalized Classes
        val typeRegex = "\\b[A-Z]\\w*\\b".toRegex()
        typeRegex.findAll(code).forEach { match ->
            addStyle(
                androidx.compose.ui.text.SpanStyle(color = typeColor),
                match.range.first,
                match.range.last + 1
            )
        }

        // 4. Function Calls
        val functionRegex = "\\b(\\w+)\\s*(?=\\()".toRegex()
        functionRegex.findAll(code).forEach { match ->
            addStyle(
                androidx.compose.ui.text.SpanStyle(color = functionColor),
                match.range.first,
                match.range.last + 1
            )
        }

        // 5. Keywords
        keywords.forEach { keyword ->
            val keywordRegex = "\\b$keyword\\b".toRegex()
            keywordRegex.findAll(code).forEach { match ->
                addStyle(
                    androidx.compose.ui.text.SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold),
                    match.range.first,
                    match.range.last + 1
                )
            }
        }

        // 6. Comments (applied last to overwrite nested items)
        val commentRegex = if (trimmedLang == "kotlin" || trimmedLang == "java" || trimmedLang == "js" || trimmedLang == "ts") {
            "//.*|/\\*[\\s\\S]*?\\*/".toRegex()
        } else {
            "#.*".toRegex()
        }
        commentRegex.findAll(code).forEach { match ->
            addStyle(
                androidx.compose.ui.text.SpanStyle(color = commentColor, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                match.range.first,
                match.range.last + 1
            )
        }
    }
}

@Composable
fun CodeBlockView(
    language: String,
    code: String,
    textColor: Color,
    surfaceColor: Color,
    primaryColor: Color
) {
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            kotlinx.coroutines.delay(2000)
            isCopied = false
        }
    }

    val displayLanguage = if (language.isNotEmpty()) language.uppercase() else "CODE"
    val languageTagBg = when (displayLanguage) {
        "BASH", "SH", "SHELL" -> Color(0xFF27C93F).copy(alpha = 0.15f)
        "KOTLIN", "KT" -> Color(0xFF7F52FF).copy(alpha = 0.15f)
        "PYTHON", "PY" -> Color(0xFF3776AB).copy(alpha = 0.15f)
        "JS", "JAVASCRIPT", "TS", "TYPESCRIPT" -> Color(0xFFF7DF1E).copy(alpha = 0.15f)
        else -> Color.White.copy(alpha = 0.1f)
    }
    val languageTagText = when (displayLanguage) {
        "BASH", "SH", "SHELL" -> Color(0xFF27C93F)
        "KOTLIN", "KT" -> Color(0xFF9F7DFF)
        "PYTHON", "PY" -> Color(0xFF458AF1)
        "JS", "JAVASCRIPT", "TS", "TYPESCRIPT" -> Color(0xFFF7DF1E)
        else -> Color.White.copy(alpha = 0.7f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D2D), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(languageTagText, CircleShape)
                )
                Text(
                    text = displayLanguage,
                    color = languageTagText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(languageTagBg, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Copied Code", code)
                        clipboard.setPrimaryClip(clip)
                        isCopied = true
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy Code",
                    tint = if (isCopied) Color(0xFF27C93F) else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = if (isCopied) {
                        when (context.resources.configuration.locales[0].language) {
                            "uz" -> "Nusxa olindi!"
                            "ru" -> "Скопировано!"
                            else -> "Copied!"
                        }
                    } else {
                        when (context.resources.configuration.locales[0].language) {
                            "uz" -> "Nusxa olish"
                            "ru" -> "Копировать"
                            else -> "Copy"
                        }
                    },
                    color = if (isCopied) Color(0xFF27C93F) else Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    text = getAnnotatedCode(code, language),
                    fontSize = 12.sp,
                    color = Color(0xFFABB2BF),
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

data class AiThemeColors(
    val primary: Color,
    val surface: Color,
    val bg: Color,
    val text: Color,
    val accent: Color
)

data class AiSession(
    val sessionId: String,
    val title: String,
    val messages: List<ChatMessage>,
    val timestamp: Long = System.currentTimeMillis()
)

fun saveAiSessions(context: Context, sessions: List<AiSession>) {
    try {
        val jsonArray = org.json.JSONArray()
        val safeSessions = sessions.take(30)
        for (session in safeSessions) {
            val sessionObj = org.json.JSONObject().apply {
                put("sessionId", session.sessionId)
                put("title", session.title)
                put("timestamp", session.timestamp)
                
                val msgsArray = org.json.JSONArray()
                val safeMessages = session.messages.takeLast(80)
                for (msg in safeMessages) {
                    val msgObj = org.json.JSONObject().apply {
                        put("id", msg.id)
                        put("text", msg.text)
                        put("isUser", msg.isUser)
                        put("timestamp", msg.timestamp)
                        msg.modelId?.let { put("modelId", it) }
                        if (msg.attachedUris.isNotEmpty()) {
                            val urisArray = org.json.JSONArray()
                            msg.attachedUris.forEach { urisArray.put(it) }
                            put("attachedUris", urisArray)
                        }
                        msg.generatedMedia?.let { media ->
                            val mediaObj = org.json.JSONObject().apply {
                                put("id", media.id)
                                put("type", media.type)
                                put("title", media.title)
                                put("uriOrUrl", media.uriOrUrl)
                                media.localFilePath?.let { put("localFilePath", it) }
                                media.mimeType?.let { put("mimeType", it) }
                                media.fileSizeFormatted?.let { put("fileSizeFormatted", it) }
                                media.description?.let { put("description", it) }
                                media.fileContentPreview?.let { put("fileContentPreview", it.take(1000)) }
                            }
                            put("generatedMedia", mediaObj)
                        }
                    }
                    msgsArray.put(msgObj)
                }
                put("messages", msgsArray)
            }
            jsonArray.put(sessionObj)
        }
        context.openFileOutput("ai_chat_history.json", Context.MODE_PRIVATE).use {
            it.write(jsonArray.toString().toByteArray())
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun loadAiSessions(context: Context): List<AiSession> {
    val file = java.io.File(context.filesDir, "ai_chat_history.json")
    if (!file.exists()) return emptyList()
    try {
        val content = file.readText()
        val jsonArray = org.json.JSONArray(content)
        val sessions = mutableListOf<AiSession>()
        for (i in 0 until jsonArray.length()) {
            val sessionObj = jsonArray.getJSONObject(i)
            val sessionId = sessionObj.getString("sessionId")
            val title = sessionObj.getString("title")
            val timestamp = sessionObj.getLong("timestamp")
            
            val msgsArray = sessionObj.getJSONArray("messages")
            val messages = mutableListOf<ChatMessage>()
            for (j in 0 until msgsArray.length()) {
                val msgObj = msgsArray.getJSONObject(j)
                val mediaItem = if (msgObj.has("generatedMedia")) {
                    val m = msgObj.getJSONObject("generatedMedia")
                    com.example.media.GeneratedMediaItem(
                        id = if (m.has("id")) m.getString("id") else java.util.UUID.randomUUID().toString(),
                        type = m.getString("type"),
                        title = m.getString("title"),
                        uriOrUrl = m.getString("uriOrUrl"),
                        localFilePath = if (m.has("localFilePath")) m.getString("localFilePath") else null,
                        mimeType = if (m.has("mimeType")) m.getString("mimeType") else null,
                        fileSizeFormatted = if (m.has("fileSizeFormatted")) m.getString("fileSizeFormatted") else null,
                        description = if (m.has("description")) m.getString("description") else null,
                        fileContentPreview = if (m.has("fileContentPreview")) m.getString("fileContentPreview") else null
                    )
                } else null

                messages.add(
                    ChatMessage(
                        id = msgObj.getString("id"),
                        text = msgObj.getString("text"),
                        isUser = msgObj.getBoolean("isUser"),
                        timestamp = msgObj.getLong("timestamp"),
                        modelId = if (msgObj.has("modelId")) msgObj.getString("modelId") else null,
                        attachedUris = if (msgObj.has("attachedUris")) {
                            val urisArray = msgObj.getJSONArray("attachedUris")
                            List(urisArray.length()) { urisArray.getString(it) }
                        } else emptyList(),
                        generatedMedia = mediaItem
                    )
                )
            }
            sessions.add(AiSession(sessionId, title, messages, timestamp))
        }
        return sessions
    } catch (e: Exception) {
        e.printStackTrace()
        return emptyList()
    }
}

fun getFileName(context: Context, uri: android.net.Uri): String {
    var name = "Attached File"
    if (uri.scheme == "file") {
        return uri.lastPathSegment ?: "Attached File"
    }
    try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return name
}

fun openUriSafely(context: Context, uri: android.net.Uri, mimeType: String) {
    try {
        val finalUri = if (uri.scheme == "file") {
            val file = java.io.File(uri.path ?: "")
            if (file.exists()) {
                androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                uri
            }
        } else {
            uri
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(finalUri, if (mimeType.isNotEmpty()) mimeType else "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        (context as? com.example.MainActivity)?.isBypassingLock = true
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Faylni ochish uchun mos ilova topilmadi", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAssistantDialog(
    viewModel: AdbViewModel,
    terminalTheme: String,
    selectedLanguage: String,
    onDismiss: () -> Unit,
    onTriggerFilePicker: ((List<android.net.Uri>) -> Unit) -> Unit,
    onExecuteAction: (actionName: String, args: Map<String, Any>) -> Unit,
    isAiFullScreen: Boolean,
    onAiFullScreenChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    val safeDismiss = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onDismiss()
    }

    var currentSessionId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var selectionResetKey by remember { mutableStateOf(0) }
    val sessionDrafts = remember { mutableStateMapOf<String, String>() }
    val promptInput = sessionDrafts[currentSessionId] ?: ""
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    var isLoading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    
    // Attached files list
    val attachedFiles = remember { mutableStateListOf<android.net.Uri>() }
    
    // Stored chat history sessions list
    val savedSessions = remember { mutableStateListOf<AiSession>() }
    var showHistoryDropdown by remember { mutableStateOf(false) }
    var historySearchQuery by remember { mutableStateOf("") }
    var showInternalFilePicker by remember { mutableStateOf(false) }
    var activeActionMessageId by remember { mutableStateOf<String?>(null) }
    var showModelDropdown by remember { mutableStateOf(false) }
    val selectedModelId by viewModel.selectedGeminiModel.collectAsState()
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var previewCodeDialogData by remember { mutableStateOf<Pair<String, String>?>(null) }
    var fullScreenVideoUrl by remember { mutableStateOf<String?>(null) }
    var showBytezKeyDialog by remember { mutableStateOf(false) }
    var bytezKeyInput by remember { mutableStateOf("") }

    // Retrieve theme colors based on active theme
    val themeColors = when (terminalTheme) {
        "ubuntu" -> AiThemeColors(Color(0xFFE95420), Color(0xFF381426), Color(0xFF2C001E), Color.White, Color(0xFFE95420))
        "matrix" -> AiThemeColors(Color(0xFF33FF33), Color(0xFF002200), Color(0xFF001100), Color(0xFF33FF33), Color(0xFF00FF00))
        "cyberpunk" -> AiThemeColors(Color(0xFFFF007F), Color(0xFF230D3E), Color(0xFF120422), Color.White, Color(0xFF7E00FF))
        "monochrome" -> AiThemeColors(Color(0xFFFFFFFF), Color(0xFF1E1E1E), Color(0xFF000000), Color.White, Color.White)
        "light" -> AiThemeColors(Color(0xFF1E293B), Color(0xFFE2E8F0), Color(0xFFF1F5F9), Color(0xFF1E293B), Color(0xFF64748B))
        else -> AiThemeColors(Color(0xFFE95420), Color(0xFF381426), Color(0xFF2C001E), Color.White, Color(0xFFE95420))
    }
    val primaryColor = themeColors.primary
    val surfaceColor = themeColors.surface
    val backgroundColor = themeColors.bg
    val textColor = themeColors.text
    val accentColor = themeColors.accent

    // Load history once at start
    LaunchedEffect(Unit) {
        savedSessions.clear()
        savedSessions.addAll(loadAiSessions(context))
    }

    val isKeyboardVisible = WindowInsets.isImeVisible
    LaunchedEffect(isKeyboardVisible) {
        if (!isKeyboardVisible) {
            focusManager.clearFocus()
        }
    }

    val welcomeText = when (selectedLanguage) {
        "uz" -> "Assalomu alaykum! Sizga qanday yordam bera olaman?"
        "ru" -> "Здравствуйте! Чем я могу помочь вам?"
        else -> "Hello! How can I help you today?"
    }

    // Manage messages and switch loaded session
    LaunchedEffect(currentSessionId) {
        val matchingSession = savedSessions.find { it.sessionId == currentSessionId }
        chatMessages.clear()
        if (matchingSession != null) {
            chatMessages.addAll(matchingSession.messages)
        } else {
            chatMessages.add(ChatMessage(id = "welcome", text = welcomeText, isUser = false))
        }
    }

    // Save session in JSON
    fun saveCurrentSessionToHistory() {
        if (chatMessages.size <= 1) return
        
        val firstUserMsg = chatMessages.firstOrNull { it.isUser }?.text ?: "Terminal AI Chat"
        val sessionTitle = if (firstUserMsg.length > 25) firstUserMsg.take(25) + "..." else firstUserMsg
        
        val newSession = AiSession(
            sessionId = currentSessionId,
            title = sessionTitle,
            messages = chatMessages.toList(),
            timestamp = System.currentTimeMillis()
        )
        
        val index = savedSessions.indexOfFirst { it.sessionId == currentSessionId }
        if (index != -1) {
            savedSessions[index] = newSession
        } else {
            savedSessions.add(0, newSession)
        }
        saveAiSessions(context, savedSessions.toList())
    }

    fun handleSendPrompt(textToSend: String) {
        if (textToSend.isBlank() && attachedFiles.isEmpty() || isLoading) return
        val rawPrompt = textToSend.trim()
        sessionDrafts[currentSessionId] = ""
        focusManager.clearFocus()
        keyboardController?.hide()

        // scroll to bottom after layout/keyboard changes
        scope.launch {
            kotlinx.coroutines.delay(100)
            if (chatMessages.isNotEmpty()) {
                listState.animateScrollToItem(chatMessages.size - 1)
            }
        }

        // Handle attached files
        var currentPrompt = rawPrompt
        val urisToProcess = attachedFiles.toList()
        if (attachedFiles.isNotEmpty()) {
            attachedFiles.clear()
        }

        // Add user message
        chatMessages.add(ChatMessage(
            id = UUID.randomUUID().toString(), 
            text = currentPrompt, 
            isUser = true,
            attachedUris = urisToProcess.map { it.toString() }
        ))
        
        // Add thinking message placeholder so loading animation is visible immediately
        chatMessages.add(ChatMessage(
            id = "thinking",
            text = when (selectedLanguage) {
                "uz" -> "O'ylanmoqda..."
                "ru" -> "Думаю..."
                else -> "Thinking..."
            },
            isUser = false,
            modelId = selectedModelId
        ))
        saveCurrentSessionToHistory()

        isLoading = true
        statusText = ""

        val historyList = mutableListOf<Pair<String, String>>()
        val recentMsgs = chatMessages.drop(1).takeLast(6)
        var tempUserMsg = ""
        for (msg in recentMsgs) {
            if (msg.isUser) {
                tempUserMsg = msg.text
            } else {
                if (tempUserMsg.isNotEmpty()) {
                    historyList.add(tempUserMsg to msg.text)
                    tempUserMsg = ""
                }
            }
        }

        scope.launch {
            try {
                // Read files in background dispatcher
                val attachedDataList = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    urisToProcess.map { uri ->
                        com.example.gemini.AttachedFileData.readFromUri(context, uri)
                    }
                }

                var actualPrompt = rawPrompt
                if (actualPrompt.isBlank() && attachedDataList.isNotEmpty()) {
                    actualPrompt = when (selectedLanguage) {
                        "uz" -> "Ushbu ilova qilingan fayllarni tahlil qiling va batafsil tushuntirib bering."
                        "ru" -> "Проанализируйте эти прикрепленные файлы и подробно опишите их содержимое."
                        else -> "Analyze these attached files and explain them in detail."
                    }
                }

                val devInfoStr = viewModel.deviceInfo.value.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                
                val responseId = UUID.randomUUID().toString()
                
                val response = GeminiClient.generateContent(
                    prompt = actualPrompt,
                    chatHistory = historyList,
                    currentLanguage = selectedLanguage,
                    deviceInfo = devInfoStr,
                    attachedFiles = attachedDataList,
                    modelId = selectedModelId,
                    onUpdate = { newText ->
                        isLoading = false
                        val idx = chatMessages.indexOfFirst { it.id == responseId }
                        if (idx != -1) {
                            chatMessages[idx] = chatMessages[idx].copy(text = newText)
                        } else {
                            val thinkingIdx = chatMessages.indexOfFirst { it.id == "thinking" }
                            if (thinkingIdx != -1) {
                                chatMessages[thinkingIdx] = ChatMessage(id = responseId, text = newText, isUser = false, modelId = selectedModelId)
                            } else {
                                chatMessages.add(ChatMessage(id = responseId, text = newText, isUser = false, modelId = selectedModelId))
                            }
                        }
                    }
                )

                isLoading = false
                statusText = ""

                var generatedMediaItem: com.example.media.GeneratedMediaItem? = null

                if (response.toolCalls.isNotEmpty()) {
                    for (tool in response.toolCalls) {
                        when (tool.name) {
                            "generate_image" -> {
                                statusText = when (selectedLanguage) {
                                    "uz" -> "Rasm yaratilmoqda..."
                                    "ru" -> "Создание изображения..."
                                    else -> "Generating image..."
                                }
                                val p = tool.args["prompt"]?.toString() ?: actualPrompt
                                val t = tool.args["title"]?.toString() ?: "AI Tasviri"
                                try {
                                    generatedMediaItem = com.example.media.MediaGenerationManager.generateImage(context, p, t)
                                } catch (e: Exception) {
                                    Log.e("AiDialog", "Image gen error", e)
                                }
                            }
                            "generate_audio" -> {
                                statusText = when (selectedLanguage) {
                                    "uz" -> "Audio yozilmoqda..."
                                    "ru" -> "Синтез аудио..."
                                    else -> "Generating audio..."
                                }
                                val t = tool.args["title"]?.toString() ?: "AI Musiqasi"
                                val type = tool.args["type"]?.toString() ?: "melody"
                                val p = tool.args["prompt"]?.toString() ?: actualPrompt
                                try {
                                    generatedMediaItem = com.example.media.MediaGenerationManager.generateAudio(context, t, type, p)
                                } catch (e: Exception) {
                                    Log.e("AiDialog", "Audio gen error", e)
                                }
                            }
                            "generate_video" -> {
                                statusText = when (selectedLanguage) {
                                    "uz" -> "Video tayyorlanmoqda..."
                                    "ru" -> "Создание видео..."
                                    else -> "Generating video..."
                                }
                                val t = tool.args["title"]?.toString() ?: "AI Videosi"
                                val p = tool.args["prompt"]?.toString() ?: actualPrompt
                                val s = tool.args["style"]?.toString() ?: "matrix"
                                try {
                                    generatedMediaItem = com.example.media.MediaGenerationManager.generateVideo(context, t, p, s)
                                } catch (e: Exception) {
                                    Log.e("AiDialog", "Video gen error", e)
                                }
                            }
                            "create_file" -> {
                                statusText = when (selectedLanguage) {
                                    "uz" -> "Fayl yaratilmoqda..."
                                    "ru" -> "Создание файла..."
                                    else -> "Creating file..."
                                }
                                val fn = tool.args["filename"]?.toString() ?: "file_${System.currentTimeMillis()}.txt"
                                val cnt = tool.args["content"]?.toString() ?: ""
                                val d = tool.args["description"]?.toString() ?: "AI Fayli"
                                try {
                                    generatedMediaItem = com.example.media.MediaGenerationManager.createFile(context, fn, cnt, d)
                                } catch (e: Exception) {
                                    Log.e("AiDialog", "File create error", e)
                                }
                            }
                            "search_web" -> {
                                statusText = when (selectedLanguage) {
                                    "uz" -> "Internetdan qidirilmoqda..."
                                    "ru" -> "Поиск в интернете..."
                                    else -> "Searching the web..."
                                }
                                val q = tool.args["query"]?.toString() ?: actualPrompt
                                try {
                                    val searchResp = com.example.net.WebSearchEngine.performSearch(q)
                                    if (searchResp.results.isNotEmpty()) {
                                        val searchAppendText = "\n\n" + searchResp.summaryText
                                        val targetMsgIdx = chatMessages.indexOfFirst { it.id == responseId }
                                        if (targetMsgIdx != -1) {
                                            val oldText = chatMessages[targetMsgIdx].text
                                            chatMessages[targetMsgIdx] = chatMessages[targetMsgIdx].copy(text = oldText + searchAppendText)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("AiDialog", "Web search tool error", e)
                                }
                            }
                            else -> {
                                statusText = when (selectedLanguage) {
                                    "uz" -> "Amal bajarilmoqda: ${tool.name}"
                                    "ru" -> "Выполнение действия: ${tool.name}"
                                    else -> "Executing: ${tool.name}"
                                }
                                onExecuteAction(tool.name, tool.args)
                            }
                        }
                    }
                }

                // Automatic Web Search Fallback if user explicitly requested web search or recent news/facts:
                val pLower = actualPrompt.lowercase()
                val isExplicitWebSearch = pLower.contains("internetdan qidir") || pLower.contains("google dan qidir") ||
                        pLower.contains("webda qidir") || pLower.contains("qidirib ber") || pLower.contains("search web") ||
                        pLower.contains("find on web") || pLower.contains("поищи в интернете") || pLower.contains("найди в гугле") ||
                        pLower.contains("yangiliklar") || pLower.contains("bugun nima yangilik")

                if (isExplicitWebSearch && response.toolCalls.none { it.name == "search_web" }) {
                    statusText = when (selectedLanguage) {
                        "uz" -> "Internetdan qidirilmoqda..."
                        "ru" -> "Поиск в интернете..."
                        else -> "Searching the web..."
                    }
                    try {
                        val searchResp = com.example.net.WebSearchEngine.performSearch(actualPrompt)
                        if (searchResp.results.isNotEmpty()) {
                            val searchAppendText = "\n\n" + searchResp.summaryText
                            val targetMsgIdx = chatMessages.indexOfFirst { it.id == responseId }
                            if (targetMsgIdx != -1) {
                                val oldText = chatMessages[targetMsgIdx].text
                                if (!oldText.contains("Internetdan qidiruv natijalari")) {
                                    chatMessages[targetMsgIdx] = chatMessages[targetMsgIdx].copy(text = oldText + searchAppendText)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AiDialog", "Explicit web search fallback error", e)
                    }
                }

                // Smart Intent Fallback: If AI did not call a tool, but user explicitly asked to create image/audio/video/file:
                if (generatedMediaItem == null) {
                    val pLower = actualPrompt.lowercase()
                    when {
                        pLower.contains("rasm") || pLower.contains("surat") || pLower.contains("tasvir") ||
                        pLower.contains("chizib ber") || pLower.contains("chiz") || pLower.contains("image") || pLower.contains("draw") ||
                        pLower.contains("picture") || pLower.contains("photo") || pLower.contains("нарисуй") ||
                        pLower.contains("создай картинку") || pLower.contains("создай фото") -> {
                            statusText = when (selectedLanguage) {
                                "uz" -> "Rasm yaratilmoqda..."
                                "ru" -> "Создание изображения..."
                                else -> "Generating image..."
                            }
                            try {
                                generatedMediaItem = com.example.media.MediaGenerationManager.generateImage(context, actualPrompt, "AI Tasviri")
                            } catch (e: Exception) {
                                Log.e("AiDialog", "Fallback image gen error", e)
                            }
                        }
                        pLower.contains("musiqa") || pLower.contains("audio") || pLower.contains("kuy") ||
                        pLower.contains("ovoz") || pLower.contains("gapir") || pLower.contains("so'z") ||
                        pLower.contains("nutq") || pLower.contains("ayt") || pLower.contains("music") ||
                        pLower.contains("sound") || pLower.contains("song") || pLower.contains("speech") ||
                        pLower.contains("voice") || pLower.contains("музыка") || pLower.contains("аудио") ||
                        pLower.contains("голос") || pLower.contains("озвучь") || pLower.contains("скажи") -> {
                            statusText = when (selectedLanguage) {
                                "uz" -> "Audio sintez qilinmoqda..."
                                "ru" -> "Синтез аудио..."
                                else -> "Synthesizing audio..."
                            }
                            val audioType = when {
                                pLower.contains("doira") || pLower.contains("maqom") || pLower.contains("sato") || pLower.contains("uzbek") -> "uzbek_national"
                                pLower.contains("tabiat") || pLower.contains("yomg'ir") || pLower.contains("suv") -> "nature"
                                pLower.contains("cyber") || pLower.contains("8bit") || pLower.contains("retro") -> "cyberpunk"
                                pLower.contains("ambient") || pLower.contains("sokin") || pLower.contains("fon") -> "ambient"
                                pLower.contains("ritm") || pLower.contains("beat") -> "beats"
                                else -> "melody"
                            }
                            try {
                                generatedMediaItem = com.example.media.MediaGenerationManager.generateAudio(context, "AI Audiosi", audioType, actualPrompt)
                            } catch (e: Exception) {
                                Log.e("AiDialog", "Fallback audio gen error", e)
                            }
                        }
                        pLower.contains("video") || pLower.contains("klip") || pLower.contains("animatsiya") ||
                        pLower.contains("movie") || pLower.contains("clip") || pLower.contains("создай видео") ||
                        pLower.contains("видео") -> {
                            statusText = when (selectedLanguage) {
                                "uz" -> "Video tayyorlanmoqda..."
                                "ru" -> "Создание видео..."
                                else -> "Generating video..."
                            }
                            val style = when {
                                pLower.contains("koinot") || pLower.contains("yulduz") || pLower.contains("space") -> "space"
                                pLower.contains("tabiat") || pLower.contains("nature") -> "nature"
                                else -> "cyber"
                            }
                            try {
                                generatedMediaItem = com.example.media.MediaGenerationManager.generateVideo(context, "AI Videosi", actualPrompt, style)
                            } catch (e: Exception) {
                                Log.e("AiDialog", "Fallback video gen error", e)
                            }
                        }
                        (pLower.contains("fayl yarat") || pLower.contains("create file") || pLower.contains("создай файл")) && response.text.contains("```") -> {
                            try {
                                val codePattern = Regex("```([a-zA-Z0-9_-]*)\\n([\\s\\S]*?)```")
                                val match = codePattern.find(response.text)
                                if (match != null) {
                                    val lang = match.groupValues[1].ifEmpty { "txt" }
                                    val code = match.groupValues[2]
                                    val ext = when (lang.lowercase()) {
                                        "python", "py" -> "py"
                                        "bash", "sh", "shell" -> "sh"
                                        "html" -> "html"
                                        "json" -> "json"
                                        "javascript", "js" -> "js"
                                        "kotlin", "kt" -> "kt"
                                        else -> "txt"
                                    }
                                    val fn = "ai_script_${System.currentTimeMillis()}.$ext"
                                    generatedMediaItem = com.example.media.MediaGenerationManager.createFile(context, fn, code, "Sun'iy intellekt kodi")
                                }
                            } catch (e: Exception) {
                                Log.e("AiDialog", "Fallback file gen error", e)
                            }
                        }
                    }
                }

                statusText = ""
                chatMessages.removeAll { it.id == "thinking" }

                val finalIdx = chatMessages.indexOfFirst { it.id == responseId }
                if (finalIdx != -1) {
                    val fallbackText = if (response.text.isBlank() && (response.toolCalls.isNotEmpty() || generatedMediaItem != null)) {
                        when (generatedMediaItem?.type) {
                            "IMAGE" -> "Mana, siz so'ragan tasvir yaratildi:"
                            "AUDIO" -> "Mana, siz so'ragan audio kompozitsiya tayyor:"
                            "VIDEO" -> "Mana, siz so'ragan video klip tayyor:"
                            "FILE" -> "Mana, siz so'ragan fayl yaratildi:"
                            else -> "Terminalda bajarildi."
                        }
                    } else {
                        response.text
                    }
                    chatMessages[finalIdx] = chatMessages[finalIdx].copy(
                        text = fallbackText,
                        modelId = response.actualModelId ?: selectedModelId,
                        generatedMedia = generatedMediaItem
                    )
                } else {
                    val fallbackText = if (response.text.isBlank() && (response.toolCalls.isNotEmpty() || generatedMediaItem != null)) {
                        when (generatedMediaItem?.type) {
                            "IMAGE" -> "Mana, siz so'ragan tasvir yaratildi:"
                            "AUDIO" -> "Mana, siz so'ragan audio kompozitsiya tayyor:"
                            "VIDEO" -> "Mana, siz so'ragan video klip tayyor:"
                            "FILE" -> "Mana, siz so'ragan fayl yaratildi:"
                            else -> "Terminalda bajarildi."
                        }
                    } else {
                        response.text
                    }
                    chatMessages.add(ChatMessage(
                        id = responseId,
                        text = fallbackText,
                        isUser = false,
                        modelId = response.actualModelId ?: selectedModelId,
                        generatedMedia = generatedMediaItem
                    ))
                }

                saveCurrentSessionToHistory()

                if (viewModel.isTtsEnabled.value) {
                    viewModel.speakText(response.text)
                }

                scope.launch {
                    kotlinx.coroutines.delay(1500)
                    if (statusText.startsWith("Amal") || statusText.startsWith("Выполнение") || statusText.startsWith("Executing")) {
                        statusText = ""
                    }
                }
            } catch (e: Exception) {
                isLoading = false
                statusText = ""
                val errorMsg = when (selectedLanguage) {
                    "uz" -> "Xatolik yuz berdi"
                    "ru" -> "Произошла ошибка"
                    else -> "An error occurred"
                }
                chatMessages.add(ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = "$errorMsg: ${e.localizedMessage}",
                    isUser = false
                ))
                saveCurrentSessionToHistory()
            }
        }
    }

    androidx.activity.compose.BackHandler(onBack = {
        safeDismiss()
    })

    val surfaceModifier = Modifier
        .fillMaxSize()
        .padding(8.dp)
    val surfaceShape = RoundedCornerShape(24.dp)
    val surfaceBorder = androidx.compose.foundation.BorderStroke(2.5.dp, Color(0xFFC0C6CC))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
                selectionResetKey++
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = surfaceModifier,
            shape = surfaceShape,
            border = surfaceBorder,
            color = backgroundColor
        ) {
                val columnModifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                Column(
                    modifier = columnModifier
                ) {
                if (!showHistoryDropdown) {
                    // Header row of the dialog (100% matched to main terminal header)
                    Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF241C1A)) // Exact terminal header background
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    // Left Part: Circle lights and Add Chat button
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Red Window Button -> Close dialog
                            Box(
                                modifier = Modifier
                                    .size(width = 20.dp, height = 32.dp)
                                    .clickable { safeDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.size(12.dp).background(Color(0xFFFF5F56), CircleShape))
                            }

                            // Yellow Window Button -> Clear active chat log
                            Box(
                                modifier = Modifier
                                    .size(width = 20.dp, height = 32.dp)
                                    .clickable {
                                        chatMessages.clear()
                                        chatMessages.add(ChatMessage(id = "welcome", text = welcomeText, isUser = false))
                                        Toast.makeText(
                                            context,
                                            when (selectedLanguage) {
                                                "uz" -> "Chat tozalandi"
                                                "ru" -> "Чат очищен"
                                                else -> "Chat cleared"
                                            },
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.size(12.dp).background(Color(0xFFFFBD2E), CircleShape))
                            }

                            // Green Window Button -> Toggle Fullscreen / Immersive
                            Box(
                                modifier = Modifier
                                    .size(width = 20.dp, height = 32.dp)
                                    .clickable { onAiFullScreenChange(!isAiFullScreen) },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.size(12.dp).background(Color(0xFF27C93F), CircleShape))
                            }
                        }

                        Spacer(modifier = Modifier.width(2.dp))

                        IconButton(
                            onClick = {
                                saveCurrentSessionToHistory()
                                currentSessionId = UUID.randomUUID().toString()
                                chatMessages.clear()
                                chatMessages.add(ChatMessage(id = "welcome", text = welcomeText, isUser = false))
                                Toast.makeText(
                                    context,
                                    when (selectedLanguage) {
                                        "uz" -> "Yangi chat boshlandi"
                                        "ru" -> "Начат новый чат"
                                        else -> "New chat started"
                                    },
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Chat",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                    }

                    // Center Title: strictly centered title text displaying "Terminal AI"
                    Text(
                        text = "Terminal AI",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    // Right Part: Settings, History (4-squares) grid and close button
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box {
                            IconButton(
                                onClick = { showModelDropdown = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Model Settings",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showModelDropdown,
                                onDismissRequest = { showModelDropdown = false },
                                modifier = Modifier
                                    .background(Color(0xFF1E293B))
                                    .border(1.dp, Color(0xFF475569), RoundedCornerShape(8.dp))
                            ) {
                                com.example.gemini.GeminiClient.AVAILABLE_MODELS.forEach { model ->
                                    val isSelected = model.id == selectedModelId
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = model.displayName,
                                                color = if (isSelected) Color(0xFF38BDF8) else Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            viewModel.updateSelectedGeminiModel(model.id)
                                            showModelDropdown = false
                                        }
                                    )
                                }

                                androidx.compose.material3.HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = Color(0xFF475569)
                                )

                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = "Bytez API Kaliti",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    onClick = {
                                        showModelDropdown = false
                                        val prefs = context.getSharedPreferences("ai_studio_prefs", Context.MODE_PRIVATE)
                                        bytezKeyInput = prefs.getString("bytez_api_key", "") ?: ""
                                        showBytezKeyDialog = true
                                    }
                                )

                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = Color(0xFFF87171),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = when (selectedLanguage) {
                                                "uz" -> "Media xotirani tozalash"
                                                "ru" -> "Очистить медиа-кэш"
                                                else -> "Clear Media Cache"
                                            },
                                            color = Color(0xFFF87171),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    onClick = {
                                        showModelDropdown = false
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            val freed = com.example.media.MediaGenerationManager.clearAllAiMediaCache(context)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                val msg = when (selectedLanguage) {
                                                    "uz" -> "Xotira tozalandi (${com.example.media.MediaGenerationManager.formatSize(freed)} bo'shatildi)"
                                                    "ru" -> "Кэш очищен (освобождено ${com.example.media.MediaGenerationManager.formatSize(freed)})"
                                                    else -> "Cache cleared (${com.example.media.MediaGenerationManager.formatSize(freed)} freed)"
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        IconButton(
                            onClick = { showHistoryDropdown = !showHistoryDropdown },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = "History",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = safeDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                }

                // Chat View Area (with potential overlay of saved history sessions list)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                activeActionMessageId = null
                                focusManager.clearFocus()
                                selectionResetKey++
                            }
                            .padding(horizontal = 16.dp)
                            .padding(vertical = 4.dp)
                    ) {
                        val lastMessage = chatMessages.lastOrNull()
                        val lastMessageText = lastMessage?.text
                        LaunchedEffect(chatMessages.size, isLoading, lastMessageText) {
                            if (chatMessages.isNotEmpty()) {
                                listState.animateScrollToItem(chatMessages.size - 1)
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            focusManager.clearFocus()
                                            selectionResetKey++
                                        }
                                    )
                                },
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(chatMessages, key = { it.id }) { msg ->
                                if (!msg.isUser) {
                                    // Custom AI layout satisfying requirement #10
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                    ) {
                                        // Header Row of AI message (logo + speaker)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Logo and spinning loading circle
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    if (isLoading && msg.id == "thinking") {
                                                        val infiniteTransition = rememberInfiniteTransition(label = "rotation")
                                                        val angle by infiniteTransition.animateFloat(
                                                            initialValue = 0f,
                                                            targetValue = 360f,
                                                            animationSpec = infiniteRepeatable(
                                                                animation = tween(1200, easing = LinearEasing),
                                                                repeatMode = RepeatMode.Restart
                                                            ),
                                                            label = "angle"
                                                        )
                                                        CircularProgressIndicator(
                                                            color = Color.White,
                                                            strokeWidth = 2.dp,
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .graphicsLayer { rotationZ = angle }
                                                        )
                                                    }
                                                    Icon(
                                                        imageVector = Icons.Default.AutoAwesome,
                                                        contentDescription = "AI Icon",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                // Model Display
                                                val msgModelId = msg.modelId ?: selectedModelId
                                                val activeModel = remember(msgModelId) {
                                                    com.example.gemini.GeminiClient.AVAILABLE_MODELS.find { it.id == msgModelId }
                                                        ?: com.example.gemini.GeminiClient.AVAILABLE_MODELS.first()
                                                }
                                                Row(
                                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = activeModel.displayName,
                                                        color = Color(0xFF94A3B8), // Och kulrang
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }

                                            // Action buttons next to each other (Volume, Play, Share, Copy)
                                            val lastAiMessageId = chatMessages.lastOrNull { !it.isUser && it.id != "thinking" && it.id != "welcome" }?.id
                                            val showActions = msg.id == lastAiMessageId || msg.id == activeActionMessageId

                                            if (msg.id != "thinking" && msg.id != "welcome" && showActions) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    // Speaker
                                                    IconButton(
                                                        onClick = { viewModel.speakText(msg.text) },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.VolumeUp,
                                                            contentDescription = "Speak Result",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }

                                                    // Execute Play Button next to speaker/volume icon
                                                    IconButton(
                                                        onClick = {
                                                            val parsed = parseMessageContent(msg.text)
                                                            val codeBlocks = parsed.filterIsInstance<MessageContentBlock.CodeBlock>()
                                                            val finalCmd = if (codeBlocks.isNotEmpty()) {
                                                                codeBlocks.joinToString("\n") { it.code.trim() }
                                                            } else {
                                                                msg.text.trim()
                                                            }
                                                            val assessment = com.example.security.CommandSecurityGateway.assessCommand(
                                                                finalCmd,
                                                                com.example.security.ExecutionOrigin.AI_TOOL
                                                            )

                                                            if (assessment.isBlockedByDefault) {
                                                                viewModel.onCommandChange(finalCmd)
                                                                Toast.makeText(context, when (selectedLanguage) {
                                                                    "uz" -> "Diqqat: Xavfli buyruq (${assessment.matchedTokens.joinToString(", ")})! Tasdiqlash uchun terminal maydoniga joylandi."
                                                                    "ru" -> "Внимание: Опасная команда (${assessment.matchedTokens.joinToString(", ")})! Загружена в терминал для проверки."
                                                                    else -> "Warning: Potentially destructive command (${assessment.matchedTokens.joinToString(", ")}) loaded into terminal for review."
                                                                }, Toast.LENGTH_LONG).show()
                                                            } else {
                                                                viewModel.executeTerminalCommand(finalCmd)
                                                                Toast.makeText(context, when (selectedLanguage) {
                                                                    "uz" -> "Terminalda bajarilmoqda..."
                                                                    "ru" -> "Выполняется в терминале..."
                                                                    else -> "Executing in terminal..."
                                                                }, Toast.LENGTH_SHORT).show()
                                                            }
                                                            safeDismiss()
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlayArrow,
                                                            contentDescription = "Execute in Terminal",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }

                                                    // Share next to volume/execute
                                                    IconButton(
                                                        onClick = {
                                                            val shareIntent = Intent().apply {
                                                                action = Intent.ACTION_SEND
                                                                type = "text/plain"
                                                                putExtra(Intent.EXTRA_TEXT, msg.text)
                                                            }
                                                            (context as? com.example.MainActivity)?.isBypassingLock = true
                                                            context.startActivity(Intent.createChooser(shareIntent, "Share text"))
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Share,
                                                            contentDescription = "Share",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }

                                                    // Copy
                                                    IconButton(
                                                        onClick = {
                                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                            val clip = android.content.ClipData.newPlainText("AI Result", msg.text)
                                                            clipboard.setPrimaryClip(clip)
                                                            Toast.makeText(
                                                                context,
                                                                when (selectedLanguage) {
                                                                    "uz" -> "Nusxa olindi!"
                                                                    "ru" -> "Скопировано!"
                                                                    else -> "Copied!"
                                                                },
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.ContentCopy,
                                                            contentDescription = "Copy",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Text result output (rendered strictly in a separate block/line below)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(surfaceColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                .border(1.dp, primaryColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                                .clickable(
                                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    focusManager.clearFocus()
                                                    selectionResetKey++
                                                    val lastAiMsgId = chatMessages.lastOrNull { !it.isUser && it.id != "thinking" && it.id != "welcome" }?.id
                                                    if (msg.id != lastAiMsgId) {
                                                        activeActionMessageId = if (activeActionMessageId == msg.id) null else msg.id
                                                    }
                                                }
                                                .padding(12.dp)
                                        ) {
                                            val parsedBlocks = remember(msg.text) { parseMessageContent(msg.text) }
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                parsedBlocks.forEach { block ->
                                                    when (block) {
                                                        is MessageContentBlock.TextBlock -> {
                                                            key(selectionResetKey) {
                                                                androidx.compose.foundation.text.selection.SelectionContainer {
                                                                    Text(
                                                                        text = block.text,
                                                                        fontSize = 13.sp,
                                                                        color = textColor,
                                                                        lineHeight = 18.sp,
                                                                        fontFamily = FontFamily.Monospace
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        is MessageContentBlock.CodeBlock -> {
                                                            key(selectionResetKey) {
                                                                CodeBlockView(
                                                                    language = block.language,
                                                                    code = block.code,
                                                                    textColor = textColor,
                                                                    surfaceColor = surfaceColor,
                                                                    primaryColor = primaryColor
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // AI Generated Media Card (Images, Audio, Video, Files)
                                        msg.generatedMedia?.let { media ->
                                            GeneratedMediaCard(
                                                media = media,
                                                primaryColor = primaryColor,
                                                surfaceColor = surfaceColor,
                                                textColor = textColor,
                                                selectedLanguage = selectedLanguage,
                                                onOpenFullScreenImage = { fullScreenImageUrl = it },
                                                onOpenCodePreview = { t, c -> previewCodeDialogData = t to c },
                                                onPlayVideo = { fullScreenVideoUrl = it }
                                            )
                                        }
                                    }
                                } else {
                                    // User prompt layout
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        if (msg.attachedUris.isNotEmpty()) {
                                            @OptIn(ExperimentalLayoutApi::class)
                                            FlowRow(
                                                modifier = Modifier.padding(bottom = if (msg.text.isNotEmpty()) 4.dp else 0.dp),
                                                horizontalArrangement = Arrangement.End,
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
                                                val imageSize = screenWidth / 5
                                                
                                                msg.attachedUris.forEach { uriStr ->
                                                    val uri = android.net.Uri.parse(uriStr)
                                                    val fileName = getFileName(context, uri)
                                                    val mimeType = try { context.contentResolver.getType(uri) ?: "" } catch (e: Exception) { "" }
                                                    
                                                    Box(
                                                        modifier = Modifier
                                                            .size(imageSize)
                                                            .padding(start = 4.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(surfaceColor.copy(alpha = 0.5f))
                                                            .border(1.dp, primaryColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                            .clickable {
                                                                val lower = fileName.lowercase()
                                                                val isImg = mimeType.startsWith("image/") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
                                                                val isVid = mimeType.startsWith("video/") || lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm")
                                                                val isCode = lower.endsWith(".py") || lower.endsWith(".sh") || lower.endsWith(".json") || lower.endsWith(".txt") || lower.endsWith(".kt") || lower.endsWith(".java") || lower.endsWith(".html")

                                                                if (isImg) {
                                                                    fullScreenImageUrl = uriStr
                                                                } else if (isVid) {
                                                                    fullScreenVideoUrl = uriStr
                                                                } else if (isCode) {
                                                                    try {
                                                                        val content = if (uri.scheme == "file") {
                                                                            java.io.File(uri.path ?: "").readText()
                                                                        } else {
                                                                            context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: ""
                                                                        }
                                                                        previewCodeDialogData = fileName to content
                                                                    } catch (e: Exception) {
                                                                        openUriSafely(context, uri, if (mimeType.isNotEmpty()) mimeType else "text/plain")
                                                                    }
                                                                } else {
                                                                    openUriSafely(context, uri, if (mimeType.isNotEmpty()) mimeType else "*/*")
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        val lower = fileName.lowercase()
                                                        val isImg = mimeType.startsWith("image/") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
                                                        val isVid = mimeType.startsWith("video/") || lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm")
                                                        val isAud = mimeType.startsWith("audio/") || lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg")

                                                        if (isImg || isVid) {
                                                            coil.compose.AsyncImage(
                                                                model = uri,
                                                                contentDescription = null,
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                            )
                                                        }
                                                        
                                                        if (!isImg) {
                                                            Icon(
                                                                imageVector = if (isVid) Icons.Default.PlayCircleOutline 
                                                                              else if (isAud) Icons.Default.AudioFile
                                                                              else Icons.Default.InsertDriveFile,
                                                                contentDescription = null,
                                                                tint = if (isVid) Color.White else textColor.copy(alpha = 0.6f),
                                                                modifier = Modifier.size(imageSize / 2)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (msg.text.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .widthIn(max = 280.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(primaryColor.copy(alpha = 0.2f))
                                                    .border(1.dp, primaryColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                                    .clickable(
                                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                        indication = null
                                                    ) {
                                                        focusManager.clearFocus()
                                                        selectionResetKey++
                                                    }
                                                    .padding(12.dp)
                                            ) {
                                                key(selectionResetKey) {
                                                    androidx.compose.foundation.text.selection.SelectionContainer {
                                                        Text(
                                                            text = msg.text,
                                                            fontSize = 13.sp,
                                                            color = textColor,
                                                            lineHeight = 17.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Saved History sessions overlay list
                    if (showHistoryDropdown) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(backgroundColor.copy(alpha = 0.98f))
                                .padding(16.dp),
                            color = backgroundColor.copy(alpha = 0.98f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (historySearchQuery.isNotEmpty()) {
                                            historySearchQuery = ""
                                        } else {
                                            showHistoryDropdown = false
                                        }
                                    }
                            ) {
                                // Top bar: Back arrow, "N Tabs" (localized), no right button
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { showHistoryDropdown = false },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = Color.White
                                        )
                                    }
                                    
                                    Text(
                                        text = when (selectedLanguage) {
                                            "uz" -> "${savedSessions.size} Oyna"
                                            "ru" -> "${savedSessions.size} Окна"
                                            else -> "${savedSessions.size} Tabs"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center
                                    )
                                    
                                    Spacer(modifier = Modifier.size(32.dp)) // To keep title centered
                                }
                                
                                // Search bar (localized)
                                OutlinedTextField(
                                    value = historySearchQuery,
                                    onValueChange = { historySearchQuery = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    placeholder = { 
                                        Text(
                                            text = when (selectedLanguage) {
                                                "uz" -> "Oynani qidirish..."
                                                "ru" -> "Поиск окна..."
                                                else -> "Search tabs..."
                                            },
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search",
                                            tint = Color.White.copy(alpha = 0.5f)
                                        )
                                    },
                                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        cursorColor = primaryColor,
                                        focusedBorderColor = primaryColor.copy(alpha = 0.5f),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        focusedContainerColor = surfaceColor,
                                        unfocusedContainerColor = surfaceColor
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    singleLine = true
                                )

                                val filteredSessions = savedSessions.filter { 
                                    it.title.contains(historySearchQuery, ignoreCase = true) 
                                }

                                if (filteredSessions.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = when (selectedLanguage) {
                                                "uz" -> "Tarix bo'sh"
                                                "ru" -> "История пуста"
                                                else -> "History is empty"
                                            },
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .clickable(
                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                indication = null
                                            ) { showHistoryDropdown = false },
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(filteredSessions) { session ->
                                            val isCurrent = session.sessionId == currentSessionId
                                            val lastMessageText = session.messages.lastOrNull()?.text?.take(80)?.let {
                                                if (it.length == 80) "$it..." else it
                                            } ?: ""

                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(130.dp)
                                                    .clickable {
                                                        currentSessionId = session.sessionId
                                                        showHistoryDropdown = false
                                                    },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp, 
                                                    if (isCurrent) Color(0xFFFF5F56) else Color.Transparent
                                                )
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = session.title,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            color = if (isCurrent) Color(0xFFFF5F56) else Color.White,
                                                            fontFamily = FontFamily.Monospace,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                                        )
                                                        // Tiny x close button replicating terminal's tab close button perfectly
                                                        Box(
                                                            modifier = Modifier
                                                                .size(16.dp)
                                                                .clip(CircleShape)
                                                                .background(Color.White.copy(alpha = 0.15f))
                                                                .clickable {
                                                                    savedSessions.remove(session)
                                                                    saveAiSessions(context, savedSessions.toList())
                                                                    if (currentSessionId == session.sessionId) {
                                                                        currentSessionId = UUID.randomUUID().toString()
                                                                    }
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "Close",
                                                                tint = Color.White.copy(alpha = 0.8f),
                                                                modifier = Modifier.size(10.dp)
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = lastMessageText,
                                                        fontSize = 9.sp,
                                                        color = Color.White.copy(alpha = 0.6f),
                                                        fontFamily = FontFamily.Monospace,
                                                        lineHeight = 12.sp,
                                                        maxLines = 4,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!showHistoryDropdown) {
                    // Show executing tool progress if relevant
                    AnimatedVisibility(
                        visible = statusText.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF27C93F),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = statusText,
                                fontSize = 11.sp,
                                color = Color(0xFF27C93F),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Render attached file chips if any exist
                    if (attachedFiles.isNotEmpty()) {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            attachedFiles.forEach { uri ->
                                val fileName = getFileName(context, uri)
                                Row(
                                    modifier = Modifier
                                        .background(surfaceColor, RoundedCornerShape(16.dp))
                                        .border(1.dp, primaryColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                        .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.InsertDriveFile,
                                        contentDescription = null,
                                        tint = primaryColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (fileName.length > 15) fileName.take(15) + "..." else fileName,
                                        fontSize = 11.sp,
                                        color = textColor,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    IconButton(
                                        onClick = { attachedFiles.remove(uri) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove attachment",
                                            tint = textColor.copy(alpha = 0.6f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Unified combined input pill layout (transparent capsule)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp)
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(32.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(32.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Paperclip Attach File button on the left inside the capsule
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onTriggerFilePicker { uris ->
                                        for (uri in uris) {
                                            if (!attachedFiles.contains(uri)) {
                                                attachedFiles.add(uri)
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Attach File",
                                tint = textColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // TextField in the middle
                        androidx.compose.foundation.text.BasicTextField(
                            value = promptInput,
                            onValueChange = { sessionDrafts[currentSessionId] = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = textColor,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Send
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (promptInput.isNotBlank() || attachedFiles.isNotEmpty()) {
                                        handleSendPrompt(promptInput)
                                    }
                                }
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(primaryColor),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                    if (promptInput.isEmpty()) {
                                        Text(
                                            text = when (selectedLanguage) {
                                                "uz" -> "AI ga buyruq bering..."
                                                "ru" -> "Напишите команду ИИ..."
                                                else -> "Ask Gemini prompt..."
                                            },
                                            fontSize = 14.sp,
                                            color = textColor.copy(alpha = 0.4f),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        // Send button on the right, also inside the capsule row
                        val isSendEnabled = (promptInput.isNotBlank() || attachedFiles.isNotEmpty()) && !isLoading
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSendEnabled) primaryColor else Color.Transparent
                                )
                                .clickable(enabled = isSendEnabled) {
                                    handleSendPrompt(promptInput)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (isSendEnabled) {
                                    if (primaryColor == Color.White) Color.Black else Color.White
                                } else {
                                    textColor.copy(alpha = 0.3f)
                                },
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBytezKeyDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showBytezKeyDialog = false },
            containerColor = Color(0xFF1E293B),
            title = {
                Text(
                    text = "Bytez API Kalitini Kiritish",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Bytez.com saytidan olgan API kalitingizni kiriting. Kalitni olish uchun Bytez veb-saytida ro'yxatdan o'ting va sozlamalar bo'limidan 'API Keys' yoki profilingizdan topishingiz mumkin.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = bytezKeyInput,
                        onValueChange = { bytezKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color(0xFF38BDF8),
                            unfocusedIndicatorColor = Color(0xFF475569)
                        ),
                        singleLine = true,
                        placeholder = { Text("Msl: btz_...", color = Color.Gray) }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val prefs = context.getSharedPreferences("ai_studio_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putString("bytez_api_key", bytezKeyInput.trim()).apply()
                        showBytezKeyDialog = false
                    }
                ) {
                    Text("Saqlash", color = Color(0xFF38BDF8))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBytezKeyDialog = false }) {
                    Text("Bekor qilish", color = Color.Gray)
                }
            }
        )
    }

    // Custom system-style file picker dialog overlay (matching native DocumentsUI screenshots)
    if (showInternalFilePicker) {
        var isGridView by remember { mutableStateOf(true) }
        var isSearchActive by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }
        var isDrawerOpen by remember { mutableStateOf(false) }
        var showMoreMenu by remember { mutableStateOf(false) }
        var selectedCategory by remember { mutableStateOf<String?>(null) }
        var hideInternalStorage by remember { mutableStateOf(false) }
        val pathStack = remember { mutableStateListOf<String>() }

        val rootDir = remember {
            val sdcard = java.io.File("/sdcard")
            if (sdcard.exists() && sdcard.canRead()) sdcard else android.os.Environment.getExternalStorageDirectory()
        }
        val currentDir = remember(pathStack.size) {
            var dir = rootDir
            for (p in pathStack) {
                dir = java.io.File(dir, p)
            }
            dir
        }

        // Helper functions for formatting
        val formatFileSize = remember {
            { bytes: Long ->
                if (bytes <= 0) "0 B"
                else {
                    val units = arrayOf("B", "KB", "MB", "GB")
                    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
                    String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
                }
            }
        }

        val formatFileDate = remember {
            { lastModified: Long ->
                val sdf = java.text.SimpleDateFormat("dd-MMM", java.util.Locale.getDefault())
                sdf.format(java.util.Date(lastModified))
            }
        }

        val getFileType = remember {
            { file: java.io.File ->
                val ext = file.extension.lowercase()
                when {
                    ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> "image"
                    ext in listOf("mp3", "wav", "ogg", "m4a", "flac", "aac") -> "audio"
                    ext in listOf("mp4", "mkv", "webm", "avi", "3gp") -> "video"
                    ext in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "json", "xml", "yaml") -> "doc"
                    else -> "other"
                }
            }
        }

        class SimFile(
            val name: String,
            val size: String,
            val date: String,
            val isDirectory: Boolean = false,
            val isApk: Boolean = false,
            val type: String = "other" // "image", "audio", "video", "doc"
        )

        // List files of current directory with filtering applied
        val displayedItems = remember(pathStack.size, selectedCategory, searchQuery, hideInternalStorage) {
            try {
                var filesList = if (selectedCategory != null) {
                    val scanDirs = when (selectedCategory) {
                        "image" -> listOf(java.io.File(rootDir, "DCIM"), java.io.File(rootDir, "Pictures"))
                        "audio" -> listOf(java.io.File(rootDir, "Music"), java.io.File(rootDir, "Voice Recorder"))
                        "video" -> listOf(java.io.File(rootDir, "Movies"), java.io.File(rootDir, "DCIM"))
                        "doc" -> listOf(java.io.File(rootDir, "Download"), rootDir)
                        "recent" -> listOf(rootDir, java.io.File(rootDir, "Download"), java.io.File(rootDir, "DCIM"), java.io.File(rootDir, "Pictures"))
                        else -> listOf(currentDir)
                    }
                    val foundFiles = mutableListOf<java.io.File>()
                    for (dir in scanDirs) {
                        if (dir.exists() && dir.isDirectory) {
                            dir.listFiles()?.forEach { file ->
                                if (!file.isDirectory) {
                                    if (selectedCategory == "recent" || getFileType(file) == selectedCategory) {
                                        foundFiles.add(file)
                                    }
                                }
                            }
                        }
                    }
                    if (selectedCategory == "recent") {
                        foundFiles.sortedByDescending { it.lastModified() }.take(20)
                    } else {
                        foundFiles
                    }
                } else {
                    currentDir.listFiles()?.toList() ?: emptyList()
                }

                // Filter search
                if (searchQuery.isNotBlank()) {
                    filesList = filesList.filter { it.name.contains(searchQuery, ignoreCase = true) }
                }

                // Filter dotfiles
                if (hideInternalStorage) {
                    filesList = filesList.filter { !it.name.startsWith(".") }
                }

                // Sort folders first, then files (if not "recent" category)
                val sorted = if (selectedCategory == "recent") {
                    filesList
                } else {
                    filesList.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                }

                sorted.map { file ->
                    SimFile(
                        name = file.name,
                        size = formatFileSize(file.length()),
                        date = formatFileDate(file.lastModified()),
                        isDirectory = file.isDirectory,
                        isApk = file.extension.lowercase() == "apk",
                        type = getFileType(file)
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        val folders = remember(displayedItems) { displayedItems.filter { it.isDirectory } }
        val files = remember(displayedItems) { displayedItems.filter { !it.isDirectory } }

        val realStorageFreeText = remember {
            try {
                val stat = android.os.StatFs(rootDir.absolutePath)
                val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
                formatFileSize(bytesAvailable) + " bo'sh"
            } catch (e: Exception) {
                "Noma'lum"
            }
        }

        val realDeviceName = remember {
            val manufacturer = android.os.Build.MANUFACTURER
            val model = android.os.Build.MODEL
            if (model.lowercase().startsWith(manufacturer.lowercase())) {
                model.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
            } else {
                "${manufacturer.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }} $model"
            }
        }

        Dialog(
            onDismissRequest = { showInternalFilePicker = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF131314)) // DocumentsUI dark charcoal background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. TOP BAR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(Color(0xFF131314))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSearchActive) {
                            IconButton(onClick = {
                                isSearchActive = false
                                searchQuery = ""
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f),
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                                singleLine = true,
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = when (selectedLanguage) {
                                                "uz" -> "Qidirish..."
                                                "ru" -> "Поиск..."
                                                else -> "Search..."
                                            },
                                            color = Color(0xFF9AA0A6),
                                            fontSize = 15.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White)
                                }
                            }
                        } else {
                            IconButton(onClick = { isDrawerOpen = true }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Smartphone,
                                contentDescription = "Device Storage",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable {
                                        pathStack.clear()
                                        selectedCategory = null
                                    }
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                            }

                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                    modifier = Modifier.background(Color(0xFF202124))
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = when (selectedLanguage) {
                                                    "uz" -> "Saralash..."
                                                    "ru" -> "Сортировка..."
                                                    else -> "Sort..."
                                                },
                                                color = Color.White,
                                                fontSize = 13.sp
                                            )
                                        },
                                        onClick = {
                                            Toast.makeText(context, "Saralash / Sort", Toast.LENGTH_SHORT).show()
                                            showMoreMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = when (selectedLanguage) {
                                                    "uz" -> "Hammasini belgilash"
                                                    "ru" -> "Выбрать все"
                                                    else -> "Select all"
                                                },
                                                color = Color.White,
                                                fontSize = 13.sp
                                            )
                                        },
                                        onClick = {
                                            Toast.makeText(context, "Hammasini belgilash / Select all", Toast.LENGTH_SHORT).show()
                                            showMoreMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (hideInternalStorage) {
                                                    when (selectedLanguage) {
                                                        "uz" -> "Ichki xotirani ko'rsatish"
                                                        "ru" -> "Показать накопитель"
                                                        else -> "Show internal storage"
                                                    }
                                                } else {
                                                    when (selectedLanguage) {
                                                        "uz" -> "Ichki xotirani berkitish"
                                                        "ru" -> "Скрыть накопитель"
                                                        else -> "Hide internal storage"
                                                    }
                                                },
                                                color = Color.White,
                                                fontSize = 13.sp
                                            )
                                        },
                                        onClick = {
                                            hideInternalStorage = !hideInternalStorage
                                            showMoreMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 2. CATEGORY PILLS ROW (Horizontal scroll)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val categories = listOf(
                            Triple("Rasmlar", "image", Color(0xFFE57373)),
                            Triple("Audio", "audio", Color(0xFFFFB74D)),
                            Triple("Videolar", "video", Color(0xFF81C784)),
                            Triple("Yuklanmalar", "download", Color(0xFF64B5F6)),
                            Triple("Hujjatlar", "doc", Color(0xFFBA68C8))
                        )

                        categories.forEach { (label, catType, iconColor) ->
                            val isSelected = selectedCategory == catType || (catType == "download" && pathStack.lastOrNull() == "Download")
                            val bg = if (isSelected) Color(0xFF303134) else Color(0xFF202124)
                            val borderCol = if (isSelected) Color(0xFF8AB4F8) else Color(0xFF3C4043)

                            Row(
                                modifier = Modifier
                                    .background(bg, CircleShape)
                                    .border(1.dp, borderCol, CircleShape)
                                    .clickable {
                                        if (catType == "download") {
                                            pathStack.clear()
                                            pathStack.add("Download")
                                            selectedCategory = null
                                        } else {
                                            if (selectedCategory == catType) {
                                                selectedCategory = null
                                            } else {
                                                selectedCategory = catType
                                                pathStack.clear()
                                            }
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(iconColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (catType) {
                                            "image" -> Icons.Default.Image
                                            "audio" -> Icons.Default.MusicNote
                                            "video" -> Icons.Default.PlayArrow
                                            "download" -> Icons.Default.ArrowDownward
                                            else -> Icons.Default.Description
                                        },
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = label,
                                    color = if (isSelected) Color(0xFF8AB4F8) else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // 3. TITLE HEADER ROW
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Smartphone,
                                contentDescription = "Device Storage",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = realDeviceName.uppercase(java.util.Locale.ROOT),
                                color = Color(0xFFE8EAED),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (pathStack.isNotEmpty()) {
                                Text(
                                    text = " > " + pathStack.joinToString(" > "),
                                    color = Color(0xFF8AB4F8),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Grid/List toggle icon
                        IconButton(
                            onClick = { isGridView = !isGridView },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isGridView) Icons.Default.List else Icons.Default.Menu,
                                contentDescription = "Toggle Grid/List",
                                tint = Color(0xFF9AA0A6)
                            )
                        }
                    }

                    // 4. LIST / GRID VIEWER
                    if (!isGridView) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            // Back/Up navigation item
                            if (pathStack.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { pathStack.removeAt(pathStack.size - 1) }
                                            .padding(vertical = 12.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = ".. (Orqaga / Up)",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                                }
                            }

                            // Folders list
                            items(folders) { folder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            pathStack.add(folder.name)
                                            selectedCategory = null
                                        }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = Color(0xFF9AA0A6),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = folder.name,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                            }

                            // Files list
                            items(files) { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val simulatedUri = android.net.Uri.parse("file:///sdcard/" + (if (pathStack.isNotEmpty()) pathStack.joinToString("/") + "/" else "") + file.name)
                                            if (!attachedFiles.contains(simulatedUri)) {
                                                attachedFiles.add(simulatedUri)
                                            }
                                            showInternalFilePicker = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (file.isApk) Icons.Default.Android else Icons.Default.InsertDriveFile,
                                        contentDescription = null,
                                        tint = if (file.isApk) Color(0xFF3DDC84) else Color(0xFF9AA0A6),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${file.size}  •  ${file.date}",
                                            color = Color(0xFF9AA0A6),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                            }
                        }
                    } else {
                        // Grid View (2-columns matching screenshots)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        ) {
                            // Back/Up folder box
                            if (pathStack.isNotEmpty()) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { pathStack.removeAt(pathStack.size - 1) }
                                            .padding(vertical = 10.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = ".. (Orqaga / Up)",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Folders items
                            items(folders) { folder ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp)
                                        .clickable {
                                            pathStack.add(folder.name)
                                            selectedCategory = null
                                        },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF202124)),
                                    border = BorderStroke(1.dp, Color(0xFF3C4043)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = Color(0xFF9AA0A6),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = folder.name,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Files items
                            items(files) { file ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .padding(4.dp)
                                        .clickable {
                                            val simulatedUri = android.net.Uri.parse("file:///sdcard/" + (if (pathStack.isNotEmpty()) pathStack.joinToString("/") + "/" else "") + file.name)
                                            if (!attachedFiles.contains(simulatedUri)) {
                                                attachedFiles.add(simulatedUri)
                                            }
                                            showInternalFilePicker = false
                                        },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF202124)),
                                    border = BorderStroke(1.dp, Color(0xFF3C4043)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        // Preview region with center big icon
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                                .background(Color(0xFF292A2D)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (file.isApk) Icons.Default.Android else Icons.Default.InsertDriveFile,
                                                contentDescription = null,
                                                tint = if (file.isApk) Color(0xFF3DDC84) else Color(0xFF9AA0A6),
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }

                                        // File meta footer
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF202124))
                                                .padding(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (file.isApk) Icons.Default.Android else Icons.Default.InsertDriveFile,
                                                    contentDescription = null,
                                                    tint = if (file.isApk) Color(0xFF3DDC84) else Color(0xFF9AA0A6),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = file.name,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${file.size}  •  ${file.date}",
                                                color = Color(0xFF9AA0A6),
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. SIDE DRAWER NAVIGATION OVERLAY
                AnimatedVisibility(
                    visible = isDrawerOpen,
                    enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it }),
                    exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it })
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .width(280.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF202124))
                                .padding(vertical = 16.dp)
                                .clickable(enabled = false) {}
                        ) {
                            Text(
                                text = when (selectedLanguage) {
                                    "uz" -> "Ochish"
                                    "ru" -> "Открыть"
                                    else -> "Open from"
                                },
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                val drawerCategories = listOf(
                                    Triple("Yaqinda", "recent", Icons.Default.History),
                                    Triple("Tasvirlar", "image", Icons.Default.Image),
                                    Triple("Videolar", "video", Icons.Default.PlayArrow),
                                    Triple("Audio", "audio", Icons.Default.MusicNote),
                                    Triple("Yuklanmalar", "download", Icons.Default.ArrowDownward)
                                )

                                drawerCategories.forEach { (label, cat, icon) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (cat == "download") {
                                                    pathStack.clear()
                                                    pathStack.add("Download")
                                                    selectedCategory = null
                                                } else {
                                                    selectedCategory = cat
                                                    pathStack.clear()
                                                }
                                                isDrawerOpen = false
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(icon, contentDescription = null, tint = Color(0xFF9AA0A6), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(label, color = Color.White, fontSize = 13.sp)
                                    }
                                }

                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)).padding(vertical = 8.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            pathStack.clear()
                                            selectedCategory = null
                                            isDrawerOpen = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Smartphone,
                                        contentDescription = "Device Storage",
                                        tint = Color(0xFF9AA0A6),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = realDeviceName,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = realStorageFreeText,
                                            color = Color(0xFF9AA0A6),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                // Placeholder for dynamically fetching external apps if needed in the future
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable { isDrawerOpen = false }
                        )
                    }
                }
            }
        }
    }

    // Fullscreen Image Viewer Dialog
    fullScreenImageUrl?.let { imageUrl ->
        FullScreenImageViewerDialog(
            imagePathOrUrl = imageUrl,
            selectedLanguage = selectedLanguage,
            onDismiss = { fullScreenImageUrl = null }
        )
    }

    // Video Player Dialog
    fullScreenVideoUrl?.let { videoUrl ->
        VideoPlayerDialog(
            videoPathOrUrl = videoUrl,
            onDismiss = { fullScreenVideoUrl = null }
        )
    }

    // Code Preview Dialog
    previewCodeDialogData?.let { (title, content) ->
        CodePreviewDialog(
            title = title,
            content = content,
            selectedLanguage = selectedLanguage,
            onDismiss = { previewCodeDialogData = null }
        )
    }
}
