package top.ellan.mahjong.render.scene;

import top.ellan.mahjong.model.SeatWind;
import top.ellan.mahjong.render.display.DisplayClickAction;
import top.ellan.mahjong.render.display.DisplayEntities;
import top.ellan.mahjong.render.TableRenderSubject;
import top.ellan.mahjong.render.snapshot.TableViewerActionOverlaySnapshot;
import top.ellan.mahjong.render.snapshot.TableViewerActionButtonSnapshot;
import top.ellan.mahjong.render.snapshot.TableViewerOverlaySnapshot;
import top.ellan.mahjong.render.snapshot.TableViewerPromptSnapshot;
import top.ellan.mahjong.render.snapshot.TableSpectatorSeatOverlaySnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Renders viewer-specific overlays: the main overlay label, spectator seat
 * overlays, viewer prompts and action buttons.
 */
public final class ViewerOverlayRenderer {
    private ViewerOverlayRenderer() {
    }

    public static List<Entity> renderViewerOverlay(TableRenderSubject session, Player viewer) {
        Location center = TableGeometry.displayCenter(session);
        UUID viewerId = viewer.getUniqueId();
        List<Entity> spawned = new ArrayList<>(session.isSpectator(viewerId) ? 5 : 1);
        if (session.isStarted() || session.isSpectator(viewerId)) {
            spawned.add(DisplayEntities.spawnLabel(
                session.bukkitPlugin(),
                center.clone().add(0.0D, TableRenderConstants.VIEWER_OVERLAY_LABEL_Y_OFFSET + TableRenderConstants.FLOATING_TEXT_Y_OFFSET, 0.0D),
                session.viewerOverlay(viewer),
                Color.fromARGB(84, 12, 12, 12),
                List.of(viewerId)
            ));
        }
        if (session.isSpectator(viewerId)) {
            for (SeatWind wind : SeatWind.values()) {
                spawned.add(DisplayEntities.spawnLabel(
                    session.bukkitPlugin(),
                    TableGeometry.add(TableGeometry.handDirectionBase(center, wind), TableGeometry.offsetAcrossSeat(wind, TableRenderConstants.SPECTATOR_OVERLAY_ACROSS_OFFSET)).add(0.0D, TableRenderConstants.SPECTATOR_OVERLAY_Y_OFFSET + TableRenderConstants.FLOATING_TEXT_Y_OFFSET, 0.0D),
                    session.spectatorSeatOverlay(viewer, wind),
                    Color.fromARGB(92, 14, 14, 18),
                    List.of(viewerId)
                ));
            }
        }
        return spawned;
    }

    public static List<Entity> renderViewerOverlay(TableRenderSubject session, TableViewerOverlaySnapshot snapshot) {
        Location center = TableGeometry.displayCenter(session);
        List<Entity> spawned = new ArrayList<>(snapshot.spectatorSeatOverlays().isEmpty() ? 1 : 1 + snapshot.spectatorSeatOverlays().size());
        if (session.isStarted() || snapshot.spectator()) {
            spawned.add(DisplayEntities.spawnLabel(
                session.bukkitPlugin(),
                center.clone().add(0.0D, TableRenderConstants.VIEWER_OVERLAY_LABEL_Y_OFFSET + TableRenderConstants.FLOATING_TEXT_Y_OFFSET, 0.0D),
                snapshot.overlay(),
                Color.fromARGB(84, 12, 12, 12),
                List.of(snapshot.viewerId())
            ));
        }
        if (snapshot.spectator()) {
            for (TableSpectatorSeatOverlaySnapshot seatOverlay : snapshot.spectatorSeatOverlays()) {
                spawned.add(DisplayEntities.spawnLabel(
                    session.bukkitPlugin(),
                    TableGeometry.add(TableGeometry.handDirectionBase(center, seatOverlay.wind()), TableGeometry.offsetAcrossSeat(seatOverlay.wind(), TableRenderConstants.SPECTATOR_OVERLAY_ACROSS_OFFSET)).add(0.0D, TableRenderConstants.SPECTATOR_OVERLAY_Y_OFFSET + TableRenderConstants.FLOATING_TEXT_Y_OFFSET, 0.0D),
                    seatOverlay.overlay(),
                    Color.fromARGB(92, 14, 14, 18),
                    List.of(snapshot.viewerId())
                ));
            }
        }
        return spawned;
    }

