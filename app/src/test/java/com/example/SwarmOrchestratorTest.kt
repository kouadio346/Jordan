package com.example

import com.example.data.SwarmOrchestratorService
import com.example.model.AiApiKeyEntry
import com.example.model.AiProvider
import com.example.model.AiSpecialty
import com.example.model.AppType
import com.example.model.ProjectFile
import com.example.model.SwarmExecutionState
import com.example.model.SwarmPhase
import com.example.model.SwarmTaskAssignment
import com.example.model.SwarmTaskStatus
import com.example.model.TargetLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwarmOrchestratorTest {

    @Test
    fun bestSpecialty_infersStrengthsAndRespectsOverride() {
        val claude = AiApiKeyEntry(provider = AiProvider.ANTHROPIC, apiKey = "sk-ant-1")
        val deepseek = AiApiKeyEntry(provider = AiProvider.DEEPSEEK, apiKey = "sk-ds-1")
        val groq = AiApiKeyEntry(provider = AiProvider.GROQ, apiKey = "gsk_1")
        val openai = AiApiKeyEntry(provider = AiProvider.OPENAI, apiKey = "sk-oai-1")
        val qwenOnOpenRouter = AiApiKeyEntry(
            provider = AiProvider.OPENROUTER,
            modelId = "qwen/qwen-2.5-coder-32b-instruct",
            apiKey = "sk-or-1"
        )
        val overridden = AiApiKeyEntry(
            provider = AiProvider.OPENAI,
            apiKey = "sk-oai-2",
            specialtyOverride = AiSpecialty.ARCHITECTURE_UI
        )

        assertEquals(AiSpecialty.ARCHITECTURE_UI, claude.bestSpecialty())
        assertEquals(AiSpecialty.CORE_LOGIC_ALGO, deepseek.bestSpecialty())
        assertEquals(AiSpecialty.INTERACTIVE_PREVIEW, groq.bestSpecialty())
        assertEquals(AiSpecialty.INTEGRATION_BUILD, openai.bestSpecialty())
        assertEquals(AiSpecialty.CORE_LOGIC_ALGO, qwenOnOpenRouter.bestSpecialty())
        assertEquals(AiSpecialty.ARCHITECTURE_UI, overridden.bestSpecialty())
    }

    @Test
    fun buildRosterAssignments_matchesEachKeyToBestSpecialtyAndHandlesOverflow() {
        val keys = listOf(
            AiApiKeyEntry(id = "k-ds", label = "DeepSeek Pro", provider = AiProvider.DEEPSEEK, apiKey = "sk-1"),
            AiApiKeyEntry(id = "k-cl", label = "Claude UI", provider = AiProvider.ANTHROPIC, apiKey = "sk-2"),
            AiApiKeyEntry(id = "k-gq", label = "Groq Fast", provider = AiProvider.GROQ, apiKey = "sk-3"),
            AiApiKeyEntry(id = "k-oa", label = "OpenAI Build", provider = AiProvider.OPENAI, apiKey = "sk-4"),
            AiApiKeyEntry(id = "k-ex", label = "Mistral Extra", provider = AiProvider.MISTRAL, apiKey = "sk-5")
        )

        val tasks = SwarmOrchestratorService.buildRosterAssignments(
            enabledKeys = keys,
            appType = AppType.GAME,
            targetLanguage = TargetLanguage.KOTLIN
        )

        assertEquals(5, tasks.size)
        val bySpec = tasks.take(4).associateBy { it.specialty }
        assertEquals("k-cl", bySpec[AiSpecialty.ARCHITECTURE_UI]?.assignedKeyId)
        assertEquals("k-ds", bySpec[AiSpecialty.CORE_LOGIC_ALGO]?.assignedKeyId)
        assertEquals("k-gq", bySpec[AiSpecialty.INTERACTIVE_PREVIEW]?.assignedKeyId)
        assertEquals("k-oa", bySpec[AiSpecialty.INTEGRATION_BUILD]?.assignedKeyId)

        val extra = tasks[4]
        assertEquals("task_extra_1", extra.id)
        assertEquals("k-ex", extra.assignedKeyId)
    }

    @Test
    fun buildRosterAssignments_fallsBackToBuiltinWhenFewerKeysConnected() {
        val onlyClaude = listOf(
            AiApiKeyEntry(id = "k-cl", label = "Claude", provider = AiProvider.ANTHROPIC, apiKey = "sk-ant")
        )

        val tasks = SwarmOrchestratorService.buildRosterAssignments(
            enabledKeys = onlyClaude,
            appType = AppType.ANDROID_APP,
            targetLanguage = TargetLanguage.AUTO
        )

        assertEquals(4, tasks.size)
        assertEquals("k-cl", tasks.first { it.specialty == AiSpecialty.ARCHITECTURE_UI }.assignedKeyId)
        val builtinCount = tasks.count { it.assignedKeyId == null && it.assignedWorkerLabel.contains("Firebase IA") }
        assertEquals(3, builtinCount)
    }

    @Test
    fun mergeOutputsIntoProject_unifiesParallelFilesDeduplicatesAndSummarizesTeam() {
        val initialState = SwarmExecutionState(
            phase = SwarmPhase.INTEGRATING,
            planPrompt = "Créer un jeu de plateforme spatial",
            appType = AppType.GAME,
            targetLanguage = TargetLanguage.KOTLIN,
            appName = "SpaceHop",
            packageName = "com.forge.spacehop",
            accentHex = "#00E5FF",
            planSummary = "Plan réparti en 4 spécialités."
        )

        val completedTasks = listOf(
            SwarmTaskAssignment(
                id = "task_1",
                title = "UI",
                description = "HUD",
                specialty = AiSpecialty.ARCHITECTURE_UI,
                assignedWorkerLabel = "Claude UI",
                assignedKeyId = "k1",
                targetFiles = listOf("app/src/main/java/com/forge/ui/MainScreen.kt"),
                status = SwarmTaskStatus.COMPLETED,
                durationMs = 1200L,
                producedFiles = listOf(
                    ProjectFile("app/src/main/java/com/forge/ui/MainScreen.kt", "Kotlin", "@Composable fun Main() {}")
                )
            ),
            SwarmTaskAssignment(
                id = "task_2",
                title = "Physics",
                description = "Moteur",
                specialty = AiSpecialty.CORE_LOGIC_ALGO,
                assignedWorkerLabel = "DeepSeek Pro",
                assignedKeyId = "k2",
                targetFiles = listOf("app/src/main/java/com/forge/core/Engine.kt"),
                status = SwarmTaskStatus.COMPLETED,
                durationMs = 1500L,
                producedFiles = listOf(
                    ProjectFile("app/src/main/java/com/forge/core/Engine.kt", "Kotlin", "class Engine {}"),
                    ProjectFile("app/src/main/java/com/forge/ui/MainScreen.kt", "Kotlin", "@Composable fun MainUpdated() {}")
                )
            ),
            SwarmTaskAssignment(
                id = "task_3",
                title = "Preview",
                description = "HTML5",
                specialty = AiSpecialty.INTERACTIVE_PREVIEW,
                assignedWorkerLabel = "Groq Fast",
                assignedKeyId = "k3",
                targetFiles = listOf("app/src/main/assets/index.html"),
                status = SwarmTaskStatus.RECOVERED,
                durationMs = 900L,
                producedHtml = "<!DOCTYPE html><html><body>Playable SpaceHop</body></html>",
                producedFiles = listOf(
                    ProjectFile("app/src/main/assets/index.html", "HTML", "<!DOCTYPE html><html><body>Playable SpaceHop</body></html>")
                )
            )
        )

        val project = SwarmOrchestratorService.mergeOutputsIntoProject(
            state = initialState,
            completedTasks = completedTasks,
            totalDurationMs = 1600L
        )

        assertEquals("SpaceHop", project.appName)
        assertEquals("com.forge.spacehop", project.packageName)
        assertEquals(3, project.files.size)
        assertTrue(project.files.first { it.path.endsWith("MainScreen.kt") }.content.contains("MainUpdated"))
        assertTrue(project.playableHtml.contains("Playable SpaceHop"))
        assertTrue(project.summary.contains("Claude UI"))
        assertTrue(project.summary.contains("DeepSeek Pro"))
        assertTrue(project.summary.contains("Groq Fast"))
    }

    @Test
    fun swarmExecutionState_tracksCompletionAndProgress() {
        val tasks = listOf(
            SwarmTaskAssignment("t1", "A", "d", AiSpecialty.ARCHITECTURE_UI, "Claude", "k1", emptyList(), SwarmTaskStatus.COMPLETED),
            SwarmTaskAssignment("t2", "B", "d", AiSpecialty.CORE_LOGIC_ALGO, "DeepSeek", "k2", emptyList(), SwarmTaskStatus.WORKING),
            SwarmTaskAssignment("t3", "C", "d", AiSpecialty.INTERACTIVE_PREVIEW, "Groq", "k3", emptyList(), SwarmTaskStatus.RECOVERED),
            SwarmTaskAssignment("t4", "D", "d", AiSpecialty.INTEGRATION_BUILD, "OpenAI", "k4", emptyList(), SwarmTaskStatus.WAITING)
        )
        val running = SwarmExecutionState(phase = SwarmPhase.EXECUTING_PARALLEL, tasks = tasks)
        assertEquals(2, running.completedCount)
        assertEquals(0.55f, running.progressFraction, 0.01f)

        val done = running.copy(phase = SwarmPhase.DONE)
        assertEquals(1f, done.progressFraction, 0.001f)
    }
}
