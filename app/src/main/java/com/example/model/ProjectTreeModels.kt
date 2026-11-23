package com.example.model

import kotlin.math.max
import kotlin.math.min

data class ProjectFileStats(
    val file: ProjectFile,
    val normalizedPath: String,
    val fileName: String,
    val folderPath: String,
    val displayGroup: String,
    val lineCount: Int,
    val byteCount: Int,
    val weight: Float,
    val languageLabel: String,
    val accentHex: String,
    val isPendingGeneration: Boolean = false
)

sealed interface ProjectTreeNode {
    val name: String
    val fullPath: String
    val totalFiles: Int
    val totalLines: Int
    val totalBytes: Int

    data class Directory(
        override val name: String,
        override val fullPath: String,
        val children: List<ProjectTreeNode>,
        override val totalFiles: Int,
        override val totalLines: Int,
        override val totalBytes: Int
    ) : ProjectTreeNode

    data class FileLeaf(
        val stats: ProjectFileStats,
        override val fullPath: String
    ) : ProjectTreeNode {
        override val name: String get() = stats.fileName
        override val totalFiles: Int get() = 1
        override val totalLines: Int get() = stats.lineCount
        override val totalBytes: Int get() = stats.byteCount
    }
}

data class LanguageSlice(
    val language: String,
    val accentHex: String,
    val fileCount: Int,
    val lineCount: Int,
    val byteCount: Int,
    val fraction: Float
)

data class NormalizedRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    val area: Float get() = width * height
}

data class TreemapTileRect(
    val stats: ProjectFileStats,
    val rect: NormalizedRect,
    val percentage: Float
)

data class TreemapGroupRect(
    val groupName: String,
    val rect: NormalizedRect,
    val accentHex: String,
    val totalLines: Int,
    val totalBytes: Int,
    val tiles: List<TreemapTileRect>
)

object ProjectHierarchyBuilder {

    fun normalizePath(raw: String): String {
        return raw.trim()
            .replace('\\', '/')
            .trimStart('/')
            .replace(Regex("/+"), "/")
    }

    fun analyzeFiles(
        files: List<ProjectFile>,
        pendingPaths: Set<String> = emptySet()
    ): List<ProjectFileStats> {
        val seen = linkedSetOf<String>()
        val result = mutableListOf<ProjectFileStats>()

        files.forEach { file ->
            val norm = normalizePath(file.path).ifBlank { "Main.kt" }
            if (seen.add(norm.lowercase())) {
                result.add(buildStats(file.copy(path = norm), norm, isPending = false))
            }
        }

        pendingPaths.forEach { rawPending ->
            val norm = normalizePath(rawPending)
            if (norm.isNotBlank() && seen.add(norm.lowercase())) {
                val inferredLang = inferLanguageFromPath(norm)
                result.add(
                    buildStats(
                        file = ProjectFile(path = norm, language = inferredLang, content = ""),
                        norm = norm,
                        isPending = true
                    )
                )
            }
        }

        return result
    }

    private fun buildStats(
        file: ProjectFile,
        norm: String,
        isPending: Boolean
    ): ProjectFileStats {
        val fileName = norm.substringAfterLast('/')
        val folderPath = if ('/' in norm) norm.substringBeforeLast('/') else ""
        val languageLabel = file.language.trim().ifBlank { inferLanguageFromPath(norm) }
        val lineCount = if (isPending || file.content.isBlank()) {
            0
        } else {
            file.content.lines().size.coerceAtLeast(1)
        }
        val byteCount = if (isPending) 0 else file.content.toByteArray(Charsets.UTF_8).size
        val weight = if (isPending) 480f else byteCount.coerceAtLeast(160).toFloat()

        return ProjectFileStats(
            file = file,
            normalizedPath = norm,
            fileName = fileName,
            folderPath = folderPath,
            displayGroup = resolveDisplayGroup(norm),
            lineCount = lineCount,
            byteCount = byteCount,
            weight = weight,
            languageLabel = languageLabel,
            accentHex = colorHexForFile(norm, languageLabel),
            isPendingGeneration = isPending
        )
    }

