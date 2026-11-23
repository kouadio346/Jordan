package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.io.File

enum class AppType(val label: String) {
    ANDROID_APP("Application Android"),
    GAME("Jeu 2D / 3D");

    companion object {
        fun fromRaw(raw: String): AppType =
            entries.firstOrNull { it.name == raw } ?: ANDROID_APP
    }
}

enum class TargetLanguage(
    val displayName: String,
    val engineLabel: String,
    val extension: String,
    val badgeColorHex: String,
    val bestFor: AppType?
) {
    AUTO("Auto (IA)", "Choix optimal", "kt", "#00E5FF", null),
    KOTLIN("Kotlin", "Jetpack Compose", "kt", "#7F52FF", AppType.ANDROID_APP),
    JAVA("Java", "Android SDK", "java", "#F89820", AppType.ANDROID_APP),
    CSHARP_UNITY("C#", "Unity 2D/3D", "cs", "#22C55E", AppType.GAME),
    GDSCRIPT_GODOT("GDScript", "Godot 4", "gd", "#478CBF", AppType.GAME),
    CPP_UNREAL("C++", "Unreal / NDK", "cpp", "#00599C", AppType.GAME),
    LUA_LOVE2D("Lua", "LÖVE2D / Solar2D", "lua", "#38BDF8", AppType.GAME),
    DART_FLUTTER("Dart", "Flutter", "dart", "#02569B", AppType.ANDROID_APP),
    PYTHON_KIVY("Python", "Kivy / Pygame", "py", "#FFD43B", null),
    HTML5_WEBGL("HTML5/JS", "WebGL / Phaser", "js", "#E34F26", null)
}

@Keep
@IgnoreExtraProperties
data class ProjectFile(
    val path: String = "",
    val language: String = "",
    val content: String = ""
)

@Keep
@IgnoreExtraProperties
data class AppProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val appName: String = "",
    val packageName: String = "",
    val versionName: String = "1.0.0",
    val versionCode: Long = 1L,
    val appType: String = AppType.ANDROID_APP.name,
    val language: String = "Kotlin",
    val prompt: String = "",
    val summary: String = "",
    val accentHex: String = "#00E5FF",
    val playableHtml: String = "",
    val files: List<ProjectFile> = emptyList(),
    val lastBuildApkName: String? = null,
    val lastBuildSizeBytes: Long? = null,
    val lastBuildSha256: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class StarterTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val appType: AppType,
    val language: TargetLanguage,
    val prompt: String,
    val accentHex: String
)

object StarterTemplates {
    val catalog: List<StarterTemplate> = listOf(
        StarterTemplate(
            id = "neon_shooter",
            title = "Neon Blaster",
            subtitle = "Jeu de tir spatial arcade avec vagues & boss",
            appType = AppType.GAME,
            language = TargetLanguage.CSHARP_UNITY,
            prompt = "Crée un jeu de tir spatial rétro néon en C# (Unity) avec vaisseau contrôlable au doigt, tirs laser automatiques, vagues d'astéroïdes et ennemis, bonus bouclier, score et effets de particules.",
            accentHex = "#00E5FF"
        ),
        StarterTemplate(
            id = "cyber_runner",
            title = "Cyber Dash",
            subtitle = "Runner infini néon avec saut & obstacles",
            appType = AppType.GAME,
            language = TargetLanguage.GDSCRIPT_GODOT,
            prompt = "Crée un jeu runner infini cyberpunk en GDScript (Godot 4) où le joueur saute par-dessus des lasers et collecte des cristaux énergétiques avec difficulté progressive.",
            accentHex = "#FFB300"
        ),
        StarterTemplate(
            id = "pixel_dungeon",
            title = "Arcane Arena",
            subtitle = "Combat d'arène arcade 2D tactique",
            appType = AppType.GAME,
            language = TargetLanguage.LUA_LOVE2D,
            prompt = "Crée un mini-jeu d'arène survie 2D en Lua (LÖVE2D) avec joystick virtuel tactile, vagues de slimes, attaque de zone et compteur de vagues.",
            accentHex = "#10B981"
        ),
        StarterTemplate(
            id = "budget_pulse",
            title = "Budget Pulse",
            subtitle = "Gestionnaire de dépenses avec graphiques",
            appType = AppType.ANDROID_APP,
            language = TargetLanguage.KOTLIN,
            prompt = "Crée une application Android moderne en Kotlin / Jetpack Compose de suivi de budget et dépenses quotidiennes par catégorie avec solde total, graphique visuel et ajout rapide.",
            accentHex = "#7F52FF"
        ),
        StarterTemplate(
            id = "beat_synth",
            title = "BeatForge Pad",
            subtitle = "Boîte à rythmes & synthé WebAudio tactile",
            appType = AppType.ANDROID_APP,
            language = TargetLanguage.HTML5_WEBGL,
            prompt = "Crée une application musicale séquenceur 16 pas et pads de batterie synthétique avec Web Audio API, tempo BPM réglable et visualisation néon en temps réel.",
            accentHex = "#EC4899"
        ),
        StarterTemplate(
            id = "focus_pomodoro",
            title = "FocusFlow Pro",
            subtitle = "Minuteur Pomodoro & suivi d'objectifs",
            appType = AppType.ANDROID_APP,
            language = TargetLanguage.DART_FLUTTER,
            prompt = "Crée une application de productivité Pomodoro en Dart/Flutter avec minuteur circulaire animé, tâches du jour, sessions complétées et mode pause.",
            accentHex = "#38BDF8"
        )
    )
}

enum class LogLevel {
    INFO,
    STEP,
    SUCCESS,
    WARN,
    ERROR
}

data class BuildLogEntry(
    val tag: String,
    val message: String,
    val level: LogLevel = LogLevel.INFO
)

data class ApkBuildResult(
    val apkFile: File,
    val apkName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val sizeBytes: Long,
    val sha256: String,
    val certFingerprint: String,
    val entryCount: Int,
    val durationMs: Long,
    val logs: List<BuildLogEntry>
)
