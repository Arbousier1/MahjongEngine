package top.ellan.mahjong.render.scene;

import top.ellan.mahjong.model.SeatWind;
import top.ellan.mahjong.render.display.DisplayEntities;
import top.ellan.mahjong.render.layout.TableRenderLayout;
import top.ellan.mahjong.render.TableRenderSubject;
import top.ellan.mahjong.render.snapshot.TableSeatRenderSnapshot;
import top.ellan.mahjong.riichi.model.ScoringStick;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;

/**
 * Renders riichi and scoring sticks, and provides stick diagnostics inspection.
 */
public final class StickRenderer {
    private StickRenderer() {
    }

    public static List<Entity> renderSticks(TableRenderSubject session, SeatWind wind) {
        Location center = TableGeometry.displayCenter(session);
        UUID playerId = session.playerAt(wind);
        if (playerId == null) {
            return List.of();
        }

        List<Entity> spawned = new ArrayList<>();
        List<ScoringStick> cornerSticks = session.cornerSticks(wind);
        for (int i = 0; i < cornerSticks.size(); i++) {
            spawned.add(spawnStick(session, TableGeometry.cornerStickCenter(center, wind, i), TableGeometry.cornerStickLongOnX(wind), cornerSticks.get(i)));
        }
        if (session.isRiichi(playerId)) {
            spawned.add(spawnStick(session, TableGeometry.riichiStickCenter(center, wind), TableGeometry.riichiStickLongOnX(wind), ScoringStick.P1000));
        }
        return spawned;
    }

    public static List<Entity> renderSticks(
        TableRenderSubject session,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan
    ) {
        if (seat.playerId() == null) {
            return List.of();
        }
        List<Entity> spawned = new ArrayList<>(plan.stickPlacements().size());
        for (TableRenderLayout.StickPlacement stickPlacement : plan.stickPlacements()) {
            spawned.add(spawnStick(session, TableGeometry.toLocation(session, stickPlacement.center()), stickPlacement.longOnX(), stickPlacement.stick()));
        }
        return spawned;
    }

    public static List<TableGeometry.StickDiagnostics> inspectSticks(TableRenderSubject session) {
        Location center = TableGeometry.displayCenter(session);
        List<TableGeometry.StickDiagnostics> diagnostics = new ArrayList<>();
        for (SeatWind wind : SeatWind.values()) {
            UUID playerId = session.playerAt(wind);
            if (playerId == null) {
                continue;
            }
            List<ScoringStick> cornerSticks = session.cornerSticks(wind);
            for (int i = 0; i < cornerSticks.size(); i++) {
                boolean longOnX = TableGeometry.cornerStickLongOnX(wind);
                ScoringStick stick = cornerSticks.get(i);
                diagnostics.add(new TableGeometry.StickDiagnostics(
                    wind,
                    i,
                    false,
                    longOnX,
                    stick,
                    TableGeometry.cornerStickCenter(center, wind, i),
                    stickFurnitureId(longOnX, stick)
                ));
            }
            if (session.isRiichi(playerId)) {
                boolean longOnX = TableGeometry.riichiStickLongOnX(wind);
                diagnostics.add(new TableGeometry.StickDiagnostics(
                    wind,
                    -1,
                    true,
                    longOnX,
                    ScoringStick.P1000,
                    TableGeometry.riichiStickCenter(center, wind),
                    stickFurnitureId(longOnX, ScoringStick.P1000)
                ));
            }
        }
        return List.copyOf(diagnostics);
    }

    static Entity spawnStick(TableRenderSubject session, Location center, boolean longOnX, ScoringStick stick) {
        Entity furniture = session.craftEngine() == null
            ? null
            : session.craftEngine().placeFurniture(center, stickFurnitureId(longOnX, stick));
        if (furniture != null) {
            return furniture;
        }
        double width = longOnX ? TableRenderConstants.STICK_WIDTH : TableRenderConstants.STICK_DEPTH;
        double depth = longOnX ? TableRenderConstants.STICK_DEPTH : TableRenderConstants.STICK_WIDTH;
        return DisplayEntities.spawnBlockDisplay(
            session.bukkitPlugin(),
            TableGeometry.centeredCuboid(center, width, TableRenderConstants.STICK_HEIGHT, depth),
            stickMaterial(stick),
            (float) width,
            (float) TableRenderConstants.STICK_HEIGHT,
            (float) depth
        );
    }

    static String stickFurnitureId(boolean longOnX, ScoringStick stick) {
        return TableRenderConstants.STICK_FURNITURE_PREFIX
            + (longOnX ? "x_" : "z_")
            + switch (stick) {
                case P100 -> "p100";
                case P5000 -> "p5000";
                case P10000 -> "p10000";
                default -> "p1000";
            };
    }

    static Material stickMaterial(ScoringStick stick) {
        return switch (stick) {
            case P100 -> Material.RED_CONCRETE;
            case P5000 -> Material.GOLD_BLOCK;
            case P10000 -> Material.EMERALD_BLOCK;
            default -> Material.QUARTZ_BLOCK;
        };
    }
}
