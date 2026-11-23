package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.example.model.AppType
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

enum class IconStylePreset(
    val label: String,
    val stylePrompt: String
) {
    GLASS_3D(
        label = "3D Glass",
        stylePrompt = "sleek 3D translucent glassmorphism emblem on deep dark slate background, crisp volumetric lighting"
    ),
    NEON_CYBER(
        label = "Néon Cyber",
        stylePrompt = "glowing electric cyan and emerald cyberpunk neon vector badge on obsidian black background"
    ),
    FLAT_MINIMAL(
        label = "Flat Minimal",
        stylePrompt = "clean minimalist modern flat geometric Android Material 3 app icon, sharp bold silhouette, rich gradient"
    ),
    PIXEL_ARCADE(
        label = "Pixel Arcade",
        stylePrompt = "vibrant high-detail retro 16-bit arcade pixel art game icon emblem centered cleanly"
    ),
    GRADIENT_LUXE(
        label = "Dégradé Pro",
        stylePrompt = "premium satin gradient app icon with subtle metallic bevel and centered symbolic glyph"
    )
}

sealed interface IconGenUiState {
    data object Idle : IconGenUiState
    data class Loading(val stageLabel: String) : IconGenUiState
    data class Success(val message: String) : IconGenUiState
    data class Error(val message: String) : IconGenUiState
}

class AppIconAiService {

    private val imageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    suspend fun generateAppIcon(
        appName: String,
        appType: AppType,
        customDescription: String,
        stylePreset: IconStylePreset
    ): Bitmap {
        val prompt = buildIconPrompt(appName, appType, customDescription, stylePreset)
        return try {
            val response = withTimeout(60_000L) {
                imageModel.generateContent(prompt)
            }
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
            imagePart?.image
                ?: throw IllegalStateException("Le modèle image n'a retourné aucune icône.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Icon generation timed out", e)
            throw IllegalStateException("La génération de l'icône a expiré après 60 secondes. Veuillez réessayer.")
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "Icon generation quota exceeded", e)
            throw IllegalStateException("Quota d'images Firebase AI atteint. Veuillez réessayer plus tard.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Icon generation failed", e)
            throw e
        }
    }

    suspend fun customizeExistingIcon(
        baseBitmap: Bitmap,
        appName: String,
        editInstruction: String,
        stylePreset: IconStylePreset
    ): Bitmap {
        val instruction = buildCustomizePrompt(appName, editInstruction, stylePreset)
        return try {
            val response = withTimeout(60_000L) {
                imageModel.generateContent(
                    content {
                        image(baseBitmap)
                        text(instruction)
                    }
                )
            }
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
            imagePart?.image
                ?: throw IllegalStateException("Le modèle n'a retourné aucune icône modifiée.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Icon customization timed out", e)
            throw IllegalStateException("La personnalisation de l'icône a expiré après 60 secondes.")
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "Icon customization quota exceeded", e)
            throw IllegalStateException("Quota d'images Firebase AI atteint. Veuillez réessayer plus tard.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Icon customization failed", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "AppIconAiService"

        fun buildIconPrompt(
            appName: String,
            appType: AppType,
            customDescription: String,
            stylePreset: IconStylePreset
        ): String {
            val subject = customDescription.trim().ifBlank {
                if (appType == AppType.GAME) {
                    "iconic stylized game emblem representing ${appName.ifBlank { "arcade adventure" }}"
                } else {
                    "iconic modern Android app symbol representing ${appName.ifBlank { "smart mobile utility" }}"
                }
            }
            return "Create a centered 1:1 square mobile app launcher icon featuring $subject. " +
                "Visual style: ${stylePreset.stylePrompt}. " +
                "Full bleed rounded-square composition, high contrast silhouette, zero text, zero letters, zero watermark."
        }

        fun buildCustomizePrompt(
            appName: String,
            editInstruction: String,
            stylePreset: IconStylePreset
        ): String {
            val details = editInstruction.trim().ifBlank {
                "enhance lighting, colors, and contrast for ${appName.ifBlank { "this app" }}"
            }
            return "Customize and transform this Android app launcher icon: $details. " +
                "Apply style: ${stylePreset.stylePrompt}. " +
                "Keep a clean centered 1:1 app icon composition with zero text or letters."
        }
    }
}
