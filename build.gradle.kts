plugins {
    `java`
    id("com.github.johnrengelman.shadow") version "8.1.1"
    kotlin("jvm") version "1.9.22" 
}

group = "top.ellan"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io") // 用于获取 mahjong-utils 和 CraftEngine (假设在jitpack)
    // 如果 CraftEngine 有私有仓库，请在这里添加，例如:
    maven("https://repo.momirealms.net/releases/") 
}

dependencies {
    // 1. Paper API 1.21.1
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    // 2. Mahjong Utils (麻将算法库)
    implementation("io.github.ssttkkl:mahjong-utils:0.7.7")
    implementation(kotlin("stdlib")) // 必须引入 Kotlin 标准库

    // 3. CraftEngine API
    compileOnly("net.momirealms:craft-engine-core:0.0.67")
    compileOnly("net.momirealms:craft-engine-bukkit:0.0.67")
}

// 配置 Java 21 (MC 1.21 必需)
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        archiveClassifier.set("")
        // 重定位 Kotlin 和 麻将库，防止冲突
        relocate("kotlin", "top.ellan.mahjongengine.libs.kotlin")
        relocate("com.github.entree.mahjongUtils", "top.ellan.mahjongengine.libs.mahjongutils")
    }

    build {
        dependsOn(shadowJar)
    }
}