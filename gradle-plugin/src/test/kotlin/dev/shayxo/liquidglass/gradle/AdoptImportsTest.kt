package dev.shayxo.liquidglass.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdoptImportsTest {
    @Test fun explicitImportsOfGlassComponentsAreSwitched() {
        val r = AdoptTask.adoptImports("""
            package app
            import androidx.compose.material3.Button
            import androidx.compose.material3.Text
            import androidx.compose.material3.NavigationBar
        """.trimIndent())
        assertEquals(2, r.changes)
        assertTrue("import com.wexpa.liquidglass.material3.Button" in r.text)
        assertTrue("import com.wexpa.liquidglass.material3.NavigationBar" in r.text)
        assertTrue("import androidx.compose.material3.Text" in r.text, "Text has no glass version and must stay")
    }

    @Test fun aStarImportGainsExplicitGlassImportsForWhatTheFileUses() {
        val r = AdoptTask.adoptImports("""
            package app
            import androidx.compose.material3.*
            fun f() { Scaffold { Card { Text("x") } } }
        """.trimIndent())
        assertEquals(2, r.changes)
        assertTrue("import com.wexpa.liquidglass.material3.Card" in r.text)
        assertTrue("import com.wexpa.liquidglass.material3.Scaffold" in r.text)
        assertTrue("import androidx.compose.material3.*" in r.text)
    }

    @Test fun explicitImportsOfComposeMultiplatformGlassComponentsAreSwitched() {
        val r = AdoptTask.adoptImports("""
            package app
            import org.jetbrains.compose.material3.Button
            import org.jetbrains.compose.material3.Text
            import org.jetbrains.compose.material3.NavigationBar
        """.trimIndent())
        assertEquals(2, r.changes)
        assertTrue("import com.wexpa.liquidglass.material3.Button" in r.text)
        assertTrue("import com.wexpa.liquidglass.material3.NavigationBar" in r.text)
        assertTrue("import org.jetbrains.compose.material3.Text" in r.text, "Text has no glass version and must stay")
    }

    @Test fun aComposeMultiplatformStarImportGainsExplicitGlassImportsForWhatTheFileUses() {
        val r = AdoptTask.adoptImports("""
            package app
            import org.jetbrains.compose.material3.*
            fun f() { Scaffold { Card { Text("x") } } }
        """.trimIndent())
        assertEquals(2, r.changes)
        assertTrue("import com.wexpa.liquidglass.material3.Card" in r.text)
        assertTrue("import com.wexpa.liquidglass.material3.Scaffold" in r.text)
        assertTrue("import org.jetbrains.compose.material3.*" in r.text)
    }

    @Test fun runningItTwiceChangesNothingMore() {
        val once = AdoptTask.adoptImports("import androidx.compose.material3.*\nfun f() { Button() }").text
        assertEquals(0, AdoptTask.adoptImports(once).changes)
    }

    @Test fun aNameTheFileDeclaresOrOnlyMentionsIsLeftAlone() {
        val r = AdoptTask.adoptImports("""
            package app
            import androidx.compose.material3.*
            @Composable fun Card(content: @Composable () -> Unit) = Box { content() }
            fun f() { Card { Text("A Switch in a string") }; Button(onClick = {}) {} }
        """.trimIndent())
        assertEquals(1, r.changes, r.text)
        assertTrue("import com.wexpa.liquidglass.material3.Button" in r.text)
        assertTrue("liquidglass.material3.Card" !in r.text, "the file's own Card must not be shadowed")
        assertTrue("liquidglass.material3.Switch" !in r.text, "a word in a string is not a use")
    }
}
