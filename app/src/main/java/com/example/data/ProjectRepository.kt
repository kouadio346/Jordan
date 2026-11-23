package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.AppProject
import com.example.model.AppType
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ProjectRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Vous devez être connecté avec Google pour accéder aux projets.")
    }

    fun observeProjects(userId: String): Flow<List<AppProject>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session utilisateur modifiée." }
        val path = "users/$uid/projects"
        emitAll(
            db.collection("users")
                .document(uid)
                .collection("projects")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AppProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createProject(project: AppProject): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/projects"
        return try {
            val uid = requireUserId()
            val sanitized = sanitizeProject(project, uid)
            val docRef = db.collection("users").document(uid).collection("projects").document()
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "appName" to sanitized.appName,
                "packageName" to sanitized.packageName,
                "versionName" to sanitized.versionName,
                "versionCode" to sanitized.versionCode,
                "appType" to sanitized.appType,
                "language" to sanitized.language,
                "prompt" to sanitized.prompt,
                "summary" to sanitized.summary,
                "accentHex" to sanitized.accentHex,
                "playableHtml" to sanitized.playableHtml,
                "files" to sanitized.files.map {
                    mapOf(
                        "path" to it.path,
                        "language" to it.language,
                        "content" to it.content
                    )
                },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateProject(projectId: String, project: AppProject): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/projects/$projectId"
        return try {
            val uid = requireUserId()
            require(projectId.isNotBlank()) { "Identifiant du projet invalide." }
            val sanitized = sanitizeProject(project, uid)
            val docRef = db.collection("users").document(uid).collection("projects").document(projectId)
            docPath = docRef.path

            val updatePayload = mapOf(
                "appName" to sanitized.appName,
                "packageName" to sanitized.packageName,
                "versionName" to sanitized.versionName,
                "versionCode" to sanitized.versionCode,
                "language" to sanitized.language,
                "prompt" to sanitized.prompt,
                "summary" to sanitized.summary,
                "accentHex" to sanitized.accentHex,
                "playableHtml" to sanitized.playableHtml,
                "files" to sanitized.files.map {
                    mapOf(
                        "path" to it.path,
                        "language" to it.language,
                        "content" to it.content
                    )
                },
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updatePayload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordBuildResult(
        projectId: String,
        apkName: String,
        sizeBytes: Long,
        sha256: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/projects/$projectId"
        return try {
            val uid = requireUserId()
            require(projectId.isNotBlank()) { "Identifiant du projet invalide." }
            val docRef = db.collection("users").document(uid).collection("projects").document(projectId)
            docPath = docRef.path

            val updatePayload = mapOf(
                "lastBuildApkName" to trimToUtf8Bytes(apkName.trim(), 120).ifBlank { "app-signed.apk" },
                "lastBuildSizeBytes" to sizeBytes.coerceAtLeast(0L),
                "lastBuildSha256" to trimToUtf8Bytes(sha256.trim(), 128).ifBlank { "sha256" },
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updatePayload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteProject(projectId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/projects/$projectId"
        return try {
            val uid = requireUserId()
            require(projectId.isNotBlank()) { "Identifiant du projet invalide." }
            val docRef = db.collection("users").document(uid).collection("projects").document(projectId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private fun sanitizeProject(project: AppProject, uid: String): AppProject {
        val cleanType = if (project.appType == AppType.GAME.name) AppType.GAME.name else AppType.ANDROID_APP.name
        val cleanName = trimToUtf8Bytes(project.appName.trim(), 60).ifBlank { "MonApp" }
        val cleanPkg = normalizePackageName(project.packageName, cleanName)
        val cleanVersion = trimToUtf8Bytes(project.versionName.trim(), 20).ifBlank { "1.0.0" }
        val cleanCode = project.versionCode.coerceIn(1L, 999999L)
        val cleanLang = trimToUtf8Bytes(project.language.trim(), 40).ifBlank { "Kotlin" }
        val cleanPrompt = trimToUtf8Bytes(project.prompt.trim(), 2000).ifBlank { cleanName }
        val cleanSummary = trimToUtf8Bytes(project.summary.trim(), 2000)
        val cleanAccent = if (project.accentHex.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
            project.accentHex
        } else {
            "#00E5FF"
        }
        val cleanHtml = trimToUtf8Bytes(project.playableHtml, 250_000)
        val cleanFiles = project.files
            .filter { it.path.isNotBlank() && it.content.isNotBlank() }
            .take(12)
            .mapNotNull { file ->
                val cleanPath = trimToUtf8Bytes(file.path.trim(), 240)
                val cleanFileLang = trimToUtf8Bytes(file.language.trim(), 40).ifBlank { cleanLang }
                val cleanContent = trimToUtf8Bytes(file.content, 45_000)
                if (cleanPath.isBlank() || cleanContent.isBlank()) null
                else com.example.model.ProjectFile(
                    path = cleanPath,
                    language = cleanFileLang,
                    content = cleanContent
                )
            }
            .ifEmpty {
                listOf(
                    com.example.model.ProjectFile(
                        path = "src/Main.kt",
                        language = cleanLang,
                        content = "// $cleanName"
                    )
                )
            }

        return project.copy(
            userId = uid,
            appName = cleanName,
            packageName = cleanPkg,
            versionName = cleanVersion,
            versionCode = cleanCode,
            appType = cleanType,
            language = cleanLang,
            prompt = cleanPrompt,
            summary = cleanSummary,
            accentHex = cleanAccent,
            playableHtml = cleanHtml,
            files = cleanFiles
        )
    }

    companion object {
        /**
         * Trims [input] cleanly on a Unicode code point boundary so its UTF-8 encoding
         * never exceeds [maxBytes]. Matches Firestore `string.size()` UTF-8 byte counting.
         */
        fun trimToUtf8Bytes(input: String, maxBytes: Int): String {
            if (maxBytes <= 0 || input.isEmpty()) return ""
            val utf8 = input.toByteArray(Charsets.UTF_8)
            if (utf8.size <= maxBytes) return input
            val sb = StringBuilder(minOf(input.length, maxBytes))
            var byteCount = 0
            var index = 0
            while (index < input.length) {
                val cp = Character.codePointAt(input, index)
                val charCount = Character.charCount(cp)
                val cpBytes = when {
                    cp <= 0x7F -> 1
                    cp <= 0x7FF -> 2
                    cp <= 0xFFFF -> 3
                    else -> 4
                }
                if (byteCount + cpBytes > maxBytes) break
                sb.appendCodePoint(cp)
                byteCount += cpBytes
                index += charCount
            }
            return sb.toString()
        }

        fun normalizePackageName(raw: String, fallbackAppName: String): String {
            val cleaned = raw.lowercase()
                .replace(Regex("[^a-z0-9.]"), "")
                .split(".")
                .filter { it.isNotBlank() }
                .map { seg ->
                    if (seg.first().isDigit()) "a$seg" else seg
                }
            if (cleaned.size >= 2) {
                return cleaned.joinToString(".").take(120)
            }
            val slug = fallbackAppName.lowercase()
                .replace(Regex("[^a-z0-9]"), "")
                .ifBlank { "forgeapp" }
                .let { if (it.first().isDigit()) "app$it" else it }
            return "com.forge.$slug".take(120)
        }
    }
}
