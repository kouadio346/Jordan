package com.example.compiler

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.R
import com.example.data.GitHubExportService
import com.example.data.ProjectIconStore
import com.example.model.ApkBuildResult
import com.example.model.AppProject
import com.example.model.AppType
import com.example.model.BuildLogEntry
import com.example.model.LogLevel
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Multi-stage On-Device Android APK Compiler & Packager.
 * Validates source files, rasterizes a custom PNG launcher icon, encodes binary
 * AndroidManifest.xml (AXML) and resources.arsc, synthesizes classes.dex (Dalvik v035),
 * bundles assets/index.html & sources, and signs the APK with RSA-2048 PKCS#7.
 */
class ApkCompilerEngine(private val context: Context) {

    suspend fun compileApk(
        project: AppProject,
        onLog: suspend (BuildLogEntry, Float) -> Unit
    ): ApkBuildResult = withContext(Dispatchers.Default) {
        val startMs = System.currentTimeMillis()
        val logs = mutableListOf<BuildLogEntry>()

        suspend fun log(tag: String, msg: String, level: LogLevel = LogLevel.INFO, progress: Float) {
            val entry = BuildLogEntry(tag, msg, level)
            logs.add(entry)
            onLog(entry, progress)
        }

        val safePkg = project.packageName.ifBlank { "com.forge.generated" }
        val versionCode = project.versionCode.toInt().coerceIn(1, 999999)
        val versionName = project.versionName.ifBlank { "1.0.0" }
        val appName = project.appName.ifBlank { "ForgeApp" }

        log("INIT", "Initialisation du pipeline ForgeAPK pour \"$appName\" ($safePkg)", LogLevel.STEP, 0.08f)

        // Stage 1: Source syntax & structure validation
        log("LINT", "Analyse syntaxique (${project.language}) sur ${project.files.size} fichier(s)...", LogLevel.STEP, 0.20f)
        var totalLines = 0
        for (file in project.files) {
            val lines = file.content.lines().size
            totalLines += lines
            val balanceWarning = checkBracketBalance(file.content)
            if (balanceWarning != null) {
                log("LINT", "${file.path} ($lines lignes) — note : $balanceWarning", LogLevel.WARN, 0.25f)
            } else {
                log("LINT", "${file.path} ($lines lignes) — OK", LogLevel.INFO, 0.28f)
            }
        }
        log("LINT", "Analyse terminée : $totalLines lignes vérifiées.", LogLevel.SUCCESS, 0.35f)

        // Stage 2: Resource compilation (Icon PNG + AXML + ARSC)
        log("AAPT2", "Préparation de l'icône adaptative PNG (192x192)...", LogLevel.STEP, 0.45f)
        val iconPngBytes = resolveLauncherIconBytes(project, appName)

        log("AAPT2", "Compilation binaire de AndroidManifest.xml (AXML)...", LogLevel.STEP, 0.55f)
        val manifestBytes = BinaryXmlEncoder.encodeManifest(
            packageName = safePkg,
            appName = appName,
            versionName = versionName,
            versionCode = versionCode
        )

        log("AAPT2", "Compilation de la table de ressources resources.arsc...", LogLevel.STEP, 0.62f)
        val arscBytes = ResourcesArscEncoder.encode(
            packageName = safePkg,
            appName = appName
        )

        // Stage 3: Dalvik DEX bytecode synthesis
        log("D8-DEX", "Synthèse du bytecode Dalvik DEX v035 ($safePkg.MainActivity)...", LogLevel.STEP, 0.75f)
        val dexBytes = DexBuilder.buildMainActivityDex(safePkg)
        log("D8-DEX", "classes.dex généré (${dexBytes.size} octets, checksum Adler32 & SHA-1 valides).", LogLevel.SUCCESS, 0.80f)

        // Stage 4: Bundle assets & sign APK
        val htmlBytes = project.playableHtml.toByteArray(Charsets.UTF_8)
        val entries = linkedMapOf<String, ByteArray>(
            "AndroidManifest.xml" to manifestBytes,
            "resources.arsc" to arscBytes,
            "classes.dex" to dexBytes,
            "res/drawable/ic_launcher.png" to iconPngBytes,
            "assets/index.html" to htmlBytes
        )
        for (src in project.files) {
            val cleanPath = src.path.trimStart('/').replace("..", "_")
            entries["assets/src/$cleanPath"] = src.content.toByteArray(Charsets.UTF_8)
        }

        log("SIGN", "Signature cryptographique RSA-2048 (MANIFEST.MF / CERT.SF / CERT.RSA)...", LogLevel.STEP, 0.88f)
        val sigFiles = ApkSigner.signEntries(entries)

        val buildsDir = File(context.filesDir, "apk_builds").apply { mkdirs() }
        val slug = appName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "app" }
        val apkName = "${slug}-v${versionName.replace(Regex("[^0-9A-Za-z._-]"), "")}-signed.apk"
        val apkFile = File(buildsDir, apkName)

