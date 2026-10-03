package com.example.terminal

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.example.MainActivity
import java.io.File
import java.io.FileOutputStream

/**
 * Native File & URL Share Receiver Activity (equivalent to Termux FileReceiverActivity).
 * Captures files, text, scripts, or URLs shared from any Android app, automatically stores them
 * in the terminal's ~/downloads/ inbox, and opens the main terminal window ready for action.
 */
class TerminalFileReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val action = intent?.action
        val type = intent?.type

        val homeDir = File(filesDir, "home").apply { mkdirs() }
        val downloadsDir = File(homeDir, "downloads").apply { mkdirs() }

        var targetCommand = ""

        if (Intent.ACTION_SEND == action && type != null) {
            if ("text/plain" == type) {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                if (sharedText.isNotBlank()) {
                    targetCommand = sharedText
                    Toast.makeText(this, "Matn Terminalga uzatildi", Toast.LENGTH_SHORT).show()
                }
            } else {
                val fileUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }

                if (fileUri != null) {
                    val savedFile = saveUriToDownloads(fileUri, downloadsDir)
                    if (savedFile != null) {
                        targetCommand = if (savedFile.name.endsWith(".sh") || savedFile.name.endsWith(".py")) {
                            "cd ~/downloads && ls -la && sh ${savedFile.name}"
                        } else {
                            "cd ~/downloads && ls -lh ${savedFile.name}"
                        }
                        Toast.makeText(this, "Fayl saqlandi: ${savedFile.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else if (Intent.ACTION_VIEW == action) {
            val dataUri = intent.data
            if (dataUri != null) {
                val savedFile = saveUriToDownloads(dataUri, downloadsDir)
                if (savedFile != null) {
                    targetCommand = "cd ~/downloads && ls -lh ${savedFile.name}"
                    Toast.makeText(this, "Fayl ochildi: ${savedFile.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Launch MainActivity and pass the command/action
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (targetCommand.isNotEmpty()) {
                putExtra("shared_terminal_command", targetCommand)
            }
        }
        startActivity(launchIntent)
        finish()
    }

    private fun saveUriToDownloads(uri: Uri, targetDir: File): File? {
        return try {
            val fileName = getFileNameFromUri(uri)
            val destFile = File(targetDir, fileName)

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.setReadable(true, false)
            destFile
        } catch (e: Exception) {
            null
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "shared_file_${System.currentTimeMillis()}"
        try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) {
                        name = it.getString(idx)
                    }
                }
            }
        } catch (_: Exception) {}
        return name
    }
}
