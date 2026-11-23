package com.example.model

enum class SwarmTaskStatus(val label: String) {
    WAITING("En attente"),
    WORKING("Travaille..."),
    COMPLETED("Terminé"),
    RECOVERED("Terminé (Relais)"),
    FAILED("Échec")
}

enum class SwarmPhase(val label: String) {
    IDLE("Prêt à répartir un plan"),
    PLANNING("Découpage du plan par spécialités..."),
    PLAN_READY("Plan réparti — Prêt au lancement parallèle"),
    EXECUTING_PARALLEL("Travail collaboratif en parallèle..."),
    INTEGRATING("Assemblage & synchronisation finale..."),
    DONE("Tous les agents ont terminé ensemble !"),
    ERROR("Erreur d'orchestration")
}

data class SwarmTaskAssignment(
    val id: String,
    val title: String,
    val description: String,
    val specialty: AiSpecialty,
    val assignedWorkerLabel: String,
    val assignedKeyId: String?,
    val targetFiles: List<String>,
    val status: SwarmTaskStatus = SwarmTaskStatus.WAITING,
    val durationMs: Long? = null,
    val producedFiles: List<ProjectFile> = emptyList(),
    val producedHtml: String = "",
    val notes: String = ""
)

data class SwarmExecutionState(
    val phase: SwarmPhase = SwarmPhase.IDLE,
    val planPrompt: String = "",
    val appType: AppType = AppType.ANDROID_APP,
    val targetLanguage: TargetLanguage = TargetLanguage.AUTO,
    val appName: String = "",
    val packageName: String = "",
    val accentHex: String = "#00E5FF",
    val planSummary: String = "",
    val plannerBadge: String = "",
    val tasks: List<SwarmTaskAssignment> = emptyList(),
    val createdProjectId: String? = null,
    val errorMessage: String? = null,
    val totalDurationMs: Long? = null
) {
    val completedCount: Int
        get() = tasks.count {
            it.status == SwarmTaskStatus.COMPLETED || it.status == SwarmTaskStatus.RECOVERED
        }

    val progressFraction: Float
        get() = when {
            phase == SwarmPhase.DONE -> 1f
            phase == SwarmPhase.INTEGRATING -> 0.92f
            tasks.isEmpty() -> if (phase == SwarmPhase.PLANNING) 0.15f else 0f
            else -> (0.2f + 0.7f * (completedCount.toFloat() / tasks.size.toFloat())).coerceIn(0f, 0.95f)
        }
}
