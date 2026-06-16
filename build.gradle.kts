import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.Test
import top.ellan.mahjong.build.CraftEngineBundleGenerator
import top.ellan.mahjong.build.MahjongCodegen
import top.ellan.mahjong.build.NativeBuildSupport
import java.io.File

plugins {
    java
    jacoco
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    id("io.papermc.paperweight.userdev") version "2.0.0-SNAPSHOT"
}

group = "top.ellan"
version = "1.1.0"

val minimumPaperDevBundleVersion = "1.20.1-R0.1-SNAPSHOT"
val latestPaperDevBundleVersion = "26.2-rc-2.build.9-alpha"
val supportedPaperApiVersion = "1.20"
val paperDevBundleVersion = providers.gradleProperty("mahjongPaperDevBundle")
    .orElse(minimumPaperDevBundleVersion)
    .get()
// Keep plugin descriptors pinned to the lowest supported Paper API so one jar can load on newer Paper versions.
val paperApiVersion = supportedPaperApiVersion
val javaTargetVersion = providers.gradleProperty("mahjongJavaTarget")
    .map(String::toInt)
    .orElse(17)
    .get()
val toolchainJavaVersion = providers.gradleProperty("mahjongJavaToolchain")
    .map(String::toInt)
    .orElse(if (Runtime.version().feature() >= javaTargetVersion) Runtime.version().feature() else javaTargetVersion)
val kotlinRuntimeVersion = "2.4.0"
val kotlinSerializationVersion = "1.11.0"
val mahjongUtilsVersion = "0.7.7"
val mariadbVersion = "3.5.9"
val mysqlVersion = "9.7.0"
val h2Version = "2.4.240"
val hikariVersion = "7.1.0"
// Keep explicit Adventure APIs on the Paper 1.20.1 line so the jar stays runtime-compatible with older servers.
val adventureVersion = "4.14.0"
val junitVersion = "6.1.0"
val testcontainersVersion = "1.21.4"
val generatedResourcesDir = layout.buildDirectory.dir("generated/resources/mahjong")
val generatedNativeResourcesDir = layout.buildDirectory.dir("generated/resources/native")

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
        MahjongCodegen.writeMessageIndex(inputDir, outputFile, "zh-CN")
    }
}

val generateLocalizedConfigs = tasks.register("generateLocalizedConfigs") {
    val templateFile = layout.projectDirectory.file("src/main/config-template/config.template.yml").asFile
    val translations = linkedMapOf(
        "config.yml" to layout.projectDirectory.file("src/main/config-template/config_comments_en.properties").asFile,
        "config_zh_CN.yml" to layout.projectDirectory.file("src/main/config-template/config_comments_zh_CN.properties").asFile,
        "config_zh_TW.yml" to layout.projectDirectory.file("src/main/config-template/config_comments_zh_TW.properties").asFile
    )
    val outputDir = generatedResourcesDir.get().asFile

    inputs.file(templateFile)
    translations.values.forEach { inputs.file(it) }
    outputs.files(translations.keys.map { outputDir.resolve(it) })

    doLast {
        outputDir.mkdirs()
        val template = templateFile.readText(Charsets.UTF_8)
        translations.forEach { (targetFileName, translationFile) ->
            val rendered = MahjongCodegen.renderTemplateWithTokens(template, MahjongCodegen.loadUtf8Properties(translationFile))
            outputDir.resolve(targetFileName).writeText(rendered, Charsets.UTF_8)
        }
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
        MahjongCodegen.writeMahjongTileResourceIndex(enumSource, itemsDir, modelsDir, texturesDir, outputFile)
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
        CraftEngineBundleGenerator.writeCraftEngineBundle(enumSource, resourcepackDir, attributionFile, outputDir, project.version.toString())
    }
}

val gbNativeSourceDir = layout.projectDirectory.dir("native/gbmahjong")
val gbNativeBuildDir = layout.buildDirectory.dir("native/gbmahjong")
val gbNativeCmakeExecutable = NativeBuildSupport.findExecutable("cmake")
val gbNativeGxxExecutable = NativeBuildSupport.findExecutable("g++")
val gbNativeNinjaExecutable = NativeBuildSupport.findExecutable("ninja")
val gbNativeToolchainAvailable = gbNativeCmakeExecutable != null && gbNativeGxxExecutable != null
val gbNativeWindowsRuntimeDir = gbNativeGxxExecutable?.let { File(it).parentFile }
val gbNativeCurrentOsName = System.getProperty("os.name", "")
val gbNativeCurrentArch = System.getProperty("os.arch", "")
val gbNativePlatformKey = NativeBuildSupport.nativePlatformKey(gbNativeCurrentOsName, gbNativeCurrentArch)
val gbNativeLibraryFileName = NativeBuildSupport.nativeLibraryFileName(gbNativeCurrentOsName)

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
    paperweight.paperDevBundle(paperDevBundleVersion)
    compileOnly(platform("net.kyori:adventure-bom:$adventureVersion"))
    compileOnly("net.kyori:adventure-api")
    compileOnly("net.kyori:adventure-text-minimessage")
    compileOnly("net.kyori:adventure-text-serializer-plain")
    implementation("io.github.ssttkkl:mahjong-utils-jvm:$mahjongUtilsVersion")
    implementation("org.mariadb.jdbc:mariadb-java-client:$mariadbVersion")
    implementation("com.mysql:mysql-connector-j:$mysqlVersion")
    implementation("com.h2database:h2:$h2Version")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinRuntimeVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerializationVersion")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testImplementation("org.testcontainers:mariadb:$testcontainersVersion")
    // Tests follow whichever Adventure version the active paperDevBundle ships with so that
    // paper-api's compiled-against interfaces (e.g. ComponentDecoder added in Adventure 4.16) resolve
    // at runtime. Production code is still constrained to the older surface via the compileOnly BOM above.
    testImplementation("net.kyori:adventure-api")
    testImplementation("net.kyori:adventure-text-minimessage")
    testImplementation("net.kyori:adventure-text-serializer-plain")
}

java {
    sourceCompatibility = JavaVersion.toVersion(javaTargetVersion)
    targetCompatibility = JavaVersion.toVersion(javaTargetVersion)
    toolchain.languageVersion.set(toolchainJavaVersion.map(JavaLanguageVersion::of))
    withSourcesJar()
}

kotlin {
    jvmToolchain(toolchainJavaVersion.get())
}

paperweight {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion.set(toolchainJavaVersion.map(JavaLanguageVersion::of))
    }
    reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(javaTargetVersion.toString()))
        }
    }

    withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(javaTargetVersion)
    }

    processResources {
        dependsOn(generateMessageIndex, generateLocalizedConfigs, verifyMahjongTileResources, generateCraftEngineBundle, packageGbMahjongNative)
        filteringCharset = Charsets.UTF_8.name()
        inputs.property("pluginVersion", project.version.toString())
        inputs.property("paperApiVersion", paperApiVersion)
        inputs.property("mahjongUtilsVersion", mahjongUtilsVersion)
        inputs.property("mariadbVersion", mariadbVersion)
        inputs.property("h2Version", h2Version)
        inputs.property("hikariVersion", hikariVersion)
        inputs.property("kotlinRuntimeVersion", kotlinRuntimeVersion)
        inputs.property("kotlinSerializationVersion", kotlinSerializationVersion)
        from(generatedResourcesDir)
        from(generatedNativeResourcesDir)
        filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
            expand(
                "version" to project.version,
                "paperApiVersion" to paperApiVersion,
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
        systemProperty("mahjong.test.expectedClassfileMajor", javaTargetVersion + 44)
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
