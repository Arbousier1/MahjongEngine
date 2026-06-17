package top.ellan.mahjong.render.scene;

import top.ellan.mahjong.model.SeatWind;
import top.ellan.mahjong.render.display.DisplayClickAction;
import top.ellan.mahjong.render.display.DisplayEntities;
import top.ellan.mahjong.render.layout.TableRenderLayout;
import top.ellan.mahjong.render.TableRenderSubject;
import top.ellan.mahjong.render.snapshot.TableSeatRenderSnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;

/**
 * Renders seat visuals, status labels and the seat action labels/interactions.
 */
public final class SeatRenderer {
    private SeatRenderer() {
    }

    public static List<Entity> renderSeatLabels(TableRenderSubject session, SeatWind wind) {
        List<Entity> spawned = new ArrayList<>(4);
        Location center = TableGeometry.displayCenter(session);
        UUID playerId = session.playerAt(wind);
        Location handBase = TableGeometry.handDirectionBase(center, wind);
        boolean active = session.currentSeat() == wind;
        Location statusLabelLocation = withSeatLabelDepthOffset(
            handBase.clone().add(0.0D, TableRenderConstants.SEAT_STATUS_LABEL_Y_OFFSET + TableRenderConstants.FLOATING_TEXT_Y_OFFSET, 0.0D),
            wind,
            -TableRenderConstants.SEAT_LABEL_DEPTH_OFFSET * 0.5D
        );
        DisplayClickAction action = seatVisualAction(session, wind);
        Component text = playerId == null
            ? Component.text(session.publicSeatStatus(wind))
            : Component.text(session.publicSeatStatus(wind) + "\n" + session.displayName(playerId, session.publicLocale()));
        spawned.add(DisplayEntities.spawnLabel(
            session.bukkitPlugin(),
            statusLabelLocation,
            text,
            seatLabelColor(wind, active),
            null,
            Display.Billboard.FIXED,
            TableGeometry.seatYaw(wind),
            0.0F
        ));
        appendSeatActionEntities(session, wind, playerId, playerId != null && session.isReady(playerId), handBase, action, spawned);
        return spawned;
    }

    public static List<Entity> renderSeatVisual(TableRenderSubject session, SeatWind wind) {
        Location center = TableGeometry.displayCenter(session);
        Location handBase = TableGeometry.handDirectionBase(center, wind);
        return renderSeatVisual(session, wind, handBase, seatChairAction(session, wind));
    }

    public static List<Entity> renderSeatLabels(
        TableRenderSubject session,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan
    ) {
        List<Entity> spawned = new ArrayList<>(4);
        boolean active = seat.wind() == session.currentSeat();
        Location statusLabelLocation = withSeatLabelDepthOffset(
            TableGeometry.toLocation(session, plan.statusLabelLocation()),
            seat.wind(),
            -TableRenderConstants.SEAT_LABEL_DEPTH_OFFSET * 0.5D
        );
        DisplayClickAction action = seatVisualAction(session, seat.wind());
        Component text = seat.playerId() == null
            ? Component.text(seat.publicSeatStatus())
            : Component.text(seat.publicSeatStatus() + "\n" + seat.displayName());
        spawned.add(DisplayEntities.spawnLabel(
            session.bukkitPlugin(),
            statusLabelLocation,
            text,
            seatLabelColor(seat.wind(), active),
            null,
            Display.Billboard.FIXED,
            TableGeometry.seatYaw(seat.wind()),
            0.0F
        ));
        Location handBase = TableGeometry.handDirectionBase(TableGeometry.displayCenter(session), seat.wind());
        appendSeatActionEntities(session, seat.wind(), seat.playerId(), seat.ready(), handBase, action, spawned);
        return spawned;
    }

