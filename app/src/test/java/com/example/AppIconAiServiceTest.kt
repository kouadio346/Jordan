package com.example

import com.example.data.AppIconAiService
import com.example.data.IconStylePreset
import com.example.model.AppType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconAiServiceTest {

    @Test
    fun buildIconPrompt_usesGameFallbackWhenDescriptionBlank() {
        val prompt = AppIconAiService.buildIconPrompt(
            appName = "Galactic Drift",
            appType = AppType.GAME,
            customDescription = "   ",
            stylePreset = IconStylePreset.NEON_CYBER
        )

        assertTrue(prompt.contains("Galactic Drift"))
        assertTrue(prompt.contains("game emblem"))
        assertTrue(prompt.contains(IconStylePreset.NEON_CYBER.stylePrompt))
        assertTrue(prompt.contains("zero text"))
    }

    @Test
    fun buildIconPrompt_usesAndroidAppFallbackWhenDescriptionBlank() {
        val prompt = AppIconAiService.buildIconPrompt(
            appName = "Budget Pulse",
            appType = AppType.ANDROID_APP,
            customDescription = "",
            stylePreset = IconStylePreset.FLAT_MINIMAL
        )

        assertTrue(prompt.contains("Budget Pulse"))
        assertTrue(prompt.contains("Android app symbol"))
        assertTrue(prompt.contains(IconStylePreset.FLAT_MINIMAL.stylePrompt))
    }

    @Test
    fun buildIconPrompt_prefersCustomDescriptionOverGenericFallback() {
        val prompt = AppIconAiService.buildIconPrompt(
            appName = "IgnoredName",
            appType = AppType.GAME,
            customDescription = "crystal phoenix soaring through stardust",
            stylePreset = IconStylePreset.GLASS_3D
        )

        assertTrue(prompt.contains("crystal phoenix soaring through stardust"))
        assertFalse(prompt.contains("IgnoredName"))
        assertTrue(prompt.contains(IconStylePreset.GLASS_3D.stylePrompt))
    }

    @Test
    fun buildCustomizePrompt_includesUserInstructionAndStylePreset() {
        val prompt = AppIconAiService.buildCustomizePrompt(
            appName = "Neon Runner",
            editInstruction = "add golden lightning bolts and boost rim light",
            stylePreset = IconStylePreset.GRADIENT_LUXE
        )

        assertTrue(prompt.contains("add golden lightning bolts and boost rim light"))
        assertTrue(prompt.contains(IconStylePreset.GRADIENT_LUXE.stylePrompt))
        assertTrue(prompt.contains("zero text"))
    }

    @Test
    fun buildCustomizePrompt_fallsBackGracefullyWhenEditInstructionBlank() {
        val prompt = AppIconAiService.buildCustomizePrompt(
            appName = "Pixel Quest",
            editInstruction = "",
            stylePreset = IconStylePreset.PIXEL_ARCADE
        )

        assertTrue(prompt.contains("Pixel Quest"))
        assertTrue(prompt.contains(IconStylePreset.PIXEL_ARCADE.stylePrompt))
    }
}
