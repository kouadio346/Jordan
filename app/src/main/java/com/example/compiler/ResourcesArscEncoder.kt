package com.example.compiler

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encodes a valid Android binary resources.arsc table with:
 * - drawable/ic_launcher (0x7f010000) -> "res/drawable/ic_launcher.png"
 * - string/app_name     (0x7f020000) -> appName
 */
object ResourcesArscEncoder {

    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_TABLE_TYPE = 0x0002
    private const val RES_TABLE_PACKAGE_TYPE = 0x0200
    private const val RES_TABLE_TYPE_TYPE = 0x0201
    private const val RES_TABLE_TYPE_SPEC_TYPE = 0x0202
    private const val TYPE_STRING = 0x03

    fun encode(packageName: String, appName: String): ByteArray {
        val globalPool = buildUtf16StringPool(
            listOf(
                "res/drawable/ic_launcher.png",
                appName
            )
        )

        val typeStringsPool = buildUtf16StringPool(listOf("drawable", "string"))
        val keyStringsPool = buildUtf16StringPool(listOf("ic_launcher", "app_name"))

        val drawableSpec = buildTypeSpec(typeId = 1, entryCount = 1)
        val drawableType = buildTypeChunk(
            typeId = 1,
            keyIndex = 0,
            stringPoolValueIndex = 0
        )

        val stringSpec = buildTypeSpec(typeId = 2, entryCount = 1)
        val stringType = buildTypeChunk(
            typeId = 2,
            keyIndex = 1,
            stringPoolValueIndex = 1
        )

        val pkgHeaderSize = 288
        val typeStringsOffset = pkgHeaderSize
        val keyStringsOffset = typeStringsOffset + typeStringsPool.size

        val pkgBody = ByteArrayOutputStream()
        pkgBody.write(typeStringsPool)
        pkgBody.write(keyStringsPool)
        pkgBody.write(drawableSpec)
        pkgBody.write(drawableType)
        pkgBody.write(stringSpec)
        pkgBody.write(stringType)
        val pkgBodyBytes = pkgBody.toByteArray()

        val pkgChunkSize = pkgHeaderSize + pkgBodyBytes.size
        val pkgBuf = ByteBuffer.allocate(pkgChunkSize).order(ByteOrder.LITTLE_ENDIAN)
        pkgBuf.putShort(RES_TABLE_PACKAGE_TYPE.toShort())
        pkgBuf.putShort(pkgHeaderSize.toShort())
        pkgBuf.putInt(pkgChunkSize)
        pkgBuf.putInt(0x7f) // package id
        val nameChars = packageName.take(127).toCharArray()
        for (i in 0 until 128) {
            pkgBuf.putShort(if (i < nameChars.size) nameChars[i].code.toShort() else 0)
        }
        pkgBuf.putInt(typeStringsOffset)
        pkgBuf.putInt(2) // lastPublicType
        pkgBuf.putInt(keyStringsOffset)
        pkgBuf.putInt(2) // lastPublicKey
        pkgBuf.putInt(0) // typeIdOffset
        pkgBuf.put(pkgBodyBytes)
        val pkgBytes = pkgBuf.array()

        val tableHeaderSize = 12
        val totalSize = tableHeaderSize + globalPool.size + pkgBytes.size
        val tableBuf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        tableBuf.putShort(RES_TABLE_TYPE.toShort())
        tableBuf.putShort(tableHeaderSize.toShort())
        tableBuf.putInt(totalSize)
        tableBuf.putInt(1) // packageCount
        tableBuf.put(globalPool)
        tableBuf.put(pkgBytes)
        return tableBuf.array()
    }

    private fun buildUtf16StringPool(strings: List<String>): ByteArray {
        val offsets = IntArray(strings.size)
        val stringData = ByteArrayOutputStream()
        for (i in strings.indices) {
            offsets[i] = stringData.size()
            val chars = strings[i].toCharArray()
            val buf = ByteBuffer.allocate(2 + chars.size * 2 + 2).order(ByteOrder.LITTLE_ENDIAN)
            buf.putShort(chars.size.toShort())
            for (c in chars) buf.putShort(c.code.toShort())
            buf.putShort(0)
            stringData.write(buf.array())
        }
        while (stringData.size() % 4 != 0) stringData.write(0)
        val rawStrings = stringData.toByteArray()

        val headerSize = 28
        val stringsStart = headerSize + strings.size * 4
        val chunkSize = stringsStart + rawStrings.size
        val out = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN)
        out.putShort(RES_STRING_POOL_TYPE.toShort())
        out.putShort(headerSize.toShort())
        out.putInt(chunkSize)
        out.putInt(strings.size)
        out.putInt(0)
        out.putInt(0) // UTF-16LE
        out.putInt(stringsStart)
        out.putInt(0)
        for (off in offsets) out.putInt(off)
        out.put(rawStrings)
        return out.array()
    }

    private fun buildTypeSpec(typeId: Int, entryCount: Int): ByteArray {
        val headerSize = 16
        val chunkSize = headerSize + entryCount * 4
        val out = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN)
        out.putShort(RES_TABLE_TYPE_SPEC_TYPE.toShort())
        out.putShort(headerSize.toShort())
        out.putInt(chunkSize)
        out.put(typeId.toByte())
        out.put(0.toByte())
        out.putShort(0)
        out.putInt(entryCount)
        for (i in 0 until entryCount) {
            out.putInt(0) // config flags
        }
        return out.array()
    }

    private fun buildTypeChunk(typeId: Int, keyIndex: Int, stringPoolValueIndex: Int): ByteArray {
        val configSize = 64
        val headerSize = 20 + configSize // 84 bytes
        val entryOffsetsSize = 4
        val entriesStart = headerSize + entryOffsetsSize
        val entrySize = 16 // ResTable_entry (8) + Res_value (8)
        val chunkSize = entriesStart + entrySize

        val out = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN)
        out.putShort(RES_TABLE_TYPE_TYPE.toShort())
        out.putShort(headerSize.toShort())
        out.putInt(chunkSize)
        out.put(typeId.toByte())
        out.put(0.toByte()) // flags
        out.putShort(0)     // reserved
        out.putInt(1)       // entryCount
        out.putInt(entriesStart)

        // ResTable_config (64 bytes): first uint32 is configSize = 64, rest zeros
        out.putInt(configSize)
        for (i in 0 until (configSize - 4)) {
            out.put(0.toByte())
        }

        // Entry offset 0 -> 0
        out.putInt(0)

        // ResTable_entry (8 bytes)
        out.putShort(8.toShort()) // size
        out.putShort(0.toShort()) // flags
        out.putInt(keyIndex)      // key string index

        // Res_value (8 bytes)
        out.putShort(8.toShort()) // size
        out.put(0.toByte())       // res0
        out.put(TYPE_STRING.toByte())
        out.putInt(stringPoolValueIndex)

        return out.array()
    }
}