    public static List<DisplayEntities.EntitySpec> renderSeatLabelSpecs(
        TableRenderSubject session,
        TableSeatRenderSnapshot seat,
        TableRenderLayout.SeatLayoutPlan plan
    ) {
        List<DisplayEntities.EntitySpec> specs = new ArrayList<>(5);
        boolean active = seat.wind() == session.currentSeat();
        Location statusLabelLocation = withSeatLabelDepthOffset(
            TableGeometry.toLocation(session, plan.statusLabelLocation()),
            seat.wind(),
            -TableRenderConstants.SEAT_LABEL_DEPTH_OFFSET * 0.5D
        );
        DisplayClickAction action = seatVisualAction(session, seat.wind());
        Component text = seat.playerId() == null
            ? Component.text(seat.publicSeatStatus())
            : Component.text(seat.publicSeatStatus() + "\n" + seat.displayName());
        specs.add(DisplayEntities.labelSpec(
            statusLabelLocation,
            text,
            seatLabelColor(seat.wind(), active),
            null,
            Display.Billboard.FIXED,
            TableGeometry.seatYaw(seat.wind()),
            0.0F,
            true
        ));
        Location handBase = TableGeometry.handDirectionBase(TableGeometry.displayCenter(session), seat.wind());
        appendSeatActionSpecs(session, seat.wind(), seat.playerId(), seat.ready(), handBase, action, specs);
        return List.copyOf(specs);
    }

    public static Location seatAnchorLocation(TableRenderSubject session, SeatWind wind) {
        return seatAnchorLocation(TableGeometry.handDirectionBase(TableGeometry.displayCenter(session), wind), wind);
    }

    public static float seatFacingYaw(SeatWind wind) {
        return TableGeometry.seatYaw(wind) + 180.0F;
    }

    static Location seatFurnitureAnchor(Location location, SeatWind wind, String furnitureId) {
        Location anchor = seatPlacementLocation(location, wind);
        return usesBuiltinSeatFurnitureAnchor(furnitureId) ? anchor : TableStructureRenderer.standardFurnitureAnchor(anchor);
    }

