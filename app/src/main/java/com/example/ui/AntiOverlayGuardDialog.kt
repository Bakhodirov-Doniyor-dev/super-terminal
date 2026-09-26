package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.Translations
import com.example.db.AntiOverlayEventEntity
import com.example.security.AntiOverlayAccessibilityService
import com.example.security.AntiOverlaySentinelManager
import com.example.viewmodel.AdbViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntiOverlayGuardDialog(
    terminalTheme: String,
    selectedLanguage: String,
    viewModel: AdbViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sentinelManager = remember { AntiOverlaySentinelManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    val isGuardEnabled by sentinelManager.isGuardEnabled.collectAsState()
    val isAggressiveMode by sentinelManager.isAggressiveMode.collectAsState()
    val whitelist by sentinelManager.whitelist.collectAsState()
    val recentEvents by sentinelManager.recentEvents.collectAsState()
    val activeThreatCount by sentinelManager.activeThreatCount.collectAsState()
    val lastScanTime by sentinelManager.lastScanTime.collectAsState()
    val lastActionStatus by sentinelManager.lastActionStatus.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Sentry Controls, 1 = Audit Logs, 2 = Whitelist
    var isScanning by remember { mutableStateOf(false) }
    var scanLog by remember { mutableStateOf("") }
    var whitelistSearchQuery by remember { mutableStateOf("") }
    var installedAppsForWhitelist by remember { mutableStateOf<List<ApplicationInfo>>(emptyList()) }
    var showAddWhitelistDialog by remember { mutableStateOf(false) }

    // Theme colors
    val (textColor, bgColor, borderColor, accentColor, cardBgColor, secText) = getThemeColors(terminalTheme)

    // Pulse animation for radar
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Load installed apps for whitelist picker
    LaunchedEffect(showAddWhitelistDialog) {
        if (showAddWhitelistDialog) {
            withContext(Dispatchers.IO) {
                try {
                    val pm = context.packageManager
                    val list = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                        .filter { !sentinelManager.isWhitelisted(it.packageName) }
                        .sortedBy { try { pm.getApplicationLabel(it).toString().lowercase() } catch (_: Exception) { "" } }
                    installedAppsForWhitelist = list
                } catch (_: Exception) {
                    installedAppsForWhitelist = emptyList()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.95f)
                    .oneUiGlassCard(
                        shape = RoundedCornerShape(26.dp),
                        backgroundColor = bgColor.copy(alpha = 0.94f),
                        borderColor = if (isGuardEnabled) accentColor.copy(alpha = 0.8f) else Color(0xFFEF4444).copy(alpha = 0.8f),
                        borderWidth = 1.5.dp
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Top Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .scale(if (isGuardEnabled) pulseScale else 1f)
                                    .oneUiGlassCapsule(
                                        backgroundColor = if (isGuardEnabled) accentColor.copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                                        borderColor = if (isGuardEnabled) accentColor.copy(alpha = 0.8f) else Color(0xFFEF4444).copy(alpha = 0.8f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isGuardEnabled) Icons.Default.Shield else Icons.Default.GppBad,
                                    contentDescription = null,
                                    tint = if (isGuardEnabled) accentColor else Color(0xFFEF4444),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = Translations.get("anti_overlay_title", selectedLanguage),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = textColor
                                )
                                Text(
                                    text = if (isGuardEnabled) Translations.get("anti_overlay_sentry_active", selectedLanguage)
                                    else Translations.get("anti_overlay_sentry_inactive", selectedLanguage),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isGuardEnabled) Color(0xFF10B981) else Color(0xFFEF4444)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .oneUiGlassCapsule(
                                    backgroundColor = Color.White.copy(alpha = 0.08f),
                                    borderColor = Color.White.copy(alpha = 0.25f)
                                )
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Navigation Tabs (OneUI Glass Capsule Bar)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .oneUiGlassCapsule(
                                shape = RoundedCornerShape(16.dp),
                                backgroundColor = cardBgColor.copy(alpha = 0.65f),
                                borderColor = borderColor.copy(alpha = 0.35f)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TabButton(
                            text = if (selectedLanguage == "uz") "Qalqon" else if (selectedLanguage == "ru") "Щит" else "Sentry",
                            icon = Icons.Default.Security,
                            isSelected = activeTab == 0,
                            accentColor = accentColor,
                            textColor = textColor,
                            modifier = Modifier.weight(1f),
                            onClick = { activeTab = 0 }
                        )
                        TabButton(
                            text = if (selectedLanguage == "uz") "Jurnal (${recentEvents.size})" else if (selectedLanguage == "ru") "Журнал (${recentEvents.size})" else "Logs (${recentEvents.size})",
                            icon = Icons.Default.History,
                            isSelected = activeTab == 1,
                            accentColor = accentColor,
                            textColor = textColor,
                            modifier = Modifier.weight(1.1f),
                            onClick = { activeTab = 1 }
                        )
                        TabButton(
                            text = if (selectedLanguage == "uz") "Oq ro'yxat (${whitelist.size})" else if (selectedLanguage == "ru") "Белый список (${whitelist.size})" else "Whitelist (${whitelist.size})",
                            icon = Icons.Default.FactCheck,
                            isSelected = activeTab == 2,
                            accentColor = accentColor,
                            textColor = textColor,
                            modifier = Modifier.weight(1.2f),
                            onClick = { activeTab = 2 }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Content based on tab
                    when (activeTab) {
                        0 -> {
                            // SENTRY CONTROLS
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item {
                                    // Status Summary Banner (OneUI Glass Card)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .oneUiGlassCard(
                                                shape = RoundedCornerShape(16.dp),
                                                backgroundColor = cardBgColor.copy(alpha = 0.85f),
                                                borderColor = borderColor.copy(alpha = 0.45f)
                                            )
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = Translations.get("anti_overlay_threat_count", selectedLanguage),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = secText
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${recentEvents.size} ta xavf bartaraf etilgan",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = if (recentEvents.isEmpty()) textColor else Color(0xFFEF4444)
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .oneUiGlassCapsule(
                                                        shape = CircleShape,
                                                        backgroundColor = if (isGuardEnabled) Color(0xFF10B981).copy(alpha = 0.25f) else Color(0xFFEF4444).copy(alpha = 0.25f),
                                                        borderColor = if (isGuardEnabled) Color(0xFF10B981).copy(alpha = 0.8f) else Color(0xFFEF4444).copy(alpha = 0.8f)
                                                    )
                                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                                            ) {
                                                Text(
                                                    text = if (isGuardEnabled) "24/7 ON" else "OFF",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = if (isGuardEnabled) Color(0xFF10B981) else Color(0xFFEF4444)
                                                )
                                            }
                                        }
                                    }
                                }

                                item {
                                    // Toggle 1: 24/7 Background Sentry
                                    SettingSwitchCard(
                                        title = Translations.get("anti_overlay_toggle_title", selectedLanguage),
                                        description = Translations.get("anti_overlay_toggle_desc", selectedLanguage),
                                        icon = Icons.Default.Shield,
                                        isChecked = isGuardEnabled,
                                        accentColor = accentColor,
                                        textColor = textColor,
                                        secText = secText,
                                        cardBgColor = cardBgColor,
                                        borderColor = borderColor,
                                        onCheckedChange = { sentinelManager.setGuardEnabled(it) }
                                    )
                                }

                                item {
                                    // Toggle 2: Aggressive Mode (Auto-Revoke + Kill)
                                    SettingSwitchCard(
                                        title = Translations.get("anti_overlay_aggressive_title", selectedLanguage),
                                        description = Translations.get("anti_overlay_aggressive_desc", selectedLanguage),
                                        icon = Icons.Default.Bolt,
                                        isChecked = isAggressiveMode,
                                        accentColor = accentColor,
                                        textColor = textColor,
                                        secText = secText,
                                        cardBgColor = cardBgColor,
                                        borderColor = borderColor,
                                        onCheckedChange = { sentinelManager.setAggressiveMode(it) }
                                    )
                                }

                                item {
                                    // Accessibility Sub-millisecond Shield Card
                                    val isAccRunning = AntiOverlayAccessibilityService.isAccessibilityServiceRunning
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .oneUiGlassCard(
                                                shape = RoundedCornerShape(16.dp),
                                                backgroundColor = cardBgColor.copy(alpha = 0.85f),
                                                borderColor = if (isAccRunning) Color(0xFF10B981).copy(alpha = 0.7f) else borderColor.copy(alpha = 0.45f)
                                            )
                                            .padding(14.dp)
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AccessibilityNew,
                                                    contentDescription = null,
                                                    tint = if (isAccRunning) Color(0xFF10B981) else accentColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = Translations.get("anti_overlay_accessibility_card_title", selectedLanguage),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = textColor,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .oneUiGlassCapsule(
                                                            shape = CircleShape,
                                                            backgroundColor = if (isAccRunning) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                                                            borderColor = if (isAccRunning) Color(0xFF10B981).copy(alpha = 0.7f) else Color(0xFFEF4444).copy(alpha = 0.7f)
                                                        )
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = if (isAccRunning) "FAOL (0ms)" else "NOFAOL",
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isAccRunning) Color(0xFF10B981) else Color(0xFFEF4444)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = Translations.get("anti_overlay_accessibility_card_desc", selectedLanguage),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = secText
                                            )
                                            if (!isAccRunning) {
                                                Spacer(modifier = Modifier.height(10.dp))
                                                OneUiGlassCapsuleButton(
                                                    text = Translations.get("anti_overlay_open_accessibility", selectedLanguage),
                                                    icon = Icons.Default.OpenInNew,
                                                    accentColor = accentColor,
                                                    textColor = textColor,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    onClick = {
                                                        try {
                                                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                            }
                                                            (context as? com.example.MainActivity)?.isBypassingLock = true
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Sozlamalarni ochib bo'lmadi", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                item {
                                    // Deep Scan Action (OneUI Glass Capsule Button)
                                    OneUiGlassCapsuleButton(
                                        text = if (isScanning) {
                                            if (selectedLanguage == "uz") "Ekranni skanerlash..." else if (selectedLanguage == "ru") "Сканирование экранов..." else "Scanning Windows..."
                                        } else {
                                            Translations.get("anti_overlay_scan_now", selectedLanguage)
                                        },
                                        icon = if (isScanning) null else Icons.Default.Radar,
                                        accentColor = accentColor,
                                        textColor = textColor,
                                        isPrimary = true,
                                        isLoading = isScanning,
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            isScanning = true
                                            scope.launch {
                                                val res = sentinelManager.performManualScan()
                                                withContext(Dispatchers.Main) {
                                                    isScanning = false
                                                    scanLog = res
                                                    Toast.makeText(context, if (res.contains("🚨")) "Xavfli ilova zararsizlantirildi!" else "Tizim tekshirildi: toza", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    )
                                }

                                if (scanLog.isNotEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .oneUiGlassCard(
                                                    shape = RoundedCornerShape(12.dp),
                                                    backgroundColor = Color.Black.copy(alpha = 0.7f),
                                                    borderColor = borderColor.copy(alpha = 0.5f)
                                                )
                                                .padding(12.dp)
                                        ) {
                                            Text(
                                                text = scanLog,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = if (scanLog.contains("🚨")) Color(0xFFEF4444) else Color(0xFF10B981)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // AUDIT LOGS / THREAT HISTORY
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = Translations.get("anti_overlay_history_title", selectedLanguage),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = textColor
                                    )
                                    if (recentEvents.isNotEmpty()) {
                                        TextButton(onClick = { sentinelManager.clearAuditHistory() }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = Translations.get("anti_overlay_clear_history", selectedLanguage),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = Color(0xFFEF4444)
                                            )
                                        }
                                    }
                                }

                                if (recentEvents.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(42.dp))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = if (selectedLanguage == "uz") "Zararsizlantirish jurnali toza" else if (selectedLanguage == "ru") "Журнал угроз пуст" else "Audit Log is clean",
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = textColor
                                            )
                                            Text(
                                                text = if (selectedLanguage == "uz") "Hech qanday noqonuniy overlay hujumi qayd etilmadi." else "No unauthorized screen blocks detected.",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = secText,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(recentEvents, key = { "${it.id}_${it.timestamp}_${it.packageName}" }) { event ->
                                            AuditLogItemCard(
                                                event = event,
                                                textColor = textColor,
                                                secText = secText,
                                                cardBgColor = cardBgColor,
                                                borderColor = borderColor,
                                                accentColor = accentColor
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // WHITELIST MANAGEMENT
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = Translations.get("anti_overlay_whitelist_title", selectedLanguage),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = textColor
                                    )
                                    OneUiGlassCapsuleButton(
                                        text = if (selectedLanguage == "uz") "Ilova Qo'shish" else if (selectedLanguage == "ru") "Добавить" else "Add App",
                                        icon = Icons.Default.Add,
                                        accentColor = accentColor,
                                        textColor = textColor,
                                        onClick = { showAddWhitelistDialog = true }
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = Translations.get("anti_overlay_whitelist_desc", selectedLanguage),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = secText
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                val whitelistItems = whitelist.toList().distinct()
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(whitelistItems, key = { it }) { pkg ->
                                        val isCoreSystem = sentinelManager.defaultSystemWhitelist.contains(pkg)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .oneUiGlassCard(
                                                    shape = RoundedCornerShape(12.dp),
                                                    backgroundColor = cardBgColor.copy(alpha = 0.75f),
                                                    borderColor = borderColor.copy(alpha = 0.35f)
                                                )
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = pkg,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = textColor,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = if (isCoreSystem) "Tizim Himoyalangan (Default)" else "Foydalanuvchi Oq Ro'yxati",
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 9.sp,
                                                        color = if (isCoreSystem) accentColor else secText
                                                    )
                                                }

                                                if (!isCoreSystem) {
                                                    IconButton(
                                                        onClick = { sentinelManager.removeFromWhitelist(pkg) },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                                    }
                                                } else {
                                                    Icon(Icons.Default.Lock, contentDescription = "Locked", tint = secText, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal to add app to whitelist
    if (showAddWhitelistDialog) {
        Dialog(onDismissRequest = { showAddWhitelistDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(8.dp)
                    .oneUiGlassCard(
                        shape = RoundedCornerShape(22.dp),
                        backgroundColor = bgColor.copy(alpha = 0.95f),
                        borderColor = borderColor.copy(alpha = 0.8f)
                    )
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text(
                        text = if (selectedLanguage == "uz") "Oq Ro'yxatga Ilova Qo'shish" else "Add App to Whitelist",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = whitelistSearchQuery,
                        onValueChange = { whitelistSearchQuery = it },
                        placeholder = { Text("Qidirish / Search...", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = secText) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = borderColor.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val filtered = installedAppsForWhitelist.filter {
                        val label = context.packageManager.getApplicationLabel(it).toString()
                        label.contains(whitelistSearchQuery, ignoreCase = true) || it.packageName.contains(whitelistSearchQuery, ignoreCase = true)
                    }

                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filtered, key = { "${it.packageName}_${it.uid}" }) { appInfo ->
                            val label = context.packageManager.getApplicationLabel(appInfo).toString()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .oneUiGlassCard(
                                        shape = RoundedCornerShape(12.dp),
                                        backgroundColor = cardBgColor.copy(alpha = 0.75f),
                                        borderColor = borderColor.copy(alpha = 0.35f)
                                    )
                                    .clickable {
                                        sentinelManager.addToWhitelist(appInfo.packageName)
                                        showAddWhitelistDialog = false
                                        Toast.makeText(context, "$label oq ro'yxatga qo'shildi!", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = label, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = textColor)
                                        Text(text = appInfo.packageName, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = secText)
                                    }
                                    Icon(Icons.Default.AddCircle, contentDescription = null, tint = accentColor)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OneUiGlassCapsuleButton(
                        text = "Yopish",
                        accentColor = accentColor,
                        textColor = textColor,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showAddWhitelistDialog = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.22f) else Color.Transparent)
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) accentColor.copy(alpha = 0.6f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) accentColor else textColor.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected) accentColor else textColor.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SettingSwitchCard(
    title: String,
    description: String,
    icon: ImageVector,
    isChecked: Boolean,
    accentColor: Color,
    textColor: Color,
    secText: Color,
    cardBgColor: Color,
    borderColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .oneUiGlassCard(
                shape = RoundedCornerShape(16.dp),
                backgroundColor = cardBgColor.copy(alpha = 0.85f),
                borderColor = borderColor.copy(alpha = 0.45f)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .oneUiGlassCapsule(
                        shape = CircleShape,
                        backgroundColor = if (isChecked) accentColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                        borderColor = if (isChecked) accentColor.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isChecked) accentColor else textColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    color = secText
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = accentColor,
                    uncheckedThumbColor = Color.LightGray,
                    uncheckedTrackColor = Color.Black.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
private fun AuditLogItemCard(
    event: AntiOverlayEventEntity,
    textColor: Color,
    secText: Color,
    cardBgColor: Color,
    borderColor: Color,
    accentColor: Color
) {
    var expanded by remember { mutableStateOf(false) }
    val timeFormatted = remember(event.timestamp) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        sdf.format(Date(event.timestamp))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .oneUiGlassCard(
                shape = RoundedCornerShape(14.dp),
                backgroundColor = cardBgColor.copy(alpha = 0.8f),
                borderColor = if (event.threatLevel == "CRITICAL") Color(0xFFEF4444).copy(alpha = 0.7f) else borderColor.copy(alpha = 0.45f)
            )
            .clickable { expanded = !expanded }
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .oneUiGlassCapsule(
                                shape = CircleShape,
                                backgroundColor = Color(0xFFEF4444).copy(alpha = 0.2f),
                                borderColor = Color(0xFFEF4444).copy(alpha = 0.6f)
                            )
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = event.threatLevel,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = Color(0xFFEF4444)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = event.appName,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = timeFormatted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp,
                    color = secText
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = event.packageName,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = secText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = "⚡ ${event.actionTaken}",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.5.sp,
                color = Color(0xFF10B981),
                fontWeight = FontWeight.Bold
            )

            if (expanded && event.details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .oneUiGlassCard(
                            shape = RoundedCornerShape(8.dp),
                            backgroundColor = Color.Black.copy(alpha = 0.6f),
                            borderColor = borderColor.copy(alpha = 0.3f)
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        text = event.details,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        color = textColor.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}
