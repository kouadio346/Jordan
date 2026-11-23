package com.example

import com.example.compiler.ApkEntryCategory
import com.example.compiler.ApkPackageInspector
import com.example.compiler.ApkSigner
import com.example.compiler.BinaryXmlEncoder
import com.example.compiler.DexBuilder
import com.example.compiler.ResourcesArscEncoder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkPackageInspectorTest {

    @Test
    fun inspectApk_parsesSignedApkExtractsHtmlIconAndHierarchy() {
        val dummyPng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val htmlContent = "<!DOCTYPE html><html><body><h1>ForgePreview</h1></body></html>"
        val entries = linkedMapOf(
            "AndroidManifest.xml" to BinaryXmlEncoder.encodeManifest("com.forge.preview", "PreviewApp", "1.0.0", 1),
            "resources.arsc" to ResourcesArscEncoder.encode("com.forge.preview", "PreviewApp"),
            "classes.dex" to DexBuilder.buildMainActivityDex("com.forge.preview"),
            "res/drawable/ic_launcher.png" to dummyPng,
            "assets/index.html" to htmlContent.toByteArray(Charsets.UTF_8),
            "assets/src/app/MainScreen.kt" to "package com.forge.preview\nfun Main() {}".toByteArray(Charsets.UTF_8)
        )
        val sig = ApkSigner.signEntries(entries)

        val tmpApk = File.createTempFile("inspect_test_", ".apk")
        try {
            ZipOutputStream(FileOutputStream(tmpApk)).use { zip ->
                for ((k, v) in entries) {
                    zip.putNextEntry(ZipEntry(k))
                    zip.write(v)
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
                zip.write(sig.manifestMf)
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("META-INF/CERT.SF"))
                zip.write(sig.certSf)
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
                zip.write(sig.certRsa)
                zip.closeEntry()
            }

            val report = ApkPackageInspector.inspectApk(tmpApk).getOrThrow()

            assertTrue(report.isIntegrityValid)
            assertTrue(report.hasManifest)
            assertTrue(report.hasResourcesArsc)
            assertTrue(report.hasValidDexHeader)
            assertTrue(report.hasSignatureBlock)
            assertEquals(9, report.entries.size)
            assertEquals(htmlContent, report.bundledHtml)
            assertArrayEquals(dummyPng, report.iconPngBytes)
            assertEquals(9, report.hierarchyFiles.size)
            assertTrue(report.hierarchyFiles.any { it.path == "assets/src/app/MainScreen.kt" && it.content.contains("fun Main()") })
        } finally {
            tmpApk.delete()
        }
    }

    @Test
    fun categorizeEntry_andFindCachedApkWorkCorrectly() {
        assertEquals(ApkEntryCategory.DEX_BYTECODE, ApkPackageInspector.categorizeEntry("classes.dex"))
        assertEquals(ApkEntryCategory.MANIFEST_RESOURCES, ApkPackageInspector.categorizeEntry("AndroidManifest.xml"))
        assertEquals(ApkEntryCategory.ASSETS_BUNDLE, ApkPackageInspector.categorizeEntry("assets/index.html"))
        assertEquals(ApkEntryCategory.SIGNATURE, ApkPackageInspector.categorizeEntry("META-INF/CERT.RSA"))

        val tempRoot = File(System.getProperty("java.io.tmpdir"), "forge_cache_test_${System.nanoTime()}")
        val buildsDir = File(tempRoot, "apk_builds").apply { mkdirs() }
        val apkFile = File(buildsDir, "space_hop-v1.0.0-signed.apk").apply { writeText("apk") }
        try {
            val found = ApkPackageInspector.findCachedApkForProject(
                filesDir = tempRoot,
                appName = "Space Hop",
                versionName = "1.0.0",
                lastBuildApkName = null
            )
            assertNotNull(found)
            assertEquals(apkFile.name, found!!.name)
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