    fun resolveDisplayGroup(normalizedPath: String): String {
        val clean = normalizePath(normalizedPath)
        val parts = clean.split('/').filter { it.isNotBlank() }
        if (parts.size <= 1) return "Racine Projet"
        val fileDir = parts.dropLast(1)
        val javaIdx = fileDir.indexOfLast { it == "java" || it == "kotlin" }
        if (javaIdx >= 0 && javaIdx < fileDir.lastIndex) {
            return fileDir.drop(javaIdx + 1).takeLast(2).joinToString(".")
        }
        return when {
            "assets" in fileDir -> "assets"
            "res" in fileDir -> fileDir.drop(fileDir.indexOf("res")).take(2).joinToString("/")
            else -> fileDir.takeLast(2).joinToString("/")
        }
    }

    fun inferLanguageFromPath(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts" -> if (path.endsWith(".gradle.kts", true)) "Gradle KTS" else "Kotlin"
            "java" -> "Java"
            "xml" -> "XML"
            "html", "htm" -> "HTML5"
            "js", "mjs" -> "JavaScript"
            "css" -> "CSS"
            "cs" -> "C#"
            "gd" -> "GDScript"
            "cpp", "cc", "h", "hpp" -> "C++"
            "lua" -> "Lua"
            "dart" -> "Dart"
            "py" -> "Python"
            "json" -> "JSON"
            "gradle" -> "Gradle"
            else -> "Code"
        }
    }

    fun colorHexForFile(path: String, language: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        val langLower = language.lowercase()
        return when {
            path.endsWith("gradle.kts", true) || path.endsWith("gradle", true) -> "#00E5FF"
            ext == "kt" || "kotlin" in langLower -> "#7F52FF"
            ext == "java" || "java" in langLower -> "#F89820"
            ext == "xml" || "xml" in langLower -> "#10B981"
            ext == "html" || ext == "htm" || ext == "js" || "html" in langLower -> "#E34F26"
            ext == "css" -> "#38BDF8"
            ext == "cs" || "c#" in langLower -> "#22C55E"
            ext == "gd" || "gdscript" in langLower -> "#478CBF"
            ext == "cpp" || ext == "h" || "c++" in langLower -> "#3B82F6"
            ext == "dart" || "dart" in langLower -> "#0EA5E9"
            ext == "py" || "python" in langLower -> "#FFD43B"
            ext == "lua" || "lua" in langLower -> "#A855F7"
            ext == "json" -> "#FFB300"
            else -> "#94A3B8"
        }
    }

    fun buildLanguageBreakdown(stats: List<ProjectFileStats>): List<LanguageSlice> {
        if (stats.isEmpty()) return emptyList()
        val totalWeight = stats.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1f)
        return stats
            .groupBy { it.languageLabel to it.accentHex }
            .map { (key, list) ->
                val groupWeight = list.sumOf { it.weight.toDouble() }.toFloat()
                LanguageSlice(
                    language = key.first,
                    accentHex = key.second,
                    fileCount = list.size,
                    lineCount = list.sumOf { it.lineCount },
                    byteCount = list.sumOf { it.byteCount },
                    fraction = (groupWeight / totalWeight).coerceIn(0f, 1f)
                )
            }
            .sortedByDescending { it.fraction }
    }

    private class MutableDirBuilder(
        val name: String,
        val fullPath: String
    ) {
        val subdirs = linkedMapOf<String, MutableDirBuilder>()
        val files = mutableListOf<ProjectFileStats>()
    }

    fun buildTree(
        rootName: String,
        files: List<ProjectFile>,
        pendingPaths: Set<String> = emptySet(),
        compactMiddleDirs: Boolean = true
    ): ProjectTreeNode.Directory {
        val statsList = analyzeFiles(files, pendingPaths)
        val rootBuilder = MutableDirBuilder(rootName, "")

        statsList.forEach { stats ->
            val segments = stats.normalizedPath.split('/').filter { it.isNotBlank() }
            if (segments.size <= 1) {
                rootBuilder.files.add(stats)
            } else {
                var cursor = rootBuilder
                val pathAcc = mutableListOf<String>()
                for (i in 0 until segments.lastIndex) {
                    val seg = segments[i]
                    pathAcc.add(seg)
                    cursor = cursor.subdirs.getOrPut(seg) {
                        MutableDirBuilder(seg, pathAcc.joinToString("/"))
                    }
                }
                cursor.files.add(stats)
            }
        }

        fun convert(builder: MutableDirBuilder): ProjectTreeNode.Directory {
            val childDirs = builder.subdirs.values
                .sortedBy { it.name.lowercase() }
                .map { convert(it) }
                .map { dir ->
                    if (compactMiddleDirs) compactDirectory(dir) else dir
                }

            val childFiles = builder.files
                .sortedBy { it.fileName.lowercase() }
                .map { ProjectTreeNode.FileLeaf(stats = it, fullPath = it.normalizedPath) }

            val allChildren = childDirs + childFiles
            return ProjectTreeNode.Directory(
                name = builder.name,
                fullPath = builder.fullPath,
                children = allChildren,
                totalFiles = allChildren.sumOf { it.totalFiles },
                totalLines = allChildren.sumOf { it.totalLines },
                totalBytes = allChildren.sumOf { it.totalBytes }
            )
        }

        return convert(rootBuilder)
    }

    private fun compactDirectory(dir: ProjectTreeNode.Directory): ProjectTreeNode.Directory {
        var current = dir
        while (current.children.size == 1 && current.children.first() is ProjectTreeNode.Directory) {
            val onlyChild = current.children.first() as ProjectTreeNode.Directory
            current = onlyChild.copy(
                name = "${current.name}/${onlyChild.name}"
            )
        }
        return current
    }

    fun collectAllDirectoryPaths(root: ProjectTreeNode.Directory): Set<String> {
        val out = linkedSetOf<String>()
        fun walk(dir: ProjectTreeNode.Directory) {
            if (dir.fullPath.isNotBlank()) out.add(dir.fullPath)
            dir.children.filterIsInstance<ProjectTreeNode.Directory>().forEach { walk(it) }
        }
        walk(root)
        return out
    }

    /**
     * Computes a two-level hierarchical Squarified TreeMap in normalized [0..1] coordinates:
     * Level 1 = module/folder groups, Level 2 = proportional files inside each group.
     */
    fun computeHierarchicalTreemap(
        files: List<ProjectFile>,
        pendingPaths: Set<String> = emptySet(),
        bounds: NormalizedRect = NormalizedRect(0f, 0f, 1f, 1f)
    ): List<TreemapGroupRect> {
        val stats = analyzeFiles(files, pendingPaths)
        if (stats.isEmpty() || bounds.width <= 0f || bounds.height <= 0f) return emptyList()

        val totalWeight = stats.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1f)
        val grouped = stats.groupBy { it.displayGroup }
            .entries
            .sortedByDescending { entry -> entry.value.sumOf { it.weight.toDouble() } }

        val groupWeights = grouped.map { entry ->
            entry.key to entry.value.sumOf { it.weight.toDouble() }.toFloat()
        }

        val groupRects = squarify(
            items = groupWeights.mapIndexed { idx, (_, w) -> idx to w },
            bounds = bounds
        )

        return groupRects.mapNotNull { (idx, gRect) ->
            val groupEntry = grouped.getOrNull(idx) ?: return@mapNotNull null
            val groupFiles = groupEntry.value.sortedByDescending { it.weight }
            val dominantAccent = groupFiles
                .groupBy { it.accentHex }
                .maxByOrNull { (_, list) -> list.sumOf { it.weight.toDouble() } }
                ?.key ?: "#00E5FF"

            val tileRects = squarify(
                items = groupFiles.mapIndexed { fIdx, f -> fIdx to f.weight },
                bounds = NormalizedRect(0f, 0f, 1f, 1f)
            )

            val tiles = tileRects.mapNotNull { (fIdx, localRect) ->
                val fStats = groupFiles.getOrNull(fIdx) ?: return@mapNotNull null
                TreemapTileRect(
                    stats = fStats,
                    rect = localRect,
                    percentage = ((fStats.weight / totalWeight) * 100f).coerceIn(0.1f, 100f)
                )
            }

            TreemapGroupRect(
                groupName = groupEntry.key,
                rect = gRect,
                accentHex = dominantAccent,
                totalLines = groupFiles.sumOf { it.lineCount },
                totalBytes = groupFiles.sumOf { it.byteCount },
                tiles = tiles
            )
        }
    }

    /**
     * Squarified Treemap partitioning algorithm (Bruls-Huizing-van Wijk) keeping aspect ratios close to 1:1.
     */
    fun <T> squarify(
        items: List<Pair<T, Float>>,
        bounds: NormalizedRect
    ): List<Pair<T, NormalizedRect>> {
        if (items.isEmpty() || bounds.width <= 0f || bounds.height <= 0f) return emptyList()
        if (items.size == 1) return listOf(items.first().first to bounds)

        val cleanItems = items
            .map { it.first to max(it.second, 0.0001f) }
            .sortedByDescending { it.second }

        val totalWeight = cleanItems.sumOf { it.second.toDouble() }.toFloat()
        val totalArea = bounds.area
        val scale = if (totalWeight > 0f) totalArea / totalWeight else 1f
        val scaled = cleanItems.map { it.first to (it.second * scale) }

        val output = mutableListOf<Pair<T, NormalizedRect>>()
        var remRect = bounds
        var index = 0

        while (index < scaled.size) {
            val row = mutableListOf<Pair<T, Float>>()
            var rowSum = 0f
            val shortSide = min(remRect.width, remRect.height).coerceAtLeast(0.0001f)

            while (index < scaled.size) {
                val candidate = scaled[index]
                if (row.isEmpty()) {
                    row.add(candidate)
                    rowSum += candidate.second
                    index++
                } else {
                    val currentWorst = worstAspectRatio(row, rowSum, shortSide)
                    val nextRow = row + candidate
                    val nextWorst = worstAspectRatio(nextRow, rowSum + candidate.second, shortSide)
                    if (nextWorst <= currentWorst) {
                        row.add(candidate)
                        rowSum += candidate.second
                        index++
                    } else {
                        break
                    }
                }
            }

            // Layout current row along the shortest side of remRect
            val horizontalSplit = remRect.width >= remRect.height
            if (horizontalSplit) {
                val stripWidth = (rowSum / remRect.height.coerceAtLeast(0.0001f))
                    .coerceIn(0f, remRect.width)
                var cursorY = remRect.y
                row.forEach { (key, area) ->
                    val h = if (stripWidth > 0f) (area / stripWidth).coerceIn(0f, remRect.y + remRect.height - cursorY) else 0f
                    output.add(key to NormalizedRect(remRect.x, cursorY, stripWidth, h))
                    cursorY += h
                }
                remRect = NormalizedRect(
                    x = remRect.x + stripWidth,
                    y = remRect.y,
                    width = (remRect.width - stripWidth).coerceAtLeast(0f),
                    height = remRect.height
                )
            } else {
                val stripHeight = (rowSum / remRect.width.coerceAtLeast(0.0001f))
                    .coerceIn(0f, remRect.height)
                var cursorX = remRect.x
                row.forEach { (key, area) ->
                    val w = if (stripHeight > 0f) (area / stripHeight).coerceIn(0f, remRect.x + remRect.width - cursorX) else 0f
                    output.add(key to NormalizedRect(cursorX, remRect.y, w, stripHeight))
                    cursorX += w
                }
                remRect = NormalizedRect(
                    x = remRect.x,
                    y = remRect.y + stripHeight,
                    width = remRect.width,
                    height = (remRect.height - stripHeight).coerceAtLeast(0f)
                )
            }
        }

        return output
    }

    private fun <T> worstAspectRatio(
        row: List<Pair<T, Float>>,
        rowSum: Float,
        shortSide: Float
    ): Float {
        if (row.isEmpty() || rowSum <= 0f || shortSide <= 0f) return Float.MAX_VALUE
        val s2 = shortSide * shortSide
        val r2 = rowSum * rowSum
        var worst = 0f
        for ((_, w) in row) {
            val a = max(w, 0.00001f)
            val ratio = max((s2 * a) / r2, r2 / (s2 * a))
            if (ratio > worst) worst = ratio
        }
        return worst
    }
}
