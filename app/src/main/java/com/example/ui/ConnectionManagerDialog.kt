package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.Translations
import com.example.adb.device.AdbDevice
import com.example.adb.device.ConnectionType
import com.example.adb.device.DeviceStatus
import com.example.adb.device.MultiCommandResult
import com.example.viewmodel.AdbViewModel

private data class ConnectionThemeColors(
    val bgColor: Color,
    val cardBg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accentColor: Color,
    val borderColor: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionManagerDialog(
    viewModel: AdbViewModel,
    terminalTheme: String,
    selectedLanguage: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val devices by viewModel.devices.collectAsState()
    val activeDeviceId by viewModel.activeDeviceId.collectAsState()
    val isMultiDeviceMode by viewModel.isMultiDeviceMode.collectAsState()
    val isExecutingMulti by viewModel.isExecutingMulti.collectAsState()
    val multiResults by viewModel.multiCommandResults.collectAsState()
    val lastCommand by viewModel.lastBroadcastCommand.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val currentPortInput by viewModel.portInput.collectAsState()

    var activeTab by rememberSaveable { mutableIntStateOf(0) } // 0: Wireless ADB, 1: USB OTG, 2: Registry & Multi
    var customCommand by rememberSaveable { mutableStateOf("getprop ro.build.version.release") }
    var showAddDeviceDialog by rememberSaveable { mutableStateOf(false) }
    var selectedResultDetails by remember { mutableStateOf<MultiCommandResult?>(null) }

    // Quick Connect form states
    var wirelessIp by rememberSaveable {
        mutableStateOf(
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                val ipAddress = wifiManager?.connectionInfo?.ipAddress ?: 0
                if (ipAddress != 0) {
                    "${ipAddress and 0xff}.${ipAddress shr 8 and 0xff}.${ipAddress shr 16 and 0xff}.${ipAddress shr 24 and 0xff}"
                } else {
                    "127.0.0.1"
                }
            } catch (e: Exception) {
                "127.0.0.1"
            }
        )
    }
    var wirelessPort by rememberSaveable {
        mutableStateOf(
            if (currentPortInput.isNotBlank() && !currentPortInput.contains(":")) currentPortInput
            else if (currentPortInput.contains(":")) currentPortInput.substringAfterLast(":")
            else "5555"
        )
    }

    val selectedCount = devices.count { it.isSelected }

    // Modern Terminal Color Scheme
    val (bgColor, cardBg, textPrimary, textSecondary, accentColor, borderColor) = when (terminalTheme) {
        "light" -> ConnectionThemeColors(
            Color(0xFFF8FAFC), Color(0xFFFFFFFF), Color(0xFF0F172A),
            Color(0xFF475569), Color(0xFF0284C7), Color(0xFFE2E8F0)
        )
        "matrix" -> ConnectionThemeColors(
            Color(0xFF020C02), Color(0xFF061806), Color(0xFF22C55E),
            Color(0xFF16A34A), Color(0xFF4ADE80), Color(0xFF14532D)
        )
        "cyberpunk" -> ConnectionThemeColors(
            Color(0xFF0F051D), Color(0xFF1C0B36), Color(0xFFF43F5E),
            Color(0xFFA855F7), Color(0xFFEC4899), Color(0xFF6B21A8)
        )
        else -> ConnectionThemeColors(
            Color(0xFF0D1117), Color(0xFF161B22), Color(0xFFF0F6FC),
            Color(0xFF8B949E), Color(0xFF58A6FF), Color(0xFF30363D)
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .heightIn(max = 660.dp)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            color = bgColor,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // Header Bar - Compact & Clean
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.15f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = "Devices",
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = Translations.get("cm_title", selectedLanguage),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = textPrimary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (isConnected) Color(0xFF22C55E) else Color(0xFF94A3B8), CircleShape)
                                )
                                Text(
                                    text = if (isConnected) (if (selectedLanguage == "uz") "Faol Ulangan" else "Connected") else (if (selectedLanguage == "uz") "Ulanmagan" else "Disconnected"),
                                    fontSize = 10.sp,
                                    color = if (isConnected) Color(0xFF22C55E) else textSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Compact Segmented Top Navigation Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardBg, RoundedCornerShape(8.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Tab 0: Wireless ADB
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (activeTab == 0) accentColor else Color.Transparent)
                            .clickable { activeTab = 0 }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Wifi,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (activeTab == 0) Color.White else textSecondary
                            )
                            Text(
                                text = if (selectedLanguage == "uz") "Simsiz ADB" else if (selectedLanguage == "ru") "Wi-Fi ADB" else "Wireless",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeTab == 0) Color.White else textSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Tab 1: USB OTG
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (activeTab == 1) accentColor else Color.Transparent)
                            .clickable { activeTab = 1 }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Usb,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (activeTab == 1) Color.White else textSecondary
                            )
                            Text(
                                text = "USB OTG",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeTab == 1) Color.White else textSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Tab 2: Devices Registry & Cluster
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (activeTab == 2) accentColor else Color.Transparent)
                            .clickable { activeTab = 2 }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.ListAlt,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (activeTab == 2) Color.White else textSecondary
                            )
                            Text(
                                text = "${if (selectedLanguage == "uz") "Qurilmalar" else if (selectedLanguage == "ru") "Устройства" else "Devices"} (${devices.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeTab == 2) Color.White else textSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Contents
                when (activeTab) {
                    0 -> {
                        // TAB 0: WIRELESS ADB DIRECT CONNECT
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Quick Connect Card
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = cardBg,
                                border = BorderStroke(1.dp, borderColor.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = if (selectedLanguage == "uz") "SIMSIZ ADB GA TO'G'RIDAN-TO'G'RI ULANISH:" else if (selectedLanguage == "ru") "ПРЯМОЕ ПОДКЛЮЧЕНИЕ WIRELESS ADB:" else "DIRECT WIRELESS ADB CONNECTION:",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // IP input
                                        OutlinedTextField(
                                            value = wirelessIp,
                                            onValueChange = { wirelessIp = it },
                                            label = { Text("IP Manzil", fontSize = 11.sp) },
                                            placeholder = { Text("127.0.0.1", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1.4f),
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = textPrimary
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                                        )

                                        // Port input
                                        OutlinedTextField(
                                            value = wirelessPort,
                                            onValueChange = { wirelessPort = it },
                                            label = { Text("Port", fontSize = 11.sp) },
                                            placeholder = { Text("5555", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = textPrimary
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                        )
                                    }

                                    // Action Buttons Row (Connect / Disconnect)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val cleanIp = wirelessIp.trim()
                                                val cleanPort = wirelessPort.trim()
                                                val fullTarget = if (cleanIp.isNotBlank() && cleanIp != "127.0.0.1" && cleanIp != "localhost") {
                                                    "$cleanIp:$cleanPort"
                                                } else {
                                                    cleanPort
                                                }
                                                viewModel.onPortChange(fullTarget)
                                                viewModel.connectAdb()
                                            },
                                            enabled = !isConnecting && wirelessPort.isNotBlank(),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp)
                                        ) {
                                            if (isConnecting) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (selectedLanguage == "uz") "Ulanmoqda..." else "Connecting...",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            } else {
                                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (selectedLanguage == "uz") "Ulanish (Connect)" else if (selectedLanguage == "ru") "Подключить" else "Connect ADB",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }

                                        if (isConnected) {
                                            OutlinedButton(
                                                onClick = { viewModel.disconnectAdb() },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(38.dp)
                                            ) {
                                                Text(
                                                    text = if (selectedLanguage == "uz") "Uzish" else "Disconnect",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Developer Options Direct Shortcut & Instruction
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = cardBg,
                                border = BorderStroke(1.dp, borderColor.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.HelpOutline, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                        Text(
                                            text = if (selectedLanguage == "uz") "Qanday ulanish kerak? (Android 11+)" else if (selectedLanguage == "ru") "Как подключиться? (Android 11+)" else "How to connect? (Android 11+)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Text(
                                        text = if (selectedLanguage == "uz") {
                                            "1. Telefon Sozlamalari -> Dasturchi parametrlari (Developer Options) ga kiring.\n2. 'Simsiz nosozliklarni tuzatish (Wireless Debugging)' ni yoqing.\n3. U yerdagi IP va ULANISH PORTI (masalan: 38452) ni yuqoriga yozing va 'Ulanish' tugmasini bosing."
                                        } else if (selectedLanguage == "ru") {
                                            "1. Откройте Настройки -> Для разработчиков.\n2. Включите 'Отладка по Wi-Fi'.\n3. Введите IP и Порт подключения (например: 38452) выше и нажмите 'Подключить'."
                                        } else {
                                            "1. Open Settings -> Developer Options.\n2. Enable 'Wireless Debugging'.\n3. Enter the IP & Connection Port (e.g. 38452) above and tap 'Connect ADB'."
                                        },
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp,
                                        color = textSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )

                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                                (context as? com.example.MainActivity)?.isBypassingLock = true
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                try {
                                                    val intent = Intent(Settings.ACTION_SETTINGS)
                                                    (context as? com.example.MainActivity)?.isBypassingLock = true
                                                    context.startActivity(intent)
                                                } catch (e2: Exception) {
                                                    Toast.makeText(context, "Cannot open Settings", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.5.dp, borderColor),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(15.dp), tint = accentColor)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (selectedLanguage == "uz") "Dasturchi Sozlamalarini Ochish" else if (selectedLanguage == "ru") "Открыть меню разработчика" else "Open Developer Options",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // TAB 1: USB OTG
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = cardBg,
                                border = BorderStroke(1.dp, borderColor.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = if (selectedLanguage == "uz") "USB OTG ORQALI BOSHQA TELEFONNI ULASH:" else if (selectedLanguage == "ru") "ПОДКЛЮЧЕНИЕ ЧЕРЕЗ USB OTG:" else "USB OTG DIRECT CONNECTION:",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor
                                    )

                                    Text(
                                        text = if (selectedLanguage == "uz") {
                                            "1. Type-C OTG kabel orqali 2-telefonni ushbu telefonga ulang.\n2. Maqsadli telefonda 'USB orqali nosozliklarni tuzatish (USB Debugging)' ni yoqing.\n3. Quyidagi 'USB Qurilmalarni Qidirish' tugmasini bosing."
                                        } else if (selectedLanguage == "ru") {
                                            "1. Подключите второй телефон через кабель Type-C OTG.\n2. На целевом телефоне включите 'Отладка по USB'.\n3. Нажмите кнопку 'Сканировать USB' ниже."
                                        } else {
                                            "1. Connect the target device using a Type-C OTG cable.\n2. Enable 'USB Debugging' on the target device.\n3. Tap 'Scan USB Devices' below."
                                        },
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp,
                                        color = textSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )

                                    Button(
                                        onClick = {
                                            viewModel.scanUsbDevices()
                                            Toast.makeText(context, if (selectedLanguage == "uz") "USB OTG shinalari qidirilmoqda..." else "Scanning USB OTG bus...", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(38.dp)
                                    ) {
                                        Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (selectedLanguage == "uz") "USB Qurilmalarni Qidirish (Scan USB)" else if (selectedLanguage == "ru") "Поиск USB устройств" else "Scan USB Devices",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // TAB 2: DEVICE REGISTRY & MULTI-DEVICE CLUSTER
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Mode selector & Add Button Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Mode Toggle (Single vs Multi)
                                Row(
                                    modifier = Modifier
                                        .background(cardBg, RoundedCornerShape(8.dp))
                                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                        .padding(2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (!isMultiDeviceMode) accentColor else Color.Transparent)
                                            .clickable { viewModel.setMultiDeviceMode(false) }
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = if (selectedLanguage == "uz") "Yakka" else "Single",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (!isMultiDeviceMode) Color.White else textSecondary,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isMultiDeviceMode) accentColor else Color.Transparent)
                                            .clickable { viewModel.setMultiDeviceMode(true) }
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = if (selectedLanguage == "uz") "Klaster (Multi)" else "Cluster",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isMultiDeviceMode) Color.White else textSecondary,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                val addBtnContentColor = if (accentColor.luminance() > 0.5f) Color.Black else Color.White
                                Button(
                                    onClick = { showAddDeviceDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentColor,
                                        contentColor = addBtnContentColor
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = addBtnContentColor)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (selectedLanguage == "uz") "Qo'shish" else "Add",
                                        fontSize = 11.sp,
                                        color = addBtnContentColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Multi-device selection bar if multi-mode
                            if (isMultiDeviceMode) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = cardBg,
                                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "$selectedCount tanlandi",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = accentColor,
                                            fontFamily = FontFamily.Monospace
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(
                                                onClick = { viewModel.selectAllDevices() },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = Translations.get("cm_select_all", selectedLanguage),
                                                    fontSize = 10.sp,
                                                    color = accentColor,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            TextButton(
                                                onClick = { viewModel.deselectAllDevices() },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = Translations.get("cm_deselect_all", selectedLanguage),
                                                    fontSize = 10.sp,
                                                    color = textSecondary,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Devices List
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (devices.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 20.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (selectedLanguage == "uz") "Qurilmalar ro'yxati bo'sh. 'Simsiz ADB' bo'limidan ulaning." else "No registered devices yet.",
                                                fontSize = 11.sp,
                                                color = textSecondary,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }

                                items(devices, key = { it.id }) { device ->
                                    DeviceCardItem(
                                        device = device,
                                        isMultiMode = isMultiDeviceMode,
                                        isActiveDevice = device.id == activeDeviceId,
                                        cardBg = cardBg,
                                        borderColor = borderColor,
                                        textPrimary = textPrimary,
                                        textSecondary = textSecondary,
                                        accentColor = accentColor,
                                        selectedLanguage = selectedLanguage,
                                        onToggleSelect = { viewModel.toggleDeviceSelection(device.id) },
                                        onMakeActive = { viewModel.setActiveDevice(device.id) },
                                        onRemove = { viewModel.removeAdbDevice(device.id) }
                                    )
                                }

                                if (isMultiDeviceMode && devices.isNotEmpty()) {
                                    item {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        MultiCommandBroadcastPanel(
                                            customCommand = customCommand,
                                            onCommandChange = { customCommand = it },
                                            isExecuting = isExecutingMulti,
                                            selectedCount = selectedCount,
                                            cardBg = cardBg,
                                            borderColor = borderColor,
                                            textPrimary = textPrimary,
                                            textSecondary = textSecondary,
                                            accentColor = accentColor,
                                            selectedLanguage = selectedLanguage,
                                            onExecute = { viewModel.executeMultiDeviceCommand(customCommand) }
                                        )
                                    }

                                    if (multiResults.isNotEmpty()) {
                                        item {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            ResultsComparisonTable(
                                                results = multiResults,
                                                command = lastCommand,
                                                cardBg = cardBg,
                                                borderColor = borderColor,
                                                textPrimary = textPrimary,
                                                textSecondary = textSecondary,
                                                accentColor = accentColor,
                                                selectedLanguage = selectedLanguage,
                                                onShowDetails = { selectedResultDetails = it },
                                                onCopyAll = {
                                                    val sb = StringBuilder()
                                                    sb.append("=== MULTI-DEVICE ADB EXECUTION RESULTS ===\n")
                                                    sb.append("Command: $lastCommand\n\n")
                                                    multiResults.forEach { r ->
                                                        sb.append("[${r.deviceName}] (${r.address}) -> Exit ${r.exitCode} (${r.executionTimeMs}ms)\n")
                                                        sb.append(r.output).append("\n---\n")
                                                    }
                                                    clipboardManager.setText(AnnotatedString(sb.toString()))
                                                    Toast.makeText(context, "Results copied to clipboard", Toast.LENGTH_SHORT).show()
                                                }
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
    }

    // Add Device Dialog
    if (showAddDeviceDialog) {
        AddDeviceDialog(
            cardBg = cardBg,
            borderColor = borderColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            accentColor = accentColor,
            selectedLanguage = selectedLanguage,
            onDismiss = { showAddDeviceDialog = false },
            onAdd = { name, model, type, address ->
                viewModel.addNewDevice(name, model, type, address)
                showAddDeviceDialog = false
            }
        )
    }

    // Result Output Inspection Dialog
    if (selectedResultDetails != null) {
        val res = selectedResultDetails!!
        AlertDialog(
            onDismissRequest = { selectedResultDetails = null },
            title = {
                Text(
                    text = "${res.deviceName} Output",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Command: ${res.command}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = accentColor
                    )
                    Text(
                        text = "Execution: ${res.executionTimeMs}ms • Exit Code: ${res.exitCode}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black, RoundedCornerShape(6.dp))
                            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = res.output.ifBlank { "(No output / empty response)" },
                            color = Color(0xFF38EF7D),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                val copyBtnContent = if (accentColor.luminance() > 0.5f) Color.Black else Color.White
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(res.output))
                        Toast.makeText(context, "Output copied", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = copyBtnContent
                    )
                ) {
                    Text("Copy Output", color = copyBtnContent)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedResultDetails = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun DeviceCardItem(
    device: AdbDevice,
    isMultiMode: Boolean,
    isActiveDevice: Boolean,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    selectedLanguage: String,
    onToggleSelect: () -> Unit,
    onMakeActive: () -> Unit,
    onRemove: () -> Unit
) {
    val (typeColor, typeLabel) = when (device.type) {
        ConnectionType.USB -> Pair(Color(0xFFA855F7), "USB")
        ConnectionType.WIRELESS_ADB -> Pair(Color(0xFF10B981), "Wireless ADB")
        ConnectionType.TCPIP -> Pair(Color(0xFF3B82F6), "TCP/IP")
    }

    val (statusColor, statusText) = when (device.status) {
        DeviceStatus.CONNECTED -> Pair(Color(0xFF22C55E), Translations.get("cm_status_connected", selectedLanguage))
        DeviceStatus.CONNECTING -> Pair(Color(0xFFEAB308), Translations.get("cm_status_connecting", selectedLanguage))
        DeviceStatus.DISCONNECTED -> Pair(Color(0xFF94A3B8), Translations.get("cm_status_disconnected", selectedLanguage))
        DeviceStatus.UNAUTHORIZED -> Pair(Color(0xFFF97316), Translations.get("cm_status_unauthorized", selectedLanguage))
        DeviceStatus.ERROR -> Pair(Color(0xFFEF4444), Translations.get("cm_status_error", selectedLanguage))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .border(
                width = if (isActiveDevice) 1.5.dp else 1.dp,
                color = if (isActiveDevice) accentColor else borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Selection Checkbox (Multi-Device Mode)
            if (isMultiMode) {
                Checkbox(
                    checked = device.isSelected,
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = accentColor,
                        uncheckedColor = textSecondary
                    ),
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("checkbox_${device.id}")
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(statusColor, CircleShape)
                )
            }

            // Device Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = device.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = textPrimary
                    )

                    // Connection Type Badge
                    Box(
                        modifier = Modifier
                            .background(typeColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .border(1.dp, typeColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = typeLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = typeColor,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Active badge if currently primary
                    if (isActiveDevice) {
                        Box(
                            modifier = Modifier
                                .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = Translations.get("cm_primary_badge", selectedLanguage),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Model & Address
                Text(
                    text = "${device.model} • ${device.address}",
                    fontSize = 11.sp,
                    color = textSecondary,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Stats: Battery, Ping, OS
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(imageVector = Icons.Default.BatteryChargingFull, contentDescription = null, tint = textSecondary, modifier = Modifier.size(12.dp))
                        Text(text = device.batteryLevel, fontSize = 10.sp, color = textSecondary, fontFamily = FontFamily.Monospace)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = textSecondary, modifier = Modifier.size(12.dp))
                        Text(text = "${device.pingMs}ms", fontSize = 10.sp, color = textSecondary, fontFamily = FontFamily.Monospace)
                    }
                    Text(text = device.androidVersion, fontSize = 10.sp, color = textSecondary, fontFamily = FontFamily.Monospace)
                }
            }

            // Right side status & action
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.size(7.dp).background(statusColor, CircleShape))
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isActiveDevice) {
                        OutlinedButton(
                            onClick = onMakeActive,
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(
                                text = Translations.get("cm_make_active", selectedLanguage),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    if (!device.isLocalDevice) {
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier
                                .size(26.dp)
                                .background(Color(0xFFEF4444).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MultiCommandBroadcastPanel(
    customCommand: String,
    onCommandChange: (String) -> Unit,
    isExecuting: Boolean,
    selectedCount: Int,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    selectedLanguage: String,
    onExecute: () -> Unit
) {
    val presets = listOf(
        "getprop ro.build.version.release",
        "getprop ro.product.model",
        "dumpsys battery | grep level",
        "uptime",
        "wm size",
        "df -h /data"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = Translations.get("cm_parallel_broadcast_title", selectedLanguage),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = textPrimary
                )
            }

            Text(
                text = "$selectedCount ${Translations.get("cm_targets_count", selectedLanguage)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                fontFamily = FontFamily.Monospace
            )
        }

        // Fast Preset Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(presets) { preset ->
                val isSelected = preset == customCommand
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.3f))
                        .border(
                            1.dp,
                            if (isSelected) accentColor else borderColor,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onCommandChange(preset) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = preset,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isSelected) accentColor else textSecondary
                    )
                }
            }
        }

        // Command Input & Run Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                value = customCommand,
                onValueChange = onCommandChange,
                modifier = Modifier
                    .weight(1f)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = textPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { onExecute() }),
                cursorBrush = SolidColor(accentColor)
            )

            val runMultiContent = if (accentColor.luminance() > 0.5f) Color.Black else Color.White
            Button(
                onClick = onExecute,
                enabled = !isExecuting && selectedCount > 0 && customCommand.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = runMultiContent
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(38.dp)
                    .testTag("run_multi_command_button")
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = runMultiContent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Run",
                        tint = runMultiContent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = Translations.get("cm_btn_run", selectedLanguage),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = runMultiContent,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun ResultsComparisonTable(
    results: List<MultiCommandResult>,
    command: String,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    selectedLanguage: String,
    onShowDetails: (MultiCommandResult) -> Unit,
    onCopyAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Table Title & Copy action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = Translations.get("cm_comparison_table_title", selectedLanguage),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = textPrimary
                )
                Text(
                    text = "${Translations.get("cm_run_broadcast", selectedLanguage)}: $command",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onCopyAll,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy all",
                    tint = textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (selectedLanguage) {
                    "uz" -> "QURILMA"
                    "ru" -> "УСТРОЙСТВО"
                    else -> "DEVICE"
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = textSecondary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1.2f)
            )
            Text(
                text = when (selectedLanguage) {
                    "uz" -> "TURI"
                    "ru" -> "ТИП"
                    else -> "TYPE"
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = textSecondary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(0.9f)
            )
            Text(
                text = when (selectedLanguage) {
                    "uz" -> "HOLATI"
                    "ru" -> "СТАТУС"
                    else -> "STATUS"
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = textSecondary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(0.9f)
            )
            Text(
                text = when (selectedLanguage) {
                    "uz" -> "VAQT"
                    "ru" -> "ВРЕМЯ"
                    else -> "TIME"
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = textSecondary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(0.7f)
            )
            Text(
                text = when (selectedLanguage) {
                    "uz" -> "NATIJA"
                    "ru" -> "ВЫВОД"
                    else -> "OUTPUT"
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = textSecondary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1.8f)
            )
        }

        // Table Rows
        results.forEach { result ->
            val (typeColor, typeName) = when (result.connectionType) {
                ConnectionType.USB -> Pair(Color(0xFFA855F7), "USB")
                ConnectionType.WIRELESS_ADB -> Pair(Color(0xFF10B981), "Wireless")
                ConnectionType.TCPIP -> Pair(Color(0xFF3B82F6), "TCP/IP")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onShowDetails(result) }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Device name
                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        text = result.deviceName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = textPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = result.address,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = textSecondary,
                        maxLines = 1
                    )
                }

                // Type Chip
                Box(
                    modifier = Modifier
                        .weight(0.9f)
                        .padding(end = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(typeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = typeName,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = typeColor,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Status
                Row(
                    modifier = Modifier.weight(0.9f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (result.isSuccess) Color(0xFF22C55E) else Color(0xFFEF4444), CircleShape)
                    )
                    Text(
                        text = if (result.isSuccess) "OK (0)" else "ERR (${result.exitCode})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (result.isSuccess) Color(0xFF22C55E) else Color(0xFFEF4444),
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Time / Latency
                Text(
                    text = "${result.executionTimeMs}ms",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = textSecondary,
                    modifier = Modifier.weight(0.7f)
                )

                // Output preview
                Box(
                    modifier = Modifier
                        .weight(1.8f)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = result.output.lines().firstOrNull() ?: "(empty)",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (result.isSuccess) Color(0xFF38EF7D) else Color(0xFFEF4444),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            HorizontalDivider(color = borderColor.copy(alpha = 0.4f))
        }
    }
}

@Composable
fun AddDeviceDialog(
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    selectedLanguage: String,
    onDismiss: () -> Unit,
    onAdd: (name: String, model: String, type: ConnectionType, address: String) -> Unit
) {
    val context = LocalContext.current
    var deviceName by rememberSaveable { mutableStateOf("") }
    var deviceModel by rememberSaveable { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ConnectionType.WIRELESS_ADB) }
    
    // Auto-detect local subnet instead of hardcoding 192.168.1.
    var hostAddress by rememberSaveable { 
        mutableStateOf(
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                val ipAddress = wifiManager.connectionInfo.ipAddress
                if (ipAddress != 0) {
                    "${ipAddress and 0xff}.${ipAddress shr 8 and 0xff}.${ipAddress shr 16 and 0xff}."
                } else {
                    ""
                }
            } catch (e: Exception) {
                ""
            }
        ) 
    }
    var port by rememberSaveable { mutableStateOf("5555") }

    val realManufacturer = android.os.Build.MANUFACTURER
    val realModel = android.os.Build.MODEL
    val defaultDeviceName = if (realModel.lowercase().startsWith(realManufacturer.lowercase())) {
        realModel.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
    } else {
        "${realManufacturer.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }} $realModel"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = Translations.get("cm_add_device_title", selectedLanguage),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Connection Type Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ConnectionType.values().forEach { type ->
                        val isSel = type == selectedType
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) accentColor else cardBg)
                                .border(1.dp, if (isSel) accentColor else borderColor, RoundedCornerShape(8.dp))
                                .clickable { selectedType = type }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = type.title,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color.White else textSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text(Translations.get("cm_device_name_label", selectedLanguage)) },
                    placeholder = { Text(defaultDeviceName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = deviceModel,
                    onValueChange = { deviceModel = it },
                    label = { Text(Translations.get("cm_device_model_label", selectedLanguage)) },
                    placeholder = { Text(realModel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedType != ConnectionType.USB) {
                    OutlinedTextField(
                        value = hostAddress,
                        onValueChange = { hostAddress = it },
                        label = { Text(Translations.get("cm_ip_address_label", selectedLanguage)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text(Translations.get("cm_port_label", selectedLanguage)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = when (selectedLanguage) {
                            "uz" -> "Qurilmani USB OTG kabel orqali ulang va 'USB OTG izlash' tugmasini bosing."
                            "ru" -> "Подключите устройство через кабель USB OTG и нажмите 'Поиск USB'."
                            else -> "Connect device via USB OTG cable and tap Scan USB in manager."
                        },
                        fontSize = 11.sp,
                        color = textSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            val addBtnContent = if (accentColor.luminance() > 0.5f) Color.Black else Color.White
            Button(
                onClick = {
                    val finalName = deviceName.ifBlank { defaultDeviceName }
                    val finalModel = deviceModel.ifBlank { realModel }
                    val finalAddr = if (selectedType == ConnectionType.USB) "USB OTG Device" else "$hostAddress:$port"
                    onAdd(finalName, finalModel, selectedType, finalAddr)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = addBtnContent
                )
            ) {
                Text(Translations.get("cm_btn_add", selectedLanguage), color = addBtnContent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Translations.get("cm_btn_cancel", selectedLanguage))
            }
        }
    )
}
