package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.net.*
import com.example.viewmodel.AdbViewModel
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanFileServerDialog(
    viewModel: AdbViewModel,
    terminalTheme: String = "monochrome",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lanManager = remember { LanFileServerManager.getInstance(context) }

    val serverState by lanManager.serverState.collectAsState()
    val serverConfig by lanManager.serverConfig.collectAsState()
    val serverStats by lanManager.serverStats.collectAsState()
    val detectedIps by lanManager.detectedLanIps.collectAsState()
    val recentLogs by lanManager.recentLogs.collectAsState()
    val connectedAdbDevice by lanManager.connectedAdbDevice.collectAsState()

    var showQrDialog by rememberSaveable { mutableStateOf(false) }
    var isTokenVisible by rememberSaveable { mutableStateOf(false) }
    var portInputText by rememberSaveable { mutableStateOf(serverConfig.port.toString()) }
    var showFolderPickerChoice by rememberSaveable { mutableStateOf(false) }

    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore if not supported
            }
            // Safely resolve path or use Download folder
            val resolvedPath = SafStorageHelper.getFullPathFromTreeUri(uri, context)
            if (!resolvedPath.isNullOrBlank()) {
                lanManager.setSharedDirectory(resolvedPath)
                Toast.makeText(context, "Papka o'zgartirildi: $resolvedPath", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Tanlangan papka saqlandi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val (textColor, bgColor, borderColor, accentColor, cardBgColor, secText) = getThemeColors(terminalTheme)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(28.dp)),
                colors = CardDefaults.cardColors(containerColor = cardBgColor)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accentColor.copy(alpha = 0.15f))
                                    .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = "LAN Server",
                                    tint = accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "LAN File Server",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Text(
                                    text = "Mahalliy HTTP 1.1 Fayl Ulashish",
                                    fontSize = 12.sp,
                                    color = secText
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Yopish",
                                tint = textColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = borderColor.copy(alpha = 0.4f), thickness = 1.dp)

                    // Scrollable Content
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Server Status Banner
                        item {
                            ServerStatusCard(
                                serverState = serverState,
                                serverStats = serverStats,
                                serverConfig = serverConfig,
                                textColor = textColor,
                                secText = secText,
                                accentColor = accentColor,
                                borderColor = borderColor,
                                onStartServer = {
                                    val parsedPort = portInputText.toIntOrNull() ?: 8080
                                    lanManager.startServer(parsedPort)
                                },
                                onStopServer = {
                                    lanManager.stopServer()
                                }
                            )
                        }

                        // 2. URL & Quick Actions (If Running)
                        if (serverState == LanServerState.RUNNING) {
                            item {
                                ServerUrlCard(
                                    serverStats = serverStats,
                                    serverConfig = serverConfig,
                                    textColor = textColor,
                                    secText = secText,
                                    accentColor = accentColor,
                                    borderColor = borderColor,
                                    onCopyUrl = {
                                        val url = serverStats.fullUrl
                                        clipboardManager.setText(AnnotatedString(url))
                                        Toast.makeText(context, "URL nusxalandi: $url", Toast.LENGTH_SHORT).show()
                                    },
                                    onShowQr = {
                                        showQrDialog = true
                                    },
                                    onOpenBrowser = {
                                        try {
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(serverStats.fullUrl))
                                            context.startActivity(browserIntent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Brauzer ochilmadi", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }

                        // 3. Runtime Statistics Grid
                        item {
                            RuntimeStatsGrid(
                                serverStats = serverStats,
                                isRunning = serverState == LanServerState.RUNNING,
                                textColor = textColor,
                                secText = secText,
                                borderColor = borderColor
                            )
                        }

                        // 4. Shared Folder Card
                        item {
                            SharedFolderCard(
                                sharedPath = serverConfig.sharedDirectoryPath,
                                textColor = textColor,
                                secText = secText,
                                accentColor = accentColor,
                                borderColor = borderColor,
                                onChangeFolder = {
                                    showFolderPickerChoice = true
                                }
                            )
                        }

                        // 5. Security & Authentication Card
                        item {
                            SecurityAuthCard(
                                serverConfig = serverConfig,
                                isTokenVisible = isTokenVisible,
                                textColor = textColor,
                                secText = secText,
                                accentColor = accentColor,
                                borderColor = borderColor,
                                onToggleAuth = { enabled ->
                                    lanManager.setAuthEnabled(enabled)
                                },
                                onToggleVisibility = {
                                    isTokenVisible = !isTokenVisible
                                },
                                onRegenerateToken = {
                                    val token = lanManager.regenerateToken()
                                    Toast.makeText(context, "Yangi token yaratildi", Toast.LENGTH_SHORT).show()
                                },
                                onCopyToken = {
                                    clipboardManager.setText(AnnotatedString(serverConfig.authToken))
                                    Toast.makeText(context, "Token nusxalandi", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        // 6. Server Settings (Port & ADB)
                        item {
                            ServerSettingsCard(
                                portInput = portInputText,
                                isRunning = serverState == LanServerState.RUNNING,
                                autoStopOnAdb = serverConfig.autoStopOnAdbDisconnect,
                                detectedIps = detectedIps,
                                connectedAdbDevice = connectedAdbDevice,
                                textColor = textColor,
                                secText = secText,
                                accentColor = accentColor,
                                borderColor = borderColor,
                                onPortChange = { newPort ->
                                    portInputText = newPort
                                    val p = newPort.toIntOrNull()
                                    if (p != null && p in 1024..65535) {
                                        lanManager.setPort(p)
                                    }
                                },
                                onAutoStopChange = { autoStop ->
                                    lanManager.setAutoStopOnAdbDisconnect(autoStop)
                                },
                                onRefreshIps = {
                                    lanManager.detectAllLanIps()
                                    Toast.makeText(context, "LAN IP manzillari yangilandi", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        // 7. Live Access Log Viewer
                        item {
                            LiveLogsCard(
                                logs = recentLogs,
                                textColor = textColor,
                                secText = secText,
                                borderColor = borderColor
                            )
                        }
                    }
                }
            }
        }
    }

    // QR Code Viewer Modal
    if (showQrDialog) {
        val tokenParam = if (serverConfig.isAuthEnabled) "?token=${serverConfig.authToken}" else ""
        val qrUrl = "${serverStats.fullUrl}$tokenParam"
        LanServerQrCodeDialog(
            url = qrUrl,
            displayUrl = serverStats.fullUrl,
            terminalTheme = terminalTheme,
            onDismiss = { showQrDialog = false }
        )
    }

    // Quick Folder Selection Dialog
    if (showFolderPickerChoice) {
        AlertDialog(
            onDismissRequest = { showFolderPickerChoice = false },
            title = { Text("Ulashiladigan papkani tanlang", color = textColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val defaultDownload = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                    val defaultDocuments = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
                    val defaultPictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath

                    FolderOptionRow(
                        title = "Download (Standart)",
                        path = defaultDownload,
                        textColor = textColor,
                        secText = secText,
                        onClick = {
                            lanManager.setSharedDirectory(defaultDownload)
                            showFolderPickerChoice = false
                        }
                    )

                    FolderOptionRow(
                        title = "Documents",
                        path = defaultDocuments,
                        textColor = textColor,
                        secText = secText,
                        onClick = {
                            lanManager.setSharedDirectory(defaultDocuments)
                            showFolderPickerChoice = false
                        }
                    )

                    FolderOptionRow(
                        title = "Pictures",
                        path = defaultPictures,
                        textColor = textColor,
                        secText = secText,
                        onClick = {
                            lanManager.setSharedDirectory(defaultPictures)
                            showFolderPickerChoice = false
                        }
                    )

                    OutlinedButton(
                        onClick = {
                            showFolderPickerChoice = false
                            try {
                                openDocumentTreeLauncher.launch(null)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Fayl menejeri ochilmadi", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Boshqa Papkani Tanlash (SAF)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFolderPickerChoice = false }) {
                    Text("Bekor qilish", color = accentColor)
                }
            },
            containerColor = cardBgColor
        )
    }
}

@Composable
private fun FolderOptionRow(
    title: String,
    path: String,
    textColor: Color,
    secText: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
            Column {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                Text(text = path, fontSize = 11.sp, color = secText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ServerStatusCard(
    serverState: LanServerState,
    serverStats: LanServerStats,
    serverConfig: LanServerConfig,
    textColor: Color,
    secText: Color,
    accentColor: Color,
    borderColor: Color,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val (statusLabel, statusColor) = when (serverState) {
        LanServerState.RUNNING -> Pair("● FAOL ISHLAMOQDA", Color(0xFF10B981))
        LanServerState.STARTING -> Pair("● ISHGA TUSHIRILMOQDA...", Color(0xFFF59E0B))
        LanServerState.ERROR -> Pair("✖ XATOLIK YUZ BERDI", Color(0xFFEF4444))
        LanServerState.STOPPED -> Pair("○ TO'XTATILGAN", Color(0xFF94A3B8))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (serverState == LanServerState.RUNNING) statusColor.copy(alpha = pulseAlpha) else statusColor
                            )
                    )
                    Text(
                        text = statusLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                Text(
                    text = "Port: ${serverConfig.port}",
                    fontSize = 12.sp,
                    color = secText,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (!serverStats.errorMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = serverStats.errorMessage,
                        fontSize = 12.sp,
                        color = Color(0xFFFCA5A5)
                    )
                }
            }

            // Start / Stop Primary Button
            if (serverState == LanServerState.RUNNING) {
                Button(
                    onClick = onStopServer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_stop_lan_server"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Serverni To'xtatish", fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else {
                Button(
                    onClick = onStartServer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_start_lan_server"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0284C7)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    enabled = serverState != LanServerState.STARTING
                ) {
                    if (serverState == LanServerState.STARTING) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Ishga tushirilmoqda...", color = Color.White)
                    } else {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Serverni Ishga Tushirish", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerUrlCard(
    serverStats: LanServerStats,
    serverConfig: LanServerConfig,
    textColor: Color,
    secText: Color,
    accentColor: Color,
    borderColor: Color,
    onCopyUrl: () -> Unit,
    onShowQr: () -> Unit,
    onOpenBrowser: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "BRAUZER ORQALI ULANISH MANZILI",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = secText,
                letterSpacing = 0.5.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = serverStats.fullUrl,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onCopyUrl,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Nusxa olish", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            // Quick Actions: Copy, QR Code, Open
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCopyUrl,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = textColor)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nusxa", fontSize = 12.sp, color = textColor)
                }

                Button(
                    onClick = onShowQr,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Icon(imageVector = Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("QR Kod", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onOpenBrowser,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp), tint = textColor)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ochish", fontSize = 12.sp, color = textColor)
                }
            }
        }
    }
}

@Composable
private fun RuntimeStatsGrid(
    serverStats: LanServerStats,
    isRunning: Boolean,
    textColor: Color,
    secText: Color,
    borderColor: Color
) {
    val uptimeFormatted = if (isRunning) {
        val hours = serverStats.uptimeSeconds / 3600
        val mins = (serverStats.uptimeSeconds % 3600) / 60
        val secs = serverStats.uptimeSeconds % 60
        String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
    } else {
        "00:00:00"
    }

    val downloadedFormatted = formatByteSize(serverStats.bytesDownloaded)
    val uploadedFormatted = formatByteSize(serverStats.bytesUploaded)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "HAQIQIY RUNTIME METRIKALAR",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = secText,
            letterSpacing = 0.5.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatMetricBox(
                modifier = Modifier.weight(1f),
                title = "Faol Mijozlar",
                value = "${serverStats.activeClients} ta",
                icon = Icons.Default.People,
                iconTint = Color(0xFF38BDF8),
                textColor = textColor,
                secText = secText,
                borderColor = borderColor
            )

            StatMetricBox(
                modifier = Modifier.weight(1f),
                title = "Ish Vaqti (Uptime)",
                value = uptimeFormatted,
                icon = Icons.Default.Timer,
                iconTint = Color(0xFF10B981),
                textColor = textColor,
                secText = secText,
                borderColor = borderColor
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatMetricBox(
                modifier = Modifier.weight(1f),
                title = "Yuklab Olingan",
                value = downloadedFormatted,
                icon = Icons.Default.Download,
                iconTint = Color(0xFFF59E0B),
                textColor = textColor,
                secText = secText,
                borderColor = borderColor
            )

            StatMetricBox(
                modifier = Modifier.weight(1f),
                title = "Yuklangan (Upload)",
                value = uploadedFormatted,
                icon = Icons.Default.Upload,
                iconTint = Color(0xFFA855F7),
                textColor = textColor,
                secText = secText,
                borderColor = borderColor
            )
        }
    }
}

@Composable
private fun StatMetricBox(
    modifier: Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    textColor: Color,
    secText: Color,
    borderColor: Color
) {
    Card(
        modifier = modifier.border(1.dp, borderColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(text = title, fontSize = 11.sp, color = secText)
                Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun SharedFolderCard(
    sharedPath: String,
    textColor: Color,
    secText: Color,
    accentColor: Color,
    borderColor: Color,
    onChangeFolder: () -> Unit
) {
    val stat = remember(sharedPath) {
        try {
            StatFs(sharedPath)
        } catch (e: Exception) {
            null
        }
    }
    val freeSpace = stat?.let { formatByteSize(it.availableBlocksLong * it.blockSizeLong) } ?: "Aniqlanmadi"
    val totalSpace = stat?.let { formatByteSize(it.blockCountLong * it.blockSizeLong) } ?: "Aniqlanmadi"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                    Text(text = "ULASHILAYOTGAN PAPKA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secText)
                }

                TextButton(onClick = onChangeFolder) {
                    Text("O'zgartirish", fontSize = 12.sp, color = accentColor, fontWeight = FontWeight.SemiBold)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = sharedPath,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Bo'sh joy: $freeSpace", fontSize = 11.sp, color = secText)
                Text(text = "Jami hajm: $totalSpace", fontSize = 11.sp, color = secText)
            }
        }
    }
}

@Composable
private fun SecurityAuthCard(
    serverConfig: LanServerConfig,
    isTokenVisible: Boolean,
    textColor: Color,
    secText: Color,
    accentColor: Color,
    borderColor: Color,
    onToggleAuth: (Boolean) -> Unit,
    onToggleVisibility: () -> Unit,
    onRegenerateToken: () -> Unit,
    onCopyToken: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                    Text(text = "XAVFSIZLIK VA TOKEN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secText)
                }

                Switch(
                    checked = serverConfig.isAuthEnabled,
                    onCheckedChange = onToggleAuth,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accentColor
                    )
                )
            }

            if (serverConfig.isAuthEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isTokenVisible) serverConfig.authToken else "••••-••••-••••-••••",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )

                    Row {
                        IconButton(onClick = onToggleVisibility, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = secText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = onCopyToken, modifier = Modifier.size(32.dp)) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = secText, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onRegenerateToken, modifier = Modifier.size(32.dp)) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Text(
                    text = "Xavfsizlik yoqilgan: Web brauzerlar fayllarga kirish uchun ushbu tokenni kiritishi yoki QR kod orqali ulanishi shart.",
                    fontSize = 11.sp,
                    color = secText,
                    lineHeight = 15.sp
                )
            } else {
                Text(
                    text = "Diqqat: Xavfsizlik o'chiq. Mahalliy tarmoqdagi har qanday qurilma brauzer orqali fayllarni erkin ko'rishi va yuklashi mumkin.",
                    fontSize = 11.sp,
                    color = Color(0xFFFBBF24)
                )
            }
        }
    }
}

@Composable
private fun ServerSettingsCard(
    portInput: String,
    isRunning: Boolean,
    autoStopOnAdb: Boolean,
    detectedIps: List<String>,
    connectedAdbDevice: com.example.adb.device.AdbDevice?,
    textColor: Color,
    secText: Color,
    accentColor: Color,
    borderColor: Color,
    onPortChange: (String) -> Unit,
    onAutoStopChange: (Boolean) -> Unit,
    onRefreshIps: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                Text(text = "SERVER VA ADB SOZLAMALARI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secText)
            }

            // Port Setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Server Porti (Default: 8080)", fontSize = 13.sp, color = textColor)
                    Text(text = "Standart 8080 yoki 1024-65535 oralig'idagi port", fontSize = 11.sp, color = secText)
                }

                OutlinedTextField(
                    value = portInput,
                    onValueChange = onPortChange,
                    modifier = Modifier.width(96.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !isRunning,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        color = textColor
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            HorizontalDivider(color = borderColor.copy(alpha = 0.3f), thickness = 1.dp)

            // Auto-stop on ADB Disconnect Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "ADB uzilganda to'xtatish", fontSize = 13.sp, color = textColor)
                    Text(text = "Ulangan ADB qurilma ajratilganda server avtomatik yopiladi", fontSize = 11.sp, color = secText)
                }

                Switch(
                    checked = autoStopOnAdb,
                    onCheckedChange = onAutoStopChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                )
            }

            HorizontalDivider(color = borderColor.copy(alpha = 0.3f), thickness = 1.dp)

            // Detected IPs list
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Aniqlangan LAN IP manzillar:", fontSize = 12.sp, color = secText)
                IconButton(onClick = onRefreshIps, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Yangilash", tint = accentColor, modifier = Modifier.size(16.dp))
                }
            }

            if (detectedIps.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (ip in detectedIps) {
                        Text(
                            text = "• $ip",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = textColor
                        )
                    }
                }
            } else {
                Text(
                    text = "Faol LAN/Wi-Fi ulanishi topilmadi",
                    fontSize = 12.sp,
                    color = Color(0xFFEF4444)
                )
            }

            if (connectedAdbDevice != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0284C7).copy(alpha = 0.15f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Ulangan ADB qurilma: ${connectedAdbDevice.name} (${connectedAdbDevice.address})",
                        fontSize = 11.sp,
                        color = Color(0xFF38BDF8)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveLogsCard(
    logs: List<LanAccessLog>,
    textColor: Color,
    secText: Color,
    borderColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                Text(text = "HTTP ACCESS LOGS (LIVE)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secText)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = "Hozircha so'rovlar yo'q. Server ishga tushganda so'rovlar shu yerda real vaqt rejimida ko'rinadi.",
                        fontSize = 11.sp,
                        color = secText,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (log in logs.take(20)) {
                            val statusColor = when (log.statusCode) {
                                in 200..299 -> Color(0xFF10B981)
                                in 300..399 -> Color(0xFF38BDF8)
                                in 400..499 -> Color(0xFFF59E0B)
                                else -> Color(0xFFEF4444)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = log.formattedTime(),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = secText
                                )
                                Text(
                                    text = "${log.statusCode}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = statusColor
                                )
                                Text(
                                    text = "${log.method} ${log.path}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = log.clientIp,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = secText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LanServerQrCodeDialog(
    url: String,
    displayUrl: String,
    terminalTheme: String = "monochrome",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val (textColor, bgColor, borderColor, accentColor, cardBgColor, secText) = getThemeColors(terminalTheme)

    val qrBitmap = remember(url) {
        QrCodeGenerator.generateQrBitmap(url, size = 600)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = cardBgColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "LAN Server QR Kodi",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )

                    Text(
                        text = "Boshqa telefon yoki kompyuter kamerasi orqali skanerlang va darhol web interfeysga ulaning:",
                        fontSize = 13.sp,
                        color = secText,
                        textAlign = TextAlign.Center
                    )

                    // QR Image Box
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "LAN Server QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator(color = Color.Black)
                        }
                    }

                    Text(
                        text = displayUrl,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(url))
                                Toast.makeText(context, "URL to'liq nusxalandi", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Nusxa olish")
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Yopish", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun formatByteSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val index = digitGroups.coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, index.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[index])
}
