package com.example

import com.example.compiler.ApkSigner
import com.example.compiler.BinaryXmlEncoder
import com.example.compiler.DexBuilder
import com.example.compiler.ResourcesArscEncoder
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkCompilerTest {

    @Test
    fun `dex builder emits valid dex header, sha1 signature, and adler32 checksum`() {
        val dex = DexBuilder.buildMainActivityDex("com.forge.testgame")
        assertTrue(dex.size > 112)

        val magic = String(dex.copyOfRange(0, 8), Charsets.US_ASCII)
        assertEquals("dex\n035\u0000", magic)

        val recordedSize = ByteBuffer.wrap(dex, 32, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(dex.size, recordedSize)

        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(dex, 32, dex.size - 32)
        assertArrayEquals(sha1.digest(), dex.copyOfRange(12, 32))

        val adler = Adler32()
        adler.update(dex, 12, dex.size - 12)
        val recordedChecksum = ByteBuffer.wrap(dex, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(adler.value.toInt(), recordedChecksum)
    }

    @Test
    fun `binary xml and resources arsc emit valid headers`() {
        val manifest = BinaryXmlEncoder.encodeManifest(
            packageName = "com.forge.testgame",
            appName = "Test Game",
            versionName = "1.0.0",
            versionCode = 1
        )
        val xmlHeader = ByteBuffer.wrap(manifest, 0, 8).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x0003.toShort(), xmlHeader.short)
        assertEquals(8.toShort(), xmlHeader.short)
        assertEquals(manifest.size, xmlHeader.int)

        val arsc = ResourcesArscEncoder.encode("com.forge.testgame", "Test Game")
        val arscHeader = ByteBuffer.wrap(arsc, 0, 8).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x0002.toShort(), arscHeader.short)
        assertEquals(12.toShort(), arscHeader.short)
        assertEquals(arsc.size, arscHeader.int)
    }

    @Test
    fun `apk signer produces valid manifest, sf, and pkcs7 cert rsa inside apk zip`() {
        val entries = linkedMapOf(
            "AndroidManifest.xml" to BinaryXmlEncoder.encodeManifest("com.forge.neon", "Neon", "1.0.0", 1),
            "resources.arsc" to ResourcesArscEncoder.encode("com.forge.neon", "Neon"),
            "classes.dex" to DexBuilder.buildMainActivityDex("com.forge.neon"),
            "assets/index.html" to "<html><body>Neon</body></html>".toByteArray(Charsets.UTF_8)
        )
        val sig = ApkSigner.signEntries(entries)
        val manifestText = String(sig.manifestMf, Charsets.UTF_8)
        assertTrue(manifestText.contains("Name: classes.dex"))
        assertTrue(manifestText.contains("SHA-256-Digest:"))
        assertTrue(sig.certRsa.isNotEmpty())
        assertEquals(0x30, sig.certRsa[0].toInt() and 0xFF) // DER SEQUENCE

        val tmpApk = File.createTempFile("forge_test_", ".apk")
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

            ZipFile(tmpApk).use { zip ->
                assertNotNull(zip.getEntry("AndroidManifest.xml"))
                assertNotNull(zip.getEntry("resources.arsc"))
                assertNotNull(zip.getEntry("classes.dex"))
                assertNotNull(zip.getEntry("assets/index.html"))
                assertNotNull(zip.getEntry("META-INF/MANIFEST.MF"))
                assertNotNull(zip.getEntry("META-INF/CERT.SF"))
                assertNotNull(zip.getEntry("META-INF/CERT.RSA"))
            }
        } finally {
            tmpApk.delete()
        }
    }
}