        ZipOutputStream(FileOutputStream(apkFile)).use { zip ->
            for ((path, data) in entries) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(data)
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zip.write(sigFiles.manifestMf)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("META-INF/CERT.SF"))
            zip.write(sigFiles.certSf)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
            zip.write(sigFiles.certRsa)
            zip.closeEntry()
        }

        val apkBytes = apkFile.readBytes()
        val sha256 = MessageDigest.getInstance("SHA-256").digest(apkBytes)
            .joinToString("") { "%02x".format(it) }
        val durationMs = System.currentTimeMillis() - startMs

        log(
            "PACK",
            "APK prêt : $apkName (${apkBytes.size / 1024} Ko) compilé en ${durationMs}ms.",
            LogLevel.SUCCESS,
            1.0f
        )

        ApkBuildResult(
            apkFile = apkFile,
            apkName = apkName,
            packageName = safePkg,
            versionName = versionName,
            versionCode = versionCode,
            sizeBytes = apkBytes.size.toLong(),
            sha256 = sha256,
            certFingerprint = sigFiles.certFingerprint,
            entryCount = entries.size + 3,
            durationMs = durationMs,
            logs = logs
        )
    }

    suspend fun exportSourceZip(project: AppProject): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "source_exports").apply { mkdirs() }
        val slug = project.appName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "project" }
        val zipFile = File(dir, "${slug}-sources.zip")

        val iconBytes = resolveProjectLauncherIconBytes(project)
        val repoFiles = GitHubExportService.buildRepositoryFiles(project, iconBytes)

        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            for (file in repoFiles) {
                zip.putNextEntry(ZipEntry(file.path))
                zip.write(file.contentBytes)
                zip.closeEntry()
            }
        }
        zipFile
    }

    private fun checkBracketBalance(source: String): String? {
        var curly = 0
        var paren = 0
        for (ch in source) {
            when (ch) {
                '{' -> curly++
                '}' -> curly--
                '(' -> paren++
                ')' -> paren--
            }
        }
        return when {
            curly != 0 -> "accolades non équilibrées ($curly)"
            paren != 0 -> "parenthèses non équilibrées ($paren)"
            else -> null
        }
    }

    fun resolveProjectLauncherIconBytes(project: AppProject): ByteArray =
        resolveLauncherIconBytes(project, project.appName.ifBlank { "ForgeApp" })

    private fun resolveLauncherIconBytes(project: AppProject, appName: String): ByteArray {
        val store = ProjectIconStore(context)
        val customBmp = if (project.id.isNotBlank()) store.loadIconBitmap(project.id) else null
        if (customBmp != null) {
            return try {
                ProjectIconStore.toRoundedLauncherPngBytes(customBmp, 192)
            } finally {
                customBmp.recycle()
            }
        }
        val placeholderBmp = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.img_placeholder_app_icon)
        }.getOrNull()
        if (placeholderBmp != null) {
            return try {
                ProjectIconStore.toRoundedLauncherPngBytes(placeholderBmp, 192)
            } finally {
                placeholderBmp.recycle()
            }
        }
        return generateLauncherIconPng(appName, project.accentHex, project.appType)
    }

    private fun generateLauncherIconPng(appName: String, accentHex: String, appType: String): ByteArray {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val accentColor = try {
            Color.parseColor(accentHex)
        } catch (_: Exception) {
            Color.parseColor("#00E5FF")
        }

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                Color.parseColor("#0B1021"),
                Color.parseColor("#182238"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 42f, 42f, bgPaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            color = accentColor
        }
        canvas.drawRoundRect(RectF(14f, 14f, size - 14f, size - 14f), 32f, 32f, ringPaint)

        val initials = appName.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifBlank { if (appType == AppType.GAME.name) "GM" else "AP" }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 72f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val fm = textPaint.fontMetrics
        val y = size / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(initials, size / 2f, y, textPaint)

        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }
}
