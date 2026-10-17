package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

object ProductImageHelper {
    private const val TAG = "ProductImageHelper"
    private const val MAX_DIMENSION = 240
    private const val JPEG_QUALITY = 80

    /**
     * Decodes, downscales, and compresses an image from [sourceUri] to a compact local JPEG file.
     * Keeps aspect ratio intact and frees bitmap memory immediately.
     * Returns the absolute path of the saved file, or null on failure.
     */
    suspend fun saveCompressedProductImage(
        context: Context,
        sourceUri: Uri,
        productId: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            // Step 1: Measure dimensions without allocating pixel memory
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return@withContext null

            val originalW = options.outWidth
            val originalH = options.outHeight
            if (originalW <= 0 || originalH <= 0) return@withContext null

            // Step 2: Compute sample size (power of 2) for low memory allocation
            var sampleSize = 1
            val halfW = originalW / 2
            val halfH = originalH / 2
            while ((halfH / sampleSize) >= MAX_DIMENSION && (halfW / sampleSize) >= MAX_DIMENSION) {
                sampleSize *= 2
            }

            // Step 3: Decode sampled bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val sampledBitmap = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            // Step 4: Scale down accurately while preserving exact aspect ratio
            val currentW = sampledBitmap.width
            val currentH = sampledBitmap.height
            val scale = minOf(
                MAX_DIMENSION.toFloat() / currentW,
                MAX_DIMENSION.toFloat() / currentH,
                1.0f
            )
            val targetW = (currentW * scale).roundToInt().coerceAtLeast(1)
            val targetH = (currentH * scale).roundToInt().coerceAtLeast(1)

            val finalBitmap = if (targetW != currentW || targetH != currentH) {
                val scaled = Bitmap.createScaledBitmap(sampledBitmap, targetW, targetH, true)
                if (scaled != sampledBitmap) {
                    sampledBitmap.recycle()
                }
                scaled
            } else {
                sampledBitmap
            }

            // Step 5: Save compact JPEG to internal app storage
            val imagesDir = File(context.filesDir, "product_images").apply { mkdirs() }
            val cleanId = productId.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val targetFile = File(imagesDir, "prod_${cleanId}_${System.currentTimeMillis()}.jpg")

            FileOutputStream(targetFile).use { outStream ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outStream)
            }
            finalBitmap.recycle()

            targetFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing product image: ${e.message}", e)
            null
        }
    }

    /**
     * Converts a local product image file to a Base64 string for embedding in backup payloads.
     */
    fun encodeImageToBase64(imagePath: String?): String? {
        if (imagePath.isNullOrBlank()) return null
        return try {
            val file = File(imagePath)
            if (file.exists() && file.length() > 0 && file.length() < 500_000) { // Max 500KB guard
                val bytes = file.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding image to base64: ${e.message}")
            null
        }
    }

    /**
     * Restores a Base64 string into a local compact JPEG file during backup restore.
     */
    fun saveBase64ToImageFile(context: Context?, base64Str: String?, productId: String): String? {
        if (context == null || base64Str.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64Str, Base64.DEFAULT)
            val imagesDir = File(context.filesDir, "product_images").apply { mkdirs() }
            val cleanId = productId.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val targetFile = File(imagesDir, "prod_${cleanId}.jpg")
            FileOutputStream(targetFile).use { out ->
                out.write(bytes)
            }
            targetFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring image from base64: ${e.message}")
            null
        }
    }

    /**
     * Safely cleans up a product image file when removed or replaced.
     */
    fun deleteProductImage(imagePath: String?) {
        if (imagePath.isNullOrBlank()) return
        try {
            val file = File(imagePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting product image: ${e.message}")
        }
    }
}
