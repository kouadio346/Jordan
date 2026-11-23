package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CodeSuggestion(
    val title: String = "",
    val filePath: String = "",
    val language: String = "",
    val code: String = ""
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sessionId: String = "",
    val role: String = "USER",
    val text: String = "",
    val suggestions: List<CodeSuggestion> = emptyList(),
    val followUps: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class ParsedAiTurn(
    val displayText: String,
    val suggestions: List<CodeSuggestion>,
    val followUps: List<String>
)

object ArchitectStarterPrompts {
    val prompts: List<Pair<String, String>> = listOf(
        "App Suivi Fitness Compose" to "Je veux créer une application Android en Kotlin & Jetpack Compose pour suivre mes séances de sport avec graphique hebdomadaire et minuteur de repos.",
        "Jeu Platformer 2D" to "Je veux concevoir un jeu de plateforme 2D rétro néon avec double saut, pièces à collecter et ennemis patrouilleurs.",
        "Gestionnaire Budget" to "Propose-moi l'architecture complète d'une app Android de budget personnel avec catégories, ajout rapide de dépenses et alertes.",
        "Quiz Interactif Multi-Niveaux" to "Crée une app Android de quiz de culture générale avec score, timer par question et révision des erreurs."
    )
}
