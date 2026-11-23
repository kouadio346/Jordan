package com.example

import com.example.data.GitHubExportService
import com.example.model.AppProject
import com.example.model.AppType
import com.example.model.ProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubExportServiceTest {

    @Test
    fun sanitizeRepoName_normalizesSpacesAndSymbolsAndFallsBack() {
        assertEquals("neon-blaster-3d", GitHubExportService.sanitizeRepoName("Neon Blaster 3D!!"))
        assertEquals("forgeapk-app", GitHubExportService.sanitizeRepoName("   ---   "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun sanitizeBranchName_rejectsDirectoryTraversal() {
        GitHubExportService.sanitizeBranchName("feat/../secret")
    }

    @Test(expected = IllegalArgumentException::class)
    fun sanitizeBranchName_rejectsDotLockSuffix() {
        GitHubExportService.sanitizeBranchName("main.lock")
    }

    @Test
    fun sanitizeRepoPath_blocksTraversalAndLeadingSlashes() {
        assertEquals("app/src/MainActivity.kt", GitHubExportService.sanitizeRepoPath("/../../app\\src/./MainActivity.kt"))
        assertEquals("README.md", GitHubExportService.sanitizeRepoPath("///README.md"))
        assertEquals("", GitHubExportService.sanitizeRepoPath("../.."))
    }

    @Test
    fun isValidGitHubWebUrl_allowsOnlyHttpsGitHubLinks() {
        assertTrue(GitHubExportService.isValidGitHubWebUrl("https://github.com/octocat/forge-app"))
        assertFalse(GitHubExportService.isValidGitHubWebUrl("http://github.com/octocat/forge-app"))
        assertFalse(GitHubExportService.isValidGitHubWebUrl("javascript:alert(1)"))
        assertFalse(GitHubExportService.isValidGitHubWebUrl("https://github.com/"))
    }

    @Test
    fun buildRepositoryFiles_assemblesSourcesReadmeGitignoreWebAndIcon() {
        val project = AppProject(
            id = "proj_1",
            appName = "Cyber Runner",
            packageName = "com.forge.cyberrunner",
            versionName = "1.2.0",
            versionCode = 4L,
            appType = AppType.GAME.name,
            language = "Kotlin",
            summary = "Jeu runner néon fluide.",
            playableHtml = "<html><body><h1>Cyber</h1></body></html>",
            files = listOf(
                ProjectFile("/app/src/main/Main.kt", "Kotlin", "fun main() = println(\"Run\")"),
                ProjectFile("../secret/Hack.kt", "Kotlin", "// sanitized")
            )
        )
        val fakeIcon = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

        val bundle = GitHubExportService.buildRepositoryFiles(project, fakeIcon)
        val byPath = bundle.associateBy { it.path }

        assertNotNull(byPath["app/src/main/Main.kt"])
        assertNotNull(byPath["secret/Hack.kt"])
        assertNotNull(byPath["README.md"])
        assertNotNull(byPath[".gitignore"])
        assertNotNull(byPath["web_bundle/index.html"])
        assertNotNull(byPath["assets/ic_launcher.png"])
        assertTrue(byPath["assets/ic_launcher.png"]!!.isBinary)

        val readmeText = String(byPath["README.md"]!!.contentBytes, Charsets.UTF_8)
        assertTrue(readmeText.contains("# Cyber Runner"))
        assertTrue(readmeText.contains("com.forge.cyberrunner"))
        assertTrue(readmeText.contains("app/src/main/Main.kt"))
    }

    @Test
    fun encodeBase64_matchesRfc4648Vectors() {
        assertEquals("", GitHubExportService.encodeBase64(byteArrayOf()))
        assertEquals("Zg==", GitHubExportService.encodeBase64("f".toByteArray(Charsets.UTF_8)))
        assertEquals("Zm8=", GitHubExportService.encodeBase64("fo".toByteArray(Charsets.UTF_8)))
        assertEquals("Zm9v", GitHubExportService.encodeBase64("foo".toByteArray(Charsets.UTF_8)))
        assertEquals("Zm9vYmFy", GitHubExportService.encodeBase64("foobar".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun extractHttpErrorMessage_returnsHelpfulFrenchDiagnostics() {
        assertTrue(GitHubExportService.extractHttpErrorMessage(401, "{\"message\":\"Bad credentials\"}").contains("invalide"))
        assertTrue(GitHubExportService.extractHttpErrorMessage(422, "{\"message\":\"name already exists on this account\"}").contains("existe déjà"))
    }
}
