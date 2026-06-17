package top.ellan.mahjong.render.scene;

import org.bukkit.Color;

/**
 * Holds the shared rendering constants used by the table scene renderers.
 * All constants are {@code public static final} so the region renderers and
 * geometry helpers can reference them directly.
 */
public final class TableRenderConstants {
    private TableRenderConstants() {
    }

    public static final double ONE_SIXTEENTH = 1.0D / 16.0D;
    public static final double TILE_WIDTH = 0.1125D;
    public static final double TILE_HEIGHT = 0.15D;
    public static final double TILE_DEPTH = 0.075D;
    public static final double TILE_PADDING = 0.0025D;
    public static final double STICK_WIDTH = 0.4D;
    public static final double STICK_HEIGHT = 0.0125D;
    public static final double STICK_DEPTH = 0.0625D;
    public static final double STICK_Y_OFFSET = 0.515625D;
    public static final int STICKS_PER_STACK = 5;
    public static final double TABLE_BOTTOM_SIZE = 14.0D * ONE_SIXTEENTH;
    public static final double TABLE_BOTTOM_HEIGHT = 2.0D * ONE_SIXTEENTH;
    public static final double TABLE_PILLAR_SIZE = 8.0D * ONE_SIXTEENTH;
    public static final double TABLE_PILLAR_HEIGHT = 12.0D * ONE_SIXTEENTH;
    public static final double TABLE_TOP_SIZE_EXPANSION = ONE_SIXTEENTH;
    public static final double TABLE_TOP_THICKNESS = 2.0D * ONE_SIXTEENTH;
    public static final double TABLE_BORDER_THICKNESS = ONE_SIXTEENTH;
    public static final double TABLE_BORDER_OUTWARD_OFFSET = 0.0D;
    public static final double TABLE_BORDER_HEIGHT = 3.0D * ONE_SIXTEENTH;
    public static final double DISPLAY_CENTER_Y_OFFSET = 0.52D;
    public static final double TABLE_VISUAL_Y_OFFSET = 0.5D;
    public static final double FLOATING_TEXT_Y_OFFSET = 1.0D;
    public static final double SEAT_DISTANCE_FROM_HAND_BASE = 0.9D;
    public static final double SEAT_BASE_Y_OFFSET = -0.62D;
    public static final double SEAT_RAISE_Y_OFFSET = 1.0D;
    public static final double SEAT_ANCHOR_Y_OFFSET = -0.18D;
    public static final double SEAT_BASE_WIDTH = 0.72D;
    public static final double SEAT_BASE_HEIGHT = 0.16D;
    public static final double SEAT_BACKREST_WIDTH = 0.72D;
    public static final double SEAT_BACKREST_HEIGHT = 0.72D;
    public static final double SEAT_BACKREST_DEPTH = 0.12D;
    public static final double SEAT_BACKREST_OFFSET = 0.26D;
    public static final double SEAT_CARPET_INSET = 0.08D;
    public static final double SEAT_CARPET_THICKNESS = 0.04D;
    public static final double SEAT_LABEL_DEPTH_OFFSET = 0.03D;
    public static final double SEAT_ACTION_LABEL_Y_OFFSET = -0.64D + FLOATING_TEXT_Y_OFFSET;
    public static final double SEAT_SIDE_ACTION_HORIZONTAL_OFFSET = 0.44D;
    public static final float SEAT_ACTION_INTERACTION_HEIGHT = 0.4F;
    public static final float SEAT_ACTION_INTERACTION_MIN_WIDTH = 0.72F;
    public static final float SEAT_ACTION_INTERACTION_MAX_WIDTH = 1.4F;
    public static final double CENTER_LABEL_Y_OFFSET = 0.55D + FLOATING_TEXT_Y_OFFSET - 0.5D;
    public static final double CENTER_LAST_DISCARD_TILE_Y_OFFSET = CENTER_LABEL_Y_OFFSET - 0.18D;
    public static final float CENTER_LAST_DISCARD_TILE_SCALE = 2.0F;
    public static final Color CENTER_LAST_DISCARD_TILE_GLOW = Color.fromRGB(255, 220, 96);
    public static final double WALL_DIRECTION_OFFSET = 1.0D;
    public static final double HAND_DIRECTION_OFFSET = WALL_DIRECTION_OFFSET + TILE_DEPTH + TILE_HEIGHT;
    public static final double HALF_TABLE_LENGTH_NO_BORDER = 0.5D + 15.0D / 16.0D;
    public static final double DEAD_WALL_GAP = TILE_PADDING * 20.0D;
    public static final double WALL_TILE_STEP = TILE_WIDTH + TILE_PADDING;
    public static final double UPRIGHT_TILE_Y = TILE_HEIGHT / 2.0D;
    public static final double FLAT_TILE_Y = TILE_DEPTH / 2.0D;
    public static final float HAND_INTERACTION_WIDTH = (float) TILE_WIDTH;
    public static final float HAND_INTERACTION_HEIGHT = (float) TILE_HEIGHT;
    public static final float SEAT_LABEL_INTERACTION_WIDTH = 1.2F;
    public static final float SEAT_LABEL_INTERACTION_HEIGHT = 0.85F;
    public static final float OVERLAY_ACTION_BUTTON_HEIGHT = 0.4F;
    public static final float OVERLAY_ACTION_BUTTON_SPACING = 0.55F;
    public static final float OVERLAY_ACTION_BUTTON_GAP = 0.16F;
    public static final int OVERLAY_ACTION_BUTTONS_PER_ROW = 4;
    public static final double OVERLAY_ACTION_Y_OFFSET = SEAT_ACTION_LABEL_Y_OFFSET;
    public static final int WALL_TILES_PER_SIDE = 34;
    public static final int TOTAL_WALL_TILES = 136;
    public static final int DEAD_WALL_SIZE = 14;
    public static final int LIVE_WALL_SIZE = TOTAL_WALL_TILES - DEAD_WALL_SIZE;
    public static final int DISCARDS_PER_ROW = 6;
    public static final double CUSTOM_FURNITURE_ORIGIN_Y_OFFSET = 1.375D;
    public static final String TABLE_VISUAL_FURNITURE_ID = "mahjongpaper:table_visual";
    public static final String SEAT_VISUAL_FURNITURE_ID = "mahjongpaper:seat_chair";
    public static final String STICK_FURNITURE_PREFIX = "mahjongpaper:stick_";

