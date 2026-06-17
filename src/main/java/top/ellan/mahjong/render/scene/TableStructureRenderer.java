package top.ellan.mahjong.render.scene;

import top.ellan.mahjong.render.display.DisplayEntities;
import top.ellan.mahjong.render.layout.TableRenderLayout;
import top.ellan.mahjong.render.TableRenderSubject;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;

/**
 * Renders the physical table structure (top, pillar, borders, hitboxes) and
 * the table visual furniture, plus table diagnostics inspection.
 */
public final class TableStructureRenderer {
    private TableStructureRenderer() {
    }

    public static List<Entity> renderTableStructure(TableRenderSubject session) {
        Location center = TableGeometry.displayCenter(session);
        TableGeometry.TableBounds bounds = TableGeometry.tableBoundsFromTiles(center);
        List<Entity> spawned = new ArrayList<>(16);
        Location tableCenter = center.clone().set(bounds.centerX(), center.getY(), bounds.centerZ());
        double topWidth = bounds.width() + TableRenderConstants.TABLE_TOP_SIZE_EXPANSION;
        double topDepth = bounds.depth() + TableRenderConstants.TABLE_TOP_SIZE_EXPANSION;
        double borderSpanX = topWidth + TableRenderConstants.TABLE_BORDER_THICKNESS;
        double borderSpanZ = topDepth + TableRenderConstants.TABLE_BORDER_THICKNESS;
        double borderCenterOffsetX = topWidth / 2.0D + TableRenderConstants.TABLE_BORDER_THICKNESS / 2.0D + TableRenderConstants.TABLE_BORDER_OUTWARD_OFFSET;
        double borderCenterOffsetZ = topDepth / 2.0D + TableRenderConstants.TABLE_BORDER_THICKNESS / 2.0D + TableRenderConstants.TABLE_BORDER_OUTWARD_OFFSET;
        Entity tableVisual = spawnTableVisual(session, tableCenter);
        if (tableVisual != null) {
            spawned.add(tableVisual);
            spawned.addAll(renderTableHitboxes(session, tableCenter, true));
            return spawned;
        }

        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.bukkitPlugin(),
            TableGeometry.centeredCuboid(tableCenter.clone().add(0.0D, -1.0D, 0.0D), TableRenderConstants.TABLE_BOTTOM_SIZE, TableRenderConstants.TABLE_BOTTOM_HEIGHT, TableRenderConstants.TABLE_BOTTOM_SIZE),
            Material.DARK_OAK_WOOD,
            (float) TableRenderConstants.TABLE_BOTTOM_SIZE,
            (float) TableRenderConstants.TABLE_BOTTOM_HEIGHT,
            (float) TableRenderConstants.TABLE_BOTTOM_SIZE
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.bukkitPlugin(),
            TableGeometry.centeredCuboid(tableCenter.clone().add(0.0D, -(TableRenderConstants.TABLE_TOP_THICKNESS + TableRenderConstants.TABLE_PILLAR_HEIGHT), 0.0D), TableRenderConstants.TABLE_PILLAR_SIZE, TableRenderConstants.TABLE_PILLAR_HEIGHT, TableRenderConstants.TABLE_PILLAR_SIZE),
            Material.DARK_OAK_WOOD,
            (float) TableRenderConstants.TABLE_PILLAR_SIZE,
            (float) TableRenderConstants.TABLE_PILLAR_HEIGHT,
            (float) TableRenderConstants.TABLE_PILLAR_SIZE
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.bukkitPlugin(),
            TableGeometry.centeredCuboid(tableCenter.clone().add(0.0D, -TableRenderConstants.TABLE_TOP_THICKNESS, 0.0D), topWidth, TableRenderConstants.TABLE_TOP_THICKNESS, topDepth),
            Material.SMOOTH_STONE,
            (float) topWidth,
            (float) TableRenderConstants.TABLE_TOP_THICKNESS,
            (float) topDepth
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.bukkitPlugin(),
            TableGeometry.centeredCuboid(tableCenter.clone().add(0.0D, -TableRenderConstants.TABLE_TOP_THICKNESS, -borderCenterOffsetZ), borderSpanX, TableRenderConstants.TABLE_BORDER_HEIGHT, TableRenderConstants.TABLE_BORDER_THICKNESS),
            Material.STRIPPED_OAK_WOOD,
            (float) borderSpanX,
            (float) TableRenderConstants.TABLE_BORDER_HEIGHT,
            (float) TableRenderConstants.TABLE_BORDER_THICKNESS
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.bukkitPlugin(),
            TableGeometry.centeredCuboid(tableCenter.clone().add(0.0D, -TableRenderConstants.TABLE_TOP_THICKNESS, borderCenterOffsetZ), borderSpanX, TableRenderConstants.TABLE_BORDER_HEIGHT, TableRenderConstants.TABLE_BORDER_THICKNESS),
            Material.STRIPPED_OAK_WOOD,
            (float) borderSpanX,
            (float) TableRenderConstants.TABLE_BORDER_HEIGHT,
            (float) TableRenderConstants.TABLE_BORDER_THICKNESS
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.bukkitPlugin(),
            TableGeometry.centeredCuboid(tableCenter.clone().add(-borderCenterOffsetX, -TableRenderConstants.TABLE_TOP_THICKNESS, 0.0D), TableRenderConstants.TABLE_BORDER_THICKNESS, TableRenderConstants.TABLE_BORDER_HEIGHT, borderSpanZ),
            Material.STRIPPED_OAK_WOOD,
            (float) TableRenderConstants.TABLE_BORDER_THICKNESS,
            (float) TableRenderConstants.TABLE_BORDER_HEIGHT,
            (float) borderSpanZ
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.bukkitPlugin(),
            TableGeometry.centeredCuboid(tableCenter.clone().add(borderCenterOffsetX, -TableRenderConstants.TABLE_TOP_THICKNESS, 0.0D), TableRenderConstants.TABLE_BORDER_THICKNESS, TableRenderConstants.TABLE_BORDER_HEIGHT, borderSpanZ),
            Material.STRIPPED_OAK_WOOD,
            (float) TableRenderConstants.TABLE_BORDER_THICKNESS,
            (float) TableRenderConstants.TABLE_BORDER_HEIGHT,
            (float) borderSpanZ
        ));
        spawned.addAll(renderTableHitboxes(session, tableCenter, false));
        return spawned;
    }

    public static List<Entity> renderTableStructure(TableRenderSubject session, TableRenderLayout.LayoutPlan plan) {
        List<Entity> spawned = new ArrayList<>(16);
        Location tableCenter = TableGeometry.toLocation(session, plan.tableCenter());
        double topWidth = plan.borderSpanX() - TableRenderConstants.TABLE_BORDER_THICKNESS;
        double topDepth = plan.borderSpanZ() - TableRenderConstants.TABLE_BORDER_THICKNESS;
        double borderSpanX = plan.borderSpanX();
        double borderSpanZ = plan.borderSpanZ();
        double borderCenterOffsetX = topWidth / 2.0D + TableRenderConstants.TABLE_BORDER_THICKNESS / 2.0D + TableRenderConstants.TABLE_BORDER_OUTWARD_OFFSET;
        double borderCenterOffsetZ = topDepth / 2.0D + TableRenderConstants.TABLE_BORDER_THICKNESS / 2.0D + TableRenderConstants.TABLE_BORDER_OUTWARD_OFFSET;
        Entity tableVisual = spawnTableVisual(session, tableCenter);
        if (tableVisual != null) {
            spawned.add(tableVisual);
            spawned.addAll(renderTableHitboxes(session, tableCenter, true));
            return spawned;
        }

        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.bukkitPlugin(),
            TableGeometry.centeredCuboid(tableCenter.clone().add(0.0D, -1.0D, 0.0D), TableRenderConstants.TABLE_BOTTOM_SIZE, TableRenderConstants.TABLE_BOTTOM_HEIGHT, TableRenderConstants.TABLE_BOTTOM_SIZE),
            Material.DARK_OAK_WOOD,
            (float) TableRenderConstants.TABLE_BOTTOM_SIZE,
            (float) TableRenderConstants.TABLE_BOTTOM_HEIGHT,
            (float) TableRenderConstants.TABLE_BOTTOM_SIZE
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.bukkitPlugin(),
            TableGeometry.centeredCuboid(tableCenter.clone().add(0.0D, -(TableRenderConstants.TABLE_TOP_THICKNESS + TableRenderConstants.TABLE_PILLAR_HEIGHT), 0.0D), TableRenderConstants.TABLE_PILLAR_SIZE, TableRenderConstants.TABLE_PILLAR_HEIGHT, TableRenderConstants.TABLE_PILLAR_SIZE),
            Material.DARK_OAK_WOOD,
            (float) TableRenderConstants.TABLE_PILLAR_SIZE,
            (float) TableRenderConstants.TABLE_PILLAR_HEIGHT,
            (float) TableRenderConstants.TABLE_PILLAR_SIZE
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.bukkitPlugin(),
            TableGeometry.centeredCuboid(tableCenter.clone().add(0.0D, -TableRenderConstants.TABLE_TOP_THICKNESS, 0.0D), topWidth, TableRenderConstants.TABLE_TOP_THICKNESS, topDepth),
            Material.SMOOTH_STONE,
            (float) topWidth,
            (float) TableRenderConstants.TABLE_TOP_THICKNESS,
            (float) topDepth
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.bukkitPlugin(),
            TableGeometry.centeredCuboid(tableCenter.clone().add(0.0D, -TableRenderConstants.TABLE_TOP_THICKNESS, -borderCenterOffsetZ), borderSpanX, TableRenderConstants.TABLE_BORDER_HEIGHT, TableRenderConstants.TABLE_BORDER_THICKNESS),
            Material.STRIPPED_OAK_WOOD,
            (float) borderSpanX,
            (float) TableRenderConstants.TABLE_BORDER_HEIGHT,
            (float) TableRenderConstants.TABLE_BORDER_THICKNESS
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.bukkitPlugin(),
            TableGeometry.centeredCuboid(tableCenter.clone().add(0.0D, -TableRenderConstants.TABLE_TOP_THICKNESS, borderCenterOffsetZ), borderSpanX, TableRenderConstants.TABLE_BORDER_HEIGHT, TableRenderConstants.TABLE_BORDER_THICKNESS),
            Material.STRIPPED_OAK_WOOD,
            (float) borderSpanX,
            (float) TableRenderConstants.TABLE_BORDER_HEIGHT,
            (float) TableRenderConstants.TABLE_BORDER_THICKNESS
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.bukkitPlugin(),
            TableGeometry.centeredCuboid(tableCenter.clone().add(-borderCenterOffsetX, -TableRenderConstants.TABLE_TOP_THICKNESS, 0.0D), TableRenderConstants.TABLE_BORDER_THICKNESS, TableRenderConstants.TABLE_BORDER_HEIGHT, borderSpanZ),
            Material.STRIPPED_OAK_WOOD,
            (float) TableRenderConstants.TABLE_BORDER_THICKNESS,
            (float) TableRenderConstants.TABLE_BORDER_HEIGHT,
            (float) borderSpanZ
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.bukkitPlugin(),
            TableGeometry.centeredCuboid(tableCenter.clone().add(borderCenterOffsetX, -TableRenderConstants.TABLE_TOP_THICKNESS, 0.0D), TableRenderConstants.TABLE_BORDER_THICKNESS, TableRenderConstants.TABLE_BORDER_HEIGHT, borderSpanZ),
            Material.STRIPPED_OAK_WOOD,
            (float) TableRenderConstants.TABLE_BORDER_THICKNESS,
            (float) TableRenderConstants.TABLE_BORDER_HEIGHT,
            (float) borderSpanZ
        ));
        spawned.addAll(renderTableHitboxes(session, tableCenter, false));
        return spawned;
    }

    public static TableGeometry.TableDiagnostics inspectTable(TableRenderSubject session) {
        Location center = TableGeometry.displayCenter(session);
        TableGeometry.TableBounds bounds = TableGeometry.tableBoundsFromTiles(center);
        Location tableCenter = center.clone().set(bounds.centerX(), center.getY(), bounds.centerZ());
        double borderSpanX = bounds.width() + TableRenderConstants.TABLE_TOP_SIZE_EXPANSION + TableRenderConstants.TABLE_BORDER_THICKNESS + TableRenderConstants.TABLE_BORDER_OUTWARD_OFFSET * 2.0D;
        double borderSpanZ = bounds.depth() + TableRenderConstants.TABLE_TOP_SIZE_EXPANSION + TableRenderConstants.TABLE_BORDER_THICKNESS + TableRenderConstants.TABLE_BORDER_OUTWARD_OFFSET * 2.0D;
        return new TableGeometry.TableDiagnostics(
            center,
            tableCenter,
            tableVisualAnchor(tableCenter),
            borderSpanX,
            borderSpanZ
        );
    }

    static List<Entity> renderTableHitboxes(TableRenderSubject session, Location tableCenter, boolean usingFurnitureVisual) {
        if (usingFurnitureVisual) {
            return List.of();
        }
        Entity furnitureHitbox = session.craftEngine().placeTableHitbox(tableCenter.clone());
        return furnitureHitbox == null ? List.of() : List.of(furnitureHitbox);
    }

    static Entity spawnTableVisual(TableRenderSubject session, Location tableCenter) {
        String tableFurnitureId = configuredTableFurnitureId(session);
        if (tableFurnitureId == null) {
            return null;
        }
        return session.craftEngine().placeFurniture(tableFurnitureAnchor(tableCenter, tableFurnitureId), tableFurnitureId);
    }

    static Location tableVisualAnchor(Location tableCenter) {
        return tableCenter.clone().add(0.0D, TableRenderConstants.TABLE_VISUAL_Y_OFFSET, 0.0D);
    }

    static Location tableFurnitureAnchor(Location tableCenter, String furnitureId) {
        Location anchor = tableVisualAnchor(tableCenter);
        return usesBuiltinTableFurnitureAnchor(furnitureId) ? anchor : standardFurnitureAnchor(anchor);
    }

    static boolean usesBuiltinTableFurnitureAnchor(String furnitureId) {
        return TableRenderConstants.TABLE_VISUAL_FURNITURE_ID.equals(furnitureId);
    }

    static Location standardFurnitureAnchor(Location anchor) {
        return anchor.clone().subtract(0.0D, TableRenderConstants.CUSTOM_FURNITURE_ORIGIN_Y_OFFSET, 0.0D);
    }

    static String configuredTableFurnitureId(TableRenderSubject session) {
        return configuredFurnitureId(session, session.settings().craftEngineTableFurnitureId());
    }

    static String configuredFurnitureId(TableRenderSubject session, String configuredValue) {
        if (session.craftEngine() == null) {
            return null;
        }
        if (configuredValue == null) {
            return null;
        }
        String trimmed = configuredValue.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
