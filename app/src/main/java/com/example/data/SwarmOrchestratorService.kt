package com.example.data

import android.util.Log
import com.example.model.AiApiKeyEntry
import com.example.model.AiSpecialty
import com.example.model.AppProject
import com.example.model.AppType
import com.example.model.ProjectFile
import com.example.model.SwarmExecutionState
import com.example.model.SwarmPhase
import com.example.model.SwarmTaskAssignment
import com.example.model.SwarmTaskStatus
import com.example.model.TargetLanguage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.generationConfig
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

class SwarmOrchestratorService(
    private val multiAiClient: MultiAiClient,
    private val vaultRepository: AiKeyVaultRepository? = null
) {
    private val builtinModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                temperature = 0.65f
            }
        )
    }

    suspend fun createDistributionPlan(
        planPrompt: String,
        appType: AppType,
        targetLanguage: TargetLanguage
    ): Result<SwarmExecutionState> {
        val trimmedPlan = planPrompt.trim()
        if (trimmedPlan.isBlank()) {
            return Result.failure(IllegalArgumentException("Veuillez décrire le plan de votre application ou jeu."))
        }

        val enabledKeys = vaultRepository?.keys?.value
            ?.filter { it.isEnabled && it.apiKey.isNotBlank() && it.effectiveEndpoint().isNotBlank() }
            .orEmpty()

        val initialAssignments = buildRosterAssignments(
            enabledKeys = enabledKeys,
            appType = appType,
            targetLanguage = targetLanguage
        )

        val rosterPromptDescription = initialAssignments.joinToString("\n") { task ->
            "- Task ID `${task.id}` | Agent: ${task.assignedWorkerLabel} | Spécialité: ${task.specialty.title} (${task.specialty.roleDescription})"
        }

        val langConstraint = if (targetLanguage == TargetLanguage.AUTO) {
            if (appType == AppType.GAME) "Choisis le meilleur langage jeu (C# Unity, GDScript Godot, Kotlin, HTML5 Canvas...)"
            else "Choisis le meilleur langage Android (Kotlin Jetpack Compose, Java, Flutter...)"
        } else {
            "Langage imposé : ${targetLanguage.displayName} (${targetLanguage.engineLabel})"
        }

        val plannerPrompt = """
            Tu es le Chef d'Orchestre d'une équipe multi-IA de développement Android & Jeux.
            Voici le PLAN fourni par l'utilisateur :
            "$trimmedPlan"

            Type : ${appType.label}
            $langConstraint

            Voici les spécialistes IA disponibles (chacun doit travailler UNIQUEMENT sur ce en quoi il est meilleur) :
            $rosterPromptDescription

            Répartis ce plan en tâches précises et complémentaires (une par Task ID ci-dessus) afin qu'ils travaillent EN PARALLÈLE sur des fichiers distincts et terminent ensemble un projet cohérent.
            Réponds UNIQUEMENT par un objet JSON valide :
            {
              "appName": "Nom court (max 24 chars)",
              "packageName": "com.forge.nomapp",
              "detectedLanguage": "Kotlin",
              "accentHex": "#00E5FF",
              "planSummary": "Résumé clair en français de comment le plan est réparti",
              "tasks": [
                {
                  "id": "task_...",
                  "title": "Titre concis de la tâche",
                  "description": "Instructions techniques précises adaptées à sa spécialité et au plan utilisateur",
                  "targetFiles": ["chemin/fichier1.kt", "chemin/fichier2.kt"]
                }
              ]
            }
        """.trimIndent()

        return try {
            val outcome = multiAiClient.generateStructuredJson(
                systemAndPrompt = plannerPrompt,
                builtinCall = {
                    val res = withTimeout(45_000L) {
                        builtinModel.generateContent(plannerPrompt)
                    }
                    res.text ?: throw IllegalStateException("Le planificateur IA a renvoyé une réponse vide.")
                }
            )

            val state = parsePlanResponse(
                rawJson = outcome.jsonText,
                fallbackAssignments = initialAssignments,
                planPrompt = trimmedPlan,
                appType = appType,
                targetLanguage = targetLanguage,
                plannerBadge = outcome.engineBadge
            )
            Result.success(state)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Planner AI fell back to deterministic split", e)
            val fallbackState = parsePlanResponse(
                rawJson = "{}",
                fallbackAssignments = initialAssignments,
                planPrompt = trimmedPlan,
                appType = appType,
                targetLanguage = targetLanguage,
                plannerBadge = "Orchestrateur local"
            )
            Result.success(fallbackState)
        }
    }

    suspend fun executePlanInParallel(
        initialState: SwarmExecutionState,
        onStateUpdate: (SwarmExecutionState) -> Unit
    ): Result<Pair<SwarmExecutionState, AppProject>> {
        if (initialState.tasks.isEmpty()) {
            return Result.failure(IllegalStateException("Aucune tâche à exécuter."))
        }

        val swarmStartMs = System.currentTimeMillis()
        val stateMutex = Mutex()
        var currentState = initialState.copy(
            phase = SwarmPhase.EXECUTING_PARALLEL,
            errorMessage = null,
            tasks = initialState.tasks.map { it.copy(status = SwarmTaskStatus.WORKING, durationMs = null) }
        )
        onStateUpdate(currentState)

        val keysSnapshot = vaultRepository?.keys?.value.orEmpty()

        return try {
            val completedTasks = coroutineScope {
                currentState.tasks.map { task ->
                    async(Dispatchers.IO) {
                        val workerStartMs = System.currentTimeMillis()
                        val assignedEntry = task.assignedKeyId?.let { id ->
                            keysSnapshot.firstOrNull { it.id == id && it.isEnabled && it.apiKey.isNotBlank() }
                        }
                        val primaryCandidate: AiCandidate = if (assignedEntry != null) {
                            AiCandidate.ExternalKey(assignedEntry)
                        } else {
                            AiCandidate.BuiltInFirebase
                        }

                        val workerResult = runSpecialistWorker(
                            task = task,
                            candidate = primaryCandidate,
                            planState = currentState
                        )

                        val elapsed = (System.currentTimeMillis() - workerStartMs).coerceAtLeast(1L)
                        val updatedTask = workerResult.copy(durationMs = elapsed)

                        stateMutex.withLock {
                            currentState = currentState.copy(
                                tasks = currentState.tasks.map { existing ->
                                    if (existing.id == updatedTask.id) updatedTask else existing
                                }
                            )
                            onStateUpdate(currentState)
                        }
                        updatedTask
                    }
                }.awaitAll()
            }

            // Synchronized barrier: all parallel specialists have finished
            currentState = currentState.copy(
                phase = SwarmPhase.INTEGRATING,
                tasks = completedTasks
            )
            onStateUpdate(currentState)

            val totalElapsed = (System.currentTimeMillis() - swarmStartMs).coerceAtLeast(1L)
            val unifiedProject = mergeOutputsIntoProject(
                state = currentState,
                completedTasks = completedTasks,
                totalDurationMs = totalElapsed
            )

            val doneState = currentState.copy(
                phase = SwarmPhase.DONE,
                totalDurationMs = totalElapsed
            )
            onStateUpdate(doneState)
            Result.success(doneState to unifiedProject)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Parallel swarm execution failed", e)
            val errState = currentState.copy(
                phase = SwarmPhase.ERROR,
                errorMessage = e.localizedMessage ?: "Échec de l'exécution collaborative."
            )
            onStateUpdate(errState)
            Result.failure(e)
        }
    }

    private suspend fun runSpecialistWorker(
        task: SwarmTaskAssignment,
        candidate: AiCandidate,
        planState: SwarmExecutionState
    ): SwarmTaskAssignment {
        val targetLangLabel = if (planState.targetLanguage == TargetLanguage.AUTO) {
            "Kotlin"
        } else {
            planState.targetLanguage.displayName
        }

        val workerSystemPrompt = """
            Tu es un développeur IA spécialiste en : ${task.specialty.title} (${task.specialty.roleDescription}).
            Tu travailles en parallèle avec d'autres IA sur le projet "${planState.appName}" (${planState.packageName}, $targetLangLabel).
            Concentre-toi UNIQUEMENT sur ta spécialité avec du code complet, propre et idiomatique.
            Réponds UNIQUEMENT par un objet JSON valide (sans markdown autour).
        """.trimIndent()

        val workerUserPrompt = if (task.specialty == AiSpecialty.INTERACTIVE_PREVIEW) {
            """
            PLAN GLOBAL DU PROJET :
            "${planState.planPrompt}"

            TA TÂCHE SPÉCIALISÉE (${task.specialty.title}) :
            ${task.title} — ${task.description}
            Couleur thème : ${planState.accentHex}

            Produis un JSON contenant :
            1. `playableHtml` : une page HTML5 + CSS + JS/Canvas 100% fonctionnelle, tactile mobile (viewport `width=device-width, initial-scale=1.0, user-scalable=no`), zéro CDN externe, zéro image externe, implémentant réellement l'application ou le jeu décrit dans le plan global !
            2. `files` : 1 fichier d'aperçu/contrôles si pertinent.
            3. `notes` : court résumé (1 phrase) de ta contribution.

            Format JSON exact :
            {"playableHtml":"<!DOCTYPE html>...","files":[{"path":"assets/index.html","language":"HTML","content":"..."}],"notes":"..."}
            """.trimIndent()
        } else {
            val suggestedPaths = task.targetFiles.joinToString(", ")
            """
            PLAN GLOBAL DU PROJET :
            "${planState.planPrompt}"
            App : ${planState.appName} | Package : ${planState.packageName} | Langage : $targetLangLabel | Accent : ${planState.accentHex}

            TA TÂCHE SPÉCIALISÉE (${task.specialty.title}) :
            ${task.title}
            Détails : ${task.description}
            Fichiers cibles suggérés : $suggestedPaths

            Produis 2 à 3 fichiers sources complets et authentiques pour ta partie uniquement.
            Format JSON exact :
            {"files":[{"path":"...","language":"$targetLangLabel","content":"..."}],"notes":"Court résumé en français"}
            """.trimIndent()
        }

        suspend fun invokeCandidate(target: AiCandidate): SwarmTaskAssignment {
            val rawJson = multiAiClient.generateWithSpecificCandidate(
                candidate = target,
                systemPrompt = workerSystemPrompt,
                userPrompt = workerUserPrompt,
                jsonMode = true,
                maxTokens = 6144,
                builtinCall = {
                    val res = withTimeout(60_000L) {
                        builtinModel.generateContent("$workerSystemPrompt\n\n$workerUserPrompt")
                    }
                    res.text ?: throw IllegalStateException("Réponse vide de Firebase IA")
                }
            )
            return parseWorkerJson(task, rawJson)
        }

        return try {
            invokeCandidate(candidate).copy(status = SwarmTaskStatus.COMPLETED)
        } catch (e: CancellationException) {
            throw e
        } catch (primaryErr: Exception) {
            Log.w(TAG, "Worker ${task.assignedWorkerLabel} failed on ${task.specialty}, attempting built-in recovery", primaryErr)
            if (candidate !is AiCandidate.BuiltInFirebase) {
                try {
                    return invokeCandidate(AiCandidate.BuiltInFirebase).copy(
                        status = SwarmTaskStatus.RECOVERED,
                        assignedWorkerLabel = "${task.assignedWorkerLabel} -> Firebase IA (Relais)",
                        notes = "Relais automatique réussi suite à : ${primaryErr.localizedMessage?.take(60) ?: "indisponible"}"
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (fallbackErr: Exception) {
                    Log.e(TAG, "Recovery also failed for ${task.id}", fallbackErr)
                }
            }
            task.copy(
                status = SwarmTaskStatus.FAILED,
                notes = primaryErr.localizedMessage?.take(100) ?: "Erreur d'exécution"
            )
        }
    }

    private fun parseWorkerJson(task: SwarmTaskAssignment, rawJson: String): SwarmTaskAssignment {
        val obj = JSONObject(rawJson)
        val playableHtml = obj.optString("playableHtml", "").trim().take(350000)
        val notes = obj.optString("notes", "").trim().take(220)
        val filesArr = obj.optJSONArray("files")
        val produced = mutableListOf<ProjectFile>()

        if (filesArr != null) {
            for (i in 0 until filesArr.length().coerceAtMost(5)) {
                val f = filesArr.optJSONObject(i) ?: continue
                val path = f.optString("path", "").trim()
                val lang = f.optString("language", "Kotlin").trim().ifBlank { "Kotlin" }
                val content = f.optString("content", "")
                if (path.isNotBlank() && content.isNotBlank()) {
                    produced.add(ProjectFile(path = path, language = lang, content = content))
                }
            }
        }

        if (produced.isEmpty() && playableHtml.isNotBlank()) {
            produced.add(ProjectFile(path = "app/src/main/assets/index.html", language = "HTML", content = playableHtml))
        }

        return task.copy(
            producedFiles = produced,
            producedHtml = playableHtml,
            notes = notes.ifBlank { "${produced.size} fichier(s) généré(s)" }
        )
    }

    companion object {
        private const val TAG = "SwarmOrchestrator"

        /**
         * Matches every coding specialty to the AI key/model best suited for it.
         * Ensures all active AI keys collaborate in parallel on their strongest domain.
         */
        fun buildRosterAssignments(
            enabledKeys: List<AiApiKeyEntry>,
            appType: AppType,
            targetLanguage: TargetLanguage
        ): List<SwarmTaskAssignment> {
            val specialties = AiSpecialty.entries
            val availablePool = enabledKeys.toMutableList()
            val chosenKeys = mutableMapOf<AiSpecialty, AiApiKeyEntry?>()

            // First pass: assign keys whose primary strength matches the specialty
            for (specialty in specialties) {
                val bestIdx = availablePool.indexOfFirst { it.bestSpecialty() == specialty }
                if (bestIdx >= 0) {
                    chosenKeys[specialty] = availablePool.removeAt(bestIdx)
                }
            }

            // Second pass: distribute any remaining enabled keys across unassigned specialties
            for (specialty in specialties) {
                if (!chosenKeys.containsKey(specialty) && availablePool.isNotEmpty()) {
                    chosenKeys[specialty] = availablePool.removeAt(0)
                }
            }

            // If user connected >4 keys, create extra module tasks so all active keys contribute too!
            val assignments = mutableListOf<SwarmTaskAssignment>()
            specialties.forEachIndexed { idx, specialty ->
                val assignedKey = chosenKeys[specialty]
                val workerLabel = if (assignedKey != null) {
                    "${assignedKey.displayTitle()}"
                } else {
                    "Firebase IA · gemini-3.8-flash"
                }
                assignments.add(
                    defaultAssignmentForSpecialty(
                        index = idx + 1,
                        specialty = specialty,
                        workerLabel = workerLabel,
                        assignedKeyId = assignedKey?.id,
                        appType = appType,
                        targetLanguage = targetLanguage
                    )
                )
            }

            // Extra keys beyond 4 share advanced core/UI modules in parallel
            availablePool.forEachIndexed { extraIdx, extraKey ->
                val spec = extraKey.bestSpecialty()
                assignments.add(
                    SwarmTaskAssignment(
                        id = "task_extra_${extraIdx + 1}",
                        title = "Module complémentaire (${spec.shortTitle})",
                        description = "Implémente des composants avancés et helpers optimisés dans ta spécialité (${spec.roleDescription}).",
                        specialty = spec,
                        assignedWorkerLabel = extraKey.displayTitle(),
                        assignedKeyId = extraKey.id,
                        targetFiles = listOf("app/src/main/java/com/forge/extra/Module${extraIdx + 1}.kt")
                    )
                )
            }

            return assignments
        }

        private fun defaultAssignmentForSpecialty(
            index: Int,
            specialty: AiSpecialty,
            workerLabel: String,
            assignedKeyId: String?,
            appType: AppType,
            targetLanguage: TargetLanguage
        ): SwarmTaskAssignment {
            val ext = when (targetLanguage) {
                TargetLanguage.JAVA -> "java"
                TargetLanguage.CSHARP_UNITY -> "cs"
                TargetLanguage.GDSCRIPT_GODOT -> "gd"
                TargetLanguage.CPP_UNREAL -> "cpp"
                TargetLanguage.LUA_LOVE2D -> "lua"
                TargetLanguage.DART_FLUTTER -> "dart"
                TargetLanguage.PYTHON_KIVY -> "py"
                TargetLanguage.HTML5_WEBGL -> "js"
                else -> "kt"
            }

            return when (specialty) {
                AiSpecialty.ARCHITECTURE_UI -> SwarmTaskAssignment(
                    id = "task_$index",
                    title = if (appType == AppType.GAME) "Interface HUD & Écrans de Jeu" else "Architecture & Écrans Material 3",
                    description = "Conçoit l'interface principale, la navigation et les composants visuels ergonomiques.",
                    specialty = specialty,
                    assignedWorkerLabel = workerLabel,
                    assignedKeyId = assignedKeyId,
                    targetFiles = listOf("app/src/main/java/com/forge/ui/MainScreen.$ext", "app/src/main/java/com/forge/ui/Theme.$ext")
                )
                AiSpecialty.CORE_LOGIC_ALGO -> SwarmTaskAssignment(
                    id = "task_$index",
                    title = if (appType == AppType.GAME) "Moteur de Jeu, Physique & Boucle" else "Logique Métier, État & Algorithmes",
                    description = "Développe les modèles de données, le moteur principal, la gestion d'état et les algorithmes.",
                    specialty = specialty,
                    assignedWorkerLabel = workerLabel,
                    assignedKeyId = assignedKeyId,
                    targetFiles = listOf("app/src/main/java/com/forge/core/Engine.$ext", "app/src/main/java/com/forge/viewmodel/AppViewModel.$ext")
                )
                AiSpecialty.INTERACTIVE_PREVIEW -> SwarmTaskAssignment(
                    id = "task_$index",
                    title = "Prototype Tactile HTML5 / Canvas",
                    description = "Construit le bundle interactif autonome exécutable dans l'aperçu mobile et embarqué dans l'APK.",
                    specialty = specialty,
                    assignedWorkerLabel = workerLabel,
                    assignedKeyId = assignedKeyId,
                    targetFiles = listOf("app/src/main/assets/index.html")
                )
                AiSpecialty.INTEGRATION_BUILD -> SwarmTaskAssignment(
                    id = "task_$index",
                    title = "Manifest, Build Config & Tests",
                    description = "Prépare le AndroidManifest.xml, la configuration Gradle et les tests unitaires.",
                    specialty = specialty,
                    assignedWorkerLabel = workerLabel,
                    assignedKeyId = assignedKeyId,
                    targetFiles = listOf("app/src/main/AndroidManifest.xml", "app/build.gradle.kts")
                )
            }
        }

        fun parsePlanResponse(
            rawJson: String,
            fallbackAssignments: List<SwarmTaskAssignment>,
            planPrompt: String,
            appType: AppType,
            targetLanguage: TargetLanguage,
            plannerBadge: String
        ): SwarmExecutionState {
            val obj = try {
                JSONObject(rawJson)
            } catch (_: Exception) {
                JSONObject()
            }

            val rawAppName = obj.optString("appName", "").trim()
            val appName = rawAppName.ifBlank {
                planPrompt.split(Regex("\\s+")).take(3).joinToString(" ").take(24).ifBlank { "ForgeSwarm" }
            }.take(40)
            val packageName = ProjectRepository.normalizePackageName(
                obj.optString("packageName", ""),
                appName
            )
            val detectedLanguage = obj.optString("detectedLanguage", "").trim().ifBlank {
                if (targetLanguage == TargetLanguage.AUTO) "Kotlin" else targetLanguage.displayName
            }
            val accentRaw = obj.optString("accentHex", "#00E5FF").trim()
            val accentHex = if (accentRaw.matches(Regex("^#[0-9A-Fa-f]{6}$"))) accentRaw else "#00E5FF"
            val planSummary = obj.optString("planSummary", "").trim().ifBlank {
                "Plan réparti entre ${fallbackAssignments.size} spécialistes IA travaillant en parallèle."
            }

            val aiTasksArr = obj.optJSONArray("tasks") ?: JSONArray()
            val byId = mutableMapOf<String, JSONObject>()
            for (i in 0 until aiTasksArr.length()) {
                val t = aiTasksArr.optJSONObject(i) ?: continue
                val tid = t.optString("id", "").trim()
                if (tid.isNotBlank()) byId[tid] = t
            }

            val finalTasks = fallbackAssignments.map { base ->
                val overrideObj = byId[base.id]
                if (overrideObj != null) {
                    val filesJson = overrideObj.optJSONArray("targetFiles")
                    val files = if (filesJson != null && filesJson.length() > 0) {
                        (0 until filesJson.length()).mapNotNull { idx ->
                            filesJson.optString(idx, "").trim().ifBlank { null }
                        }
                    } else {
                        base.targetFiles
                    }
                    base.copy(
                        title = overrideObj.optString("title", base.title).trim().ifBlank { base.title },
                        description = overrideObj.optString("description", base.description).trim().ifBlank { base.description },
                        targetFiles = files
                    )
                } else {
                    base
                }
            }

            return SwarmExecutionState(
                phase = SwarmPhase.PLAN_READY,
                planPrompt = planPrompt,
                appName = appName,
                packageName = packageName,
                accentHex = accentHex,
                planSummary = "${planSummary}\nLangage : $detectedLanguage",
                plannerBadge = plannerBadge,
                tasks = finalTasks
            )
        }

        fun mergeOutputsIntoProject(
            state: SwarmExecutionState,
            completedTasks: List<SwarmTaskAssignment>,
            totalDurationMs: Long
        ): AppProject {
            val mergedByPath = linkedMapOf<String, ProjectFile>()
            completedTasks.forEach { task ->
                task.producedFiles.forEach { file ->
                    mergedByPath[file.path] = file
                }
            }

            if (mergedByPath.isEmpty()) {
                mergedByPath["app/src/main/java/com/forge/MainActivity.kt"] = ProjectFile(
                    path = "app/src/main/java/com/forge/MainActivity.kt",
                    language = "Kotlin",
                    content = "// Généré par Équipe Multi-IA ForgeAPK\npackage ${state.packageName}\n"
                )
            }

            val htmlCandidate = completedTasks
                .firstOrNull { it.producedHtml.isNotBlank() }
                ?.producedHtml
                ?: buildFallbackTeamHtml(state, completedTasks)

            val langSummary = if (state.targetLanguage == TargetLanguage.AUTO) {
                mergedByPath.values.firstOrNull { !it.language.equals("HTML", true) && !it.language.equals("XML", true) }?.language ?: "Kotlin"
            } else {
                state.targetLanguage.displayName
            }

            val contributions = completedTasks.joinToString("\n") { t ->
                val sec = ((t.durationMs ?: 0L) / 1000.0)
                "• [${t.specialty.shortTitle}] ${t.assignedWorkerLabel} : ${t.status.label} (${String.format("%.1f", sec)}s)"
            }
            val fullSummary = buildString {
                append(state.planSummary.trim())
                append("\n\nTravail collaboratif synchronisé (${String.format("%.1f", totalDurationMs / 1000.0)}s) :\n")
                append(contributions)
            }.take(2000)

            return AppProject(
                appName = state.appName.ifBlank { "ForgeSwarm" },
                packageName = state.packageName.ifBlank { "com.forge.swarmapp" },
                versionName = "1.0.0",
                versionCode = 1L,
                appType = state.appType.name,
                language = langSummary,
                prompt = state.planPrompt.take(2000),
                summary = fullSummary,
                accentHex = state.accentHex,
                playableHtml = htmlCandidate,
                files = mergedByPath.values.toList().take(12)
            )
        }

        private fun buildFallbackTeamHtml(
            state: SwarmExecutionState,
            tasks: List<SwarmTaskAssignment>
        ): String {
            val accent = state.accentHex
            val cardsHtml = tasks.joinToString("") { t ->
                "<div class='t'><b>${t.specialty.shortTitle}</b> · ${t.assignedWorkerLabel}<br/><span>${t.title}</span></div>"
            }
            return """
                <!DOCTYPE html>
                <html lang="fr">
                <head>
                <meta charset="utf-8"/>
                <meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no"/>
                <style>
                  body{margin:0;background:#090d18;color:#f1f5f9;font-family:sans-serif;padding:20px;}
                  h1{color:$accent;margin:0 0 8px 0;font-size:22px;}
                  .t{background:#111829;border:1px solid #2c3c5e;border-radius:12px;padding:12px;margin-top:10px;font-size:13px;}
                  span{color:#94a3b8;}
                </style>
                </head>
                <body>
                  <h1>${state.appName}</h1>
                  <p>${state.planPrompt}</p>
                  $cardsHtml
                </body>
                </html>
            """.trimIndent()
        }
    }
}
