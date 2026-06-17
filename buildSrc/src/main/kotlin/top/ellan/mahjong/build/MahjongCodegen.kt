package top.ellan.mahjong.build

import org.gradle.api.GradleException
import java.io.File
import java.util.Properties

/**
 * Build-time code generation utilities used by the project's main build.gradle.kts.
 *
 * Hosting these helpers in buildSrc gives them three concrete benefits:
 *  - the IDE provides full Kotlin completion and refactor support, which is
 *    awkward when the same code lives at the top of build.gradle.kts;
 *  - the helpers can be unit tested directly via the buildSrc test target;
 *  - extracting them keeps build.gradle.kts focused on task wiring rather
 *    than embedding ~200 lines of generator logic.
 *
 * Each function is intentionally self-contained: it takes plain File / String
 * inputs, performs IO when asked to, and either returns a value or throws a
 * GradleException. There is no Gradle Project state captured here.
 */
object MahjongCodegen {
    /**
     * Encodes [value] as a JSON string literal (including surrounding quotes).
     * Matches the subset of escapes the build emits: backslash, double quote,
     * and the common control characters. Anything else is passed through
     * verbatim, which is fine because the inputs we feed it are restricted to
     * locale tags, file paths, and tile names.
     */
    fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }

    /** Reads [file] as a UTF-8 [Properties] file and returns it as an immutable map. */
    fun loadUtf8Properties(file: File): Map<String, String> {
        val properties = Properties()
        file.reader(Charsets.UTF_8).use { reader ->
            properties.load(reader)
        }
        return properties.stringPropertyNames().associateWith { properties.getProperty(it) }
    }

    /**
     * Replaces `{{token}}` occurrences in [template] with the matching value
     * from [tokens]. Throws if the template references any token that is not
     * supplied; a missing key in production builds nearly always means a
     * forgotten translation entry, so failing fast surfaces that earlier than
     * shipping a half-rendered config to users.
     */
    fun renderTemplateWithTokens(template: String, tokens: Map<String, String>): String {
        val pattern = Regex("""\{\{([a-zA-Z0-9_.-]+)}}""")
        val missing = linkedSetOf<String>()
        val rendered = pattern.replace(template) { match ->
            val key = match.groupValues[1]
            val value = tokens[key]
            if (value == null) {
                missing += key
                match.value
            } else {
                value
            }
        }
        if (missing.isNotEmpty()) {
            throw GradleException("Missing config template tokens: ${missing.joinToString(", ")}")
        }
        return rendered
    }

    /**
     * Parses the `MahjongTile` Java enum body and returns the lowercase tile
     * names it declares. The parser is deliberately small (substring + split)
     * because the enum has no annotations, comments, or trailing parens; if
     * those ever appear, the assertions in [writeMahjongTileResourceIndex]
     * will surface the mismatch quickly.
     */
    fun parseMahjongTileNames(sourceFile: File): List<String> {
        val enumBody = sourceFile.readText(Charsets.UTF_8)
            .substringAfter("public enum MahjongTile {")
            .substringBefore(";")

        return enumBody
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.substringBefore('\n').trim() }
            .map { it.lowercase() }
    }

    /**
     * Writes the i18n bundle index used at runtime by `MessageService` to
     * discover localized resources. The English bundle is forced to the front
     * because plugin code treats it as the fallback locale.
     */
    fun writeMessageIndex(inputDir: File, outputFile: File, defaultLocale: String) {
        val languageDir = inputDir.resolve("language")
        val bundleFiles = languageDir.walkTopDown()
            .filter { it.isFile && it.name.matches(Regex("""messages.*\.properties""")) }
            .sortedBy { it.name }
            .toList()
        val bundles = linkedMapOf<String, String>()
        if (bundleFiles.none { it.name == "messages.properties" }) {
            throw GradleException("Expected src/main/resources/language/messages.properties to exist.")
        }
        bundles["en"] = "language/messages.properties"
        bundleFiles
            .filter { it.name != "messages.properties" }
            .forEach { file ->
                val localeTag = file.name.removePrefix("messages_").removeSuffix(".properties").replace('_', '-')
                bundles[localeTag] = "language/" + file.name
            }
        if (!bundles.containsKey(defaultLocale)) {
            throw GradleException("Default locale $defaultLocale is missing from generated message bundles.")
        }

        val json = buildString {
            appendLine("{")
            appendLine("  \"defaultLocale\": ${jsonString(defaultLocale)},")
            appendLine("  \"bundles\": {")
            bundles.entries.forEachIndexed { index, entry ->
                val suffix = if (index + 1 == bundles.size) "" else ","
                appendLine("    ${jsonString(entry.key)}: ${jsonString(entry.value)}$suffix")
            }
            appendLine("  }")
            appendLine("}")
        }

        outputFile.parentFile.mkdirs()
        outputFile.writeText(json, Charsets.UTF_8)
    }

    /**
     * Cross-checks that the resourcepack ships an items definition, model, and
     * texture for every tile declared in [enumSource], plus the implicit
     * "back" entry, then writes a stable index of those entries to
     * [outputFile]. Any inconsistency throws a [GradleException] listing every
     * missing or misaligned file - this catches resource desync at build time
     * rather than at the first time a player tries to render that tile.
     */
    fun writeMahjongTileResourceIndex(
        enumSource: File,
        itemsDir: File,
        modelsDir: File,
        texturesDir: File,
        outputFile: File
    ) {
        val expectedEntries = parseMahjongTileNames(enumSource).toMutableSet().apply {
            add("back")
        }.sorted()

        val itemFiles = itemsDir.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .associateBy { it.nameWithoutExtension }
        val modelFiles = modelsDir.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .associateBy { it.nameWithoutExtension.removePrefix("mahjong_tile_") }
        val textureFiles = texturesDir.walkTopDown()
            .filter { it.isFile && it.extension == "png" }
            .associateBy { it.nameWithoutExtension.removePrefix("mahjong_tile_") }

        val missingItems = expectedEntries.filterNot(itemFiles.keys::contains)
        val missingModels = expectedEntries.filterNot(modelFiles.keys::contains)
        val missingTextures = expectedEntries.filterNot(textureFiles.keys::contains)
        if (missingItems.isNotEmpty() || missingModels.isNotEmpty() || missingTextures.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("Mahjong tile resources are out of sync.")
                    if (missingItems.isNotEmpty()) append(" missing items=$missingItems")
                    if (missingModels.isNotEmpty()) append(" missing models=$missingModels")
                    if (missingTextures.isNotEmpty()) append(" missing textures=$missingTextures")
                }
            )
        }

        expectedEntries.forEach { entry ->
            val itemText = itemFiles.getValue(entry).readText(Charsets.UTF_8)
            val modelText = modelFiles.getValue(entry).readText(Charsets.UTF_8)
            val expectedModelPath = "mahjongcraft:item/mahjong_tile/mahjong_tile_$entry"
            if (!itemText.contains(expectedModelPath)) {
                throw GradleException("Item definition for $entry does not point at $expectedModelPath")
            }
            if (!modelText.contains("\"0\": \"mahjongcraft:item/mahjong_tile/mahjong_tile_$entry\"")) {
                throw GradleException(
                    "Model definition for $entry does not point at mahjongcraft:item/mahjong_tile/mahjong_tile_$entry"
                )
            }
        }

        val json = buildString {
            appendLine("{")
            appendLine("  \"namespace\": \"mahjongcraft\",")
            appendLine("  \"kind\": \"mahjong_tile\",")
            appendLine("  \"entries\": [")
            expectedEntries.forEachIndexed { index, entry ->
                val suffix = if (index + 1 == expectedEntries.size) "" else ","
                appendLine(
                    "    {\"name\": ${jsonString(entry)}, \"item\": ${jsonString("mahjongcraft:mahjong_tile/$entry")}, \"model\": ${jsonString("mahjongcraft:item/mahjong_tile/mahjong_tile_$entry")}, \"texture\": ${jsonString("mahjongcraft:item/mahjong_tile/mahjong_tile_$entry")}}$suffix"
                )
            }
            appendLine("  ]")
            appendLine("}")
        }

        outputFile.parentFile.mkdirs()
        outputFile.writeText(json, Charsets.UTF_8)
    }
}
