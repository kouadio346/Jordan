package com.example.compiler

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64
import java.util.Date

/**
 * Generates an on-device RSA-2048 X.509 certificate and signs APK ZIP entries
 * using standard JAR v1 APK signature format (MANIFEST.MF, CERT.SF, CERT.RSA PKCS#7 DER).
 */
object ApkSigner {

    data class SigningIdentity(
        val keyPair: KeyPair,
        val certDer: ByteArray,
        val sha256Fingerprint: String
    )

    @Volatile
    private var cachedIdentity: SigningIdentity? = null

    fun getOrCreateIdentity(): SigningIdentity {
        cachedIdentity?.let { return it }
        synchronized(this) {
            cachedIdentity?.let { return it }
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            val keyPair = kpg.generateKeyPair()
            val certDer = buildSelfSignedX509Certificate(keyPair)
            val fp = MessageDigest.getInstance("SHA-256").digest(certDer)
                .joinToString(":") { "%02X".format(it) }
            val id = SigningIdentity(keyPair, certDer, fp)
            cachedIdentity = id
            return id
        }
    }

    data class SignatureFiles(
        val manifestMf: ByteArray,
        val certSf: ByteArray,
        val certRsa: ByteArray,
        val certFingerprint: String
    )

    fun signEntries(entries: Map<String, ByteArray>): SignatureFiles {
        val identity = getOrCreateIdentity()
        val sha256 = MessageDigest.getInstance("SHA-256")
        val b64 = Base64.getEncoder()

        val manifestSb = StringBuilder()
        manifestSb.append("Manifest-Version: 1.0\r\n")
        manifestSb.append("Created-By: ForgeAPK Compiler 1.0\r\n\r\n")

        val sfSections = StringBuilder()
        for ((name, data) in entries.toSortedMap()) {
            val entryDigest = b64.encodeToString(sha256.digest(data))
            val sectionText = "Name: $name\r\nSHA-256-Digest: $entryDigest\r\n\r\n"
            manifestSb.append(sectionText)

            val sectionDigest = b64.encodeToString(sha256.digest(sectionText.toByteArray(Charsets.UTF_8)))
            sfSections.append("Name: $name\r\nSHA-256-Digest: $sectionDigest\r\n\r\n")
        }

        val manifestBytes = manifestSb.toString().toByteArray(Charsets.UTF_8)
        val manifestDigest = b64.encodeToString(sha256.digest(manifestBytes))

        val sfHeader = StringBuilder()
        sfHeader.append("Signature-Version: 1.0\r\n")
        sfHeader.append("Created-By: ForgeAPK Signer 1.0\r\n")
        sfHeader.append("SHA-256-Digest-Manifest: $manifestDigest\r\n\r\n")
        val sfBytes = (sfHeader.toString() + sfSections.toString()).toByteArray(Charsets.UTF_8)

        val certRsa = buildPkcs7SignedData(
            sfBytes = sfBytes,
            certDer = identity.certDer,
            privateKey = identity.keyPair.private
        )

        return SignatureFiles(
            manifestMf = manifestBytes,
            certSf = sfBytes,
            certRsa = certRsa,
            certFingerprint = identity.sha256Fingerprint
        )
    }

    private fun buildSelfSignedX509Certificate(keyPair: KeyPair): ByteArray {
        val now = Date()
        val expires = Date(now.time + 25L * 365 * 24 * 60 * 60 * 1000)

        val dn = derSeq(
            derSet(derSeq(derOid("2.5.4.6"), derPrintableString("FR"))),
            derSet(derSeq(derOid("2.5.4.10"), derUtf8String("ForgeAPK"))),
            derSet(derSeq(derOid("2.5.4.3"), derUtf8String("ForgeAPK Studio")))
        )

        val sha256WithRsaAlg = derSeq(derOid("1.2.840.113549.1.1.11"), derNull())

        val tbsCert = derSeq(
            derExplicit(0, derInt(BigInteger.valueOf(2))), // v3
            derInt(BigInteger.valueOf(System.currentTimeMillis() and 0x7FFFFFFFL)),
            sha256WithRsaAlg,
            dn, // issuer
            derSeq(derUtcTime(now), derUtcTime(expires)),
            dn, // subject
            keyPair.public.encoded // SubjectPublicKeyInfo (already DER)
        )

        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(keyPair.private)
        sig.update(tbsCert)
        val sigBytes = sig.sign()

        return derSeq(
            tbsCert,
            sha256WithRsaAlg,
            derBitString(sigBytes)
        )
    }

    private fun buildPkcs7SignedData(
        sfBytes: ByteArray,
        certDer: ByteArray,
        privateKey: PrivateKey
    ): ByteArray {
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(privateKey)
        sig.update(sfBytes)
        val signatureBytes = sig.sign()

        val sha256OidAlg = derSeq(derOid("2.16.840.1.101.3.4.2.1"), derNull())
        val rsaEncryptionAlg = derSeq(derOid("1.2.840.113549.1.1.1"), derNull())

        val issuerAndSerial = extractIssuerAndSerial(certDer)

        val signerInfo = derSeq(
            derInt(BigInteger.ONE), // version = 1
            issuerAndSerial,
            sha256OidAlg,
            rsaEncryptionAlg,
            derOctetString(signatureBytes)
        )

        val encapContentInfo = derSeq(derOid("1.2.840.113549.1.7.1")) // pkcs7-data (detached)

        val signedData = derSeq(
            derInt(BigInteger.ONE), // version = 1
            derSet(sha256OidAlg),   // digestAlgorithms
            encapContentInfo,
            derImplicitConstructed(0, certDer), // certificates [0] IMPLICIT
            derSet(signerInfo)      // signerInfos
        )

        return derSeq(
            derOid("1.2.840.113549.1.7.2"), // pkcs7-signedData
            derExplicit(0, signedData)
        )
    }

