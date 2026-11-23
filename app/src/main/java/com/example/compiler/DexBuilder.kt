package com.example.compiler

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32

/**
 * Synthesizes a valid Dalvik DEX v035 bytecode binary (`classes.dex`) containing
 * `<package>.MainActivity` extending `android.app.Activity`, initializing a `WebView`
 * with JavaScript & DOM storage enabled and loading `file:///android_asset/index.html`.
 */
object DexBuilder {

    private const val HEADER_SIZE = 112
    private const val ACC_PUBLIC = 0x0001
    private const val ACC_CONSTRUCTOR = 0x10000

    private data class ProtoDef(
        val shorty: String,
        val returnType: String,
        val params: List<String>
    )

    private data class MethodDef(
        val classType: String,
        val name: String,
        val proto: ProtoDef
    )

    fun buildMainActivityDex(packageName: String): ByteArray {
        val mainDescriptor = "L${packageName.replace('.', '/')}/MainActivity;"
        val assetUrl = "file:///android_asset/index.html"

        // All types referenced
        val activityType = "Landroid/app/Activity;"
        val contextType = "Landroid/content/Context;"
        val bundleType = "Landroid/os/Bundle;"
        val viewType = "Landroid/view/View;"
        val webSettingsType = "Landroid/webkit/WebSettings;"
        val webViewType = "Landroid/webkit/WebView;"
        val stringType = "Ljava/lang/String;"
        val voidType = "V"

        val types = sortedSetOf(
            mainDescriptor,
            activityType,
            contextType,
            bundleType,
            viewType,
            webSettingsType,
            webViewType,
            stringType,
            voidType
        ).toList()

        // Protos
        val protoVoid = ProtoDef("V", voidType, emptyList())
        val protoVoidBundle = ProtoDef("VL", voidType, listOf(bundleType))
        val protoVoidContext = ProtoDef("VL", voidType, listOf(contextType))
        val protoVoidView = ProtoDef("VL", voidType, listOf(viewType))
        val protoVoidString = ProtoDef("VL", voidType, listOf(stringType))
        val protoVoidBool = ProtoDef("VZ", voidType, listOf("Z"))
        val protoSettings = ProtoDef("L", webSettingsType, emptyList())

        val allTypesSet = (types + listOf("Z")).toSortedSet().toList()

        // Strings (must be sorted strictly by UTF-8/UTF-16 codepoint order)
        val stringSet = sortedSetOf<String>()
        stringSet.addAll(allTypesSet)
        stringSet.addAll(
            listOf(
                "<init>",
                "onCreate",
                "getSettings",
                "setJavaScriptEnabled",
                "setDomStorageEnabled",
                "loadUrl",
                "setContentView",
                assetUrl,
                "V",
                "VL",
                "VZ",
                "L"
            )
        )
        val strings = stringSet.toList()
        val strIdx = strings.withIndex().associate { it.value to it.index }

        val typeList = allTypesSet // already sorted lexicographically by descriptor string
        val typeIdx = typeList.withIndex().associate { it.value to it.index }

        // Sort protos by (returnTypeIdx, params...)
        val protos = listOf(
            protoVoid,
            protoVoidBundle,
            protoVoidContext,
            protoVoidView,
            protoVoidString,
            protoVoidBool,
            protoSettings
        ).distinct().sortedWith { a, b ->
            val cmpRet = typeIdx.getValue(a.returnType).compareTo(typeIdx.getValue(b.returnType))
            if (cmpRet != 0) return@sortedWith cmpRet
            val minSize = minOf(a.params.size, b.params.size)
            for (i in 0 until minSize) {
                val cmpP = typeIdx.getValue(a.params[i]).compareTo(typeIdx.getValue(b.params[i]))
                if (cmpP != 0) return@sortedWith cmpP
            }
            a.params.size.compareTo(b.params.size)
        }
        val protoIdx = protos.withIndex().associate { it.value to it.index }

        // Methods
        val mActivityInit = MethodDef(activityType, "<init>", protoVoid)
        val mActivityOnCreate = MethodDef(activityType, "onCreate", protoVoidBundle)
        val mActivitySetContentView = MethodDef(activityType, "setContentView", protoVoidView)
        val mWebViewInit = MethodDef(webViewType, "<init>", protoVoidContext)
        val mWebViewGetSettings = MethodDef(webViewType, "getSettings", protoSettings)
        val mWebViewLoadUrl = MethodDef(webViewType, "loadUrl", protoVoidString)
        val mSettingsJs = MethodDef(webSettingsType, "setJavaScriptEnabled", protoVoidBool)
        val mSettingsDom = MethodDef(webSettingsType, "setDomStorageEnabled", protoVoidBool)
        val mMainInit = MethodDef(mainDescriptor, "<init>", protoVoid)
        val mMainOnCreate = MethodDef(mainDescriptor, "onCreate", protoVoidBundle)

        val methods = listOf(
            mActivityInit,
            mActivityOnCreate,
            mActivitySetContentView,
            mWebViewInit,
            mWebViewGetSettings,
            mWebViewLoadUrl,
            mSettingsJs,
            mSettingsDom,
            mMainInit,
            mMainOnCreate
        ).sortedWith { a, b ->
            val cmpClass = typeIdx.getValue(a.classType).compareTo(typeIdx.getValue(b.classType))
            if (cmpClass != 0) return@sortedWith cmpClass
            val cmpName = strIdx.getValue(a.name).compareTo(strIdx.getValue(b.name))
            if (cmpName != 0) return@sortedWith cmpName
            protoIdx.getValue(a.proto).compareTo(protoIdx.getValue(b.proto))
        }
        val methodIdx = methods.withIndex().associate { it.value to it.index }

        // Section offsets
        val stringIdsSize = strings.size
        val typeIdsSize = typeList.size
        val protoIdsSize = protos.size
        val methodIdsSize = methods.size
        val classDefsSize = 1

        val stringIdsOff = HEADER_SIZE
        val typeIdsOff = stringIdsOff + stringIdsSize * 4
        val protoIdsOff = typeIdsOff + typeIdsSize * 4
        val methodIdsOff = protoIdsOff + protoIdsSize * 12
        val classDefsOff = methodIdsOff + methodIdsSize * 8
        val dataOff = classDefsOff + classDefsSize * 32

        val dataStream = ByteArrayOutputStream()
        fun currentDataFileOffset() = dataOff + dataStream.size()
        fun alignData4() {
            while (currentDataFileOffset() % 4 != 0) {
                dataStream.write(0)
            }
        }

        // 1. Write type_list items for non-empty parameter lists (4-byte aligned)
        val typeListOffsets = mutableMapOf<List<String>, Int>()
        for (p in protos) {
            if (p.params.isNotEmpty() && !typeListOffsets.containsKey(p.params)) {
                alignData4()
                typeListOffsets[p.params] = currentDataFileOffset()
                val buf = ByteBuffer.allocate(4 + p.params.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                buf.putInt(p.params.size)
                for (param in p.params) {
                    buf.putShort(typeIdx.getValue(param).toShort())
                }
                dataStream.write(buf.array())
            }
        }

        // 2. Write string_data_item items
        val stringDataOffsets = IntArray(strings.size)
        for (i in strings.indices) {
            stringDataOffsets[i] = currentDataFileOffset()
            val s = strings[i]
            writeUleb128(dataStream, s.length)
            dataStream.write(encodeMutf8(s))
            dataStream.write(0)
        }

        // 3. Write code_item for <init>()V (4-byte aligned)
        alignData4()
        val initCodeOff = currentDataFileOffset()
        val initInsns = shortArrayOf(
            0x1070.toShort(), // invoke-direct {p0} (count=1, regC=0)
            methodIdx.getValue(mActivityInit).toShort(),
            0x0000.toShort(),
            0x000e.toShort()  // return-void
        )
        writeCodeItem(dataStream, registers = 1, ins = 1, outs = 1, insns = initInsns)

        // 4. Write code_item for onCreate(Bundle)V (4-byte aligned)
        // Registers: v0=webView, v1=settings/url, v2=const 1, v3=p0(this), v4=p1(savedInstanceState)
        alignData4()
        val onCreateCodeOff = currentDataFileOffset()
        val onCreateInsns = shortArrayOf(
            // invoke-super {v3, v4}, Activity.onCreate(Bundle)
            0x206f.toShort(),
            methodIdx.getValue(mActivityOnCreate).toShort(),
            0x0043.toShort(),
            // new-instance v0, WebView
            0x0022.toShort(),
            typeIdx.getValue(webViewType).toShort(),
            // invoke-direct {v0, v3}, WebView.<init>(Context)
            0x2070.toShort(),
            methodIdx.getValue(mWebViewInit).toShort(),
            0x0030.toShort(),
            // invoke-virtual {v0}, WebView.getSettings()
            0x106e.toShort(),
            methodIdx.getValue(mWebViewGetSettings).toShort(),
            0x0000.toShort(),
            // move-result-object v1
            0x010c.toShort(),
            // const/4 v2, 1
            0x1212.toShort(),
            // invoke-virtual {v1, v2}, WebSettings.setJavaScriptEnabled(true)
            0x206e.toShort(),
            methodIdx.getValue(mSettingsJs).toShort(),
            0x0021.toShort(),
            // invoke-virtual {v1, v2}, WebSettings.setDomStorageEnabled(true)
            0x206e.toShort(),
            methodIdx.getValue(mSettingsDom).toShort(),
            0x0021.toShort(),
            // const-string v1, "file:///android_asset/index.html"
            0x011a.toShort(),
            strIdx.getValue(assetUrl).toShort(),
            // invoke-virtual {v0, v1}, WebView.loadUrl(String)
            0x206e.toShort(),
            methodIdx.getValue(mWebViewLoadUrl).toShort(),
            0x0010.toShort(),
            // invoke-virtual {v3, v0}, Activity.setContentView(View)
            0x206e.toShort(),
            methodIdx.getValue(mActivitySetContentView).toShort(),
            0x0003.toShort(),
            // return-void
            0x000e.toShort()
        )
        writeCodeItem(dataStream, registers = 5, ins = 2, outs = 2, insns = onCreateInsns)

        // 5. Write class_data_item
        val classDataOff = currentDataFileOffset()
        writeUleb128(dataStream, 0) // static_fields_size
        writeUleb128(dataStream, 0) // instance_fields_size
        writeUleb128(dataStream, 1) // direct_methods_size (<init>)
        writeUleb128(dataStream, 1) // virtual_methods_size (onCreate)
        // direct method: <init>
        writeUleb128(dataStream, methodIdx.getValue(mMainInit))
        writeUleb128(dataStream, ACC_PUBLIC or ACC_CONSTRUCTOR)
        writeUleb128(dataStream, initCodeOff)
        // virtual method: onCreate
        writeUleb128(dataStream, methodIdx.getValue(mMainOnCreate))
        writeUleb128(dataStream, ACC_PUBLIC)
        writeUleb128(dataStream, onCreateCodeOff)

        // 6. Write map_list (4-byte aligned)
        alignData4()
        val mapOff = currentDataFileOffset()
        val mapEntries = mutableListOf(
            Triple(0x0000, 1, 0), // TYPE_HEADER_ITEM
            Triple(0x0001, stringIdsSize, stringIdsOff), // TYPE_STRING_ID_ITEM
            Triple(0x0002, typeIdsSize, typeIdsOff),     // TYPE_TYPE_ID_ITEM
            Triple(0x0003, protoIdsSize, protoIdsOff),   // TYPE_PROTO_ID_ITEM
            Triple(0x0005, methodIdsSize, methodIdsOff), // TYPE_METHOD_ID_ITEM
            Triple(0x0006, classDefsSize, classDefsOff), // TYPE_CLASS_DEF_ITEM
            Triple(0x1001, typeListOffsets.size, typeListOffsets.values.first()), // TYPE_TYPE_LIST
            Triple(0x2000, 1, classDataOff),             // TYPE_CLASS_DATA_ITEM
            Triple(0x2001, 2, initCodeOff),              // TYPE_CODE_ITEM
            Triple(0x2002, strings.size, stringDataOffsets.first()), // TYPE_STRING_DATA_ITEM
            Triple(0x1000, 1, mapOff)                    // TYPE_MAP_LIST
        )
        val mapBuf = ByteBuffer.allocate(4 + mapEntries.size * 12).order(ByteOrder.LITTLE_ENDIAN)
        mapBuf.putInt(mapEntries.size)
        for ((type, size, off) in mapEntries) {
            mapBuf.putShort(type.toShort())
            mapBuf.putShort(0)
            mapBuf.putInt(size)
            mapBuf.putInt(off)
        }
        dataStream.write(mapBuf.array())
        val dataBytes = dataStream.toByteArray()
        val fileSize = dataOff + dataBytes.size

        // Assemble full DEX buffer
        val dex = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN)

        // Header (112 bytes)
        dex.put("dex\n035\u0000".toByteArray(Charsets.US_ASCII))
        dex.putInt(0) // checksum placeholder
        dex.put(ByteArray(20)) // SHA-1 signature placeholder
        dex.putInt(fileSize)
        dex.putInt(HEADER_SIZE)
        dex.putInt(0x12345678) // endian_tag
        dex.putInt(0) // link_size
        dex.putInt(0) // link_off
        dex.putInt(mapOff)
        dex.putInt(stringIdsSize)
        dex.putInt(stringIdsOff)
        dex.putInt(typeIdsSize)
        dex.putInt(typeIdsOff)
        dex.putInt(protoIdsSize)
        dex.putInt(protoIdsOff)
        dex.putInt(0) // field_ids_size
        dex.putInt(0) // field_ids_off
        dex.putInt(methodIdsSize)
        dex.putInt(methodIdsOff)
        dex.putInt(classDefsSize)
        dex.putInt(classDefsOff)
        dex.putInt(dataBytes.size)
        dex.putInt(dataOff)

        // string_ids
        for (off in stringDataOffsets) dex.putInt(off)

        // type_ids
        for (t in typeList) dex.putInt(strIdx.getValue(t))

        // proto_ids
        for (p in protos) {
            dex.putInt(strIdx.getValue(p.shorty))
            dex.putInt(typeIdx.getValue(p.returnType))
            dex.putInt(if (p.params.isEmpty()) 0 else typeListOffsets.getValue(p.params))
        }

        // method_ids
        for (m in methods) {
            dex.putShort(typeIdx.getValue(m.classType).toShort())
            dex.putShort(protoIdx.getValue(m.proto).toShort())
            dex.putInt(strIdx.getValue(m.name))
        }

        // class_defs (1 class: MainActivity)
        dex.putInt(typeIdx.getValue(mainDescriptor))
        dex.putInt(ACC_PUBLIC)
        dex.putInt(typeIdx.getValue(activityType))
        dex.putInt(0) // interfaces_off
        dex.putInt(-1) // source_file_idx
        dex.putInt(0) // annotations_off
        dex.putInt(classDataOff)
        dex.putInt(0) // static_values_off

        // data section
        dex.put(dataBytes)

        val result = dex.array()

        // Compute SHA-1 over [32..fileSize)
        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(result, 32, fileSize - 32)
        val digest = sha1.digest()
        System.arraycopy(digest, 0, result, 12, 20)

        // Compute Adler32 over [12..fileSize)
        val adler = Adler32()
        adler.update(result, 12, fileSize - 12)
        val checksum = adler.value.toInt()
        ByteBuffer.wrap(result, 8, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(checksum)

        return result
    }

    private fun writeCodeItem(
        out: ByteArrayOutputStream,
        registers: Int,
        ins: Int,
        outs: Int,
        insns: ShortArray
    ) {
        val buf = ByteBuffer.allocate(16 + insns.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(registers.toShort())
        buf.putShort(ins.toShort())
        buf.putShort(outs.toShort())
        buf.putShort(0) // tries_size
        buf.putInt(0)   // debug_info_off
        buf.putInt(insns.size)
        for (s in insns) buf.putShort(s)
        out.write(buf.array())
    }

    private fun writeUleb128(out: ByteArrayOutputStream, value: Int) {
        var v = value
        do {
            var b = v and 0x7f
            v = v ushr 7
            if (v != 0) b = b or 0x80
            out.write(b)
        } while (v != 0)
    }

    private fun encodeMutf8(s: String): ByteArray {
        val out = ByteArrayOutputStream(s.length)
        for (ch in s) {
            val c = ch.code
            if (c != 0 && c <= 0x7f) {
                out.write(c)
            } else if (c <= 0x7ff) {
                out.write(0xc0 or (0x1f and (c shr 6)))
                out.write(0x80 or (0x3f and c))
            } else {
                out.write(0xe0 or (0x0f and (c shr 12)))
                out.write(0x80 or (0x3f and (c shr 6)))
                out.write(0x80 or (0x3f and c))
            }
        }
        return out.toByteArray()
    }
}
