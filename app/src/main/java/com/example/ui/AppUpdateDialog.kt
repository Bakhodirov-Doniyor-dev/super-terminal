package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.updater.AppUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateDialog(
    terminalTheme: String,
    selectedLanguage: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val updateManager = remember { AppUpdateManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    val currentVersionName = remember { updateManager.getCurrentVersionName() }
    val currentVersionCode = remember { updateManager.getCurrentVersionCode() }

    val downloadState by updateManager.downloadState.collectAsState()
    val availableUpdate by updateManager.availableUpdate.collectAsState()
    val isChecking by updateManager.isChecking.collectAsState()

    var statusMessage by remember { mutableStateOf("") }

    val (textColor, bgColor, borderColor, accentColor, cardBgColor, secText) = getThemeColors(terminalTheme)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(horizontal = 14.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .heightIn(max = 680.dp)
                    .oneUiGlassCard(
                        shape = RoundedCornerShape(26.dp),
                        backgroundColor = cardBgColor.copy(alpha = 0.90f),
                        borderColor = accentColor.copy(alpha = 0.40f),
                        borderWidth = 1.2.dp
                    ),
                shape = RoundedCornerShape(26.dp),
                color = Color.Transparent
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header with illuminated OneUI glass capsule badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .oneUiGlassCapsule(
                                        backgroundColor = accentColor.copy(alpha = 0.25f),
                                        accentColor = accentColor.copy(alpha = 0.60f),
                                        borderWidth = 1.dp
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = when (selectedLanguage) {
                                        "uz" -> "Ilovani Yangilash (OTA)"
                                        "ru" -> "Обновление (OTA)"
                                        else -> "In-App OTA Updater"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                )
                                Text(
                                    text = "Joriy versiya: v$currentVersionName (Build $currentVersionCode)",
                                    fontSize = 11.5.sp,
                                    color = secText,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Close button in glass capsule
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .oneUiGlassCapsule(
                                    backgroundColor = Color.White.copy(alpha = 0.10f),
                                    accentColor = Color.White.copy(alpha = 0.30f),
                                    borderWidth = 1.dp
                                )
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = borderColor.copy(alpha = 0.25f), thickness = 1.dp)

                    // Status banner if update found or checked
                    if (statusMessage.isNotEmpty()) {
                        val isSuccess = statusMessage.contains("eng so'nggi", ignoreCase = true) || statusMessage.contains("mavjud", ignoreCase = true)
                        val bannerColor = if (isSuccess) Color(0xFF10B981) else accentColor
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .oneUiGlassCard(
                                    shape = RoundedCornerShape(14.dp),
                                    backgroundColor = bannerColor.copy(alpha = 0.15f),
                                    borderColor = bannerColor.copy(alpha = 0.45f),
                                    borderWidth = 1.dp
                                )
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = bannerColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = statusMessage,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // If an update is available, show Details Card
                    val update = availableUpdate
                    if (update != null && update.hasUpdate) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .oneUiGlassCard(
                                    shape = RoundedCornerShape(16.dp),
                                    backgroundColor = cardBgColor.copy(alpha = 0.85f),
                                    borderColor = Color(0xFF10B981).copy(alpha = 0.50f),
                                    borderWidth = 1.2.dp
                                )
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "YANGI TALQIN: v${update.latestVersionName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF34D399)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .oneUiGlassCapsule(
                                                backgroundColor = Color(0xFF10B981).copy(alpha = 0.25f),
                                                accentColor = Color(0xFF10B981).copy(alpha = 0.60f)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = update.releaseDate.ifEmpty { "Yangi" },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF34D399),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Text(
                                    text = "Nimalar yangilandi (Changelog):",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = update.changelog,
                                        fontSize = 11.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFFE2E8F0),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    // Download Progress Card
                    when (val state = downloadState) {
                        is AppUpdateManager.DownloadProgress.Downloading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .oneUiGlassCard(
                                        shape = RoundedCornerShape(16.dp),
                                        backgroundColor = cardBgColor.copy(alpha = 0.85f),
                                        borderColor = accentColor.copy(alpha = 0.50f),
                                        borderWidth = 1.2.dp
                                    )
                                    .padding(14.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Yuklab olinmoqda: ${state.percent}%",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color.White
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%.1f MB/s", state.speedMbPerSec),
                                            fontSize = 11.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = accentColor
                                        )
                                    }

                                    LinearProgressIndicator(
                                        progress = { (state.percent / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(CircleShape),
                                        color = Color(0xFF10B981),
                                        trackColor = Color.White.copy(alpha = 0.15f)
                                    )

                                    Text(
                                        text = "${state.bytesDownloaded / (1024 * 1024)} MB / ${state.totalBytes / (1024 * 1024)} MB",
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = secText
                                    )
                                }
                            }
                        }
                        is AppUpdateManager.DownloadProgress.Completed -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .oneUiGlassCard(
                                        shape = RoundedCornerShape(14.dp),
                                        backgroundColor = Color(0xFF064E3B).copy(alpha = 0.45f),
                                        borderColor = Color(0xFF10B981).copy(alpha = 0.60f),
                                        borderWidth = 1.dp
                                    )
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(24.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "APK muvaffaqiyatli yuklab olindi!",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "O'rnatish uchun quyidagi tugmani bosing.",
                                            fontSize = 11.sp,
                                            color = Color(0xFF6EE7B7),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                        is AppUpdateManager.DownloadProgress.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .oneUiGlassCard(
                                        shape = RoundedCornerShape(14.dp),
                                        backgroundColor = Color(0xFF7F1D1D).copy(alpha = 0.45f),
                                        borderColor = Color(0xFFEF4444).copy(alpha = 0.60f),
                                        borderWidth = 1.dp
                                    )
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "Yuklab olishda xatolik: ${state.message}",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFFFCA5A5),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        else -> {}
                    }

                    // Primary Action Buttons in OneUI Glass Capsule Style
                    val isDownloading = downloadState is AppUpdateManager.DownloadProgress.Downloading
                    val isDownloadCompleted = downloadState is AppUpdateManager.DownloadProgress.Completed

                    // Automatically trigger check on first opening if not already checked
                    LaunchedEffect(Unit) {
                        if (availableUpdate == null && !isChecking) {
                            updateManager.checkForUpdates { statusMessage = it }
                        }
                    }

                    if (isDownloadCompleted) {
                        val file = (downloadState as AppUpdateManager.DownloadProgress.Completed).file
                        OneUiGlassCapsuleButton(
                            onClick = {
                                scope.launch {
                                    updateManager.installApk(file) { statusMessage = it }
                                }
                            },
                            backgroundColor = Color(0xFF10B981).copy(alpha = 0.85f),
                            accentColor = Color(0xFF34D399),
                            contentColor = Color.Black,
                            padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                        ) {
                            Icon(Icons.Default.DownloadDone, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Hozir O'rnatish (Install APK)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.Black,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else if (update != null && update.hasUpdate) {
                        OneUiGlassCapsuleButton(
                            onClick = {
                                scope.launch {
                                    val dlRes = updateManager.downloadApk(update.downloadUrl, update.latestVersionName)
                                    if (dlRes.isSuccess) {
                                        val apkFile = dlRes.getOrNull()!!
                                        updateManager.installApk(apkFile) { statusMessage = it }
                                    }
                                }
                            },
                            enabled = !isDownloading,
                            backgroundColor = Color(0xFF10B981).copy(alpha = 0.88f),
                            accentColor = Color(0xFF34D399),
                            contentColor = Color.Black,
                            padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Yuklanmoqda...", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Yuklab Olish va O'rnatish (OTA Upgrade)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = Color.Black,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        // Even on latest version, allow user to Re-download & Reinstall!
                        OneUiGlassCapsuleButton(
                            onClick = {
                                val dlUrl = update?.downloadUrl?.ifBlank { null }
                                    ?: "https://github.com/Bakhodirov-Doniyor-dev/super-terminal/releases/download/v1.1.3/SuperTerminal_v1.1.3.apk"
                                val targetVer = update?.latestVersionName?.ifBlank { null } ?: currentVersionName
                                scope.launch {
                                    val dlRes = updateManager.downloadApk(dlUrl, targetVer)
                                    if (dlRes.isSuccess) {
                                        val apkFile = dlRes.getOrNull()!!
                                        updateManager.installApk(apkFile) { statusMessage = it }
                                    }
                                }
                            },
                            enabled = !isDownloading,
                            backgroundColor = Color(0xFF059669).copy(alpha = 0.80f),
                            accentColor = Color(0xFF34D399),
                            contentColor = Color.White,
                            padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Qayta yuklanmoqda...", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "APK-ni Qayta Yuklab Olish va O'rnatish",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Secondary Glass Action Row (Check & Browser)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OneUiGlassCapsuleButton(
                            onClick = {
                                scope.launch {
                                    val res = updateManager.checkForUpdates { statusMessage = it }
                                    if (res.isFailure) {
                                        statusMessage = "Tekshirishda xatolik: ${res.exceptionOrNull()?.localizedMessage}"
                                    }
                                }
                            },
                            enabled = !isChecking && !isDownloading,
                            backgroundColor = accentColor.copy(alpha = 0.22f),
                            accentColor = accentColor.copy(alpha = 0.50f),
                            contentColor = Color.White,
                            padding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 46.dp)
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tekshirilmoqda...", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Qayta Tekshirish",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Direct Browser Download Fallback Button
                        OneUiGlassCapsuleButton(
                            onClick = {
                                val targetUrl = update?.downloadUrl?.ifBlank { null }
                                    ?: "https://github.com/Bakhodirov-Doniyor-dev/super-terminal/releases"
                                try {
                                    val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(targetUrl)).apply {
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(browserIntent)
                                } catch (e: Exception) {
                                    statusMessage = "Brauzerni ochib bo'lmadi: ${e.localizedMessage}"
                                }
                            },
                            backgroundColor = Color.White.copy(alpha = 0.12f),
                            accentColor = Color.White.copy(alpha = 0.35f),
                            contentColor = Color.White,
                            padding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 46.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "GitHub / Brauzer",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
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
