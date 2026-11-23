package com.example.model

data class GitHubUserProfile(
    val login: String,
    val name: String? = null,
    val avatarUrl: String? = null
)

data class GitHubRepoSummary(
    val id: Long,
    val name: String,
    val fullName: String,
    val htmlUrl: String,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val description: String? = null
)

data class GitHubRepoPayloadFile(
    val path: String,
    val contentBytes: ByteArray,
    val isBinary: Boolean = false
)

data class GitHubExportResult(
    val repoFullName: String,
    val repoUrl: String,
    val commitUrl: String,
    val commitSha: String,
    val branch: String,
    val fileCount: Int,
    val pushedAtMs: Long = System.currentTimeMillis()
)

sealed interface GitHubAccountState {
    data object Disconnected : GitHubAccountState
    data object Connecting : GitHubAccountState
    data class Connected(
        val profile: GitHubUserProfile,
        val repos: List<GitHubRepoSummary>
    ) : GitHubAccountState
    data class Error(val message: String) : GitHubAccountState
}

sealed interface GitHubExportUiState {
    data object Idle : GitHubExportUiState
    data class Loading(val stageLabel: String, val progress: Float) : GitHubExportUiState
    data class Success(val result: GitHubExportResult) : GitHubExportUiState
    data class Error(val message: String) : GitHubExportUiState
}
