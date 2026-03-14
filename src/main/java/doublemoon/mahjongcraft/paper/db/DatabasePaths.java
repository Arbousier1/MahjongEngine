package doublemoon.mahjongcraft.paper.db;

import java.nio.file.Path;
import java.util.Objects;

public final class DatabasePaths {
    private DatabasePaths() {
    }

    public static Path resolveH2FilePath(Path pluginDataFolder, String rawPath) {
        String normalized = Objects.requireNonNull(rawPath, "database.h2.path").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("database.h2.path must not be blank");
        }

        Path path;
        if ("~".equals(normalized)) {
            path = Path.of(System.getProperty("user.home"));
        } else if (normalized.startsWith("~/") || normalized.startsWith("~\\")) {
            path = Path.of(System.getProperty("user.home")).resolve(normalized.substring(2));
        } else {
            path = Path.of(normalized);
            if (!path.isAbsolute()) {
                path = pluginDataFolder.toAbsolutePath().normalize().resolve(path);
            }
        }
        return path.toAbsolutePath().normalize();
    }
}
