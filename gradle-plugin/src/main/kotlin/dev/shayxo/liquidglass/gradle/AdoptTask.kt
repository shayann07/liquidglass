package dev.shayxo.liquidglass.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

/**
 * `./gradlew liquidGlassAdopt`: switches the project's Material 3 imports to the Liquid Glass versions,
 * once. Explicit `import androidx.compose.material3.Button` or `import org.jetbrains.compose.material3.Button`
 * lines become `import com.wexpa.liquidglass.material3.Button`; a file with wildcard imports (`androidx.compose.material3.*`
 * or `org.jetbrains.compose.material3.*`) gains explicit glass imports for the components it uses, which take precedence
 * over the star. Run with `--dry-run` to see the changes without writing them.
 */
abstract class AdoptTask : DefaultTask() {
    @get:InputFiles abstract val sources: ConfigurableFileCollection
    @get:Input abstract val dryRun: Property<Boolean>

    @Option(option = "dry-run", description = "List the changes without writing them.")
    fun setDryRunOption(value: Boolean) = dryRun.set(value)

    @TaskAction
    fun adopt() {
        var files = 0; var imports = 0
        for (file in sources.files.filter { it.isFile && it.extension == "kt" }) {
            val text = file.readText()
            val result = adoptImports(text)
            if (result.changes == 0) continue
            files++; imports += result.changes
            logger.lifecycle("liquidGlassAdopt: ${file.relativeTo(project.projectDir)}: ${result.changes} import(s)")
            if (!dryRun.get()) file.writeText(result.text)
        }
        logger.lifecycle("liquidGlassAdopt: ${if (dryRun.get()) "would change" else "changed"} $imports import(s) in $files file(s).")
    }

    internal data class Result(val text: String, val changes: Int)

    internal companion object {
        /** Material 3 names with a glass version in com.wexpa.liquidglass.material3. */
        val GLASS = setOf(
            "Scaffold", "NavigationBar", "NavigationBarItem", "TopAppBar", "CenterAlignedTopAppBar", "MediumTopAppBar",
            "LargeTopAppBar", "BottomAppBar", "NavigationRail", "NavigationRailItem", "Button", "FilledTonalButton",
            "ElevatedButton", "OutlinedButton", "TextButton", "IconButton", "FilledIconButton", "FilledTonalIconButton",
            "OutlinedIconButton", "FloatingActionButton", "SmallFloatingActionButton", "LargeFloatingActionButton",
            "ExtendedFloatingActionButton", "Card", "ElevatedCard", "OutlinedCard", "AssistChip", "FilterChip", "InputChip",
            "SuggestionChip", "Switch", "Slider", "SingleChoiceSegmentedButtonRow", "MultiChoiceSegmentedButtonRow",
            "SegmentedButton", "SegmentedButtonDefaults", "PrimaryTabRow", "SecondaryTabRow", "TabRow", "ScrollableTabRow",
            "Tab", "SearchBar", "DockedSearchBar", "AlertDialog", "BasicAlertDialog", "ModalBottomSheet",
            "rememberModalBottomSheetState", "SheetState", "DropdownMenu", "DropdownMenuItem", "Snackbar",
            "IconToggleButton", "FilledIconToggleButton", "FilledTonalIconToggleButton", "OutlinedIconToggleButton",
            "ElevatedAssistChip", "ElevatedFilterChip", "ElevatedSuggestionChip", "RangeSlider", "Surface",
            "TooltipBox", "TooltipScope", "PlainTooltip", "RichTooltip", "ModalDrawerSheet", "DismissibleDrawerSheet",
            "PermanentDrawerSheet", "NavigationDrawerItem", "DatePicker", "DateRangePicker", "DatePickerDialog",
            "TimePickerDialog", "LeadingIconTab", "PrimaryScrollableTabRow", "SecondaryScrollableTabRow",
        )
        private val FROM_PREFIXES = listOf("androidx.compose.material3.", "org.jetbrains.compose.material3.")
        private const val TO = "com.wexpa.liquidglass.material3."

        fun adoptImports(text: String): Result {
            var changes = 0
            val lines = text.lines().toMutableList()
            for (i in lines.indices) {
                val m = Regex("""^import\s+(?:androidx|org\.jetbrains)\.compose\.material3\.(\w+)\s*$""").find(lines[i]) ?: continue
                val name = m.groupValues[1]
                if (name in GLASS) { lines[i] = "import $TO$name"; changes++ }
            }
            val star = lines.indexOfFirst { line ->
                val trimmed = line.trim()
                FROM_PREFIXES.any { trimmed == "import $it*" }
            }
            if (star >= 0) {
                val body = lines.filterNot { it.startsWith("import ") || it.startsWith("package ") }.joinToString("\n")
                val already = lines.filter { it.startsWith("import $TO") }.map { it.removePrefix("import $TO").trim() }.toSet()
                // Used as a call or a reference (`Card(`, `Card {`, `SegmentedButtonDefaults.`), and not the
                // file's own declaration of that name, which an explicit import would otherwise shadow.
                val used = GLASS.filter { name ->
                    name !in already &&
                        Regex("""(?<![\w.])$name\s*[({.<]""").containsMatchIn(body) &&
                        !Regex("""\b(fun|class|object|val|var|typealias|interface)\s+(<[^>]*>\s*)?$name\b""").containsMatchIn(body)
                }.sorted()
                if (used.isNotEmpty()) {
                    lines.addAll(star + 1, used.map { "import $TO$it" })
                    changes += used.size
                }
            }
            return Result(lines.joinToString("\n"), changes)
        }
    }
}