    // Extracted magic numbers

    /** Across-seat offset used for spectator seat overlays. */
    public static final double SPECTATOR_OVERLAY_ACROSS_OFFSET = 0.42D;
    /** Y offset for spectator seat overlays (added to {@link #FLOATING_TEXT_Y_OFFSET}). */
    public static final double SPECTATOR_OVERLAY_Y_OFFSET = 0.62D;
    /** Y offset for the main viewer overlay label (added to {@link #FLOATING_TEXT_Y_OFFSET}). */
    public static final double VIEWER_OVERLAY_LABEL_Y_OFFSET = 0.9D;
    /** Y offset for the seat status label (added to {@link #FLOATING_TEXT_Y_OFFSET}). */
    public static final double SEAT_STATUS_LABEL_Y_OFFSET = 0.45D;
    /** Per-row vertical step for viewer action buttons. */
    public static final double VIEWER_ACTION_BUTTON_ROW_STEP = 0.24D;
    /** Hitbox Y subtract applied to label interactions. */
    public static final double LABEL_INTERACTION_Y_OFFSET = 0.1D;
    /** Per-visual-unit width estimate for action labels. */
    public static final float ACTION_LABEL_WIDTH_PER_UNIT = 0.085F;
    /** Base width used when estimating action label widths. */
    public static final float ACTION_LABEL_BASE_WIDTH = 0.24F;
    /** Minimum estimated action label width. */
    public static final float ACTION_LABEL_MIN_WIDTH = 0.7F;
    /** Maximum estimated action label width. */
    public static final float ACTION_LABEL_MAX_WIDTH = 2.2F;
    /** Base width used when computing seat action interaction widths. */
    public static final float SEAT_ACTION_INTERACTION_BASE_WIDTH = 0.3F;
    /** Per-character width used when computing seat action interaction widths. */
    public static final float SEAT_ACTION_INTERACTION_PER_CHAR_WIDTH = 0.07F;
    /** Multiplier applied to {@link #TILE_WIDTH} when computing the wall starting position. */
    public static final double WALL_START_POSITION_MULTIPLIER = 17.0D;
}
