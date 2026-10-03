package com.example.ui

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.media.GeneratedMediaItem
import java.io.File
import kotlin.math.sin

@Composable
fun GeneratedMediaCard(
    media: GeneratedMediaItem,
    primaryColor: Color,
    surfaceColor: Color,
    textColor: Color,
    selectedLanguage: String,
    onOpenFullScreenImage: (String) -> Unit,
    onOpenCodePreview: (String, String) -> Unit,
    onPlayVideo: (String) -> Unit
) {
    val context = LocalContext.current

    when (media.type.uppercase()) {
        "IMAGE" -> {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                Column {
                    // Image container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .background(Color(0xFF0F172A))
                            .clickable {
                                onOpenFullScreenImage(media.localFilePath ?: media.uriOrUrl)
                            }
                    ) {
                        AsyncImage(
                            model = media.localFilePath?.let { File(it) } ?: media.uriOrUrl,
                            contentDescription = media.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Top Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            modifier = Modifier
                                .padding(10.dp)
                                .align(Alignment.TopStart)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                
                                val ext = media.localFilePath?.substringAfterLast('.')?.uppercase() ?: media.mimeType?.substringAfterLast('/')?.uppercase() ?: "JPG"
                                val textLabel = when(selectedLanguage) {
                                    "uz" -> "RASM • $ext"
                                    "ru" -> "ИЗОБРАЖЕНИЕ • $ext"
                                    else -> "IMAGE • $ext"
                                }
                                Text(
                                    text = textLabel,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Size badge
                        media.fileSizeFormatted?.let { size ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.75f),
                                modifier = Modifier
                                    .padding(10.dp)
                                    .align(Alignment.TopEnd)
                            ) {
                                Text(
                                    text = size,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Content details
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = media.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!media.description.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = media.description,
                                color = textColor.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                                maxLines = 2,
                                lineHeight = 16.sp
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onOpenFullScreenImage(media.localFilePath ?: media.uriOrUrl) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(when(selectedLanguage){ "uz" -> "Kattalashtirish"; "ru" -> "Увеличить"; else -> "Zoom" }, fontSize = 12.sp)
                            }

                            Button(
                                onClick = { shareMedia(context, media, selectedLanguage) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(when(selectedLanguage){ "uz" -> "Ulashish"; "ru" -> "Поделиться"; else -> "Share" }, fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        "AUDIO" -> {
            AudioPlayerCard(
                media = media,
                primaryColor = primaryColor,
                surfaceColor = surfaceColor,
                textColor = textColor,
                selectedLanguage = selectedLanguage
            )
        }

        "VIDEO" -> {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                Column {
                    // Video thumbnail area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF312E81))
                                )
                            )
                            .clickable {
                                onPlayVideo(media.localFilePath ?: media.uriOrUrl)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Ambient glow orb
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF6366F1).copy(alpha = 0.25f))
                        )

                        // Play Button
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF6366F1),
                            modifier = Modifier.size(56.dp),
                            shadowElevation = 8.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // Top Badges
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            modifier = Modifier
                                .padding(10.dp)
                                .align(Alignment.TopStart)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(5.dp))
                                
                                val ext = media.localFilePath?.substringAfterLast('.')?.uppercase() ?: media.mimeType?.substringAfterLast('/')?.uppercase() ?: "MP4"
                                val textLabel = when(selectedLanguage) {
                                    "uz" -> "VIDEO • $ext"
                                    "ru" -> "ВИДЕО • $ext"
                                    else -> "VIDEO • $ext"
                                }
                                Text(textLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        media.fileSizeFormatted?.let { size ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.75f),
                                modifier = Modifier
                                    .padding(10.dp)
                                    .align(Alignment.TopEnd)
                            ) {
                                Text(size, color = Color(0xFFCBD5E1), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                            }
                        }
                    }

                    // Bottom info
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = media.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!media.description.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = media.description,
                                color = textColor.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                                maxLines = 2
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onPlayVideo(media.localFilePath ?: media.uriOrUrl) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(when(selectedLanguage){ "uz" -> "Ko'rish"; "ru" -> "Смотреть"; else -> "Play" }, fontSize = 12.sp, color = Color.White)
                            }

                            OutlinedButton(
                                onClick = { shareMedia(context, media, selectedLanguage) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(when(selectedLanguage){ "uz" -> "Ulashish"; "ru" -> "Поделиться"; else -> "Share" }, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        "FILE" -> {
            val ext = media.title.substringAfterLast('.', "txt").lowercase()
            val (badgeColor, extLabel) = when (ext) {
                "py" -> Color(0xFF10B981) to "PYTHON"
                "sh", "bash" -> Color(0xFF059669) to "SHELL"
                "html" -> Color(0xFFF97316) to "HTML"
                "json" -> Color(0xFFA855F7) to "JSON"
                "js", "ts" -> Color(0xFFEAB308) to "JS"
                "kt" -> Color(0xFF8B5CF6) to "KOTLIN"
                "java" -> Color(0xFFEF4444) to "JAVA"
                "csv" -> Color(0xFF06B6D4) to "CSV"
                else -> Color(0xFF3B82F6) to ext.uppercase()
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Colored File Icon Badge
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(badgeColor.copy(alpha = 0.2f))
                                .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = extLabel.take(3),
                                color = badgeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = media.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${media.fileSizeFormatted ?: ""} ${if (media.fileSizeFormatted != null) "• " else ""}" + when(selectedLanguage){ "uz" -> "Fayl"; "ru" -> "Файл"; else -> "File" },
                                color = textColor.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (!media.description.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = media.description,
                            color = textColor.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Open external
                        OutlinedButton(
                            onClick = {
                                openFileWithDefaultApp(context, media, selectedLanguage)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(when(selectedLanguage){ "uz" -> "Ochish"; "ru" -> "Открыть"; else -> "Open" }, fontSize = 11.sp)
                        }

                        // Preview code
                        OutlinedButton(
                            onClick = {
                                val content = media.localFilePath?.let { path ->
                                    try { File(path).readText() } catch (e: Exception) { media.fileContentPreview ?: "" }
                                } ?: (media.fileContentPreview ?: "")
                                onOpenCodePreview(media.title, content)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(when(selectedLanguage){ "uz" -> "Ko'rish"; "ru" -> "Просмотр"; else -> "View" }, fontSize = 11.sp)
                        }

                        // Share
                        Button(
                            onClick = { shareMedia(context, media, selectedLanguage) },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = badgeColor),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(when(selectedLanguage){ "uz" -> "Ulashish"; "ru" -> "Поделиться"; else -> "Share" }, fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioPlayerCard(
    media: GeneratedMediaItem,
    primaryColor: Color,
    surfaceColor: Color,
    textColor: Color,
    selectedLanguage: String
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var totalDuration by remember { mutableIntStateOf(0) }

    val mediaPlayer = remember {
        MediaPlayer().apply {
            try {
                if (media.localFilePath != null) {
                    setDataSource(media.localFilePath)
                } else {
                    setDataSource(context, Uri.parse(media.uriOrUrl))
                }
                prepare()
                totalDuration = duration.coerceAtLeast(1000)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(Unit) {
        mediaPlayer.setOnCompletionListener {
            isPlaying = false
            currentPosition = totalDuration
        }
        onDispose {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    // Polling progress while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                currentPosition = mediaPlayer.currentPosition
            } catch (e: Exception) {
                // Ignore
            }
            kotlinx.coroutines.delay(100)
        }
    }

    // Infinite animation for visualizer bars
    val infiniteTransition = rememberInfiniteTransition(label = "audio_bars")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981).copy(alpha = 0.2f))
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = media.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    
                    val ext = media.localFilePath?.substringAfterLast('.')?.uppercase() ?: media.mimeType?.substringAfterLast('/')?.uppercase() ?: "AUDIO"
                    val textLabel = when(selectedLanguage) {
                        "uz" -> "AUDIO • $ext"
                        "ru" -> "АУДИО • $ext"
                        else -> "AUDIO • $ext"
                    }
                    Text(
                        text = textLabel,
                        color = Color(0xFF10B981),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.4f)
                ) {
                    if (media.fileSizeFormatted != null) { Text(
                        text = media.fileSizeFormatted,
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    ) }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Visualizer waveform & Play button row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // Play/Pause Button
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF10B981),
                    modifier = Modifier
                        .size(42.dp)
                        .clickable {
                            try {
                                if (isPlaying) {
                                    mediaPlayer.pause()
                                    isPlaying = false
                                } else {
                                    if (currentPosition >= totalDuration) {
                                        mediaPlayer.seekTo(0)
                                    }
                                    mediaPlayer.start()
                                    isPlaying = true
                                }
                            } catch (e: Exception) {
                                val msg = when(selectedLanguage){ "uz" -> "Xatolik: ${e.message}"; "ru" -> "Ошибка: ${e.message}"; else -> "Error: ${e.message}" }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Waveform Animated Bars
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val barCount = 20
                    for (i in 0 until barCount) {
                        val barHeight = if (isPlaying) {
                            val wave = (sin(phase + i * 0.4) + 1.0) / 2.0
                            val dynamicHeight = (8 + (wave * 26)).dp
                            dynamicHeight
                        } else {
                            (6 + (i % 5) * 3).dp
                        }

                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (isPlaying) Color(0xFF34D399) else Color(0xFF10B981).copy(alpha = 0.4f)
                                )
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Timer text
                val currentSec = currentPosition / 1000
                val totalSec = totalDuration / 1000
                Text(
                    text = String.format("%02d:%02d / %02d:%02d", currentSec / 60, currentSec % 60, totalSec / 60, totalSec % 60),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(10.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    
                    onClick = {
                        try {
                            val sourcePath = media.localFilePath ?: media.uriOrUrl.replace("file://", "")
                            val sourceFile = File(sourcePath)
                            if (sourceFile.exists()) {
                                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                val destFile = File(downloadsDir, media.title)
                                sourceFile.copyTo(destFile, overwrite = true)
                                val msg = when(selectedLanguage) {
                                    "uz" -> "Downloads papkasiga saqlandi!"
                                    "ru" -> "Сохранено в загрузки!"
                                    else -> "Saved to Downloads!"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            } else {
                                throw Exception("Source file not found")
                            }
                        } catch (e: Exception) {
                            val msg = when(selectedLanguage) {
                                "uz" -> "Saqlashda xatolik: ${e.localizedMessage}"
                                "ru" -> "Ошибка сохранения: ${e.localizedMessage}"
                                else -> "Save error: ${e.localizedMessage}"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(when(selectedLanguage){ "uz" -> "Saqlash"; "ru" -> "Сохранить"; else -> "Save" }, fontSize = 11.sp)
                }

                Button(
                    onClick = { shareMedia(context, media, selectedLanguage) },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(when(selectedLanguage){ "uz" -> "Ulashish"; "ru" -> "Поделиться"; else -> "Share" }, fontSize = 11.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun FullScreenImageViewerDialog(
    imagePathOrUrl: String,
    selectedLanguage: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            // Close Button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart)
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            // Action Share / Download
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        val file = File(imagePathOrUrl)
                        if (file.exists()) {
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                val title = when(selectedLanguage){ "uz" -> "Rasm ulashish"; "ru" -> "Поделиться изображением"; else -> "Share Image" }
                                (context as? com.example.MainActivity)?.isBypassingLock = true
                                context.startActivity(Intent.createChooser(intent, title))
                            } catch (e: Exception) {
                                val msg = when(selectedLanguage){ "uz" -> "Xatolik: ${e.message}"; "ru" -> "Ошибка: ${e.message}"; else -> "Error: ${e.message}" }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                }
            }

            // Image with zoom container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                val imageModel = when {
                    imagePathOrUrl.startsWith("content://") || imagePathOrUrl.startsWith("file://") -> Uri.parse(imagePathOrUrl)
                    imagePathOrUrl.startsWith("/") -> File(imagePathOrUrl)
                    else -> imagePathOrUrl
                }
                AsyncImage(
                    model = imageModel,
                    contentDescription = "Full Screen AI Image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun VideoPlayerDialog(
    videoPathOrUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f)),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart)
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        val mediaController = android.widget.MediaController(ctx)
                        mediaController.setAnchorView(this)
                        setMediaController(mediaController)

                        val file = File(videoPathOrUrl)
                        if (file.exists() && file.length() > 0) {
                            setVideoPath(file.absolutePath)
                        } else {
                            val uri = if (videoPathOrUrl.startsWith("content://") || videoPathOrUrl.startsWith("file://") || videoPathOrUrl.startsWith("http")) {
                                Uri.parse(videoPathOrUrl)
                            } else {
                                Uri.fromFile(File(videoPathOrUrl))
                            }
                            setVideoURI(uri)
                        }

                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            start()
                        }
                        setOnErrorListener { _, what, extra ->
                            Log.w("VideoPlayerDialog", "Video playback handled: what=$what extra=$extra")
                            true
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
        }
    }
}

@Composable
fun CodePreviewDialog(
    title: String,
    content: String,
    selectedLanguage: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Code", content))
                                val msg = when(selectedLanguage){ "uz" -> "Kod nusxalandi!"; "ru" -> "Код скопирован!"; else -> "Code copied!" }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White, modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Code scroll view
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = content,
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}

private fun openFileWithDefaultApp(context: Context, media: GeneratedMediaItem, selectedLanguage: String) {
    try {
        val file = media.localFilePath?.let { File(it) }
        if (file != null && file.exists()) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, media.mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            (context as? com.example.MainActivity)?.isBypassingLock = true
            context.startActivity(Intent.createChooser(intent, "Open"))
        } else {
            val msg = when(selectedLanguage){ "uz" -> "Fayl topilmadi"; "ru" -> "Файл не найден"; else -> "File not found" }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        val msg = when(selectedLanguage){ "uz" -> "Faylni ochish uchun ilova topilmadi: ${e.localizedMessage}"; "ru" -> "Приложение не найдено: ${e.localizedMessage}"; else -> "App not found: ${e.localizedMessage}" }
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}

private fun shareMedia(context: Context, media: GeneratedMediaItem, selectedLanguage: String) {
    try {
        val file = media.localFilePath?.let { File(it) }
        val uri = if (file != null && file.exists()) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            Uri.parse(media.uriOrUrl)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = media.mimeType ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "${media.title}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        (context as? com.example.MainActivity)?.isBypassingLock = true
        context.startActivity(Intent.createChooser(intent, media.title))
    } catch (e: Exception) {
        val msg = when(selectedLanguage){ "uz" -> "Ulashishda xatolik: ${e.localizedMessage}"; "ru" -> "Ошибка при отправке: ${e.localizedMessage}"; else -> "Share error: ${e.localizedMessage}" }
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}
