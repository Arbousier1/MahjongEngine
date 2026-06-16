package top.ellan.mahjong.build

import java.io.File

object NativeBuildSupport {
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
}
