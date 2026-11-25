package com.example

import com.example.data.ProjectRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectSanitizationTest {

    @Test
    fun trimToUtf8Bytes_preservesAsciiAndNeverExceedsByteBudgetOnFrenchAccents() {
        val frenchText = "Création d'une application météo avancée avec géolocalisation, prévisions détaillées et thème néon — très réactif ! ".repeat(30)
        assertTrue(frenchText.toByteArray(Charsets.UTF_8).size > 2000)

        val trimmed = ProjectRepository.trimToUtf8Bytes(frenchText, 2000)
        val byteLen = trimmed.toByteArray(Charsets.UTF_8).size
        assertTrue("Expected <= 2000 UTF-8 bytes, got $byteLen", byteLen <= 2000)
        assertTrue(byteLen >= 1990)
    }

    @Test
    fun trimToUtf8Bytes_handlesFourByteEmojisWithoutSplittingSurrogates() {
        val emojis = "🚀🎮🔥⚡".repeat(20) // 4 bytes each -> 320 bytes
        val trimmed = ProjectRepository.trimToUtf8Bytes(emojis, 10)
        // Only 2 full 4-byte emojis fit inside 10 bytes (8 bytes total)
        assertEquals(8, trimmed.toByteArray(Charsets.UTF_8).size)
        assertEquals("🚀🎮", trimmed)
    }

    @Test
    fun normalizePackageName_buildsValidReverseDnsIdentifier() {
        assertEquals("com.forge.neonrunner", ProjectRepository.normalizePackageName("", "Neon Runner!"))
        assertEquals("com.forge.a123game", ProjectRepository.normalizePackageName("com.forge.123game", "Fallback"))
        assertEquals("com.aistudio.jordan", ProjectRepository.normalizePackageName("com.aistudio.jordan", "Jordan"))
    }
}
