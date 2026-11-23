package com.example.data

import android.util.Log
import com.example.model.AppProject
import com.example.model.AppType
import com.example.model.GitHubExportResult
import com.example.model.GitHubRepoPayloadFile
import com.example.model.GitHubRepoSummary
import com.example.model.GitHubUserProfile
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GitHubExportService {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun verifyTokenAndFetchProfile(token: String): Result<GitHubUserProfile> =
        withContext(Dispatchers.IO) {
            try {
                val cleanToken = token.trim()
                if (cleanToken.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Le token GitHub est vide."))
                }
                val bodyStr = executeRequest(
                    token = cleanToken,
                    method = "GET",
                    path = "/user"
                )
                val json = JSONObject(bodyStr)
                val login = json.optString("login", "").trim()
                if (login.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("Réponse utilisateur GitHub invalide."))
                }
                val name = json.optString("name", "").takeIf { it.isNotBlank() && it != "null" }
                val avatarUrl = json.optString("avatar_url", "")
                    .takeIf { isValidGitHubAvatarOrWebUrl(it) }
                Result.success(GitHubUserProfile(login = login, name = name, avatarUrl = avatarUrl))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "GitHub token verification failed", e)
                Result.failure(e)
            }
        }

    suspend fun listUserRepositories(token: String): Result<List<GitHubRepoSummary>> =
        withContext(Dispatchers.IO) {
            try {
                val cleanToken = token.trim()
                if (cleanToken.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Token GitHub requis."))
                }
                val bodyStr = executeRequest(
                    token = cleanToken,
                    method = "GET",
                    path = "/user/repos?sort=updated&affiliation=owner,collaborator&per_page=40"
                )
                val arr = JSONArray(bodyStr)
                val repos = mutableListOf<GitHubRepoSummary>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val fullName = obj.optString("full_name", "")
                    val name = obj.optString("name", "")
                    val htmlUrl = obj.optString("html_url", "")
                    if (name.isBlank() || fullName.isBlank()) continue
                    repos.add(
                        GitHubRepoSummary(
                            id = obj.optLong("id", i.toLong()),
                            name = name,
                            fullName = fullName,
                            htmlUrl = if (isValidGitHubWebUrl(htmlUrl)) htmlUrl else "https://github.com/$fullName",
                            defaultBranch = obj.optString("default_branch", "main").ifBlank { "main" },
                            isPrivate = obj.optBoolean("private", false),
                            description = obj.optString("description", "").takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                }
                Result.success(repos)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Listing GitHub repositories failed", e)
                Result.failure(e)
            }
        }

    suspend fun createRepository(
        token: String,
        name: String,
        description: String,
        isPrivate: Boolean
    ): Result<GitHubRepoSummary> = withContext(Dispatchers.IO) {
        try {
            val cleanName = sanitizeRepoName(name)
            val payload = JSONObject().apply {
                put("name", cleanName)
                if (description.isNotBlank()) {
                    put("description", description.trim().take(300))
                }
                put("private", isPrivate)
                put("auto_init", false)
            }
            val bodyStr = executeRequest(
                token = token.trim(),
                method = "POST",
                path = "/user/repos",
                jsonBody = payload.toString()
            )
            val obj = JSONObject(bodyStr)
            val fullName = obj.optString("full_name", cleanName)
            val htmlUrl = obj.optString("html_url", "https://github.com/$fullName")
            Result.success(
                GitHubRepoSummary(
                    id = obj.optLong("id", 0L),
                    name = obj.optString("name", cleanName),
                    fullName = fullName,
                    htmlUrl = if (isValidGitHubWebUrl(htmlUrl)) htmlUrl else "https://github.com/$fullName",
                    defaultBranch = obj.optString("default_branch", "main").ifBlank { "main" },
                    isPrivate = obj.optBoolean("private", isPrivate),
                    description = obj.optString("description", "").takeIf { it.isNotBlank() && it != "null" }
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Creating GitHub repository failed", e)
            Result.failure(e)
        }
    }

    suspend fun pushProjectToGitHub(
        token: String,
        owner: String,
        repoName: String,
        branch: String,
        commitMessage: String,
        createRepoFirst: Boolean,
        isPrivate: Boolean,
        repoDescription: String,
        project: AppProject,
        launcherIconBytes: ByteArray?,
        onProgress: suspend (String, Float) -> Unit
    ): Result<GitHubExportResult> = withContext(Dispatchers.IO) {
        try {
            val cleanToken = token.trim()
            if (cleanToken.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Token GitHub manquant."))
            }
            val validOwner = sanitizeOwnerLogin(owner)
            val cleanRepo = sanitizeRepoName(repoName)
            val cleanBranch = sanitizeBranchName(branch)
            val cleanMessage = commitMessage.trim().ifBlank {
                "feat: export ${project.appName.ifBlank { "ForgeApp" }} from ForgeAPK"
            }

            var actualOwner = validOwner
            var actualRepo = cleanRepo

            if (createRepoFirst) {
                onProgress("Création du dépôt GitHub $cleanRepo...", 0.08f)
                val created = createRepository(
                    token = cleanToken,
                    name = cleanRepo,
                    description = repoDescription,
                    isPrivate = isPrivate
                ).getOrThrow()
                val parts = created.fullName.split('/')
                if (parts.size == 2) {
                    actualOwner = sanitizeOwnerLogin(parts[0])
                    actualRepo = sanitizeRepoName(parts[1])
                }
            }

            onProgress("Préparation des fichiers source du projet...", 0.16f)
            val allFiles = buildRepositoryFiles(
                project = project,
                launcherIconBytes = launcherIconBytes
            )
            if (allFiles.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Aucun fichier à exporter vers GitHub."))
            }

            onProgress("Vérification de la branche $cleanBranch...", 0.24f)
            var (headCommitSha, baseTreeSha) = resolveOrInitializeBranchBase(
                token = cleanToken,
                owner = actualOwner,
                repo = actualRepo,
                branch = cleanBranch,
                gitignoreBytes = allFiles.firstOrNull { it.path == ".gitignore" }?.contentBytes
                    ?: generateGitignore().toByteArray(Charsets.UTF_8)
            )

            // Upload all project files as Git blobs
            val treeEntries = JSONArray()
            val total = allFiles.size
            allFiles.forEachIndexed { idx, file ->
                val fraction = 0.28f + (0.50f * (idx + 1).toFloat() / total.toFloat())
                onProgress("Envoi de ${file.path} (${idx + 1}/$total)...", fraction)
                val blobPayload = JSONObject().apply {
                    put("content", encodeBase64(file.contentBytes))
                    put("encoding", "base64")
                }
                val blobResp = JSONObject(
                    executeRequest(
                        token = cleanToken,
                        method = "POST",
                        path = "/repos/$actualOwner/$actualRepo/git/blobs",
                        jsonBody = blobPayload.toString()
                    )
                )
                val blobSha = blobResp.getString("sha")
                treeEntries.put(
                    JSONObject().apply {
                        put("path", file.path)
                        put("mode", "100644")
                        put("type", "blob")
                        put("sha", blobSha)
                    }
                )
            }

            onProgress("Construction de l'arbre Git ($total fichiers)...", 0.84f)
            val treePayload = JSONObject().apply {
                if (!baseTreeSha.isNullOrBlank()) {
                    put("base_tree", baseTreeSha)
                }
                put("tree", treeEntries)
            }
            val treeResp = JSONObject(
                executeRequest(
                    token = cleanToken,
                    method = "POST",
                    path = "/repos/$actualOwner/$actualRepo/git/trees",
                    jsonBody = treePayload.toString()
                )
            )
            val newTreeSha = treeResp.getString("sha")

            onProgress("Création du commit GitHub...", 0.92f)
            val commitPayload = JSONObject().apply {
                put("message", cleanMessage)
                put("tree", newTreeSha)
                put("parents", JSONArray().apply { put(headCommitSha) })
            }
            val commitResp = JSONObject(
                executeRequest(
                    token = cleanToken,
                    method = "POST",
                    path = "/repos/$actualOwner/$actualRepo/git/commits",
                    jsonBody = commitPayload.toString()
                )
            )
            val newCommitSha = commitResp.getString("sha")

            onProgress("Mise à jour de la branche $cleanBranch...", 0.97f)
            val refPatchPayload = JSONObject().apply {
                put("sha", newCommitSha)
                put("force", false)
            }
            executeRequest(
                token = cleanToken,
                method = "PATCH",
                path = "/repos/$actualOwner/$actualRepo/git/refs/heads/$cleanBranch",
                jsonBody = refPatchPayload.toString()
            )

            val fullName = "$actualOwner/$actualRepo"
            val repoUrl = "https://github.com/$fullName"
            val commitUrl = "$repoUrl/commit/$newCommitSha"
            onProgress("Export GitHub terminé !", 1.0f)

            Result.success(
                GitHubExportResult(
                    repoFullName = fullName,
                    repoUrl = repoUrl,
                    commitUrl = commitUrl,
                    commitSha = newCommitSha,
                    branch = cleanBranch,
                    fileCount = allFiles.size
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "GitHub push failed", e)
            Result.failure(e)
        }
    }

    private fun resolveOrInitializeBranchBase(
        token: String,
        owner: String,
        repo: String,
        branch: String,
        gitignoreBytes: ByteArray
    ): Pair<String, String?> {
        // 1. Try direct branch ref lookup
        val refAttempt = executeRequestRaw(
            token = token,
            method = "GET",
            path = "/repos/$owner/$repo/git/ref/heads/$branch"
        )
        if (refAttempt.code in 200..299) {
            val headSha = JSONObject(refAttempt.body).getJSONObject("object").getString("sha")
            val commitResp = JSONObject(
                executeRequest(
                    token = token,
                    method = "GET",
                    path = "/repos/$owner/$repo/git/commits/$headSha"
                )
            )
            val treeSha = commitResp.getJSONObject("tree").getString("sha")
            return headSha to treeSha
        }

        // 2. Check if repository has any existing branches (e.g. 'main' exists and user wants new branch)
        val branchesResp = executeRequestRaw(
            token = token,
            method = "GET",
            path = "/repos/$owner/$repo/branches?per_page=10"
        )
        if (branchesResp.code in 200..299) {
            val branches = JSONArray(branchesResp.body)
            if (branches.length() > 0) {
                val baseBranchObj = branches.getJSONObject(0)
                val baseHeadSha = baseBranchObj.getJSONObject("commit").getString("sha")
                // Create target branch pointing to existing default commit
                val createRefPayload = JSONObject().apply {
                    put("ref", "refs/heads/$branch")
                    put("sha", baseHeadSha)
                }
                executeRequest(
                    token = token,
                    method = "POST",
                    path = "/repos/$owner/$repo/git/refs",
                    jsonBody = createRefPayload.toString()
                )
                val commitResp = JSONObject(
                    executeRequest(
                        token = token,
                        method = "GET",
                        path = "/repos/$owner/$repo/git/commits/$baseHeadSha"
                    )
                )
                val treeSha = commitResp.getJSONObject("tree").getString("sha")
                return baseHeadSha to treeSha
            }
        }

        // 3. Repository is completely empty: initialize branch via Contents API with .gitignore
        val initPayload = JSONObject().apply {
            put("message", "chore: initialize ForgeAPK repository")
            put("content", encodeBase64(gitignoreBytes))
            put("branch", branch)
        }
        val initBody = executeRequest(
            token = token,
            method = "PUT",
            path = "/repos/$owner/$repo/contents/.gitignore",
            jsonBody = initPayload.toString()
        )
        val initJson = JSONObject(initBody)
        val initCommit = initJson.getJSONObject("commit")
        val initCommitSha = initCommit.getString("sha")
        val initTreeSha = initCommit.optJSONObject("tree")?.optString("sha")?.takeIf { it.isNotBlank() }
        return initCommitSha to initTreeSha
    }

    private data class RawHttpResponse(val code: Int, val body: String)

    private fun executeRequest(
        token: String,
        method: String,
        path: String,
        jsonBody: String? = null
    ): String {
        val raw = executeRequestRaw(token, method, path, jsonBody)
        if (raw.code !in 200..299) {
            throw IOException(extractHttpErrorMessage(raw.code, raw.body))
        }
        return raw.body
    }

    private fun executeRequestRaw(
        token: String,
        method: String,
        path: String,
        jsonBody: String? = null
    ): RawHttpResponse {
        val url = "$GITHUB_API_BASE$path"
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "ForgeAPK-Android")

        val requestBody = jsonBody?.toRequestBody(JSON_MEDIA_TYPE)
        when (method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody ?: "".toRequestBody(JSON_MEDIA_TYPE))
            "PUT" -> builder.put(requestBody ?: "".toRequestBody(JSON_MEDIA_TYPE))
            "PATCH" -> builder.patch(requestBody ?: "".toRequestBody(JSON_MEDIA_TYPE))
            else -> builder.method(method, requestBody)
        }

        httpClient.newCall(builder.build()).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            return RawHttpResponse(response.code, bodyStr)
        }
    }

    companion object {
        private const val TAG = "GitHubExportService"
        private const val GITHUB_API_BASE = "https://api.github.com"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val OWNER_REGEX = Regex("^[A-Za-z0-9-]+$")
        private val REPO_REGEX = Regex("^[A-Za-z0-9._-]+$")
        private val BRANCH_REGEX = Regex("^[A-Za-z0-9._/-]+$")

        fun sanitizeOwnerLogin(raw: String): String {
            val clean = raw.trim()
            require(clean.isNotBlank() && OWNER_REGEX.matches(clean)) {
                "Nom d'utilisateur GitHub invalide."
            }
            return clean
        }

        fun sanitizeRepoName(raw: String): String {
            val fallback = raw.trim()
                .lowercase()
                .replace(Regex("[^a-z0-9._-]+"), "-")
                .replace(Regex("-{2,}"), "-")
                .trim('-', '.')
                .ifBlank { "forgeapk-app" }
                .take(96)
            require(REPO_REGEX.matches(fallback)) {
                "Nom de dépôt GitHub invalide."
            }
            return fallback
        }

        fun sanitizeBranchName(raw: String): String {
            val clean = raw.trim().trim('/')
                .ifBlank { "main" }
            require(
                BRANCH_REGEX.matches(clean) &&
                    !clean.contains("..") &&
                    !clean.endsWith(".lock")
            ) {
                "Nom de branche Git invalide."
            }
            return clean
        }

        fun sanitizeRepoPath(rawPath: String): String {
            val segments = rawPath.replace('\\', '/')
                .split('/')
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "." && it != ".." }
            return segments.joinToString("/")
        }

        fun isValidGitHubWebUrl(url: String): Boolean {
            val trimmed = url.trim()
            return trimmed.startsWith("https://github.com/") && trimmed.length > "https://github.com/".length
        }

        private fun isValidGitHubAvatarOrWebUrl(url: String): Boolean {
            val trimmed = url.trim()
            return trimmed.startsWith("https://avatars.githubusercontent.com/") ||
                trimmed.startsWith("https://github.com/")
        }

        fun buildRepositoryFiles(
            project: AppProject,
            launcherIconBytes: ByteArray?
        ): List<GitHubRepoPayloadFile> {
            val byPath = linkedMapOf<String, GitHubRepoPayloadFile>()

            for (file in project.files) {
                val safePath = sanitizeRepoPath(file.path)
                if (safePath.isNotEmpty()) {
                    byPath[safePath] = GitHubRepoPayloadFile(
                        path = safePath,
                        contentBytes = file.content.toByteArray(Charsets.UTF_8),
                        isBinary = false
                    )
                }
            }

            if (!byPath.containsKey("README.md")) {
                byPath["README.md"] = GitHubRepoPayloadFile(
                    path = "README.md",
                    contentBytes = generateProjectReadme(project).toByteArray(Charsets.UTF_8),
                    isBinary = false
                )
            }

            if (!byPath.containsKey(".gitignore")) {
                byPath[".gitignore"] = GitHubRepoPayloadFile(
                    path = ".gitignore",
                    contentBytes = generateGitignore().toByteArray(Charsets.UTF_8),
                    isBinary = false
                )
            }

            if (project.playableHtml.isNotBlank() && !byPath.containsKey("web_bundle/index.html")) {
                byPath["web_bundle/index.html"] = GitHubRepoPayloadFile(
                    path = "web_bundle/index.html",
                    contentBytes = project.playableHtml.toByteArray(Charsets.UTF_8),
                    isBinary = false
                )
            }

            if (launcherIconBytes != null && launcherIconBytes.isNotEmpty()) {
                byPath["assets/ic_launcher.png"] = GitHubRepoPayloadFile(
                    path = "assets/ic_launcher.png",
                    contentBytes = launcherIconBytes,
                    isBinary = true
                )
            }

            return byPath.values.toList()
        }

        fun generateProjectReadme(project: AppProject): String {
            val appName = project.appName.ifBlank { "ForgeAPK Project" }
            val appTypeLabel = AppType.fromRaw(project.appType).label
            val pkg = project.packageName.ifBlank { "com.forge.generated" }
            val lang = project.language.ifBlank { "Kotlin" }
            val summaryText = project.summary.ifBlank { project.prompt.ifBlank { "Projet généré avec ForgeAPK Studio." } }

            return buildString {
                appendLine("# $appName")
                appendLine()
                appendLine("> Généré avec **ForgeAPK** · $appTypeLabel (`$lang`)")
                appendLine()
                appendLine("## Informations du projet")
                appendLine("- **Package Android** : `$pkg`")
                appendLine("- **Version** : `${project.versionName}` (build `${project.versionCode}`)")
                appendLine("- **Langage / Moteur** : `$lang`")
                appendLine("- **Fichiers inclus** : `${project.files.size}` fichier(s) source")
                appendLine()
                appendLine("## Description & Architecture")
                appendLine(summaryText.trim())
                appendLine()
                if (project.files.isNotEmpty()) {
                    appendLine("## Structure des fichiers")
                    project.files.forEach { f ->
                        val p = sanitizeRepoPath(f.path)
                        if (p.isNotEmpty()) {
                            appendLine("- `$p` (${f.language.ifBlank { "text" }})")
                        }
                    }
                    appendLine()
                }
                if (project.playableHtml.isNotBlank()) {
                    appendLine("## Aperçu Web interactif")
                    appendLine("Un bundle autonome est inclus dans `web_bundle/index.html`.")
                }
            }
        }

        fun generateGitignore(): String = """
            # Gradle & Android
            .gradle/
            build/
            local.properties
            *.apk
            *.aab
            *.keystore
            *.jks

            # IDE
            .idea/
            *.iml
            .vscode/

            # OS
            .DS_Store
            Thumbs.db
        """.trimIndent() + "\n"

        fun extractHttpErrorMessage(code: Int, rawBody: String): String {
            val serverMessage = extractJsonMessageField(rawBody)
            return when (code) {
                401 -> "Token GitHub invalide ou expiré. Vérifiez votre Personal Access Token."
                403 -> "Accès refusé par GitHub ($serverMessage). Assurez-vous que le token possède la permission 'repo' (ou Contents: Read & write)."
                404 -> "Dépôt ou branche introuvable sur GitHub ($serverMessage)."
                422 -> {
                    if (serverMessage.contains("already exists", ignoreCase = true)) {
                        "Ce nom de dépôt existe déjà sur votre compte GitHub. Choisissez-le dans 'Dépôt existant' ou utilisez un autre nom."
                    } else {
                        "Validation GitHub refusée : $serverMessage"
                    }
                }
                else -> "Erreur GitHub HTTP $code : $serverMessage"
            }
        }

        private fun extractJsonMessageField(rawBody: String): String {
            val match = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(rawBody)
            return match?.groupValues?.getOrNull(1)?.take(180)
                ?: rawBody.trim().take(160).ifBlank { "erreur inconnue" }
        }

        private const val BASE64_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

        fun encodeBase64(data: ByteArray): String {
            if (data.isEmpty()) return ""
            val out = StringBuilder(((data.size + 2) / 3) * 4)
            var i = 0
            while (i < data.size) {
                val b0 = data[i].toInt() and 0xFF
                val hasB1 = i + 1 < data.size
                val hasB2 = i + 2 < data.size
                val b1 = if (hasB1) data[i + 1].toInt() and 0xFF else 0
                val b2 = if (hasB2) data[i + 2].toInt() and 0xFF else 0

                out.append(BASE64_ALPHABET[b0 ushr 2])
                out.append(BASE64_ALPHABET[((b0 and 0x03) shl 4) or (b1 ushr 4)])
                out.append(if (hasB1) BASE64_ALPHABET[((b1 and 0x0F) shl 2) or (b2 ushr 6)] else '=')
                out.append(if (hasB2) BASE64_ALPHABET[b2 and 0x3F] else '=')
                i += 3
            }
            return out.toString()
        }
    }
}