    public static List<DisplayEntities.EntitySpec> renderViewerOverlaySpecs(TableRenderSubject session, TableViewerOverlaySnapshot snapshot) {
        Location center = TableGeometry.displayCenter(session);
        List<DisplayEntities.EntitySpec> specs = new ArrayList<>(
            snapshot.spectatorSeatOverlays().isEmpty() ? 1 : 1 + snapshot.spectatorSeatOverlays().size()
        );
        if (session.isStarted() || snapshot.spectator()) {
            specs.add(DisplayEntities.labelSpec(
                center.clone().add(0.0D, TableRenderConstants.VIEWER_OVERLAY_LABEL_Y_OFFSET + TableRenderConstants.FLOATING_TEXT_Y_OFFSET, 0.0D),
                snapshot.overlay(),
                Color.fromARGB(84, 12, 12, 12),
                List.of(snapshot.viewerId()),
                Display.Billboard.CENTER,
                0.0F,
                0.0F,
                true
            ));
        }
        if (snapshot.spectator()) {
            for (TableSpectatorSeatOverlaySnapshot seatOverlay : snapshot.spectatorSeatOverlays()) {
                specs.add(DisplayEntities.labelSpec(
                    TableGeometry.add(TableGeometry.handDirectionBase(center, seatOverlay.wind()), TableGeometry.offsetAcrossSeat(seatOverlay.wind(), TableRenderConstants.SPECTATOR_OVERLAY_ACROSS_OFFSET)).add(0.0D, TableRenderConstants.SPECTATOR_OVERLAY_Y_OFFSET + TableRenderConstants.FLOATING_TEXT_Y_OFFSET, 0.0D),
                    seatOverlay.overlay(),
                    Color.fromARGB(92, 14, 14, 18),
                    List.of(snapshot.viewerId()),
                    Display.Billboard.CENTER,
                    0.0F,
                    0.0F,
                    true
                ));
            }
        }
        return List.copyOf(specs);
    }

    public static List<DisplayEntities.EntitySpec> renderViewerPromptSpecs(TableRenderSubject session, TableViewerPromptSnapshot snapshot) {
        if (snapshot == null || !snapshot.visible()) {
            return List.of();
        }
        Location center = TableGeometry.displayCenter(session);
        return List.of(DisplayEntities.labelSpec(
            center.clone().add(0.0D, 0.62D + TableRenderConstants.FLOATING_TEXT_Y_OFFSET, 0.0D),
            snapshot.prompt(),
            Color.fromARGB(72, 20, 18, 4),
            List.of(snapshot.viewerId()),
            Display.Billboard.CENTER,
            0.0F,
            0.0F,
            true
        ));
    }

    public static List<DisplayEntities.EntitySpec> renderViewerActionOverlaySpecs(TableRenderSubject session, TableViewerActionOverlaySnapshot snapshot) {
        if (snapshot == null || snapshot.actionButtons().isEmpty()) {
            return List.of();
        }
        Location center = TableGeometry.displayCenter(session);
        List<DisplayEntities.EntitySpec> specs = new ArrayList<>(snapshot.actionButtons().size() * 2);
        appendViewerActionButtonSpecs(session, snapshot.viewerId(), snapshot.actionButtons(), center, specs);
        return List.copyOf(specs);
    }

