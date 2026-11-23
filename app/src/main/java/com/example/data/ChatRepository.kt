package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.ChatMessage
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

class ChatRepository(
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
            ?: throw IllegalStateException("Vous devez être connecté avec Google pour utiliser le chat IA.")
    }

    fun observeMessages(userId: String, sessionId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session utilisateur modifiée." }
        val cleanSession = sessionId.trim().ifBlank { "global_architect" }.take(128)
        val path = "users/$uid/chat_messages"
        emitAll(
            db.collection("users")
                .document(uid)
                .collection("chat_messages")
                .whereEqualTo("sessionId", cleanSession)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(Long.MAX_VALUE, 0) }
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

    suspend fun addMessage(message: ChatMessage): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/chat_messages"
        return try {
            val uid = requireUserId()
            val cleanRole = if (message.role == "MODEL") "MODEL" else "USER"
            val cleanSession = message.sessionId.trim().ifBlank { "global_architect" }.take(128)
            val cleanText = message.text.trim().ifBlank { "..." }.take(30000)
            val cleanSuggestions = message.suggestions.take(8).map { s ->
                mapOf(
                    "title" to s.title.take(120),
                    "filePath" to s.filePath.take(160),
                    "language" to s.language.take(40),
                    "code" to s.code.take(12000)
                )
            }
            val cleanFollowUps = message.followUps
                .map { it.trim().take(140) }
                .filter { it.isNotBlank() }
                .take(5)

            val docRef = db.collection("users").document(uid).collection("chat_messages").document()
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "sessionId" to cleanSession,
                "role" to cleanRole,
                "text" to cleanText,
                "suggestions" to cleanSuggestions,
                "followUps" to cleanFollowUps,
                "createdAt" to FieldValue.serverTimestamp()
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

    suspend fun clearSession(userId: String, sessionId: String): Result<Unit> {
        val path = "users/$userId/chat_messages"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Session utilisateur modifiée." }
            val cleanSession = sessionId.trim().ifBlank { "global_architect" }.take(128)
            val docs = db.collection("users")
                .document(uid)
                .collection("chat_messages")
                .whereEqualTo("sessionId", cleanSession)
                .get()
                .await()

            if (!docs.isEmpty) {
                val batch = db.batch()
                docs.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
