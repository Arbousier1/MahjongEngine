package doublemoon.mahjongcraft.paper.render;

import org.bukkit.configuration.ConfigurationSection;

public record EntityCullingConfig(
    boolean enabled,
    long intervalTicks,
    double viewDistance,
    boolean rateLimitingEnabled,
    int bucketSize,
    int restorePerTick
) {
    private static final long DEFAULT_INTERVAL_TICKS = 2L;
    private static final double DEFAULT_VIEW_DISTANCE = 48.0D;
    private static final int DEFAULT_BUCKET_SIZE = 64;
    private static final int DEFAULT_RESTORE_PER_TICK = 8;

    public static EntityCullingConfig from(ConfigurationSection root) {
        ConfigurationSection section = root == null ? null : root.getConfigurationSection("clientOptimization.entityCulling");
        if (section == null) {
            return defaults();
        }
        ConfigurationSection rateLimitSection = section.getConfigurationSection("rateLimiting");
        return new EntityCullingConfig(
            section.getBoolean("enabled", false),
            Math.max(1L, section.getLong("intervalTicks", DEFAULT_INTERVAL_TICKS)),
            Math.max(1.0D, section.getDouble("viewDistance", DEFAULT_VIEW_DISTANCE)),
            rateLimitSection == null || rateLimitSection.getBoolean("enabled", true),
            Math.max(1, rateLimitSection == null ? DEFAULT_BUCKET_SIZE : rateLimitSection.getInt("bucketSize", DEFAULT_BUCKET_SIZE)),
            Math.max(1, rateLimitSection == null ? DEFAULT_RESTORE_PER_TICK : rateLimitSection.getInt("restorePerTick", DEFAULT_RESTORE_PER_TICK))
        );
    }

    public static EntityCullingConfig defaults() {
        return new EntityCullingConfig(
            false,
            DEFAULT_INTERVAL_TICKS,
            DEFAULT_VIEW_DISTANCE,
            true,
            DEFAULT_BUCKET_SIZE,
            DEFAULT_RESTORE_PER_TICK
        );
    }
}
