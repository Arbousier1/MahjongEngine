package doublemoon.mahjongcraft.paper.gb.jni;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class GbMahjongNativeLibrary {
    static final String DEFAULT_LIBRARY_NAME = "mahjongpaper_gb";
    static final String PATH_PROPERTY = "mahjongpaper.gb.native.path";

    private static volatile LoadState state;

    private GbMahjongNativeLibrary() {
    }

    static LoadState ensureLoaded() {
        LoadState current = state;
        if (current != null) {
            return current;
        }
        synchronized (GbMahjongNativeLibrary.class) {
            current = state;
            if (current != null) {
                return current;
            }
            state = current = loadLibrary();
            return current;
        }
    }

    static String platformLibraryFileName(String osName) {
        String normalized = osName == null ? "" : osName.trim().toLowerCase();
        if (normalized.contains("win")) {
            return DEFAULT_LIBRARY_NAME + ".dll";
        }
        if (normalized.contains("mac") || normalized.contains("darwin")) {
            return DEFAULT_LIBRARY_NAME + ".dylib";
        }
        return DEFAULT_LIBRARY_NAME + ".so";
    }

    private static LoadState loadLibrary() {
        String osName = System.getProperty("os.name", "");
        String expectedFile = platformLibraryFileName(osName);
        String platformKey = platformResourceKey(osName, System.getProperty("os.arch", ""));
        String configuredPath = System.getProperty(PATH_PROPERTY, "").trim();
        try {
            if (!configuredPath.isEmpty()) {
                System.load(configuredPath);
                return new LoadState(true, "loaded from " + configuredPath);
            }
            Path bundledLibrary = extractBundledLibrary(platformKey, expectedFile);
            if (bundledLibrary != null) {
                loadLibraryWithDependencies(bundledLibrary, bundledDependencyFileNames(platformKey));
                return new LoadState(true, "loaded bundled native library from " + bundledLibrary);
            }
            for (Path candidate : developmentLibraryCandidates(expectedFile)) {
                if (!Files.isRegularFile(candidate)) {
                    continue;
                }
                loadLibraryWithDependencies(candidate, bundledDependencyFileNames(platformKey));
                return new LoadState(true, "loaded development native library from " + candidate);
            }
            System.loadLibrary(DEFAULT_LIBRARY_NAME);
            return new LoadState(true, "loaded from java.library.path");
        } catch (UnsatisfiedLinkError | SecurityException exception) {
            String source = configuredPath.isEmpty()
                ? "java.library.path"
                : configuredPath;
            return new LoadState(
                false,
                "GB Mahjong native library unavailable (" + expectedFile + " via " + source + "): " + exception.getMessage()
            );
        }
    }

    static String platformResourceKey(String osName, String architecture) {
        String normalizedOs = osName == null ? "" : osName.trim().toLowerCase(Locale.ROOT);
        String normalizedArch = architecture == null ? "" : architecture.trim().toLowerCase(Locale.ROOT);
        String osKey;
        if (normalizedOs.contains("win")) {
            osKey = "windows";
        } else if (normalizedOs.contains("mac") || normalizedOs.contains("darwin")) {
            osKey = "macos";
        } else {
            osKey = "linux";
        }
        String archKey = switch (normalizedArch) {
            case "amd64", "x86_64", "x64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            default -> normalizedArch.isEmpty() ? "unknown" : normalizedArch;
        };
        return osKey + "-" + archKey;
    }

    static List<Path> developmentLibraryCandidates(String expectedFile) {
        Path root = Path.of("").toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();
        candidates.add(root.resolve("build").resolve("native").resolve("gbmahjong").resolve(expectedFile));
        candidates.add(root.resolve("build").resolve("native").resolve("gbmahjong").resolve("Release").resolve(expectedFile));
        candidates.add(root.resolve("native").resolve("gbmahjong").resolve("build").resolve(expectedFile));
        candidates.add(root.resolve("native").resolve("gbmahjong").resolve("build").resolve("Release").resolve(expectedFile));
        return candidates;
    }

    private static Path extractBundledLibrary(String platformKey, String expectedFile) {
        String resourceRoot = "native/" + platformKey + "/";
        String resourcePath = resourceRoot + expectedFile;
        try (InputStream input = GbMahjongNativeLibrary.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                return null;
            }
            Path target = Path.of(
                System.getProperty("java.io.tmpdir"),
                "mahjongpaper",
                "native",
                platformKey,
                expectedFile
            );
            Files.createDirectories(target.getParent());
            for (String dependency : bundledDependencyFileNames(platformKey)) {
                copyBundledFile(resourceRoot + dependency, target.getParent().resolve(dependency));
            }
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException exception) {
            return null;
        }
    }

    private static String[] bundledDependencyFileNames(String platformKey) {
        if ("windows-x86_64".equals(platformKey)) {
            return new String[] {"libwinpthread-1.dll"};
        }
        return new String[0];
    }

    private static void copyBundledFile(String resourcePath, Path target) throws IOException {
        try (InputStream dependency = GbMahjongNativeLibrary.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (dependency == null) {
                return;
            }
            Files.copy(dependency, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void loadLibraryWithDependencies(Path library, String[] dependencyNames) {
        Path directory = library.toAbsolutePath().getParent();
        for (String dependencyName : dependencyNames) {
            Path dependency = directory.resolve(dependencyName);
            if (Files.isRegularFile(dependency)) {
                System.load(dependency.toAbsolutePath().toString());
            }
        }
        System.load(library.toAbsolutePath().toString());
    }

    record LoadState(boolean available, String detail) {
    }
}
