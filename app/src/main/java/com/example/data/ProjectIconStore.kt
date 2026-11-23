package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class ProjectIconStore(private val context: Context) {

    private val iconsDir: File
        get() = File(context.filesDir, "project_icons").apply { mkdirs() }

    private val _iconVersions = MutableStateFlow<Map<String, Int>>(emptyMap())
    val iconVersions: StateFlow<Map<String, Int>> = _iconVersions.asStateFlow()

    fun getIconFile(projectKey: String): File {
        val safeKey = sanitizeKey(projectKey)
        return File(iconsDir, "$safeKey.png")
    }

    fun hasCustomIcon(projectKey: String): Boolean {
        val f = getIconFile(projectKey)
        return f.exists() && f.isFile && f.length() > 0L
    }

    fun loadIconBytes(projectKey: String): ByteArray? {
        val f = getIconFile(projectKey)
        return if (f.exists() && f.isFile && f.length() > 0L) {
            runCatching { f.readBytes() }.getOrNull()
        } else {
            null
        }
    }

    fun loadIconBitmap(projectKey: String): Bitmap? {
        val f = getIconFile(projectKey)
        return if (f.exists() && f.isFile && f.length() > 0L) {
            runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
        } else {
            null
        }
    }

    suspend fun saveIconBitmap(projectKey: String, source: Bitmap): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = normalizeSquareIconBitmap(source, ICON_TARGET_SIZE_PX)
            val file = getIconFile(projectKey)
            FileOutputStream(file).use { out ->
                normalized.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bumpVersion(projectKey)
            normalized
        }
    }

    suspend fun importFromUri(projectKey: String, uri: Uri): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            } ?: throw IllegalStateException("Impossible d'ouvrir l'image sélectionnée.")

            var sampleSize = 1
            val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
            while (maxDim / sampleSize > 1024) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: throw IllegalStateException("Impossible de décoder l'image sélectionnée.")

            try {
                val normalized = normalizeSquareIconBitmap(decoded, ICON_TARGET_SIZE_PX)
                val file = getIconFile(projectKey)
                FileOutputStream(file).use { out ->
                    normalized.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bumpVersion(projectKey)
                normalized
            } finally {
                decoded.recycle()
            }
        }
    }

    fun promoteDraftToProject(targetProjectId: String): Boolean {
        if (targetProjectId.isBlank()) return false
        val draft = getIconFile(DRAFT_PROJECT_KEY)
        if (!draft.exists() || draft.length() <= 0L) return false
        val dest = getIconFile(targetProjectId)
        val copied = runCatching {
            draft.copyTo(dest, overwrite = true)
            draft.delete()
        }.isSuccess
        if (copied) {
            bumpVersion(DRAFT_PROJECT_KEY)
            bumpVersion(targetProjectId)
        }
        return copied
    }

    fun clearIcon(projectKey: String) {
        val file = getIconFile(projectKey)
        if (file.exists()) {
            file.delete()
            bumpVersion(projectKey)
        }
    }

    private fun bumpVersion(projectKey: String) {
        val safeKey = sanitizeKey(projectKey)
        _iconVersions.update { current ->
            current + (safeKey to ((current[safeKey] ?: 0) + 1))
        }
    }

    private fun sanitizeKey(raw: String): String {
        return raw.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { DRAFT_PROJECT_KEY }
    }

    companion object {
        const val DRAFT_PROJECT_KEY = "draft_new_project"
        const val ICON_TARGET_SIZE_PX = 256

        fun normalizeSquareIconBitmap(source: Bitmap, targetPx: Int): Bitmap {
            val side = minOf(source.width, source.height).coerceAtLeast(1)
            val left = (source.width - side) / 2
            val top = (source.height - side) / 2
            val cropped = Bitmap.createBitmap(source, left, top, side, side)
            val scaled = if (cropped.width == targetPx && cropped.height == targetPx) {
                cropped.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                Bitmap.createScaledBitmap(cropped, targetPx, targetPx, true)
            }
            if (cropped !== source && cropped !== scaled) {
                cropped.recycle()
            }
            return scaled
        }

        fun toRoundedLauncherPngBytes(source: Bitmap, sizePx: Int = 192): ByteArray {
            val square = normalizeSquareIconBitmap(source, sizePx)
            val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = Rect(0, 0, sizePx, sizePx)
            val rectF = RectF(rect)
            val radius = sizePx * 0.22f
            canvas.drawARGB(0, 0, 0, 0)
            canvas.drawRoundRect(rectF, radius, radius, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(square, rect, rect, paint)

            val baos = ByteArrayOutputStream()
            output.compress(Bitmap.CompressFormat.PNG, 100, baos)
            output.recycle()
            if (square !== source) square.recycle()
            return baos.toByteArray()
        }
    }
}
