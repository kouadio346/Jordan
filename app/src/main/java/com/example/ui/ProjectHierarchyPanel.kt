package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LanguageSlice
import com.example.model.ProjectFile
import com.example.model.ProjectFileStats
import com.example.model.ProjectHierarchyBuilder
import com.example.model.ProjectTreeNode
import com.example.model.TreemapGroupRect
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldBuild
import com.example.ui.theme.ForgeAmber
import com.example.ui.theme.ForgeBorder
import com.example.ui.theme.ForgeSurface
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SlateMuted
import java.util.Locale

enum class HierarchyVisualMode(val label: String) {
    TREE("Arborescence"),
    TREEMAP("TreeMap")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectFileHierarchyExplorer(
    projectName: String,
    files: List<ProjectFile>,
    pendingFilePaths: Set<String> = emptySet(),
    selectedFilePath: String? = null,
    initialMode: HierarchyVisualMode = HierarchyVisualMode.TREE,
    openFileActionLabel: String = "Ouvrir dans l'éditeur",
    treemapHeight: Dp = 300.dp,
    onSelectFile: (ProjectFileStats) -> Unit,
    modifier: Modifier = Modifier
) {
    var visualMode by remember(initialMode) { mutableStateOf(initialMode) }

    val statsList = remember(files, pendingFilePaths) {
        ProjectHierarchyBuilder.analyzeFiles(files, pendingFilePaths)
    }
    val rootTree = remember(projectName, files, pendingFilePaths) {
        ProjectHierarchyBuilder.buildTree(
            rootName = projectName.ifBlank { "Projet" },
            files = files,
            pendingPaths = pendingFilePaths,
            compactMiddleDirs = true
        )
    }
    val allDirPaths = remember(rootTree) {
        ProjectHierarchyBuilder.collectAllDirectoryPaths(rootTree)
    }
    var expandedDirs by remember(rootTree) {
        mutableStateOf(allDirPaths)
    }

    val treemapGroups = remember(files, pendingFilePaths) {
        ProjectHierarchyBuilder.computeHierarchicalTreemap(
            files = files,
            pendingPaths = pendingFilePaths
        )
    }
    val languageSlices = remember(statsList) {
        ProjectHierarchyBuilder.buildLanguageBreakdown(statsList)
    }

    var inspectedPath by remember(selectedFilePath, statsList) {
        mutableStateOf(
            selectedFilePath?.let { ProjectHierarchyBuilder.normalizePath(it) }
                ?: statsList.firstOrNull()?.normalizedPath
        )
    }
    val inspectedStats = remember(inspectedPath, statsList) {
        statsList.firstOrNull { it.normalizedPath.equals(inspectedPath, ignoreCase = true) }
            ?: statsList.firstOrNull()
    }

    val pendingCount = statsList.count { it.isPendingGeneration }
    val generatedCount = statsList.size - pendingCount
    val totalLines = statsList.sumOf { it.lineCount }
    val totalBytes = statsList.sumOf { it.byteCount }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Overview Metrics + Mode Switcher Card
        Surface(
            color = ForgeSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ForgeBorder, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Architecture des fichiers",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val metricsText = buildString {
                            append("$generatedCount fichier(s)")
                            if (pendingCount > 0) append(" · +$pendingCount en cours")
                            append(" · $totalLines lignes · ${formatBytesCompact(totalBytes)}")
                        }
                        Text(
                            text = metricsText,
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }

                    if (visualMode == HierarchyVisualMode.TREE && allDirPaths.isNotEmpty()) {
                        val allExpanded = expandedDirs.containsAll(allDirPaths)
                        IconButton(
                            onClick = {
                                expandedDirs = if (allExpanded) emptySet() else allDirPaths
                            },
                            modifier = Modifier.testTag("hierarchy_expand_collapse_all")
                        ) {
                            Icon(
                                imageVector = if (allExpanded) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                                contentDescription = if (allExpanded) "Tout replier" else "Tout déplier",
                                tint = ElectricCyan
                            )
                        }
                    }
                }

