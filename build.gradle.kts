import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.Test
import java.io.File

plugins {
    java
    jacoco
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

group = "top.ellan"
version = "0.6.2"

val kotlinRuntimeVersion = "2.3.20"
val kotlinSerializationVersion = "1.10.0"
val mahjongUtilsVersion = "0.7.7"
val mariadbVersion = "3.5.7"
val h2Version = "2.4.240"
val hikariVersion = "7.0.2"
val adventureVersion = "4.26.1"
val junitVersion = "6.0.3"
val generatedResourcesDir = layout.buildDirectory.dir("generated/resources/mahjong")
val generatedNativeResourcesDir = layout.buildDirectory.dir("generated/resources/native")

fun nativeLibraryFileName(osName: String): String {
    val normalized = osName.trim().lowercase()
    return when {
        "win" in normalized -> "mahjongpaper_gb.dll"
        "mac" in normalized || "darwin" in normalized -> "mahjongpaper_gb.dylib"
        else -> "mahjongpaper_gb.so"
    }
}

fun nativePlatformKey(osName: String, architecture: String): String {
    val normalizedOs = osName.trim().lowercase()
    val normalizedArch = architecture.trim().lowercase()
    val osKey = when {
        "win" in normalizedOs -> "windows"
        "mac" in normalizedOs || "darwin" in normalizedOs -> "macos"
        else -> "linux"
    }
    val archKey = when (normalizedArch) {
        "amd64", "x86_64", "x64" -> "x86_64"
        "aarch64", "arm64" -> "aarch64"
        "" -> "unknown"
        else -> normalizedArch
    }
    return "$osKey-$archKey"
}

fun findExecutable(executableName: String): String? {
    val candidates = linkedSetOf<String>()
    val isWindows = System.getProperty("os.name", "").contains("Windows", ignoreCase = true)
    val normalizedName = if (isWindows && !executableName.endsWith(".exe", ignoreCase = true)) {
        "$executableName.exe"
    } else {
        executableName
    }

    (System.getenv("PATH") ?: "")
        .split(File.pathSeparatorChar)
        .filter { it.isNotBlank() }
        .forEach { entry ->
            candidates += File(entry, normalizedName).absolutePath
        }

    val localAppData = System.getenv("LOCALAPPDATA")
    if (!localAppData.isNullOrBlank()) {
        val winGetPackagesDir = File(localAppData, "Microsoft/WinGet/Packages")
        if (winGetPackagesDir.isDirectory) {
            winGetPackagesDir.listFiles()?.forEach { packageDir ->
                packageDir.walkTopDown()
                    .maxDepth(4)
                    .filter { it.isFile && it.name.equals(normalizedName, ignoreCase = true) }
                    .forEach { candidates += it.absolutePath }
            }
        }
    }

    return candidates.firstOrNull { File(it).isFile }
}

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

fun writeMahjongTileResourceIndex(enumSource: File, itemsDir: File, modelsDir: File, texturesDir: File, outputFile: File) {
    val expectedEntries = parseMahjongTileNames(enumSource).toMutableSet().apply {
        add("back")
    }.sorted()

    val itemFiles = itemsDir.walkTopDown().filter { it.isFile && it.extension == "json" }.associateBy { it.nameWithoutExtension }
    val modelFiles = modelsDir.walkTopDown().filter { it.isFile && it.extension == "json" }.associateBy {
        it.nameWithoutExtension.removePrefix("mahjong_tile_")
    }
    val textureFiles = texturesDir.walkTopDown().filter { it.isFile && it.extension == "png" }.associateBy {
        it.nameWithoutExtension.removePrefix("mahjong_tile_")
    }

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
            throw GradleException("Model definition for $entry does not point at mahjongcraft:item/mahjong_tile/mahjong_tile_$entry")
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

fun formatTileLabel(name: String): String = name.split('_').joinToString(" ") { part ->
    part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

fun writeCraftEngineBundle(enumSource: File, resourcepackDir: File, attributionFile: File, outputDir: File) {
    val outputRoot = outputDir.resolve("craftengine").resolve("mahjongpaper")
    val outputResourcepackDir = outputRoot.resolve("resourcepack")
    val outputAssetsDir = outputResourcepackDir.resolve("assets")
    val outputConfigDir = outputRoot.resolve("configuration").resolve("items")
    val tileNames = parseMahjongTileNames(enumSource).toMutableSet().apply {
        add("back")
    }.sorted()

    outputRoot.deleteRecursively()
    outputAssetsDir.mkdirs()
    outputConfigDir.mkdirs()

    resourcepackDir.resolve("assets").copyRecursively(outputAssetsDir, overwrite = true)
    attributionFile.copyTo(outputRoot.resolve("ATTRIBUTION.md"), overwrite = true)

    outputRoot.resolve("pack.yml").writeText(
        """
        author: openai and ellan
        version: ${project.version}
        description: MahjongPaper CraftEngine assets
        namespace: mahjongpaper
        """.trimIndent() + "\n",
        Charsets.UTF_8
    )

    val itemConfig = buildString {
        appendLine("items:")
        appendLine("  mahjongpaper:table_visual_model:")
        appendLine("    material: paper")
        appendLine("    settings:")
        appendLine("      tags:")
        appendLine("        - mahjongpaper:table_visual")
        appendLine("    data:")
        appendLine("      item-name: <!i><gray>Mahjong Table Visual</gray>")
        appendLine("    item-model: mahjongcraft:table_visual")
        appendLine("  mahjongpaper:table_visual:")
        appendLine("    material: paper")
        appendLine("    settings:")
        appendLine("      tags:")
        appendLine("        - mahjongpaper:table_visual")
        appendLine("    data:")
        appendLine("      item-name: <!i><gray>Mahjong Table Visual Furniture</gray>")
        appendLine("    item-model: mahjongcraft:table_visual")
        appendLine("    behavior:")
        appendLine("      type: furniture_item")
        appendLine("      rules:")
        appendLine("        ground:")
        appendLine("          rotation: four")
        appendLine("          alignment: center")
        appendLine("      furniture:")
        appendLine("        settings:")
        appendLine("          item: mahjongpaper:table_visual")
        appendLine("          hit-times: 2147483647")
        appendLine("          sounds:")
        appendLine("            break: minecraft:block.wood.break")
        appendLine("            place: minecraft:block.wood.place")
        appendLine("            hit: minecraft:block.wood.hit")
        appendLine("        variants:")
        appendLine("          ground:")
        appendLine("            elements:")
        appendLine("              - item: mahjongpaper:table_visual_model")
        appendLine("                display-transform: none")
        appendLine("                billboard: fixed")
        appendLine("                position: 0,0,0")
        appendLine("                translation: 0,0,0")
        appendLine("                shadow-radius: 0")
        appendLine("                shadow-strength: 0")
        appendLine("            hitboxes:")
        listOf("-1,-1.5,-1", "-1,-1.5,0", "-1,-1.5,1", "0,-1.5,-1", "0,-1.5,0", "0,-1.5,1", "1,-1.5,-1", "1,-1.5,0", "1,-1.5,1").forEach { position ->
            appendLine("              - position: $position")
            appendLine("                type: shulker")
            appendLine("                direction: up")
            appendLine("                scale: 1")
            appendLine("                peek: 0")
            appendLine("                blocks-building: true")
            appendLine("                interactive: false")
            appendLine("                interaction-entity: false")
        }
        listOf(
            Triple("p100", "mahjongcraft:stick_p100", "<!i><red>100 Point Stick</red>"),
            Triple("p1000", "mahjongcraft:stick_p1000", "<!i><white>1000 Point Stick</white>"),
            Triple("p5000", "mahjongcraft:stick_p5000", "<!i><gold>5000 Point Stick</gold>"),
            Triple("p10000", "mahjongcraft:stick_p10000", "<!i><green>10000 Point Stick</green>")
        ).forEach { (suffix, model, displayName) ->
            appendLine("  mahjongpaper:$suffix:")
            appendLine("    material: paper")
            appendLine("    settings:")
            appendLine("      tags:")
            appendLine("        - mahjongpaper:stick_visual")
            appendLine("    data:")
            appendLine("      item-name: $displayName")
            appendLine("    item-model: $model")
            listOf(
                "stick_x_$suffix" to "0,0,0",
                "stick_z_$suffix" to "0,90,0"
            ).forEach { (furnitureId, rotation) ->
                appendLine("  mahjongpaper:$furnitureId:")
                appendLine("    material: paper")
                appendLine("    settings:")
                appendLine("      tags:")
                appendLine("        - mahjongpaper:stick_furniture")
                appendLine("    data:")
                appendLine("      item-name: $displayName")
                appendLine("    item-model: $model")
                appendLine("    behavior:")
                appendLine("      type: furniture_item")
                appendLine("      rules:")
                appendLine("        ground:")
                appendLine("          rotation: four")
                appendLine("          alignment: center")
                appendLine("      furniture:")
                appendLine("        settings:")
                appendLine("          item: mahjongpaper:$furnitureId")
                appendLine("          hit-times: 1")
                appendLine("        variants:")
                appendLine("          ground:")
                appendLine("            elements:")
                appendLine("              - item: mahjongpaper:$suffix")
                appendLine("                display-transform: none")
                appendLine("                billboard: fixed")
                appendLine("                position: 0,0,0")
                appendLine("                translation: 0,0,0")
                appendLine("                rotation: $rotation")
                appendLine("                shadow-radius: 0")
                appendLine("                shadow-strength: 0")
            }
        }
        appendLine("  mahjongpaper:table_hitbox:")
        appendLine("    material: paper")
        appendLine("    settings:")
        appendLine("      tags:")
        appendLine("        - mahjongpaper:table_hitbox")
        appendLine("    data:")
        appendLine("      item-name: <!i><gray>Mahjong Table Hitbox</gray>")
        appendLine("    item-model: mahjongcraft:mahjong_tile/back")
        appendLine("    behavior:")
        appendLine("      type: furniture_item")
        appendLine("      rules:")
        appendLine("        ground:")
        appendLine("          rotation: four")
        appendLine("          alignment: center")
        appendLine("      furniture:")
        appendLine("        settings:")
        appendLine("          item: mahjongpaper:table_hitbox")
        appendLine("          hit-times: 2147483647")
        appendLine("          sounds:")
        appendLine("            break: minecraft:block.wood.break")
        appendLine("            place: minecraft:block.wood.place")
        appendLine("            hit: minecraft:block.wood.hit")
        appendLine("        variants:")
        appendLine("          ground:")
        appendLine("            elements:")
        appendLine("              - item: mahjongpaper:back")
        appendLine("                display-transform: none")
        appendLine("                billboard: fixed")
        appendLine("                position: 0,0,0")
        appendLine("                translation: 0,-16,0")
        appendLine("                shadow-radius: 0")
        appendLine("                shadow-strength: 0")
        appendLine("            hitboxes:")
        listOf("-1,-1.5,-1", "-1,-1.5,0", "-1,-1.5,1", "0,-1.5,-1", "0,-1.5,0", "0,-1.5,1", "1,-1.5,-1", "1,-1.5,0", "1,-1.5,1").forEach { position ->
            appendLine("              - position: $position")
            appendLine("                type: shulker")
            appendLine("                direction: up")
            appendLine("                scale: 1")
            appendLine("                peek: 0")
            appendLine("                blocks-building: true")
            appendLine("                interactive: false")
            appendLine("                interaction-entity: false")
        }
        appendLine("  mahjongpaper:hand_tile_hitbox:")
        appendLine("    material: paper")
        appendLine("    settings:")
        appendLine("      tags:")
        appendLine("        - mahjongpaper:hand_tile_hitbox")
        appendLine("    data:")
        appendLine("      item-name: <!i><gray>Mahjong Hand Tile Hitbox</gray>")
        appendLine("    item-model: mahjongcraft:mahjong_tile/back")
        appendLine("    behavior:")
        appendLine("      type: furniture_item")
        appendLine("      rules:")
        appendLine("        ground:")
        appendLine("          rotation: four")
        appendLine("          alignment: center")
        appendLine("      furniture:")
        appendLine("        settings:")
        appendLine("          item: mahjongpaper:hand_tile_hitbox")
        appendLine("          hit-times: 1")
        appendLine("        variants:")
        appendLine("          ground:")
        appendLine("            elements:")
        appendLine("              - item: mahjongpaper:back")
        appendLine("                display-transform: none")
        appendLine("                billboard: fixed")
        appendLine("                position: 0,0,0")
        appendLine("                translation: 0,-16,0")
        appendLine("                shadow-radius: 0")
        appendLine("                shadow-strength: 0")
        appendLine("            hitboxes:")
        appendLine("              - type: interaction")
        appendLine("                position: 0,0,0")
        appendLine("                width: 0.1")
        appendLine("                height: 0.18")
        appendLine("                blocks-building: false")
        appendLine("                interactive: true")
        appendLine("                invisible: true")
        appendLine("  mahjongpaper:seat_chair_model:")
        appendLine("    material: paper")
        appendLine("    settings:")
        appendLine("      tags:")
        appendLine("        - mahjongpaper:seat_visual")
        appendLine("    data:")
        appendLine("      item-name: <!i><gray>Mahjong Seat Chair</gray>")
        appendLine("    item-model: mahjongcraft:seat_chair")
        appendLine("  mahjongpaper:seat_chair:")
        appendLine("    material: paper")
        appendLine("    settings:")
        appendLine("      tags:")
        appendLine("        - mahjongpaper:seat_visual")
        appendLine("    data:")
        appendLine("      item-name: <!i><gray>Mahjong Seat Chair Furniture</gray>")
        appendLine("    item-model: mahjongcraft:seat_chair")
        appendLine("    behavior:")
        appendLine("      type: furniture_item")
        appendLine("      rules:")
        appendLine("        ground:")
        appendLine("          rotation: four")
        appendLine("          alignment: center")
        appendLine("      furniture:")
        appendLine("        settings:")
        appendLine("          item: mahjongpaper:seat_chair")
        appendLine("          hit-times: 2147483647")
        appendLine("        variants:")
        appendLine("          ground:")
        appendLine("            elements:")
        appendLine("              - item: mahjongpaper:seat_chair_model")
        appendLine("                display-transform: none")
        appendLine("                billboard: fixed")
        appendLine("                position: 0,0,0")
        appendLine("                translation: 0,0,0")
        appendLine("                shadow-radius: 0")
        appendLine("                shadow-strength: 0")
        appendLine("            hitboxes:")
        appendLine("              - type: shulker")
        appendLine("                position: 0,-1.5,0")
        appendLine("                blocks-building: false")
        appendLine("                interactive: true")
        appendLine("                invisible: true")
        appendLine("                seats:")
        appendLine("                  - 0,-1.5,0")
        appendLine("  mahjongpaper:seat_hitbox:")
        appendLine("    material: paper")
        appendLine("    settings:")
        appendLine("      tags:")
        appendLine("        - mahjongpaper:seat_hitbox")
        appendLine("    data:")
        appendLine("      item-name: <!i><gray>Mahjong Seat Hitbox</gray>")
        appendLine("    item-model: mahjongcraft:mahjong_tile/back")
        appendLine("    behavior:")
        appendLine("      type: furniture_item")
        appendLine("      rules:")
        appendLine("        ground:")
        appendLine("          rotation: four")
        appendLine("          alignment: center")
        appendLine("      furniture:")
        appendLine("        settings:")
        appendLine("          item: mahjongpaper:seat_hitbox")
        appendLine("          hit-times: 2147483647")
        appendLine("        variants:")
        appendLine("          ground:")
        appendLine("            elements:")
        appendLine("              - item: mahjongpaper:back")
        appendLine("                display-transform: none")
        appendLine("                billboard: fixed")
        appendLine("                position: 0,0,0")
        appendLine("                translation: 0,-16,0")
        appendLine("                shadow-radius: 0")
        appendLine("                shadow-strength: 0")
        appendLine("            hitboxes:")
        appendLine("              - type: shulker")
        appendLine("                position: 0,-1.5,0")
        appendLine("                blocks-building: false")
        appendLine("                interactive: true")
        appendLine("                invisible: true")
        appendLine("                seats:")
        appendLine("                  - 0,-1.5,0")
        tileNames.forEach { tileName ->
            appendLine("  mahjongpaper:$tileName:")
            appendLine("    material: paper")
            appendLine("    settings:")
            appendLine("      tags:")
            appendLine("        - mahjongpaper:mahjong_tile")
            appendLine("    data:")
            appendLine("      item-name: <!i><white>${formatTileLabel(tileName)}</white>")
            appendLine("    item-model: mahjongcraft:mahjong_tile/$tileName")
        }
        listOf(
            Triple("tile_standing", "0,0,0", false),
            Triple("tile_standing_face_down", "0,180,0", true),
            Triple("tile_flat_face_up", "-90,0,0", false),
            Triple("tile_flat_face_down", "90,0,0", true)
        ).forEach { (prefix, rotation, faceDown) ->
            tileNames.forEach { tileName ->
                appendLine("  mahjongpaper:${prefix}_$tileName:")
                appendLine("    material: paper")
                appendLine("    settings:")
                appendLine("      tags:")
                appendLine("        - mahjongpaper:mahjong_tile_furniture")
                appendLine("    data:")
                appendLine("      item-name: <!i><white>${formatTileLabel(tileName)} ${prefix.replace('_', ' ')}</white>")
                appendLine("    item-model: mahjongcraft:mahjong_tile/${if (faceDown) "back" else tileName}")
                appendLine("    behavior:")
                appendLine("      type: furniture_item")
                appendLine("      rules:")
                appendLine("        ground:")
                appendLine("          rotation: four")
                appendLine("          alignment: center")
                appendLine("      furniture:")
                appendLine("        settings:")
                appendLine("          item: mahjongpaper:${prefix}_$tileName")
                appendLine("          hit-times: 1")
                appendLine("        variants:")
                appendLine("          ground:")
                appendLine("            elements:")
                appendLine("              - item: mahjongpaper:${if (faceDown) "back" else tileName}")
                appendLine("                display-transform: head")
                appendLine("                billboard: fixed")
                appendLine("                position: 0,0,0")
                appendLine("                translation: 0,0,0")
                appendLine("                rotation: $rotation")
                appendLine("                shadow-radius: 0")
                appendLine("                shadow-strength: 0")
            }
        }
    }
    outputConfigDir.resolve("mahjong_tiles.yml").writeText(itemConfig, Charsets.UTF_8)

    val bundleFiles = outputRoot.walkTopDown()
        .filter(File::isFile)
        .map { it.relativeTo(outputRoot).invariantSeparatorsPath }
        .sorted()
        .toList()
    outputRoot.resolve("_bundle_index.txt").writeText(bundleFiles.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://jitpack.io")
}

val generateMessageIndex = tasks.register("generateMessageIndex") {
    val inputDir = layout.projectDirectory.dir("src/main/resources").asFile
    val outputFile = generatedResourcesDir.map { it.file("i18n/_index.json") }.get().asFile
    inputs.dir(inputDir)
    outputs.file(outputFile)
    doLast {
        writeMessageIndex(inputDir, outputFile, "zh-CN")
    }
}

val verifyMahjongTileResources = tasks.register("verifyMahjongTileResources") {
    val enumSource = layout.projectDirectory.file("src/main/java/top/ellan/mahjong/model/MahjongTile.java").asFile
    val itemsDir = layout.projectDirectory.dir("resourcepack/assets/mahjongcraft/items/mahjong_tile").asFile
    val modelsDir = layout.projectDirectory.dir("resourcepack/assets/mahjongcraft/models/item/mahjong_tile").asFile
    val texturesDir = layout.projectDirectory.dir("resourcepack/assets/mahjongcraft/textures/item/mahjong_tile").asFile
    val outputFile = generatedResourcesDir.map { it.file("resourcepack/mahjong_tile_index.json") }.get().asFile
    inputs.file(enumSource)
    inputs.dir(itemsDir)
    inputs.dir(modelsDir)
    inputs.dir(texturesDir)
    outputs.file(outputFile)
    doLast {
        writeMahjongTileResourceIndex(enumSource, itemsDir, modelsDir, texturesDir, outputFile)
    }
}

val generateCraftEngineBundle = tasks.register("generateCraftEngineBundle") {
    val enumSource = layout.projectDirectory.file("src/main/java/top/ellan/mahjong/model/MahjongTile.java").asFile
    val resourcepackDir = layout.projectDirectory.dir("resourcepack").asFile
    val attributionFile = layout.projectDirectory.file("resourcepack/ATTRIBUTION.md").asFile
    val outputDir = generatedResourcesDir.get().asFile
    inputs.file(enumSource)
    inputs.dir(resourcepackDir)
    inputs.file(attributionFile)
    outputs.dir(outputDir.resolve("craftengine"))
    doLast {
        writeCraftEngineBundle(enumSource, resourcepackDir, attributionFile, outputDir)
    }
}

val gbNativeSourceDir = layout.projectDirectory.dir("native/gbmahjong")
val gbNativeBuildDir = layout.buildDirectory.dir("native/gbmahjong")
val gbNativeCmakeExecutable = findExecutable("cmake")
val gbNativeGxxExecutable = findExecutable("g++")
val gbNativeNinjaExecutable = findExecutable("ninja")
val gbNativeToolchainAvailable = gbNativeCmakeExecutable != null && gbNativeGxxExecutable != null
val gbNativeWindowsRuntimeDir = gbNativeGxxExecutable?.let { File(it).parentFile }
val gbNativeCurrentOsName = System.getProperty("os.name", "")
val gbNativeCurrentArch = System.getProperty("os.arch", "")
val gbNativePlatformKey = nativePlatformKey(gbNativeCurrentOsName, gbNativeCurrentArch)
val gbNativeLibraryFileName = nativeLibraryFileName(gbNativeCurrentOsName)

val configureGbMahjongNative = tasks.register<Exec>("configureGbMahjongNative") {
    group = "build"
    description = "Configure the JNI GB Mahjong native project with CMake."
    onlyIf { gbNativeToolchainAvailable }
    val sourceDir = gbNativeSourceDir.asFile
    val buildDir = gbNativeBuildDir.get().asFile
    inputs.dir(sourceDir)
    outputs.dir(buildDir)
    doFirst {
        buildDir.mkdirs()
    }
    val command = mutableListOf(
        gbNativeCmakeExecutable ?: "cmake",
        "-S", sourceDir.absolutePath,
        "-B", buildDir.absolutePath
    )
    if (gbNativeNinjaExecutable != null) {
        command += listOf("-G", "Ninja", "-DCMAKE_MAKE_PROGRAM=${gbNativeNinjaExecutable}")
    }
    if (gbNativeGxxExecutable != null) {
        command += listOf("-DCMAKE_CXX_COMPILER=${gbNativeGxxExecutable}")
    }
    commandLine(command)
}

val buildGbMahjongNative = tasks.register<Exec>("buildGbMahjongNative") {
    group = "build"
    description = "Build the JNI GB Mahjong native project with CMake."
    dependsOn(configureGbMahjongNative)
    onlyIf { gbNativeToolchainAvailable }
    val buildDir = gbNativeBuildDir.get().asFile
    inputs.dir(gbNativeSourceDir)
    outputs.dir(buildDir)
    commandLine(
        gbNativeCmakeExecutable ?: "cmake",
        "--build", buildDir.absolutePath,
        "--config", "Release"
    )
}

val packageGbMahjongNative = tasks.register("packageGbMahjongNative") {
    group = "build"
    description = "Copy built GB Mahjong native runtime files into generated resources."
    dependsOn(buildGbMahjongNative)
    onlyIf { gbNativeToolchainAvailable }
    val buildDir = gbNativeBuildDir.get().asFile
    val outputDir = generatedNativeResourcesDir.get().asFile.resolve("native/$gbNativePlatformKey")
    inputs.dir(buildDir)
    outputs.dir(outputDir)
    doLast {
        outputDir.mkdirs()
        val library = buildDir.resolve(gbNativeLibraryFileName)
        if (!library.isFile) {
            throw GradleException("Expected GB Mahjong native library at ${library.absolutePath}")
        }
        library.copyTo(outputDir.resolve(library.name), overwrite = true)

        if (gbNativePlatformKey == "windows-x86_64") {
            val winPthread = gbNativeWindowsRuntimeDir?.resolve("libwinpthread-1.dll")
            if (winPthread?.isFile == true) {
                winPthread.copyTo(outputDir.resolve(winPthread.name), overwrite = true)
            }
        }
    }
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    implementation("io.github.ssttkkl:mahjong-utils-jvm:$mahjongUtilsVersion")
    implementation("org.mariadb.jdbc:mariadb-java-client:$mariadbVersion")
    implementation("com.h2database:h2:$h2Version")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinRuntimeVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerializationVersion")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("net.kyori:adventure-api:$adventureVersion")
    testImplementation("net.kyori:adventure-text-minimessage:$adventureVersion")
    testImplementation("net.kyori:adventure-text-serializer-plain:$adventureVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

kotlin {
    jvmToolchain(21)
}

paperweight {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(21)
    }
    reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.REOBF_PRODUCTION
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
    }

    processResources {
        dependsOn(generateMessageIndex, verifyMahjongTileResources, generateCraftEngineBundle, packageGbMahjongNative)
        filteringCharset = Charsets.UTF_8.name()
        from(generatedResourcesDir)
        from(generatedNativeResourcesDir)
        filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
            expand(
                "version" to project.version,
                "mahjongUtilsVersion" to mahjongUtilsVersion,
                "mariadbVersion" to mariadbVersion,
                "h2Version" to h2Version,
                "hikariVersion" to hikariVersion,
                "kotlinRuntimeVersion" to kotlinRuntimeVersion,
                "kotlinSerializationVersion" to kotlinSerializationVersion
            )
        }
    }

    test {
        useJUnitPlatform {
            excludeTags("perf")
        }
        jvmArgs("-Dnet.bytebuddy.experimental=true")
        finalizedBy(jacocoTestReport)
    }

    register<Test>("perfTest") {
        group = "verification"
        description = "Runs performance benchmarks tagged with @Tag(\"perf\")."
        dependsOn(testClasses)
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        useJUnitPlatform {
            includeTags("perf")
        }
        jvmArgs("-Dnet.bytebuddy.experimental=true")
        systemProperty("mahjong.perf.warmupIterations", providers.gradleProperty("perfWarmups").orElse("5").get())
        systemProperty("mahjong.perf.measurementIterations", providers.gradleProperty("perfIterations").orElse("10").get())
        systemProperty("mahjong.perf.batchSize", providers.gradleProperty("perfBatchSize").orElse("200").get())
        systemProperty(
            "mahjong.perf.reportDir",
            layout.buildDirectory.dir("reports/performance").get().asFile.absolutePath
        )
        shouldRunAfter(test)
    }

    assemble {
        dependsOn(reobfJar)
    }

    jacocoTestReport {
        dependsOn(test)
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
    }

    check {
        dependsOn(jacocoTestReport)
        dependsOn(verifyMahjongTileResources)
        dependsOn(generateCraftEngineBundle)
    }
}