    private fun extractIssuerAndSerial(certDer: ByteArray): ByteArray {
        // Parse minimal DER inside TBSCertificate: [0] version, serial, sigAlg, issuer
        var pos = 0
        fun readLen(buf: ByteArray, start: Int): Pair<Int, Int> {
            val b = buf[start].toInt() and 0xFF
            if (b < 0x80) return b to (start + 1)
            val numBytes = b and 0x7F
            var len = 0
            for (i in 1..numBytes) {
                len = (len shl 8) or (buf[start + i].toInt() and 0xFF)
            }
            return len to (start + 1 + numBytes)
        }

        // outer SEQUENCE
        pos += 1
        val (_, certBodyStart) = readLen(certDer, pos)
        // tbsCertificate SEQUENCE
        pos = certBodyStart + 1
        val (_, tbsStart) = readLen(certDer, pos)
        pos = tbsStart

        // [0] version
        if ((certDer[pos].toInt() and 0xFF) == 0xA0) {
            val (vLen, vBody) = readLen(certDer, pos + 1)
            pos = vBody + vLen
        }
        // serialNumber INTEGER
        val serialStart = pos
        val (sLen, sBody) = readLen(certDer, pos + 1)
        val serialTlv = certDer.copyOfRange(serialStart, sBody + sLen)
        pos = sBody + sLen

        // signature AlgorithmIdentifier
        val (aLen, aBody) = readLen(certDer, pos + 1)
        pos = aBody + aLen

        // issuer Name
        val issuerStart = pos
        val (iLen, iBody) = readLen(certDer, pos + 1)
        val issuerTlv = certDer.copyOfRange(issuerStart, iBody + iLen)

        return derSeq(issuerTlv, serialTlv)
    }

    // Minimal DER helpers
    private fun derTlv(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val len = content.size
        when {
            len < 128 -> out.write(len)
            len < 256 -> {
                out.write(0x81)
                out.write(len)
            }
            len < 65536 -> {
                out.write(0x82)
                out.write(len shr 8)
                out.write(len and 0xFF)
            }
            else -> {
                out.write(0x83)
                out.write(len shr 16)
                out.write((len shr 8) and 0xFF)
                out.write(len and 0xFF)
            }
        }
        out.writeBytes(content)
        return out.toByteArray()
    }

    private fun derSeq(vararg items: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        for (item in items) out.writeBytes(item)
        return derTlv(0x30, out.toByteArray())
    }

    private fun derSet(vararg items: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        for (item in items) out.writeBytes(item)
        return derTlv(0x31, out.toByteArray())
    }

    private fun derExplicit(tagNum: Int, item: ByteArray): ByteArray =
        derTlv(0xA0 or tagNum, item)

    private fun derImplicitConstructed(tagNum: Int, item: ByteArray): ByteArray =
        derTlv(0xA0 or tagNum, item)

    private fun derInt(value: BigInteger): ByteArray =
        derTlv(0x02, value.toByteArray())

    private fun derOctetString(data: ByteArray): ByteArray =
        derTlv(0x04, data)

    private fun derBitString(data: ByteArray): ByteArray {
        val padded = ByteArray(data.size + 1)
        padded[0] = 0
        System.arraycopy(data, 0, padded, 1, data.size)
        return derTlv(0x03, padded)
    }

    private fun derNull(): ByteArray = byteArrayOf(0x05, 0x00)

    private fun derPrintableString(s: String): ByteArray =
        derTlv(0x13, s.toByteArray(Charsets.US_ASCII))

    private fun derUtf8String(s: String): ByteArray =
        derTlv(0x0C, s.toByteArray(Charsets.UTF_8))

    private fun derUtcTime(date: Date): ByteArray {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.time = date
        val yy = cal.get(java.util.Calendar.YEAR) % 100
        val mm = cal.get(java.util.Calendar.MONTH) + 1
        val dd = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val hh = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val mi = cal.get(java.util.Calendar.MINUTE)
        val ss = cal.get(java.util.Calendar.SECOND)
        val formatted = "%02d%02d%02d%02d%02d%02dZ".format(yy, mm, dd, hh, mi, ss)
        return derTlv(0x17, formatted.toByteArray(Charsets.US_ASCII))
    }

    private fun derOid(oid: String): ByteArray {
        val parts = oid.split(".").map { it.toInt() }
        val out = ByteArrayOutputStream()
        out.write(parts[0] * 40 + parts[1])
        for (i in 2 until parts.size) {
            var v = parts[i]
            val stack = ArrayDeque<Int>()
            stack.addFirst(v and 0x7F)
            v = v ushr 7
            while (v > 0) {
                stack.addFirst((v and 0x7F) or 0x80)
                v = v ushr 7
            }
            for (b in stack) out.write(b)
        }
        return derTlv(0x06, out.toByteArray())
    }
}