                // Mode Switcher: Arborescence vs TreeMap
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MidnightSlate)
                        .border(1.dp, ForgeBorder, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HierarchyModeButton(
                        label = HierarchyVisualMode.TREE.label,
                        icon = Icons.Filled.AccountTree,
                        selected = visualMode == HierarchyVisualMode.TREE,
                        onClick = { visualMode = HierarchyVisualMode.TREE },
                        testTag = "hierarchy_mode_tree",
                        modifier = Modifier.weight(1f)
                    )
                    HierarchyModeButton(
                        label = HierarchyVisualMode.TREEMAP.label,
                        icon = Icons.Filled.GridView,
                        selected = visualMode == HierarchyVisualMode.TREEMAP,
                        onClick = { visualMode = HierarchyVisualMode.TREEMAP },
                        testTag = "hierarchy_mode_treemap",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Multi-language proportional breakdown bar
                if (languageSlices.isNotEmpty()) {
                    LanguageBreakdownStrip(slices = languageSlices)
                }
            }
        }

        if (statsList.isEmpty()) {
            Surface(
                color = MidnightSlate,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ForgeBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountTree,
                        contentDescription = null,
                        tint = SlateMuted,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "Aucun fichier source généré pour le moment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateMuted
                    )
                }
            }
        } else {
            when (visualMode) {
                HierarchyVisualMode.TREE -> {
                    VisualTreeHierarchyCard(
                        root = rootTree,
                        expandedDirs = expandedDirs,
                        selectedFilePath = inspectedPath,
                        onToggleDir = { dirPath ->
                            expandedDirs = if (dirPath in expandedDirs) {
                                expandedDirs - dirPath
                            } else {
                                expandedDirs + dirPath
                            }
                        },
                        onSelectFile = { stats ->
                            inspectedPath = stats.normalizedPath
                            onSelectFile(stats)
                        }
                    )
                }
                HierarchyVisualMode.TREEMAP -> {
                    InteractiveTreemapCard(
                        groups = treemapGroups,
                        selectedFilePath = inspectedPath,
                        treemapHeight = treemapHeight,
                        openFileActionLabel = openFileActionLabel,
                        inspectedStats = inspectedStats,
                        totalBytes = totalBytes,
                        onTapTile = { stats ->
                            inspectedPath = stats.normalizedPath
                        },
                        onOpenFile = onSelectFile
                    )
                }
            }
        }
    }
}