    private static void appendViewerActionButtonSpecs(
        TableRenderSubject session,
        UUID viewerId,
        List<TableViewerActionButtonSnapshot> actionButtons,
        Location center,
        List<DisplayEntities.EntitySpec> specs
    ) {
        if (actionButtons.isEmpty()) {
            return;
        }
        SeatWind viewerSeat = session.seatOf(viewerId);
        Location actionAnchor = viewerSeat == null
            ? center.clone().add(0.0D, TableRenderConstants.OVERLAY_ACTION_Y_OFFSET, 0.0D)
            : TableGeometry.add(TableGeometry.handDirectionBase(center, viewerSeat), TableGeometry.offsetTowardTableCenter(viewerSeat, 0.42D)).add(0.0D, TableRenderConstants.OVERLAY_ACTION_Y_OFFSET, 0.0D);
        float yaw = viewerSeat == null ? 0.0F : TableGeometry.seatYaw(viewerSeat);
        int row = 0;
        for (int rowStart = 0; rowStart < actionButtons.size(); rowStart += TableRenderConstants.OVERLAY_ACTION_BUTTONS_PER_ROW) {
            int rowEnd = Math.min(actionButtons.size(), rowStart + TableRenderConstants.OVERLAY_ACTION_BUTTONS_PER_ROW);
            List<TableViewerActionButtonSnapshot> rowButtons = actionButtons.subList(rowStart, rowEnd);
            double rowWidth = 0.0D;
            for (TableViewerActionButtonSnapshot rowButton : rowButtons) {
                rowWidth += viewerActionButtonWidth(rowButton);
            }
            rowWidth += Math.max(0, rowButtons.size() - 1) * TableRenderConstants.OVERLAY_ACTION_BUTTON_GAP;
            double cursor = -rowWidth / 2.0D;
            for (TableViewerActionButtonSnapshot button : rowButtons) {
                double buttonWidth = viewerActionButtonWidth(button);
                double xOffset = cursor + buttonWidth / 2.0D;
                cursor += buttonWidth + TableRenderConstants.OVERLAY_ACTION_BUTTON_GAP;
                Location labelLocation = viewerSeat == null
                    ? actionAnchor.clone().add(xOffset, -row * TableRenderConstants.VIEWER_ACTION_BUTTON_ROW_STEP, 0.0D)
                    : TableGeometry.add(actionAnchor.clone().add(0.0D, -row * TableRenderConstants.VIEWER_ACTION_BUTTON_ROW_STEP, 0.0D), TableGeometry.offsetAcrossSeat(viewerSeat, xOffset));
                specs.add(DisplayEntities.labelSpec(
                    labelLocation,
                    net.kyori.adventure.text.Component.text("[" + button.label() + "]", button.color()),
                    Color.fromARGB(60, 0, 0, 0),
                    List.of(viewerId),
                    Display.Billboard.FIXED,
                    yaw,
                    0.0F,
                    true
                ));
                specs.add(DisplayEntities.interactionSpec(
                    labelLocation.clone().subtract(0.0D, TableRenderConstants.LABEL_INTERACTION_Y_OFFSET, 0.0D),
                    (float) buttonWidth,
                    TableRenderConstants.OVERLAY_ACTION_BUTTON_HEIGHT,
                    DisplayClickAction.playerCommand(session.id(), viewerId, button.command()),
                    List.of(viewerId)
                ));
            }
            row++;
        }
    }

    private static float viewerActionButtonWidth(TableViewerActionButtonSnapshot button) {
        if (button == null) {
            return TableRenderConstants.OVERLAY_ACTION_BUTTON_SPACING;
        }
        float estimated = estimateActionLabelWidth(button.label());
        return Math.max(Math.max(TableRenderConstants.OVERLAY_ACTION_BUTTON_SPACING, button.hitboxWidth()), estimated);
    }

    private static float estimateActionLabelWidth(String label) {
        if (label == null || label.isBlank()) {
            return TableRenderConstants.ACTION_LABEL_MIN_WIDTH;
        }
        int visualUnits = 0;
        for (int i = 0; i < label.length(); i++) {
            char ch = label.charAt(i);
            if (Character.isWhitespace(ch)) {
                visualUnits += 1;
                continue;
            }
            visualUnits += isWideGlyph(ch) ? 2 : 1;
        }
        float estimated = TableRenderConstants.ACTION_LABEL_BASE_WIDTH + visualUnits * TableRenderConstants.ACTION_LABEL_WIDTH_PER_UNIT;
        return Math.max(TableRenderConstants.ACTION_LABEL_MIN_WIDTH, Math.min(TableRenderConstants.ACTION_LABEL_MAX_WIDTH, estimated));
    }

    private static boolean isWideGlyph(char ch) {
        return (ch >= 0x2E80 && ch <= 0x9FFF)
            || (ch >= 0xF900 && ch <= 0xFAFF)
            || (ch >= 0xFF01 && ch <= 0xFF60)
            || (ch >= 0xFFE0 && ch <= 0xFFE6);
    }
}
