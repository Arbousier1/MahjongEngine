package doublemoon.mahjongcraft.paper.gb.jni;

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
            return "lib" + DEFAULT_LIBRARY_NAME + ".dylib";
        }
        return "lib" + DEFAULT_LIBRARY_NAME + ".so";
    }

    private static LoadState loadLibrary() {
        String configuredPath = System.getProperty(PATH_PROPERTY, "").trim();
        try {
            if (!configuredPath.isEmpty()) {
                System.load(configuredPath);
                return new LoadState(true, "loaded from " + configuredPath);
            }
            System.loadLibrary(DEFAULT_LIBRARY_NAME);
            return new LoadState(true, "loaded from java.library.path");
        } catch (UnsatisfiedLinkError | SecurityException exception) {
            String expectedFile = platformLibraryFileName(System.getProperty("os.name", ""));
            String source = configuredPath.isEmpty()
                ? "java.library.path"
                : configuredPath;
            return new LoadState(
                false,
                "GB Mahjong native library unavailable (" + expectedFile + " via " + source + "): " + exception.getMessage()
            );
        }
    }

    record LoadState(boolean available, String detail) {
    }
}
