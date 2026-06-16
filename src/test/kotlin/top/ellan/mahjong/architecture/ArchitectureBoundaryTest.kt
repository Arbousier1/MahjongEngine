package top.ellan.mahjong.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class ArchitectureBoundaryTest {
    @Test
    fun `rules packages stay free of platform and presentation dependencies`() {
        val violations = sourceFiles(
            "src/main/java/top/ellan/mahjong/riichi",
            "src/main/kotlin/top/ellan/mahjong/riichi",
            "src/main/java/top/ellan/mahjong/gb",
            "src/main/kotlin/top/ellan/mahjong/gb"
        ).flatMap { file ->
            Files.readString(file).lineSequence()
                .withIndex()
                .filter { (_, line) -> forbiddenRuleImports.any(line::startsWith) }
                .map { (index, line) -> "${projectRoot.relativize(file)}:${index + 1}: $line" }
                .toList()
        }

        assertTrue(
            violations.isEmpty(),
            "Rules packages should not import platform or presentation layers:\n${violations.joinToString("\n")}"
        )
    }

    @Test
    fun `render package stays free of table and bootstrap dependencies`() {
        val violations = sourceFiles(
            "src/main/java/top/ellan/mahjong/render",
            "src/main/kotlin/top/ellan/mahjong/render"
        ).flatMap { file ->
            Files.readString(file).lineSequence()
                .withIndex()
                .filter { (_, line) -> forbiddenRenderReferences.any(line::contains) }
                .map { (index, line) -> "${projectRoot.relativize(file)}:${index + 1}: $line" }
                .toList()
        }

        assertTrue(
            violations.isEmpty(),
            "Render package should not depend on table internals or bootstrap services:\n${violations.joinToString("\n")}"
        )
    }

    @Test
    fun `ui package only imports the public table surface`() {
        // The ui package legitimately reads tables to render UI, but should
        // not reach into the table package internals (Session*Coordinator,
        // TableSessionContext, TableSessionMutator, ...). Add such a class to
        // the allowlist only after deliberate API design.
        val violations = sourceFiles(
            "src/main/java/top/ellan/mahjong/ui",
            "src/main/kotlin/top/ellan/mahjong/ui"
        ).flatMap { file ->
            Files.readString(file).lineSequence()
                .withIndex()
                .filter { (_, line) ->
                    line.startsWith("import top.ellan.mahjong.table.")
                        && allowedUiTableImports.none { line.startsWith(it) }
                }
                .map { (index, line) -> "${projectRoot.relativize(file)}:${index + 1}: $line" }
                .toList()
        }

        assertTrue(
            violations.isEmpty(),
            "ui -> table imports must use the public table surface only; see allowedUiTableImports.\n${violations.joinToString("\n")}"
        )
    }

    @Test
    fun `table package does not depend on bootstrap`() {
        val violations = sourceFiles(
            "src/main/java/top/ellan/mahjong/table",
            "src/main/kotlin/top/ellan/mahjong/table"
        ).flatMap { file ->
            Files.readString(file).lineSequence()
                .withIndex()
                .filter { (_, line) -> line.startsWith("import top.ellan.mahjong.bootstrap.") }
                .map { (index, line) -> "${projectRoot.relativize(file)}:${index + 1}: $line" }
                .toList()
        }

        assertTrue(
            violations.isEmpty(),
            "table -> bootstrap imports must go through TableRuntimeServices.\n${violations.joinToString("\n")}"
        )
    }

    @Test
    fun `bootstrap package does not import command, render, or ui internals`() {
        // Bootstrap is allowed to wire up table.core, but should not reach
        // into command subcommand classes, render snapshot internals, or ui
        // inventory internals -- those are platform glue that table or
        // command surface owns.
        val violations = sourceFiles(
            "src/main/java/top/ellan/mahjong/bootstrap",
            "src/main/kotlin/top/ellan/mahjong/bootstrap"
        ).flatMap { file ->
            Files.readString(file).lineSequence()
                .withIndex()
                .filter { (_, line) ->
                    line.startsWith("import top.ellan.mahjong.command.subcommand.")
                        || line.startsWith("import top.ellan.mahjong.render.snapshot.")
                        || line.startsWith("import top.ellan.mahjong.render.layout.")
                        || line.startsWith("import top.ellan.mahjong.render.scene.")
                        || line.startsWith("import top.ellan.mahjong.ui.")
                }
                .map { (index, line) -> "${projectRoot.relativize(file)}:${index + 1}: $line" }
                .toList()
        }

        assertTrue(
            violations.isEmpty(),
            "bootstrap should only import top-level command/render packages and table.core, not their internals.\n${violations.joinToString("\n")}"
        )
    }

    @Test
    fun `production kotlin stays in rules and native model packages`() {
        val violations = sourceFiles("src/main/kotlin")
            .filter { file ->
                allowedProductionKotlinRoots.none { root ->
                    file.normalize().startsWith(root)
                }
            }
            .map { projectRoot.relativize(it) }

        assertTrue(
            violations.isEmpty(),
            "Production Kotlin should stay in rule or serialization-model packages; update CONTRIBUTING.md and this allowlist for deliberate new roots:\n${violations.joinToString("\n")}"
        )
    }

    @Test
    fun `known large orchestration files stay under size budgets`() {
        val violations = lineBudgets.mapNotNull { budget ->
            val file = projectRoot.resolve(budget.path)
            val lineCount = Files.readAllLines(file).size
            if (lineCount <= budget.maxLines) {
                null
            } else {
                "${budget.path}: $lineCount lines exceeds budget ${budget.maxLines}"
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Large files should shrink or stay flat; raise a budget only with an accompanying split plan:\n${violations.joinToString("\n")}"
        )
    }

    @Test
    fun `known broad public surfaces stay under method budgets`() {
        val violations = publicMethodBudgets.mapNotNull { budget ->
            val file = projectRoot.resolve(budget.path)
            val methodCount = Files.readString(file).lineSequence()
                .count { line -> publicMemberPattern.matches(line) }
            if (methodCount <= budget.maxPublicMembers) {
                null
            } else {
                "${budget.path}: $methodCount public members exceeds budget ${budget.maxPublicMembers}"
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Broad public surfaces should shrink or stay flat; prefer narrow interfaces before adding more public members:\n${violations.joinToString("\n")}"
        )
    }

    @Test
    fun `World spawn callers do not use the removed Consumer overload`() {
        // Paper 1.21+ removed the deprecated org.bukkit.util.Consumer overload of
        // World.spawn(Location, Class, Consumer). Lambdas compiled against the 1.20
        // dev bundle were silently bound to that removed overload, breaking every
        // render tick on 1.21+ servers (see commit history around v1.1.1). To prevent
        // a regression, reject any call site that supplies a third argument to
        // World.spawn(Location, Class, ...). The portable two-argument form is fine.
        val pattern = Regex("""\.spawn\s*\(\s*[^)]*\.class\s*,\s*[A-Za-z_]""")
        val violations = mutableListOf<String>()
        sourceFiles(
            "src/main/java",
            "src/main/kotlin"
        ).forEach { file ->
            val text = Files.readString(file)
            // Slice line by line so the violation report points at the offending site.
            text.lineSequence().forEachIndexed { index, line ->
                if (pattern.containsMatchIn(line)) {
                    violations += "${projectRoot.relativize(file)}:${index + 1}: $line"
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Use World.spawn(Location, Class) and configure the entity afterwards; the Consumer overload was removed in Paper 1.21:\n${violations.joinToString("\n")}"
        )
    }

    private companion object {
        data class LineBudget(val path: String, val maxLines: Int)
        data class PublicMethodBudget(val path: String, val maxPublicMembers: Int)

        val projectRoot: Path = Path.of("").toAbsolutePath()
        val forbiddenRuleImports = listOf(
            "import org.bukkit.",
            "import io.papermc.",
            "import net.kyori.",
            "import top.ellan.mahjong.bootstrap.",
            "import top.ellan.mahjong.command.",
            "import top.ellan.mahjong.compat.",
            "import top.ellan.mahjong.config.",
            "import top.ellan.mahjong.db.",
            "import top.ellan.mahjong.i18n.",
            "import top.ellan.mahjong.metrics.",
            "import top.ellan.mahjong.render.",
            "import top.ellan.mahjong.runtime.",
            "import top.ellan.mahjong.ui."
        )
        val forbiddenRenderReferences = listOf(
            "top.ellan.mahjong.table.",
            "top.ellan.mahjong.bootstrap."
        )

        /**
         * Imports the ui/ package is allowed to take from the table/ package.
         * Anything outside this list is expected to break out into a render
         * snapshot, an i18n key, or a new ui-facing API on MahjongTableSession
         * rather than directly reaching for table internals.
         */
        val allowedUiTableImports = listOf(
            "import top.ellan.mahjong.table.core.MahjongTableManager",
            "import top.ellan.mahjong.table.core.MahjongTableSession",
            "import top.ellan.mahjong.table.core.TableFinalStanding"
        )
        val allowedProductionKotlinRoots = listOf(
            "src/main/kotlin/top/ellan/mahjong/riichi",
            "src/main/kotlin/top/ellan/mahjong/gb/jni"
        ).map { projectRoot.resolve(it).normalize() }
        val lineBudgets = listOf(
            LineBudget("build.gradle.kts", 350),
            LineBudget("src/main/java/top/ellan/mahjong/table/core/MahjongTableSession.java", 1660),
            LineBudget("src/main/java/top/ellan/mahjong/table/core/MahjongTableManager.java", 1050),
            LineBudget("src/main/java/top/ellan/mahjong/render/scene/TableRenderer.java", 2150)
        )
        val publicMethodBudgets = listOf(
            PublicMethodBudget("src/main/java/top/ellan/mahjong/table/core/MahjongTableSession.java", 235),
            PublicMethodBudget("src/main/java/top/ellan/mahjong/table/core/MahjongTableManager.java", 50),
            PublicMethodBudget("src/main/java/top/ellan/mahjong/render/scene/TableRenderer.java", 40)
        )
        val publicMemberPattern = Regex("""^\s+public (?!class|interface|enum|record).*""")

        fun sourceFiles(vararg roots: String): List<Path> = roots.flatMap { root ->
            val rootPath = projectRoot.resolve(root)
            if (!Files.exists(rootPath)) {
                return@flatMap emptyList()
            }
            Files.walk(rootPath).use { stream ->
                stream.filter { file ->
                    Files.isRegularFile(file) && (file.toString().endsWith(".java") || file.toString().endsWith(".kt"))
                }.sorted().toList()
            }
        }
    }
}
