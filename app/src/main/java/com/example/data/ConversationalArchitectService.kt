package com.example.data

import android.util.Log
import com.example.model.AppProject
import com.example.model.ChatMessage
import com.example.model.CodeSuggestion
import com.example.model.ParsedAiTurn
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

sealed interface StreamChunk {
    data class EngineActive(val badge: String, val failoverUsed: Boolean) : StreamChunk
    data class Partial(val accumulatedText: String) : StreamChunk
    data class Completed(val parsed: ParsedAiTurn) : StreamChunk
}

class ConversationalArchitectService(
    private val multiAiClient: MultiAiClient = MultiAiClient()
) {

    private val systemPromptText = """
        You are ForgeAPK Architect, an expert conversational Android & Game AI pair programmer.
        You specialize in Jetpack Compose, Kotlin, Java, Room, Navigation, MVVM, plus game languages (C# Unity, GDScript Godot, C++, Lua, Dart/Flutter, HTML5/Canvas).
        
        Behavior rules:
        1. Reply in the same language the user writes in (French or English).
        2. Give clear, concise, iterative architectural and implementation suggestions tailored to the user's app idea or current project.
        3. Whenever you propose code, ALWAYS place each file inside a fenced markdown block with a file path comment on the very first line inside the block, like:
           ```kotlin
           // file: app/src/main/java/com/app/ui/TaskScreen.kt
           // title: Écran Liste des Tâches
           ...code...
           ```
           (For XML/HTML use `<!-- file: ... -->`, for Python/GDScript use `# file: ...`).
        4. Keep code snippets clean, idiomatic, Material 3 compliant, and directly usable in an Android project.
        5. At the very end of every response, ALWAYS add a single line with 3 concise iterative follow-up suggestions the user can click next:
           [FOLLOWUPS: "Ajouter la persistance" | "Améliorer le design M3" | "Ajouter des tests unitaires"]
    """.trimIndent()

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content { text(systemPromptText) }
        )
    }

    fun sendMessageStream(
        historyMessages: List<ChatMessage>,
        userInput: String,
        activeProject: AppProject?
    ): Flow<StreamChunk> = flow {
        val historyTurns = mutableListOf<Pair<String, String>>()
        val firebaseHistory = mutableListOf<Content>()

        if (activeProject != null) {
            val filesSummary = activeProject.files.joinToString("\n") {
                "- ${it.path} (${it.language}, ${it.content.lines().size} lignes)"
            }
            val projectContextText = "Contexte du projet Android actif :\n" +
                "Nom: ${activeProject.appName}\n" +
                "Package: ${activeProject.packageName}\n" +
                "Langage principal: ${activeProject.language}\n" +
                "Résumé: ${activeProject.summary}\n" +
                "Fichiers actuels:\n$filesSummary"
            val ackText = "Compris ! Je connais l'architecture de ${activeProject.appName}. Que souhaitez-vous améliorer, ajouter ou générer ensuite ?"

            historyTurns.add("user" to projectContextText)
            historyTurns.add("model" to ackText)
            firebaseHistory.add(content("user") { text(projectContextText) })
            firebaseHistory.add(content("model") { text(ackText) })
        }

        // Include recent conversation turns for multi-turn continuity
        for (msg in historyMessages.takeLast(14)) {
            val role = if (msg.role == "MODEL") "model" else "user"
            val fullMsgText = buildString {
                append(msg.text)
                if (msg.suggestions.isNotEmpty()) {
                    msg.suggestions.forEach { s ->
                        append("\n```${s.language.lowercase()}\n// file: ${s.filePath}\n${s.code}\n```")
                    }
                }
            }
            historyTurns.add(role to fullMsgText)
            firebaseHistory.add(content(role) { text(fullMsgText) })
        }

        var latestFull = ""
        try {
            multiAiClient.streamChat(
                systemPrompt = systemPromptText,
                historyTurns = historyTurns,
                userInput = userInput,
                builtinStream = {
                    flow {
                        val chat = model.startChat(history = firebaseHistory)
                        val acc = StringBuilder()
                        withTimeout(60_000L) {
                            chat.sendMessageStream(userInput).collect { chunk ->
                                val chunkText = chunk.text.orEmpty()
                                if (chunkText.isNotEmpty()) {
                                    acc.append(chunkText)
                                    emit(acc.toString())
                                }
                            }
                        }
                    }
                }
            ).collect { event ->
                when (event) {
                    is MultiStreamEvent.EngineSelected -> {
                        emit(StreamChunk.EngineActive(event.badge, event.failoverUsed))
                    }
                    is MultiStreamEvent.Chunk -> {
                        latestFull = event.accumulatedText
                        emit(StreamChunk.Partial(stripFollowupsLine(latestFull)))
                    }
                }
            }

            if (latestFull.isBlank()) {
                throw IllegalStateException("L'assistant IA a renvoyé une réponse vide.")
            }
            emit(StreamChunk.Completed(parseAiResponse(latestFull)))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Chat stream timed out", e)
            throw IllegalStateException("La réponse IA a expiré après 60 secondes. Réessayez avec une question plus ciblée.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Chat stream failed", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "ConvArchitectSvc"

        private val CODE_BLOCK_REGEX = Regex("```([a-zA-Z0-9+#_-]*)\\s*\\n([\\s\\S]*?)```")
        private val FOLLOWUPS_REGEX = Regex("\\[FOLLOWUPS:\\s*(.*?)\\]", RegexOption.IGNORE_CASE)

        fun stripFollowupsLine(raw: String): String {
            return raw.replace(FOLLOWUPS_REGEX, "").trim()
        }

        fun parseAiResponse(raw: String): ParsedAiTurn {
            val followUps = mutableListOf<String>()
            val followMatch = FOLLOWUPS_REGEX.find(raw)
            if (followMatch != null) {
                val inner = followMatch.groupValues[1]
                inner.split("|")
                    .map { it.trim().trim('"', '\'', ' ') }
                    .filter { it.isNotBlank() }
                    .take(5)
                    .forEach { followUps.add(it) }
            }

            val withoutFollowups = stripFollowupsLine(raw)
            val suggestions = mutableListOf<CodeSuggestion>()

            val proseBuilder = StringBuilder()
            var lastIndex = 0
            CODE_BLOCK_REGEX.findAll(withoutFollowups).forEachIndexed { idx, match ->
                proseBuilder.append(withoutFollowups.substring(lastIndex, match.range.first))
                lastIndex = match.range.last + 1

                val rawLang = match.groupValues[1].ifBlank { "kotlin" }
                val rawBody = match.groupValues[2].trimEnd()
                val lines = rawBody.lines().toMutableList()

                var filePath = ""
                var title = ""

                // Inspect first 2 lines for // file: or // title: metadata comments
                val consumed = mutableListOf<Int>()
                for (i in 0 until minOf(2, lines.size)) {
                    val line = lines[i].trim()
                    val fileMarker = extractMarkerValue(line, "file:")
                    if (fileMarker != null) {
                        filePath = fileMarker
                        consumed.add(i)
                        continue
                    }
                    val titleMarker = extractMarkerValue(line, "title:")
                    if (titleMarker != null) {
                        title = titleMarker
                        consumed.add(i)
                    }
                }
                val cleanCode = lines.filterIndexed { index, _ -> index !in consumed }.joinToString("\n").trim()

                val resolvedExt = when (rawLang.lowercase()) {
                    "kotlin", "kt" -> "kt"
                    "java" -> "java"
                    "xml" -> "xml"
                    "csharp", "cs", "c#" -> "cs"
                    "gdscript", "gd" -> "gd"
                    "cpp", "c++" -> "cpp"
                    "lua" -> "lua"
                    "dart" -> "dart"
                    "python", "py" -> "py"
                    "html", "html5" -> "html"
                    "javascript", "js" -> "js"
                    else -> "kt"
                }

                val resolvedPath = filePath.ifBlank { "app/src/main/java/com/app/Generated${idx + 1}.$resolvedExt" }
                val resolvedTitle = title.ifBlank { resolvedPath.substringAfterLast('/') }
                val displayLang = rawLang.replaceFirstChar { it.uppercase() }

                if (cleanCode.isNotBlank()) {
                    suggestions.add(
                        CodeSuggestion(
                            title = resolvedTitle,
                            filePath = resolvedPath,
                            language = displayLang,
                            code = cleanCode
                        )
                    )
                    proseBuilder.append("\n[Suggestion #${suggestions.size} : $resolvedTitle]\n")
                }
            }
            proseBuilder.append(withoutFollowups.substring(lastIndex))

            val cleanProse = proseBuilder.toString()
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
                .ifBlank { "Voici mes suggestions de code pour votre projet :" }

            val defaultFollowUps = if (followUps.isEmpty()) {
                listOf(
                    "Ajouter la gestion d'état ViewModel",
                    "Améliorer l'interface Material 3",
                    "Ajouter des animations Compose"
                )
            } else {
                followUps
            }

            return ParsedAiTurn(
                displayText = cleanProse,
                suggestions = suggestions.take(8),
                followUps = defaultFollowUps
            )
        }

        private fun extractMarkerValue(line: String, key: String): String? {
            val idx = line.indexOf(key, ignoreCase = true)
            if (idx == -1) return null
            return line.substring(idx + key.length)
                .replace("-->", "")
                .replace("*/", "")
                .trim()
                .ifBlank { null }
        }
    }
}
