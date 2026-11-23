package com.example.compiler

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encodes an Android Binary XML (AXML) AndroidManifest.xml directly in Kotlin.
 */
object BinaryXmlEncoder {

    private const val RES_XML_TYPE = 0x0003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_RESOURCE_MAP_TYPE = 0x0180
    private const val RES_XML_START_NAMESPACE_TYPE = 0x0100
    private const val RES_XML_END_NAMESPACE_TYPE = 0x0101
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val RES_XML_END_ELEMENT_TYPE = 0x0103

    private const val TYPE_REFERENCE = 0x01
    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_HEX = 0x11
    private const val TYPE_INT_BOOLEAN = 0x12

    private const val ANDROID_NS_URI = "http://schemas.android.com/apk/res/android"
    private const val ANDROID_PREFIX = "android"

    fun encodeManifest(
        packageName: String,
        appName: String,
        versionName: String,
        versionCode: Int,
        minSdk: Int = 24,
        targetSdk: Int = 34
    ): ByteArray {
        // Attributes that map to framework android.R.attr resource IDs MUST come first
        val resAttrDefs = listOf(
            "versionCode" to 0x0101021b,
            "versionName" to 0x0101021c,
            "minSdkVersion" to 0x0101020c,
            "targetSdkVersion" to 0x01010270,
            "name" to 0x01010003,
            "label" to 0x01010001,
            "icon" to 0x01010002,
            "exported" to 0x01010010,
            "usesCleartextTraffic" to 0x010104ec,
            "configChanges" to 0x0101001f
        )

        val strings = mutableListOf<String>()
        val stringIndices = mutableMapOf<String, Int>()
        fun intern(s: String): Int = stringIndices.getOrPut(s) {
            strings.add(s)
            strings.size - 1
        }

        resAttrDefs.forEach { intern(it.first) }

        val idxNsUri = intern(ANDROID_NS_URI)
        val idxNsPrefix = intern(ANDROID_PREFIX)
        val idxManifest = intern("manifest")
        val idxPackage = intern("package")
        val idxPkgVal = intern(packageName)
        val idxVersionNameVal = intern(versionName)
        val idxUsesSdk = intern("uses-sdk")
        val idxUsesPerm = intern("uses-permission")
        val idxPermInternet = intern("android.permission.INTERNET")
        val idxApplication = intern("application")
        val idxAppNameVal = intern(appName)
        val idxActivity = intern("activity")
        val idxMainActivity = intern(".MainActivity")
        val idxIntentFilter = intern("intent-filter")
        val idxAction = intern("action")
        val idxActionMain = intern("android.intent.action.MAIN")
        val idxCategory = intern("category")
        val idxCatLauncher = intern("android.intent.category.LAUNCHER")

        val idxVersionCodeAttr = stringIndices.getValue("versionCode")
        val idxVersionNameAttr = stringIndices.getValue("versionName")
        val idxMinSdkAttr = stringIndices.getValue("minSdkVersion")
        val idxTargetSdkAttr = stringIndices.getValue("targetSdkVersion")
        val idxNameAttr = stringIndices.getValue("name")
        val idxLabelAttr = stringIndices.getValue("label")
        val idxIconAttr = stringIndices.getValue("icon")
        val idxExportedAttr = stringIndices.getValue("exported")
        val idxCleartextAttr = stringIndices.getValue("usesCleartextTraffic")
        val idxConfigChangesAttr = stringIndices.getValue("configChanges")

        val body = ByteArrayOutputStream()
        body.write(buildStringPool(strings))
        body.write(buildResourceMap(resAttrDefs.map { it.second }))

        // Start android namespace
        body.write(buildNamespaceNode(RES_XML_START_NAMESPACE_TYPE, 2, idxNsPrefix, idxNsUri))

        // <manifest package="..." android:versionCode="..." android:versionName="...">
        body.write(
            buildStartElement(
                line = 2,
                ns = -1,
                name = idxManifest,
                attrs = listOf(
                    Attr(-1, idxPackage, idxPkgVal, TYPE_STRING, idxPkgVal),
                    Attr(idxNsUri, idxVersionCodeAttr, -1, TYPE_INT_DEC, versionCode),
                    Attr(idxNsUri, idxVersionNameAttr, idxVersionNameVal, TYPE_STRING, idxVersionNameVal)
                )
            )
        )

        // <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="34" />
        body.write(
            buildStartElement(
                line = 4,
                ns = -1,
                name = idxUsesSdk,
                attrs = listOf(
                    Attr(idxNsUri, idxMinSdkAttr, -1, TYPE_INT_DEC, minSdk),
                    Attr(idxNsUri, idxTargetSdkAttr, -1, TYPE_INT_DEC, targetSdk)
                )
            )
        )
        body.write(buildEndElement(4, -1, idxUsesSdk))

        // <uses-permission android:name="android.permission.INTERNET" />
        body.write(
            buildStartElement(
                line = 5,
                ns = -1,
                name = idxUsesPerm,
                attrs = listOf(
                    Attr(idxNsUri, idxNameAttr, idxPermInternet, TYPE_STRING, idxPermInternet)
                )
            )
        )
        body.write(buildEndElement(5, -1, idxUsesPerm))

        // <application android:label="..." android:icon="@drawable/ic_launcher" android:usesCleartextTraffic="true">
        body.write(
            buildStartElement(
                line = 7,
                ns = -1,
                name = idxApplication,
                attrs = listOf(
                    Attr(idxNsUri, idxLabelAttr, idxAppNameVal, TYPE_STRING, idxAppNameVal),
                    Attr(idxNsUri, idxIconAttr, -1, TYPE_REFERENCE, 0x7f010000),
                    Attr(idxNsUri, idxCleartextAttr, -1, TYPE_INT_BOOLEAN, -1)
                )
            )
        )

        // <activity android:name=".MainActivity" android:exported="true" android:configChanges="0x480">
        body.write(
            buildStartElement(
                line = 12,
                ns = -1,
                name = idxActivity,
                attrs = listOf(
                    Attr(idxNsUri, idxNameAttr, idxMainActivity, TYPE_STRING, idxMainActivity),
                    Attr(idxNsUri, idxExportedAttr, -1, TYPE_INT_BOOLEAN, -1),
                    Attr(idxNsUri, idxConfigChangesAttr, -1, TYPE_INT_HEX, 0x0480)
                )
            )
        )

        // <intent-filter>
        body.write(buildStartElement(16, -1, idxIntentFilter, emptyList()))

        // <action android:name="android.intent.action.MAIN" />
        body.write(
            buildStartElement(
                line = 17,
                ns = -1,
                name = idxAction,
                attrs = listOf(
                    Attr(idxNsUri, idxNameAttr, idxActionMain, TYPE_STRING, idxActionMain)
                )
            )
        )
        body.write(buildEndElement(17, -1, idxAction))

        // <category android:name="android.intent.category.LAUNCHER" />
        body.write(
            buildStartElement(
                line = 18,
                ns = -1,
                name = idxCategory,
                attrs = listOf(
                    Attr(idxNsUri, idxNameAttr, idxCatLauncher, TYPE_STRING, idxCatLauncher)
                )
            )
        )
        body.write(buildEndElement(18, -1, idxCategory))

        body.write(buildEndElement(19, -1, idxIntentFilter))
        body.write(buildEndElement(20, -1, idxActivity))
        body.write(buildEndElement(21, -1, idxApplication))
        body.write(buildEndElement(22, -1, idxManifest))
        body.write(buildNamespaceNode(RES_XML_END_NAMESPACE_TYPE, 22, idxNsPrefix, idxNsUri))

        val bodyBytes = body.toByteArray()
        val totalSize = 8 + bodyBytes.size
        val out = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        out.putShort(RES_XML_TYPE.toShort())
        out.putShort(8.toShort())
        out.putInt(totalSize)
        out.put(bodyBytes)
        return out.array()
    }

