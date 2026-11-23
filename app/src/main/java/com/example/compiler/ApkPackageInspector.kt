package com.example.compiler

import com.example.model.ProjectFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

enum class ApkEntryCategory(val label: String, val accentHex: String) {
    DEX_BYTECODE("Bytecode Dalvik DEX", "#7F52FF"),
    MANIFEST_RESOURCES("Manifest & Ressources", "#10B981"),
    ASSETS_BUNDLE("Bundle & Code Assets", "#E34F26"),
    SIGNATURE("Signature RSA PKCS#7", "#FFB300"),
    OTHER("Autres entrées", "#94A3B8")
}

data class ApkArchiveEntry(
    val path: String,
    val sizeBytes: Long,
    val compressedBytes: Long,
    val category: ApkEntryCategory
)

data class ApkInspectionReport(
    val apkFile: File,
    val apkName: String,
    val totalSizeBytes: Long,
    val entries: List<ApkArchiveEntry>,
    val hasManifest: Boolean,
    val hasResourcesArsc: Boolean,
    val hasValidDexHeader: Boolean,
    val dexSizeBytes: Long,
    val hasSignatureBlock: Boolean,
    val iconPngBytes: ByteArray?,
    val bundledHtml: String,
    val hierarchyFiles: List<ProjectFile>
) {
    val isIntegrityValid: Boolean
        get() = hasManifest && hasResourcesArsc && hasValidDexHeader && hasSignatureBlock
}

object ApkPackageInspector {

    fun categorizeEntry(path: String): ApkEntryCategory {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".dex") -> ApkEntryCategory.DEX_BYTECODE
            lower == "androidmanifest.xml" || lower == "resources.arsc" || lower.startsWith("res/") ->
                ApkEntryCategory.MANIFEST_RESOURCES
            lower.startsWith("assets/") -> ApkEntryCategory.ASSETS_BUNDLE
            lower.startsWith("meta-inf/") -> ApkEntryCategory.SIGNATURE
            else -> ApkEntryCategory.OTHER
        }
    }

    fun inspectApk(apkFile: File): Result<ApkInspectionReport> = runCatching {
        require(apkFile.exists() && apkFile.isFile) {
            "Le fichier APK est introuvable (${apkFile.name})."
        }

        val entries = mutableListOf<ApkArchiveEntry>()
        var hasManifest = false
        var hasResources = false
        var hasValidDex = false
        var dexSize = 0L
        var hasManifestMf = false
        var hasCertSf = false
        var hasCertRsa = false
        var iconBytes: ByteArray? = null
        var htmlText = ""
        val hierarchyFiles = mutableListOf<ProjectFile>()

        ZipInputStream(FileInputStream(apkFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val path = entry.name.trimStart('/')
                    val rawBytes = readEntryBytes(zis)
                    val uncompressedSize = rawBytes.size.toLong()
                    val compressedSize = entry.compressedSize.takeIf { it >= 0L } ?: uncompressedSize
                    val category = categorizeEntry(path)

                    entries.add(
                        ApkArchiveEntry(
                            path = path,
                            sizeBytes = uncompressedSize,
                            compressedBytes = compressedSize,
                            category = category
                        )
                    )

                    when (path.lowercase()) {
                        "androidmanifest.xml" -> hasManifest = rawBytes.isNotEmpty()
                        "resources.arsc" -> hasResources = rawBytes.isNotEmpty()
                        "classes.dex" -> {
                            dexSize = uncompressedSize
                            hasValidDex = isDexMagicValid(rawBytes)
                        }
                        "meta-inf/manifest.mf" -> hasManifestMf = rawBytes.isNotEmpty()
                        "meta-inf/cert.sf" -> hasCertSf = rawBytes.isNotEmpty()
                        "meta-inf/cert.rsa" -> hasCertRsa = rawBytes.isNotEmpty()
                        "res/drawable/ic_launcher.png" -> iconBytes = rawBytes
                        "assets/index.html" -> {
                            htmlText = rawBytes.toString(Charsets.UTF_8)
                        }
                    }

                    val previewContent = when {
                        path.equals("assets/index.html", ignoreCase = true) -> htmlText
                        path.startsWith("assets/src/", ignoreCase = true) ||
                            path.endsWith(".mf", ignoreCase = true) ||
                            path.endsWith(".sf", ignoreCase = true) -> rawBytes.toString(Charsets.UTF_8)
                        else -> "// Entrée binaire APK ($uncompressedSize octets)\n" +
                            " ".repeat(uncompressedSize.toInt().coerceIn(32, 4096))
                    }
                    hierarchyFiles.add(
                        ProjectFile(
                            path = path,
                            language = languageLabelForApkEntry(path, category),
                            content = previewContent
                        )
                    )
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        ApkInspectionReport(
            apkFile = apkFile,
            apkName = apkFile.name,
            totalSizeBytes = apkFile.length(),
            entries = entries,
            hasManifest = hasManifest,
            hasResourcesArsc = hasResources,
            hasValidDexHeader = hasValidDex,
            dexSizeBytes = dexSize,
            hasSignatureBlock = hasManifestMf && hasCertSf && hasCertRsa,
            iconPngBytes = iconBytes,
            bundledHtml = htmlText,
            hierarchyFiles = hierarchyFiles
        )
    }

    fun findCachedApkForProject(
        filesDir: File,
        appName: String,
        versionName: String,
        lastBuildApkName: String?
    ): File? {
        val buildsDir = File(filesDir, "apk_builds")
        if (!buildsDir.exists()) return null

        if (!lastBuildApkName.isNullOrBlank()) {
            val exact = File(buildsDir, lastBuildApkName)
            if (exact.exists() && exact.isFile) return exact
        }

        val slug = appName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "app" }
        val safeVer = versionName.replace(Regex("[^0-9A-Za-z._-]"), "")
        val expected = File(buildsDir, "${slug}-v${safeVer}-signed.apk")
        if (expected.exists() && expected.isFile) return expected

        return buildsDir.listFiles()
            ?.firstOrNull { it.isFile && it.name.startsWith("${slug}-") && it.name.endsWith(".apk") }
    }

    private fun isDexMagicValid(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        return bytes[0] == 'd'.code.toByte() &&
            bytes[1] == 'e'.code.toByte() &&
            bytes[2] == 'x'.code.toByte() &&
            bytes[3] == '\n'.code.toByte()
    }

    private fun languageLabelForApkEntry(path: String, category: ApkEntryCategory): String {
        return when {
            path.endsWith(".dex", true) -> "DEX"
            path.endsWith(".xml", true) -> "XML"
            path.endsWith(".arsc", true) -> "ARSC"
            path.endsWith(".png", true) -> "PNG"
            path.endsWith(".html", true) -> "HTML5"
            path.endsWith(".kt", true) -> "Kotlin"
            path.endsWith(".java", true) -> "Java"
            path.endsWith(".cs", true) -> "C#"
            path.endsWith(".gd", true) -> "GDScript"
            category == ApkEntryCategory.SIGNATURE -> "RSA-SIG"
            else -> "Asset"
        }
    }

    private fun readEntryBytes(zis: ZipInputStream): ByteArray {
        val buffer = ByteArray(4096)
        val out = ByteArrayOutputStream()
        var read = zis.read(buffer)
        while (read != -1) {
            out.write(buffer, 0, read)
            read = zis.read(buffer)
        }
        return out.toByteArray()
    }
}