    private static List<Entity> renderSeatVisual(
        TableRenderSubject session,
        SeatWind wind,
        Location handBase,
        DisplayClickAction action
    ) {
        String seatFurnitureId = configuredSeatFurnitureId(session);
        if (seatFurnitureId != null) {
            Entity furniture = spawnSeatFurniture(session, seatBaseLocation(handBase, wind), wind, seatFurnitureId, action);
            if (furniture != null) {
                return List.of(furniture);
            }
        }

        List<Entity> spawned = new ArrayList<>(3);
        Location seatBase = seatBaseLocation(handBase, wind);
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.bukkitPlugin(),
            TableGeometry.centeredCuboid(seatBase.clone(), TableRenderConstants.SEAT_BASE_WIDTH, TableRenderConstants.SEAT_BASE_HEIGHT, TableRenderConstants.SEAT_BASE_WIDTH),
            Material.SMOOTH_STONE,
            (float) TableRenderConstants.SEAT_BASE_WIDTH,
            (float) TableRenderConstants.SEAT_BASE_HEIGHT,
            (float) TableRenderConstants.SEAT_BASE_WIDTH,
            true,
            null,
            action
        ));
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.bukkitPlugin(),
            TableGeometry.centeredCuboid(
                seatBase.clone().add(0.0D, TableRenderConstants.SEAT_BASE_HEIGHT, 0.0D),
                TableRenderConstants.SEAT_BASE_WIDTH - TableRenderConstants.SEAT_CARPET_INSET * 2.0D,
                TableRenderConstants.SEAT_CARPET_THICKNESS,
                TableRenderConstants.SEAT_BASE_WIDTH - TableRenderConstants.SEAT_CARPET_INSET * 2.0D
            ),
            Material.GREEN_WOOL,
            (float) (TableRenderConstants.SEAT_BASE_WIDTH - TableRenderConstants.SEAT_CARPET_INSET * 2.0D),
            (float) TableRenderConstants.SEAT_CARPET_THICKNESS,
            (float) (TableRenderConstants.SEAT_BASE_WIDTH - TableRenderConstants.SEAT_CARPET_INSET * 2.0D),
            true,
            null,
            action
        ));

        TableGeometry.Offset backrestOffset = TableGeometry.offsetTowardSeatFront(wind, -TableRenderConstants.SEAT_BACKREST_OFFSET);
        spawned.add(DisplayEntities.spawnBlockDisplay(
            session.bukkitPlugin(),
            TableGeometry.centeredCuboid(seatBase.clone().add(backrestOffset.x(), TableRenderConstants.SEAT_BACKREST_HEIGHT / 2.0D, backrestOffset.z()), TableRenderConstants.SEAT_BACKREST_WIDTH, TableRenderConstants.SEAT_BACKREST_HEIGHT, TableRenderConstants.SEAT_BACKREST_DEPTH),
            Material.STRIPPED_OAK_WOOD,
            (float) TableRenderConstants.SEAT_BACKREST_WIDTH,
            (float) TableRenderConstants.SEAT_BACKREST_HEIGHT,
            (float) TableRenderConstants.SEAT_BACKREST_DEPTH,
            true,
            null,
            action
        ));
        return spawned;
    }

    private static Location seatAnchorLocation(Location handBase, SeatWind wind) {
        return seatBaseLocation(handBase, wind).add(0.0D, TableRenderConstants.SEAT_ANCHOR_Y_OFFSET, 0.0D);
    }

    private static Location seatBaseLocation(Location handBase, SeatWind wind) {
        TableGeometry.Offset forward = TableGeometry.offsetTowardSeatFront(wind, TableRenderConstants.SEAT_DISTANCE_FROM_HAND_BASE);
        return handBase.clone().add(forward.x(), TableRenderConstants.SEAT_BASE_Y_OFFSET + TableRenderConstants.SEAT_RAISE_Y_OFFSET, forward.z());
    }

    private static Location withSeatLabelDepthOffset(Location location, SeatWind wind, double amount) {
        TableGeometry.Offset offset = TableGeometry.offsetTowardSeatFront(wind, amount);
        return location.clone().add(offset.x(), 0.0D, offset.z());
    }

    private static Location seatLabelInteractionLocation(Location labelLocation) {
        return labelLocation.clone().subtract(0.0D, TableRenderConstants.LABEL_INTERACTION_Y_OFFSET, 0.0D);
    }

    private static Location seatPlacementLocation(Location location, SeatWind wind) {
        Location placed = location.clone();
        placed.setYaw(TableGeometry.seatYaw(wind) + 180.0F);
        placed.setPitch(0.0F);
        return placed;
    }

    private static boolean usesBuiltinSeatFurnitureAnchor(String furnitureId) {
        return TableRenderConstants.SEAT_VISUAL_FURNITURE_ID.equals(furnitureId);
    }

    private static String configuredSeatFurnitureId(TableRenderSubject session) {
        return TableStructureRenderer.configuredFurnitureId(session, session.settings().craftEngineSeatFurnitureId());
    }

    private static Entity spawnSeatFurniture(
        TableRenderSubject session,
        Location location,
        SeatWind wind,
        String furnitureId,
        DisplayClickAction action
    ) {
        if (session.craftEngine() == null || furnitureId == null || furnitureId.isBlank()) {
            return null;
        }
        return session.craftEngine().placeSeatFurniture(
            seatFurnitureAnchor(location, wind, furnitureId),
            furnitureId,
            action
        );
    }

    private static DisplayClickAction seatVisualAction(TableRenderSubject session, SeatWind wind) {
        if (session == null || wind == null) {
            return null;
        }
        if (session.isStarted() || session.isRoundStartInProgress()) {
            return null;
        }
        UUID seatedPlayer = session.playerAt(wind);
        if (seatedPlayer == null) {
            return DisplayClickAction.joinSeat(session.id(), wind);
        }
        return DisplayClickAction.toggleReady(session.id(), wind);
    }

    private static DisplayClickAction seatChairAction(TableRenderSubject session, SeatWind wind) {
        if (session == null || wind == null) {
            return null;
        }
        return DisplayClickAction.joinSeat(session.id(), wind);
    }

    private static Component seatActionLabel(TableRenderSubject session, DisplayClickAction action, boolean ready) {
        if (action == null || session == null) {
            return Component.empty();
        }
        java.util.Locale locale = session.publicLocale();
        return switch (action.actionType()) {
            case JOIN_SEAT -> Component.text(
                "[" + session.messages().plain(locale, "table.action.join_seat") + "]",
                NamedTextColor.GREEN
            ).decorate(TextDecoration.BOLD);
            case TOGGLE_READY -> Component.text(
                "[" + session.messages().plain(locale, ready ? "table.action.unready" : "table.action.ready") + "]",
                ready ? NamedTextColor.YELLOW : NamedTextColor.AQUA
            ).decorate(TextDecoration.BOLD);
            case PLAYER_COMMAND -> {
                if ("lobby:leave".equalsIgnoreCase(action.command())) {
                    yield Component.text(
                        "[" + session.messages().plain(locale, "table.action.leave") + "]",
                        NamedTextColor.RED
                    ).decorate(TextDecoration.BOLD);
                }
                yield Component.empty();
            }
            default -> Component.empty();
        };
    }

    private static Color seatActionLabelColor(DisplayClickAction action) {
        if (action == null) {
            return TableRenderConstants.SEAT_ACTION_DEFAULT_BACKGROUND;
        }
        return switch (action.actionType()) {
            case JOIN_SEAT -> TableRenderConstants.SEAT_ACTION_JOIN_BACKGROUND;
            case TOGGLE_READY -> TableRenderConstants.SEAT_ACTION_READY_BACKGROUND;
            case PLAYER_COMMAND -> "lobby:leave".equalsIgnoreCase(action.command())
                ? TableRenderConstants.SEAT_ACTION_LEAVE_BACKGROUND
                : TableRenderConstants.SEAT_ACTION_DEFAULT_BACKGROUND;
            default -> TableRenderConstants.SEAT_ACTION_DEFAULT_BACKGROUND;
        };
    }

    private static float seatActionInteractionWidth(Component label) {
        String plain = label == null ? "" : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(label);
        float estimated = TableRenderConstants.SEAT_ACTION_INTERACTION_BASE_WIDTH + Math.max(0, plain.length()) * TableRenderConstants.SEAT_ACTION_INTERACTION_PER_CHAR_WIDTH;
        return Math.max(TableRenderConstants.SEAT_ACTION_INTERACTION_MIN_WIDTH, Math.min(TableRenderConstants.SEAT_ACTION_INTERACTION_MAX_WIDTH, estimated));
    }

    private static Collection<UUID> seatActionPrivateViewers(UUID seatedPlayerId, DisplayClickAction action) {
        if (action == null) {
            return null;
        }
        if (action.actionType() == DisplayClickAction.ActionType.TOGGLE_READY && seatedPlayerId != null) {
            return List.of(seatedPlayerId);
        }
        if (action.actionType() == DisplayClickAction.ActionType.PLAYER_COMMAND && action.ownerId() != null) {
            return List.of(action.ownerId());
        }
        return null;
    }

    private static void appendSeatActionEntities(
        TableRenderSubject session,
        SeatWind wind,
        UUID seatedPlayerId,
        boolean ready,
        Location handBase,
        DisplayClickAction action,
        List<Entity> spawned
    ) {
        appendSeatActions(session, wind, seatedPlayerId, ready, handBase, action, spawned, SeatRenderer::appendSeatActionEntitiesFromData);
    }

    private static void appendSeatActionSpecs(
        TableRenderSubject session,
        SeatWind wind,
        UUID seatedPlayerId,
        boolean ready,
        Location handBase,
        DisplayClickAction action,
        List<DisplayEntities.EntitySpec> specs
    ) {
        appendSeatActions(session, wind, seatedPlayerId, ready, handBase, action, specs, SeatRenderer::appendSeatActionSpecsFromData);
    }

    private static <T> void appendSeatActions(
        TableRenderSubject session,
        SeatWind wind,
        UUID seatedPlayerId,
        boolean ready,
        Location handBase,
        DisplayClickAction action,
        List<T> target,
        SeatActionAppender<T> appender
    ) {
        if (action == null) {
            return;
        }
        if (action.actionType() == DisplayClickAction.ActionType.TOGGLE_READY && seatedPlayerId != null && !session.isStarted()) {
            appendSeatAction(session, wind, seatedPlayerId, handBase, action, ready, -TableRenderConstants.SEAT_SIDE_ACTION_HORIZONTAL_OFFSET, target, appender);
            appendSeatAction(
                session,
                wind,
                seatedPlayerId,
                handBase,
                DisplayClickAction.playerCommand(session.id(), seatedPlayerId, "lobby:leave"),
                ready,
                TableRenderConstants.SEAT_SIDE_ACTION_HORIZONTAL_OFFSET,
                target,
                appender
            );
            return;
        }
        appendSeatAction(session, wind, seatedPlayerId, handBase, action, ready, 0.0D, target, appender);
    }

    private static <T> void appendSeatAction(
        TableRenderSubject session,
        SeatWind wind,
        UUID seatedPlayerId,
        Location handBase,
        DisplayClickAction action,
        boolean ready,
        double acrossOffset,
        List<T> target,
        SeatActionAppender<T> appender
    ) {
        Component actionLabel = seatActionLabel(session, action, ready);
        if (isBlankActionLabel(actionLabel)) {
            return;
        }
        float actionWidth = seatActionInteractionWidth(actionLabel);
        Collection<UUID> actionViewers = seatActionPrivateViewers(seatedPlayerId, action);
        Location actionLabelLocation = seatActionLabelLocation(handBase, wind, acrossOffset);
        appender.append(
            target,
            new SeatActionRenderData(
                session,
                wind,
                action,
                actionLabel,
                actionWidth,
                actionViewers,
                actionLabelLocation
            )
        );
    }

    private static void appendSeatActionEntitiesFromData(List<Entity> spawned, SeatActionRenderData data) {
        spawned.add(DisplayEntities.spawnLabel(
            data.session().bukkitPlugin(),
            data.actionLabelLocation(),
            data.actionLabel(),
            seatActionLabelColor(data.action()),
            data.actionViewers(),
            Display.Billboard.FIXED,
            TableGeometry.seatYaw(data.wind()),
            0.0F,
            true
        ));
        Entity interaction = DisplayEntities.spawnInteraction(
            data.session().bukkitPlugin(),
            seatLabelInteractionLocation(data.actionLabelLocation()),
            data.actionWidth(),
            TableRenderConstants.SEAT_ACTION_INTERACTION_HEIGHT,
            data.action(),
            data.actionViewers()
        );
        if (interaction != null) {
            spawned.add(interaction);
        }
    }

    private static void appendSeatActionSpecsFromData(List<DisplayEntities.EntitySpec> specs, SeatActionRenderData data) {
        specs.add(DisplayEntities.labelSpec(
            data.actionLabelLocation(),
            data.actionLabel(),
            seatActionLabelColor(data.action()),
            data.actionViewers(),
            Display.Billboard.FIXED,
            TableGeometry.seatYaw(data.wind()),
            0.0F,
            true
        ));
        specs.add(DisplayEntities.interactionSpec(
            seatLabelInteractionLocation(data.actionLabelLocation()),
            data.actionWidth(),
            TableRenderConstants.SEAT_ACTION_INTERACTION_HEIGHT,
            data.action(),
            data.actionViewers()
        ));
    }

    private static Location seatActionLabelLocation(Location handBase, SeatWind wind, double acrossOffset) {
        return withSeatLabelDepthOffset(
            TableGeometry.add(handBase.clone(), TableGeometry.offsetAcrossSeat(wind, acrossOffset)).add(0.0D, TableRenderConstants.SEAT_ACTION_LABEL_Y_OFFSET, 0.0D),
            wind,
            -TableRenderConstants.SEAT_LABEL_DEPTH_OFFSET * 0.5D
        );
    }

    private static boolean isBlankActionLabel(Component label) {
        if (label == null) {
            return true;
        }
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(label);
        return plain == null || plain.isBlank();
    }

    private static Color seatLabelColor(SeatWind wind, boolean active) {
        if (active) {
            return TableRenderConstants.SEAT_LABEL_ACTIVE_BACKGROUND;
        }
        return switch (wind) {
            case EAST -> TableRenderConstants.SEAT_LABEL_EAST_BACKGROUND;
            case SOUTH -> TableRenderConstants.SEAT_LABEL_SOUTH_BACKGROUND;
            case WEST -> TableRenderConstants.SEAT_LABEL_WEST_BACKGROUND;
            case NORTH -> TableRenderConstants.SEAT_LABEL_NORTH_BACKGROUND;
        };
    }

    @FunctionalInterface
    public interface SeatActionAppender<T> {
        void append(List<T> target, SeatActionRenderData data);
    }

    public record SeatActionRenderData(
        TableRenderSubject session,
        SeatWind wind,
        DisplayClickAction action,
        Component actionLabel,
        float actionWidth,
        Collection<UUID> actionViewers,
        Location actionLabelLocation
    ) {
    }
}
