package com.example

import com.example.data.ConversationalArchitectService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationalArchitectParserTest {

    @Test
    fun parseAiResponse_extractsMultipleSuggestionsAndFollowups() {
        val raw = """
            Here is an MVVM screen and state holder for your habit tracker app.

            ```kotlin
            // file: app/src/main/java/com/app/ui/HabitScreen.kt
            // title: Habit Tracker Screen
            @Composable
            fun HabitScreen() {
                Text("Habits")
            }
            ```

            And here is the manifest update:

            ```xml
            <!-- file: app/src/main/AndroidManifest.xml -->
            <manifest package="com.app.habits" />
            ```

            [FOLLOWUPS: "Add Room caching" | "Add streak animations" | "Add daily reminder"]
        """.trimIndent()

        val parsed = ConversationalArchitectService.parseAiResponse(raw)

        assertEquals(2, parsed.suggestions.size)
        assertEquals("Habit Tracker Screen", parsed.suggestions[0].title)
        assertEquals("app/src/main/java/com/app/ui/HabitScreen.kt", parsed.suggestions[0].filePath)
        assertEquals("Kotlin", parsed.suggestions[0].language)
        assertTrue(parsed.suggestions[0].code.startsWith("@Composable"))
        assertFalse(parsed.suggestions[0].code.contains("// file:"))

        assertEquals("AndroidManifest.xml", parsed.suggestions[1].title)
        assertEquals("app/src/main/AndroidManifest.xml", parsed.suggestions[1].filePath)
        assertEquals("Xml", parsed.suggestions[1].language)
        assertEquals("<manifest package=\"com.app.habits\" />", parsed.suggestions[1].code)

        assertEquals(
            listOf("Add Room caching", "Add streak animations", "Add daily reminder"),
            parsed.followUps
        )
        assertFalse(parsed.displayText.contains("[FOLLOWUPS:"))
        assertTrue(parsed.displayText.contains("[Suggestion #1 : Habit Tracker Screen]"))
    }

    @Test
    fun parseAiResponse_providesFallbackPathAndDefaultFollowups() {
        val raw = """
            Let's start with a simple utility function.

            ```gdscript
            extends Node2D
            func _ready():
                print("Hello")
            ```
        """.trimIndent()

        val parsed = ConversationalArchitectService.parseAiResponse(raw)

        assertEquals(1, parsed.suggestions.size)
        assertEquals("app/src/main/java/com/app/Generated1.gd", parsed.suggestions[0].filePath)
        assertEquals("Generated1.gd", parsed.suggestions[0].title)
        assertEquals(3, parsed.followUps.size)
    }

    @Test
    fun stripFollowupsLine_removesFollowupsTagCleanly() {
        val raw = "Streaming reply text...\n[FOLLOWUPS: \"One\" | \"Two\"]"
        assertEquals("Streaming reply text...", ConversationalArchitectService.stripFollowupsLine(raw))
    }
}