    private data class Attr(
        val ns: Int,
        val name: Int,
        val rawValue: Int,
        val dataType: Int,
        val data: Int
    )

    private fun buildStringPool(strings: List<String>): ByteArray {
        val offsets = IntArray(strings.size)
        val stringData = ByteArrayOutputStream()
        for (i in strings.indices) {
            offsets[i] = stringData.size()
            val chars = strings[i].toCharArray()
            val len = chars.size
            val buf = ByteBuffer.allocate(2 + len * 2 + 2).order(ByteOrder.LITTLE_ENDIAN)
            buf.putShort(len.toShort())
            for (c in chars) {
                buf.putShort(c.code.toShort())
            }
            buf.putShort(0)
            stringData.write(buf.array())
        }
        while (stringData.size() % 4 != 0) {
            stringData.write(0)
        }
        val rawStrings = stringData.toByteArray()
        val headerSize = 28
        val stringsStart = headerSize + strings.size * 4
        val chunkSize = stringsStart + rawStrings.size

        val out = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN)
        out.putShort(RES_STRING_POOL_TYPE.toShort())
        out.putShort(headerSize.toShort())
        out.putInt(chunkSize)
        out.putInt(strings.size) // stringCount
        out.putInt(0)            // styleCount
        out.putInt(0)            // flags (UTF-16LE)
        out.putInt(stringsStart)
        out.putInt(0)            // stylesStart
        for (off in offsets) {
            out.putInt(off)
        }
        out.put(rawStrings)
        return out.array()
    }

    private fun buildResourceMap(resourceIds: List<Int>): ByteArray {
        val chunkSize = 8 + resourceIds.size * 4
        val out = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN)
        out.putShort(RES_XML_RESOURCE_MAP_TYPE.toShort())
        out.putShort(8.toShort())
        out.putInt(chunkSize)
        for (id in resourceIds) {
            out.putInt(id)
        }
        return out.array()
    }

    private fun buildNamespaceNode(type: Int, line: Int, prefix: Int, uri: Int): ByteArray {
        val out = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        out.putShort(type.toShort())
        out.putShort(16.toShort())
        out.putInt(24)
        out.putInt(line)
        out.putInt(-1) // comment
        out.putInt(prefix)
        out.putInt(uri)
        return out.array()
    }

    private fun buildStartElement(line: Int, ns: Int, name: Int, attrs: List<Attr>): ByteArray {
        val chunkSize = 36 + attrs.size * 20
        val out = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN)
        out.putShort(RES_XML_START_ELEMENT_TYPE.toShort())
        out.putShort(16.toShort())
        out.putInt(chunkSize)
        out.putInt(line)
        out.putInt(-1) // comment
        out.putInt(ns)
        out.putInt(name)
        out.putShort(20.toShort()) // attributeStart
        out.putShort(20.toShort()) // attributeSize
        out.putShort(attrs.size.toShort())
        out.putShort(0) // idIndex
        out.putShort(0) // classIndex
        out.putShort(0) // styleIndex

        for (attr in attrs) {
            out.putInt(attr.ns)
            out.putInt(attr.name)
            out.putInt(attr.rawValue)
            out.putShort(8.toShort()) // Res_value.size
            out.put(0.toByte())       // res0
            out.put(attr.dataType.toByte())
            out.putInt(attr.data)
        }
        return out.array()
    }

    private fun buildEndElement(line: Int, ns: Int, name: Int): ByteArray {
        val out = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        out.putShort(RES_XML_END_ELEMENT_TYPE.toShort())
        out.putShort(16.toShort())
        out.putInt(24)
        out.putInt(line)
        out.putInt(-1) // comment
        out.putInt(ns)
        out.putInt(name)
        return out.array()
    }
}
