package com.example

import com.example.model.NormalizedRect
import com.example.model.ProjectFile
import com.example.model.ProjectHierarchyBuilder
import com.example.model.ProjectTreeNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectHierarchyBuilderTest {

    @Test
    fun analyzeFiles_normalizesPathsAndMarksPendingOnlyWhenNotGenerated() {
        val generated = listOf(
            ProjectFile(
                path = "/app\\src\\main\\java\\com\\forge\\ui\\MainScreen.kt",
                language = "Kotlin",
                content = "package com.forge.ui\n@Composable fun MainScreen() {}"
            )
        )
        val pending = setOf(
            "app/src/main/java/com/forge/ui/MainScreen.kt",
            "app/src/main/java/com/forge/core/Engine.kt"
        )

        val stats = ProjectHierarchyBuilder.analyzeFiles(generated, pending)

        assertEquals(2, stats.size)
        val mainStats = stats.first { it.fileName == "MainScreen.kt" }
        val engineStats = stats.first { it.fileName == "Engine.kt" }

        assertEquals("app/src/main/java/com/forge/ui/MainScreen.kt", mainStats.normalizedPath)
        assertFalse(mainStats.isPendingGeneration)
        assertEquals(2, mainStats.lineCount)

        assertTrue(engineStats.isPendingGeneration)
        assertEquals(0, engineStats.lineCount)
        assertEquals("Kotlin", engineStats.languageLabel)
    }

    @Test
    fun buildTree_compactsSingleChildDirectoriesAndAggregatesCounts() {
        val files = listOf(
            ProjectFile("app/src/main/java/com/forge/ui/MainScreen.kt", "Kotlin", "line1\nline2\nline3"),
            ProjectFile("app/src/main/java/com/forge/core/Engine.kt", "Kotlin", "line1\nline2"),
            ProjectFile("app/src/main/assets/index.html", "HTML", "<html></html>"),
            ProjectFile("build.gradle.kts", "Kotlin", "plugins {}")
        )

        val tree = ProjectHierarchyBuilder.buildTree(
            rootName = "ForgeApp",
            files = files,
            compactMiddleDirs = true
        )

        assertEquals("ForgeApp", tree.name)
        assertEquals(4, tree.totalFiles)
        assertEquals(7, tree.totalLines)

        val appMain = tree.children.filterIsInstance<ProjectTreeNode.Directory>().first()
        assertEquals("app/src/main", appMain.name)
        assertEquals(3, appMain.totalFiles)

        val javaBranch = appMain.children.filterIsInstance<ProjectTreeNode.Directory>()
            .first { it.name.startsWith("java") }
        assertEquals("java/com/forge", javaBranch.name)
        assertEquals(2, javaBranch.children.size)

        val rootLeaf = tree.children.filterIsInstance<ProjectTreeNode.FileLeaf>().single()
        assertEquals("build.gradle.kts", rootLeaf.name)
    }

    @Test
    fun squarify_partitionsAreaCompletelyAndProportionally() {
        val items = listOf(
            "big" to 60f,
            "medium" to 30f,
            "small" to 10f
        )

        val rects = ProjectHierarchyBuilder.squarify(items, NormalizedRect(0f, 0f, 1f, 1f))
        assertEquals(3, rects.size)

        val byKey = rects.toMap()
        val totalArea = rects.sumOf { it.second.area.toDouble() }.toFloat()
        assertEquals(1.0f, totalArea, 0.002f)

        val bigArea = byKey.getValue("big").area
        val mediumArea = byKey.getValue("medium").area
        val smallArea = byKey.getValue("small").area
        assertTrue(bigArea > mediumArea)
        assertTrue(mediumArea > smallArea)

        rects.forEach { (_, r) ->
            assertTrue(r.width > 0f && r.height > 0f)
            assertTrue(r.x >= -0.0001f && r.y >= -0.0001f)
            assertTrue(r.x + r.width <= 1.001f && r.y + r.height <= 1.001f)
        }
    }

    @Test
    fun computeHierarchicalTreemap_createsGroupedFolderClustersAndLanguageSlices() {
        val files = listOf(
            ProjectFile(
                "app/src/main/java/com/forge/ui/MainScreen.kt",
                "Kotlin",
                "a\n".repeat(40)
            ),
            ProjectFile(
                "app/src/main/java/com/forge/ui/Theme.kt",
                "Kotlin",
                "b\n".repeat(15)
            ),
            ProjectFile(
                "app/src/main/assets/index.html",
                "HTML5",
                "<div/>".repeat(30)
            )
        )

        val groups = ProjectHierarchyBuilder.computeHierarchicalTreemap(files)
        assertEquals(2, groups.size)

        val groupAreaSum = groups.sumOf { it.rect.area.toDouble() }.toFloat()
        assertEquals(1.0f, groupAreaSum, 0.002f)

        val uiGroup = groups.first { it.groupName == "forge.ui" }
        assertEquals(2, uiGroup.tiles.size)

        val stats = ProjectHierarchyBuilder.analyzeFiles(files)
        val breakdown = ProjectHierarchyBuilder.buildLanguageBreakdown(stats)
        assertEquals(2, breakdown.size)
        assertEquals(1.0f, breakdown.sumOf { it.fraction.toDouble() }.toFloat(), 0.002f)
    }
}
