package com.example.data

import android.util.Log
import com.example.model.AppProject
import com.example.model.AppType
import com.example.model.ProjectFile
import com.example.model.TargetLanguage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class GeneratedProjectOutcome(
    val project: AppProject,
    val engineBadge: String,
    val failoverUsed: Boolean
)

class AiCodeGenerator(
    private val multiAiClient: MultiAiClient = MultiAiClient()
) {

    private val fileSchema = Schema.obj(
        properties = mapOf(
            "path" to Schema.string(description = "Relative source file path, e.g. app/src/main/java/com/app/MainActivity.kt or Assets/Scripts/Player.cs"),
            "language" to Schema.string(description = "File language name, e.g. Kotlin, C#, GDScript, C++, Lua, Dart, Python, XML, HTML"),
            "content" to Schema.string(description = "Complete, clean, well-structured source code of the file")
        )
    )

    private val projectSchema = Schema.obj(
        properties = mapOf(
            "appName" to Schema.string(description = "Short catchy app or game name (max 30 chars)"),
            "packageName" to Schema.string(description = "Valid reverse-DNS Android package name like com.forge.neonblaster"),
            "versionName" to Schema.string(description = "Semantic version like 1.0.0"),
            "detectedLanguage" to Schema.string(description = "Primary language/engine used, e.g. Kotlin, Java, C# (Unity), GDScript (Godot), C++ (Unreal/NDK), Lua (LÖVE2D), Dart (Flutter), Python, HTML5/JS"),
            "accentHex" to Schema.string(description = "Vibrant 6-digit hex color matching the app theme, e.g. #00E5FF"),
            "summary" to Schema.string(description = "Concise French summary of architecture, gameplay/features, and files generated"),
            "files" to Schema.array(
                items = fileSchema,
                description = "4 to 6 complete source code files for the requested language and Android build configuration"
            ),
            "playableHtml" to Schema.string(
                description = "A complete, self-contained, offline-capable single-file HTML5 + CSS + JavaScript (or Canvas/WebGL) mobile-first interactive build of the exact app or game, with touch support, dark/modern UI matching accentHex, and zero external CDN imports."
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = projectSchema
                temperature = 0.7f
            }
        )
    }

    suspend fun generateProject(
        userPrompt: String,
        appType: AppType,
        targetLanguage: TargetLanguage,
        requestedAppName: String,
        requestedPackageName: String
    ): Result<GeneratedProjectOutcome> {
        val langInstruction = if (targetLanguage == TargetLanguage.AUTO) {
            if (appType == AppType.GAME) {
                "Choisis le langage et moteur de jeu le plus adapté (ex: C# Unity, GDScript Godot, Lua LÖVE2D, C++ ou HTML5 Canvas/WebGL)."
            } else {
                "Choisis le langage Android le plus adapté (ex: Kotlin Jetpack Compose, Java Android SDK ou Dart Flutter)."
            }
        } else {
            "Utilise IMPÉRATIVEMENT le langage / moteur : ${targetLanguage.displayName} (${targetLanguage.engineLabel})."
        }

        val nameHint = if (requestedAppName.isNotBlank()) {
            "Nom souhaité : \"$requestedAppName\"."
        } else {
            "Propose un nom accrocheur et court."
        }

        val pkgHint = if (requestedPackageName.isNotBlank()) {
            "Package Android souhaité : \"$requestedPackageName\"."
        } else {
            "Propose un package Android valide au format com.forge.<nom>."
        }

        val systemAndPrompt = """
            Tu es un architecte logiciel et développeur de jeux vidéo polyglotte d'élite, expert en TOUS les langages (Kotlin, Java, C# Unity, GDScript Godot, C++ Unreal/NDK, Lua LÖVE2D, Dart Flutter, Python Kivy/Pygame, TypeScript/JavaScript/WebGL/Phaser).

            TÂCHE :
            Génère un projet complet de type ${appType.label} correspondant à la demande suivante :
            "$userPrompt"

            CONTRAINTES TECHNIQUES :
            1. $langInstruction
            2. $nameHint $pkgHint
            3. Génère 4 à 6 fichiers sources complets, authentiques et idiomatiques dans `files` (incluant le point d'entrée principal, la logique métier ou moteur de jeu, la configuration/build comme `AndroidManifest.xml` ou `build.gradle.kts` / `project.godot` / `pubspec.yaml`).
            4. Génère un champ `playableHtml` qui est une application ou un jeu 100% FONCTIONNEL, JOUABLE ET INTERACTIF en une seule page HTML5 + CSS + JS/Canvas autonome (zéro CDN externe, zéro image externe, tout dessiné en Canvas/SVG/CSS), optimisé pour écran tactile mobile (viewport `width=device-width, initial-scale=1.0, user-scalable=no`, boutons tactiles / drag / tap fluides), avec un design ultra soigné utilisant la couleur `accentHex`. Ce bundle `playableHtml` sera exécuté en aperçu direct ET compilé dans l'APK Android final (`assets/index.html`) !
            5. Rédige `summary` en français clair.
            6. Réponds UNIQUEMENT par un objet JSON valide respectant exactement la structure suivante :
               {"appName":"...","packageName":"com.forge....","versionName":"1.0.0","detectedLanguage":"...","accentHex":"#00E5FF","summary":"...","files":[{"path":"...","language":"...","content":"..."}],"playableHtml":"<!DOCTYPE html>..."}
        """.trimIndent()

        return executeGeneration(systemAndPrompt, userPrompt, appType)
    }

    suspend fun refineProject(
        existing: AppProject,
        refinementInstruction: String
    ): Result<GeneratedProjectOutcome> {
        val currentFilesDigest = existing.files.joinToString("\n\n") { file ->
            "--- ${file.path} (${file.language}) ---\n${file.content.take(3500)}"
        }

        val prompt = """
            Tu es un architecte logiciel et développeur de jeux expert multi-langages.
            Voici un projet existant :
            - Nom : ${existing.appName}
            - Package : ${existing.packageName}
            - Version : ${existing.versionName}
            - Type : ${existing.appType}
            - Langage principal : ${existing.language}
            - Couleur accent : ${existing.accentHex}

            Fichiers actuels :
            $currentFilesDigest

            NOUVELLE DEMANDE DE L'UTILISATEUR :
            "$refinementInstruction"

            Mets à jour les fichiers sources (`files`) ET le bundle interactif autonome (`playableHtml`) pour intégrer cette demande (ou convertir le projet vers le langage demandé si l'utilisateur demande un portage). Conserve une haute qualité de code et un `playableHtml` 100% autonome sans dépendances externes.
            Réponds UNIQUEMENT par un objet JSON valide contenant `appName`, `packageName`, `versionName`, `detectedLanguage`, `accentHex`, `summary`, `files` ([{path,language,content}]), et `playableHtml`.
        """.trimIndent()

        val appType = AppType.fromRaw(existing.appType)
        return executeGeneration(prompt, existing.prompt, appType).map { outcome ->
            outcome.copy(
                project = outcome.project.copy(
                    id = existing.id,
                    userId = existing.userId,
                    versionCode = existing.versionCode + 1L,
                    lastBuildApkName = existing.lastBuildApkName,
                    lastBuildSizeBytes = existing.lastBuildSizeBytes,
                    lastBuildSha256 = existing.lastBuildSha256
                )
            )
        }
    }

    private suspend fun executeGeneration(
        fullPrompt: String,
        originalPrompt: String,
        appType: AppType
    ): Result<GeneratedProjectOutcome> {
        return try {
            val outcome = multiAiClient.generateStructuredJson(
                systemAndPrompt = fullPrompt,
                builtinCall = {
                    val response = withTimeout(60_000L) {
                        model.generateContent(fullPrompt)
                    }
                    response.text ?: throw IllegalStateException("L'IA a renvoyé une réponse vide.")
                }
            )
            val jsonText = outcome.jsonText
            if (jsonText.isBlank()) {
                return Result.failure(IllegalStateException("L'IA a renvoyé une réponse vide."))
            }

            val obj = JSONObject(jsonText)
            val appName = obj.optString("appName", "ForgeApp").trim().ifBlank { "ForgeApp" }.take(60)
            val rawPkg = obj.optString("packageName", "")
            val packageName = ProjectRepository.normalizePackageName(rawPkg, appName)
            val versionName = obj.optString("versionName", "1.0.0").trim().ifBlank { "1.0.0" }.take(20)
            val detectedLang = obj.optString("detectedLanguage", "Kotlin").trim().ifBlank { "Kotlin" }.take(40)
            val accentRaw = obj.optString("accentHex", "#00E5FF").trim()
            val accentHex = if (accentRaw.matches(Regex("^#[0-9A-Fa-f]{6}$"))) accentRaw else "#00E5FF"
            val summary = obj.optString("summary", "").trim().take(2000)
            val playableHtml = obj.optString("playableHtml", "").trim().take(350000)

            val filesArray = obj.optJSONArray("files")
            val files = mutableListOf<ProjectFile>()
            if (filesArray != null) {
                for (i in 0 until filesArray.length().coerceAtMost(12)) {
                    val fileObj = filesArray.optJSONObject(i) ?: continue
                    val path = fileObj.optString("path", "").trim()
                    val lang = fileObj.optString("language", detectedLang).trim().ifBlank { detectedLang }
                    val content = fileObj.optString("content", "")
                    if (path.isNotBlank() && content.isNotBlank()) {
                        files.add(ProjectFile(path = path, language = lang, content = content))
                    }
                }
            }

            if (files.isEmpty()) {
                return Result.failure(IllegalStateException("Aucun fichier source généré par l'IA."))
            }

            val finalHtml = playableHtml.ifBlank {
                buildFallbackInteractiveHtml(appName, detectedLang, summary, accentHex)
            }

            Result.success(
                GeneratedProjectOutcome(
                    project = AppProject(
                        appName = appName,
                        packageName = packageName,
                        versionName = versionName,
                        versionCode = 1L,
                        appType = appType.name,
                        language = detectedLang,
                        prompt = originalPrompt.take(2000),
                        summary = summary,
                        accentHex = accentHex,
                        playableHtml = finalHtml,
                        files = files
                    ),
                    engineBadge = outcome.engineBadge,
                    failoverUsed = outcome.failoverUsed
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI generation timed out", e)
            Result.failure(IllegalStateException("La génération IA a expiré après 60 secondes. Veuillez réessayer avec une description plus ciblée."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI generation failed", e)
            Result.failure(e)
        }
    }

    private fun buildFallbackInteractiveHtml(
        appName: String,
        language: String,
        summary: String,
        accentHex: String
    ): String = """
        <!DOCTYPE html>
        <html lang="fr">
        <head>
        <meta charset="utf-8"/>
        <meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no"/>
        <style>
          body{margin:0;background:#090d18;color:#f1f5f9;font-family:sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:100vh;padding:24px;box-sizing:border-box;text-align:center;}
          .card{background:#111829;border:1px solid $accentHex;border-radius:20px;padding:24px;max-width:360px;width:100%;box-shadow:0 0 24px ${accentHex}33;}
          h1{color:$accentHex;margin:0 0 8px 0;font-size:22px;}
          .badge{display:inline-block;padding:4px 12px;border-radius:999px;background:${accentHex}22;color:$accentHex;font-size:12px;margin-bottom:12px;}
          p{color:#94a3b8;font-size:14px;line-height:1.5;}
        </style>
        </head>
        <body>
          <div class="card">
            <span class="badge">$language</span>
            <h1>$appName</h1>
            <p>$summary</p>
          </div>
        </body>
        </html>
    """.trimIndent()

    private companion object {
        const val TAG = "AiCodeGenerator"
    }
}
