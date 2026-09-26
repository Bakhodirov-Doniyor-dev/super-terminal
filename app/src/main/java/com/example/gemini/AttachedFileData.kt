package com.example.gemini

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream

data class AttachedFileData(
    val name: String,
    val mimeType: String?,
    val textContent: String? = null,
    val base64Data: String? = null
) {
    companion object {
        fun readFromUri(context: Context, uri: Uri): AttachedFileData {
            val name = com.example.ui.getFileName(context, uri)
            var mimeType = context.contentResolver.getType(uri)
            val lowerName = name.lowercase()

            // Resolve mimeType using extension if contentResolver returned null or empty
            if (mimeType.isNullOrEmpty()) {
                mimeType = when {
                    lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") -> "image/jpeg"
                    lowerName.endsWith(".png") -> "image/png"
                    lowerName.endsWith(".webp") -> "image/webp"
                    lowerName.endsWith(".gif") -> "image/gif"
                    lowerName.endsWith(".heic") -> "image/heic"
                    lowerName.endsWith(".heif") -> "image/heif"
                    lowerName.endsWith(".pdf") -> "application/pdf"
                    lowerName.endsWith(".txt") -> "text/plain"
                    lowerName.endsWith(".kt") || lowerName.endsWith(".java") -> "text/plain"
                    lowerName.endsWith(".json") -> "application/json"
                    lowerName.endsWith(".xml") -> "application/xml"
                    else -> "application/octet-stream"
                }
            }

            return try {
                val isImage = mimeType?.startsWith("image/") == true ||
                        lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                        lowerName.endsWith(".png") || lowerName.endsWith(".webp") ||
                        lowerName.endsWith(".heic") || lowerName.endsWith(".heif")

                if (isImage) {
                    val compressedBytes = compressImage(context, uri)
                    if (compressedBytes != null) {
                        val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
                        val finalMime = if (mimeType?.startsWith("image/") == true) mimeType else "image/jpeg"
                        AttachedFileData(name = name, mimeType = finalMime, base64Data = base64)
                    } else {
                        // Fallback to reading raw bytes if compression failed
                        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                        if (inputStream != null) {
                            val bytes = inputStream.readBytes()
                            inputStream.close()
                            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            AttachedFileData(name = name, mimeType = mimeType ?: "image/jpeg", base64Data = base64)
                        } else {
                            AttachedFileData(name = name, mimeType = mimeType, textContent = "Could not read image file.")
                        }
                    }
                } else {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val bytes = inputStream.readBytes()
                        inputStream.close()

                        val isTextMime = mimeType?.startsWith("text/") == true ||
                                mimeType?.contains("json") == true ||
                                mimeType?.contains("javascript") == true ||
                                mimeType?.contains("xml") == true ||
                                mimeType?.contains("yaml") == true ||
                                lowerName.endsWith(".txt") ||
                                lowerName.endsWith(".kt") ||
                                lowerName.endsWith(".java") ||
                                lowerName.endsWith(".json") ||
                                lowerName.endsWith(".xml") ||
                                lowerName.endsWith(".sh") ||
                                lowerName.endsWith(".gradle") ||
                                lowerName.endsWith(".properties") ||
                                lowerName.endsWith(".md") ||
                                lowerName.endsWith(".html") ||
                                lowerName.endsWith(".css") ||
                                lowerName.endsWith(".js") ||
                                lowerName.endsWith(".ts")

                        if (isTextMime) {
                            val text = String(bytes, Charsets.UTF_8)
                            AttachedFileData(name = name, mimeType = mimeType ?: "text/plain", textContent = text)
                        } else {
                            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            AttachedFileData(name = name, mimeType = mimeType ?: "application/octet-stream", base64Data = base64)
                        }
                    } else {
                        AttachedFileData(name = name, mimeType = mimeType, textContent = "Could not read file.")
                    }
                }
            } catch (e: Exception) {
                AttachedFileData(name = name, mimeType = mimeType, textContent = "Error reading file: ${e.localizedMessage}")
            }
        }

        private fun compressImage(context: Context, uri: Uri): ByteArray? {
            return try {
                var inputStream = context.contentResolver.openInputStream(uri) ?: return null
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()

                var width = options.outWidth
                var height = options.outHeight
                val maxDimension = 1024
                var inSampleSize = 1

                if (width > maxDimension || height > maxDimension) {
                    val halfHeight = height / 2
                    val halfWidth = width / 2
                    while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
                        inSampleSize *= 2
                    }
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                }
                inputStream = context.contentResolver.openInputStream(uri) ?: return null
                val bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
                inputStream.close()

                if (bitmap == null) return null

                val finalBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                    val (newWidth, newHeight) = if (ratio > 1) {
                        Pair(maxDimension, (maxDimension / ratio).toInt())
                    } else {
                        Pair((maxDimension * ratio).toInt(), maxDimension)
                    }
                    val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                    if (scaled != bitmap) {
                        bitmap.recycle()
                    }
                    scaled
                } else {
                    bitmap
                }

                val outputStream = ByteArrayOutputStream()
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                finalBitmap.recycle()
                outputStream.toByteArray()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