@Composable
private fun HierarchyModeButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) ElectricCyan.copy(alpha = 0.18f) else Color.Transparent,
        shape = RoundedCornerShape(9.dp),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(9.dp))
            .then(
                if (selected) Modifier.border(1.dp, ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(9.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) ElectricCyan else SlateMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) ElectricCyan else SlateMuted,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageBreakdownStrip(slices: List<LanguageSlice>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(ObsidianNight)
        ) {
            var cursorX = 0f
            val totalW = size.width
            val h = size.height
            slices.forEachIndexed { idx, slice ->
                val sliceW = if (idx == slices.lastIndex) {
                    (totalW - cursorX).coerceAtLeast(0f)
                } else {
                    (totalW * slice.fraction).coerceAtLeast(if (slice.fraction > 0f) 4f else 0f)
                }
                if (sliceW > 0f) {
                    drawRoundRect(
                        color = parseHexColor(slice.accentHex, ElectricCyan),
                        topLeft = Offset(cursorX, 0f),
                        size = Size(sliceW, h),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    cursorX += sliceW + 2f
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            slices.forEach { slice ->
                val dotColor = parseHexColor(slice.accentHex, ElectricCyan)
                val pct = (slice.fraction * 100f).toInt().coerceAtLeast(1)
                Surface(
                    color = MidnightSlate,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.border(1.dp, dotColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${slice.language} · $pct% (${slice.fileCount})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualTreeHierarchyCard(
    root: ProjectTreeNode.Directory,
    expandedDirs: Set<String>,
    selectedFilePath: String?,
    onToggleDir: (String) -> Unit,
    onSelectFile: (ProjectFileStats) -> Unit
) {
    Surface(
        color = MidnightSlate,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ForgeBorder, RoundedCornerShape(16.dp))
            .testTag("visual_tree_container")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Root Project Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = root.name,
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricCyan,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${root.totalFiles} fichiers",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateMuted
                )
            }

            root.children.forEach { child ->
                TreeNodeBranch(
                    node = child,
                    depth = 1,
                    expandedDirs = expandedDirs,
                    selectedFilePath = selectedFilePath,
                    onToggleDir = onToggleDir,
                    onSelectFile = onSelectFile
                )
            }
        }
    }
}

@Composable
private fun TreeNodeBranch(
    node: ProjectTreeNode,
    depth: Int,
    expandedDirs: Set<String>,
    selectedFilePath: String?,
    onToggleDir: (String) -> Unit,
    onSelectFile: (ProjectFileStats) -> Unit
) {
    val guideColor = ForgeBorder
    when (node) {
        is ProjectTreeNode.Directory -> {
            val isExpanded = node.fullPath in expandedDirs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onToggleDir(node.fullPath) }
                    .drawBehind {
                        drawIndentGuides(depth = depth, lineColor = guideColor)
                    }
                    .padding(
                        start = (depth * 14).dp,
                        end = 8.dp,
                        top = 8.dp,
                        bottom = 8.dp
                    )
                    .testTag("tree_dir_${node.fullPath}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Replier" else "Déplier",
                    tint = SlateMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                    contentDescription = null,
                    tint = ForgeAmber,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = node.name,
                    fontFamily = MonoFontFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = ForgeSurface,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${node.totalFiles}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateMuted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    node.children.forEach { child ->
                        TreeNodeBranch(
                            node = child,
                            depth = depth + 1,
                            expandedDirs = expandedDirs,
                            selectedFilePath = selectedFilePath,
                            onToggleDir = onToggleDir,
                            onSelectFile = onSelectFile
                        )
                    }
                }
            }
        }

        is ProjectTreeNode.FileLeaf -> {
            val stats = node.stats
            val isSelected = stats.normalizedPath.equals(selectedFilePath, ignoreCase = true)
            val accent = parseHexColor(stats.accentHex, ElectricCyan)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) accent.copy(alpha = 0.14f) else Color.Transparent
                    )
                    .then(
                        if (isSelected) Modifier.border(1.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                        else Modifier
                    )
                    .clickable { onSelectFile(stats) }
                    .drawBehind {
                        drawIndentGuides(depth = depth, lineColor = guideColor)
                    }
                    .padding(
                        start = (depth * 14 + 6).dp,
                        end = 8.dp,
                        top = 8.dp,
                        bottom = 8.dp
                    )
                    .testTag("tree_file_${stats.normalizedPath}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (stats.isPendingGeneration) ForgeAmber else accent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = if (isSelected) accent else SlateMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stats.fileName,
                    fontFamily = MonoFontFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color(0xFFCBD5E1),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = if (stats.isPendingGeneration) {
                        ForgeAmber.copy(alpha = 0.18f)
                    } else {
                        accent.copy(alpha = 0.14f)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (stats.isPendingGeneration) "En cours" else "${stats.lineCount} L",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (stats.isPendingGeneration) ForgeAmber else accent,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIndentGuides(
    depth: Int,
    lineColor: Color
) {
    val stepPx = 14.dp.toPx()
    for (level in 1 until depth) {
        val x = (level * stepPx) - (stepPx * 0.45f)
        drawLine(
            color = lineColor.copy(alpha = 0.7f),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1.dp.toPx()
        )
    }
    if (depth > 0) {
        val branchX = (depth * stepPx) - (stepPx * 0.45f)
        val midY = size.height / 2f
        drawLine(
            color = lineColor.copy(alpha = 0.7f),
            start = Offset(branchX, 0f),
            end = Offset(branchX, size.height),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = lineColor.copy(alpha = 0.7f),
            start = Offset(branchX, midY),
            end = Offset(branchX + 6.dp.toPx(), midY),
            strokeWidth = 1.dp.toPx()
        )
    }
}

@Composable
private fun InteractiveTreemapCard(
    groups: List<TreemapGroupRect>,
    selectedFilePath: String?,
    treemapHeight: Dp,
    openFileActionLabel: String,
    inspectedStats: ProjectFileStats?,
    totalBytes: Int,
    onTapTile: (ProjectFileStats) -> Unit,
    onOpenFile: (ProjectFileStats) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            color = ObsidianNight,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ForgeBorder, RoundedCornerShape(16.dp))
                .testTag("visual_treemap_container")
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(treemapHeight)
                    .padding(6.dp)
            ) {
                val containerW = maxWidth
                val containerH = maxHeight
                val groupHeaderHeight = 18.dp

                groups.forEach { group ->
                    val gx = containerW * group.rect.x
                    val gy = containerH * group.rect.y
                    val gw = (containerW * group.rect.width).coerceAtLeast(0.dp)
                    val gh = (containerH * group.rect.height).coerceAtLeast(0.dp)
                    val groupColor = parseHexColor(group.accentHex, ElectricCyan)

                    if (gw > 14.dp && gh > 14.dp) {
                        Box(
                            modifier = Modifier
                                .offset(x = gx, y = gy)
                                .size(width = gw, height = gh)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MidnightSlate.copy(alpha = 0.85f))
                                .border(1.dp, groupColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        ) {
                            val showGroupHeader = gh >= 46.dp && gw >= 64.dp
                            val headerReserve = if (showGroupHeader) groupHeaderHeight else 0.dp
                            val innerW = (gw - 4.dp).coerceAtLeast(0.dp)
                            val innerH = (gh - 4.dp - headerReserve).coerceAtLeast(0.dp)

                            if (showGroupHeader) {
                                Text(
                                    text = group.groupName,
                                    fontFamily = MonoFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = groupColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .offset(x = 0.dp, y = headerReserve)
                                    .size(width = innerW, height = innerH)
                            ) {
                                group.tiles.forEach { tile ->
                                    val tx = innerW * tile.rect.x
                                    val ty = innerH * tile.rect.y
                                    val tw = (innerW * tile.rect.width).coerceAtLeast(0.dp)
                                    val th = (innerH * tile.rect.height).coerceAtLeast(0.dp)
                                    val tileColor = if (tile.stats.isPendingGeneration) {
                                        ForgeAmber
                                    } else {
                                        parseHexColor(tile.stats.accentHex, ElectricCyan)
                                    }
                                    val isSelected = tile.stats.normalizedPath.equals(selectedFilePath, ignoreCase = true)

                                    if (tw > 4.dp && th > 4.dp) {
                                        Box(
                                            modifier = Modifier
                                                .offset(x = tx, y = ty)
                                                .size(width = tw, height = th)
                                                .padding(1.5.dp)
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        colors = listOf(
                                                            tileColor.copy(alpha = if (isSelected) 0.36f else 0.20f),
                                                            ForgeSurface.copy(alpha = 0.92f)
                                                        )
                                                    )
                                                )
                                                .border(
                                                    width = if (isSelected) 1.5.dp else 0.8.dp,
                                                    color = if (isSelected) ElectricCyan else tileColor.copy(alpha = 0.65f),
                                                    shape = RoundedCornerShape(7.dp)
                                                )
                                                .clickable { onTapTile(tile.stats) }
                                                .padding(4.dp)
                                                .testTag("treemap_tile_${tile.stats.normalizedPath}")
                                        ) {
                                            if (tw >= 46.dp && th >= 24.dp) {
                                                Column(
                                                    verticalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    Text(
                                                        text = tile.stats.fileName,
                                                        fontFamily = MonoFontFamily,
                                                        fontSize = 10.sp,
                                                        lineHeight = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = if (th >= 52.dp) 2 else 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (th >= 42.dp && tw >= 58.dp) {
                                                        val badgeText = if (tile.stats.isPendingGeneration) {
                                                            "En cours"
                                                        } else {
                                                            "${tile.stats.lineCount}L · ${tile.percentage.toInt().coerceAtLeast(1)}%"
                                                        }
                                                        Text(
                                                            text = badgeText,
                                                            fontSize = 9.sp,
                                                            color = tileColor,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selected Tile Inspector Card
        if (inspectedStats != null) {
            val accent = if (inspectedStats.isPendingGeneration) {
                ForgeAmber
            } else {
                parseHexColor(inspectedStats.accentHex, ElectricCyan)
            }
            val pct = if (totalBytes > 0 && !inspectedStats.isPendingGeneration) {
                String.format(Locale.US, "%.0f%%", (inspectedStats.byteCount * 100f) / totalBytes)
            } else if (inspectedStats.isPendingGeneration) {
                "Planifié"
            } else {
                "—"
            }

            Surface(
                color = ForgeSurface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    .testTag("treemap_inspector_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = inspectedStats.normalizedPath,
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val detailLine = if (inspectedStats.isPendingGeneration) {
                            "${inspectedStats.languageLabel} · Génération en cours par l'IA"
                        } else {
                            "${inspectedStats.languageLabel} · ${inspectedStats.lineCount} lignes · ${formatBytesCompact(inspectedStats.byteCount)} · $pct du projet"
                        }
                        Text(
                            text = detailLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent
                        )
                    }
                    if (!inspectedStats.isPendingGeneration) {
                        Button(
                            onClick = { onOpenFile(inspectedStats) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = ObsidianNight
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("treemap_open_selected_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Code,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = openFileActionLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Surface(
                            color = ForgeAmber.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "En attente",
                                style = MaterialTheme.typography.labelSmall,
                                color = ForgeAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytesCompact(bytes: Int): String {
    return when {
        bytes <= 0 -> "0 o"
        bytes < 1024 -> "$bytes o"
        else -> String.format(Locale.US, "%.1f Ko", bytes / 1024.0)
    }
}
