import org.gradle.api.GradleException
import java.io.File

plugins {
    java
    jacoco
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
}

group = "doublemoon.mahjongcraft"
version = "0.1.0-SNAPSHOT"

val kotlinRuntimeVersion = "2.2.0"
val kotlinSerializationVersion = "1.9.0"
val packetEventsVersion = "2.11.2"
val mahjongUtilsVersion = "0.7.7"
val mariadbVersion = "3.5.3"
val h2Version = "2.3.232"
val hikariVersion = "6.3.0"
val adventureVersion = "4.17.0"
val junitVersion = "5.12.2"
val generatedResourcesDir = layout.buildDirectory.dir("generated/resources/mahjong")

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
    val bundleFiles = inputDir.walkTopDown().filter { it.isFile && it.name.matches(Regex("""messages.*\.properties""")) }.sortedBy { it.name }.toList()
    val bundles = linkedMapOf<String, String>()
    if (bundleFiles.none { it.name == "messages.properties" }) {
        throw GradleException("Expected src/main/resources/messages.properties to exist.")
    }
    bundles["en"] = "messages.properties"
    bundleFiles
        .filter { it.name != "messages.properties" }
        .forEach { file ->
            val localeTag = file.name.removePrefix("messages_").removeSuffix(".properties").replace('_', '-')
            bundles[localeTag] = file.name
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
        author: OpenAI
        version: ${project.version}
        description: MahjongPaper CraftEngine assets
        namespace: mahjongpaper
        """.trimIndent() + "\n",
        Charsets.UTF_8
    )

    val itemConfig = buildString {
        appendLine("items:")
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
    val enumSource = layout.projectDirectory.file("src/main/java/doublemoon/mahjongcraft/paper/model/MahjongTile.java").asFile
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
    val enumSource = layout.projectDirectory.file("src/main/java/doublemoon/mahjongcraft/paper/model/MahjongTile.java").asFile
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

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.retrooper:packetevents-spigot:$packetEventsVersion")
    implementation("io.github.ssttkkl:mahjong-utils-jvm:$mahjongUtilsVersion")
    implementation("org.mariadb.jdbc:mariadb-java-client:$mariadbVersion")
    implementation("com.h2database:h2:$h2Version")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinRuntimeVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerializationVersion")
    testCompileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testRuntimeOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("net.kyori:adventure-api:$adventureVersion")
    testImplementation("net.kyori:adventure-text-minimessage:$adventureVersion")
    testImplementation("net.kyori:adventure-text-serializer-plain:$adventureVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
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
        dependsOn(generateMessageIndex, verifyMahjongTileResources, generateCraftEngineBundle)
        filteringCharset = Charsets.UTF_8.name()
        from(generatedResourcesDir)
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
        useJUnitPlatform()
        finalizedBy(jacocoTestReport)
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
