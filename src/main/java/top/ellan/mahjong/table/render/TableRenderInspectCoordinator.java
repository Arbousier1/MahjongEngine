package top.ellan.mahjong.table.render;

import top.ellan.mahjong.render.scene.TableRenderer;
import top.ellan.mahjong.table.core.MahjongTableSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class TableRenderInspectCoordinator {
    private final MahjongTableSession session;

    public TableRenderInspectCoordinator(MahjongTableSession session) {
        this.session = session;
    }

    public void inspectRender(Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        InspectRenderSnapshot snapshot = this.captureInspectRenderSnapshot(this.session.plugin().messages().resolveLocale(viewer));
        this.highlightTableDiagnostics(viewer, snapshot.table());
        for (StickInspectSnapshot stick : snapshot.sticks()) {
            this.highlightStickDiagnostics(viewer, stick);
            viewer.sendMessage(stick.message());
        }
        viewer.sendMessage(snapshot.summaryMessage());
        for (String debugLine : snapshot.debugLines()) {
            this.session.plugin().debug().log("render", debugLine);
        }
    }

    private InspectRenderSnapshot captureInspectRenderSnapshot(Locale locale) {
        TableRenderer.TableDiagnostics tableDiagnostics = this.session.renderer().inspectTable(this.session);
        TableInspectSnapshot table = this.captureTableInspectSnapshot(locale, tableDiagnostics);
        List<StickInspectSnapshot> sticks = new ArrayList<>();
        List<String> debugLines = new ArrayList<>();
        debugLines.add(table.debugLine());
        for (TableRenderer.StickDiagnostics stickDiagnostics : this.session.renderer().inspectSticks(this.session)) {
            StickInspectSnapshot stickSnapshot = this.captureStickInspectSnapshot(locale, stickDiagnostics);
            sticks.add(stickSnapshot);
            debugLines.add(stickSnapshot.debugLine());
        }
        return new InspectRenderSnapshot(table, List.copyOf(sticks), table.summaryMessage(), List.copyOf(debugLines));
    }

    private TableInspectSnapshot captureTableInspectSnapshot(Locale locale, TableRenderer.TableDiagnostics diagnostics) {
        String center = this.formatLocation(diagnostics.tableCenter());
        String anchor = this.formatLocation(diagnostics.visualAnchor());
        String spanX = formatDecimal(diagnostics.borderSpanX());
        String spanZ = formatDecimal(diagnostics.borderSpanZ());
        Component summaryMessage = this.session.plugin().messages().render(
            locale,
            "command.inspect_summary",
            this.session.plugin().messages().tag("table_id", this.session.id()),
            this.session.plugin().messages().tag("center", center),
            this.session.plugin().messages().tag("anchor", anchor),
            this.session.plugin().messages().tag("span_x", spanX),
            this.session.plugin().messages().tag("span_z", spanZ)
        );
        String debugLine = "Inspect table=" + this.session.id()
            + " center=" + center
            + " anchor=" + anchor
            + " span=" + spanX + "x" + spanZ;
        return new TableInspectSnapshot(diagnostics, summaryMessage, debugLine);
    }

    private StickInspectSnapshot captureStickInspectSnapshot(Locale locale, TableRenderer.StickDiagnostics diagnostics) {
        String kind = this.session.plugin().messages().plain(
            locale,
            diagnostics.riichi() ? "command.inspect.kind.riichi" : "command.inspect.kind.stick"
        );
        String wind = this.session.seatDisplayName(diagnostics.wind(), locale);
        String index = diagnostics.index() >= 0 ? "#" + diagnostics.index() : "";
        String location = this.formatLocation(diagnostics.center());
        String axis = this.session.plugin().messages().plain(locale, diagnostics.longOnX() ? "command.inspect.axis.x" : "command.inspect.axis.z");
        Component message = this.session.plugin().messages().render(
            locale,
            "command.inspect_stick",
            this.session.plugin().messages().tag("kind", kind),
            this.session.plugin().messages().tag("wind", wind),
            this.session.plugin().messages().tag("index", index),
            this.session.plugin().messages().tag("furniture_id", diagnostics.furnitureId()),
            this.session.plugin().messages().tag("location", location),
            this.session.plugin().messages().tag("axis", axis)
        );
        String debugLine = "Inspect table=" + this.session.id()
            + " stick=" + diagnostics.furnitureId()
            + " wind=" + diagnostics.wind().name()
            + " riichi=" + diagnostics.riichi()
            + " center=" + location
            + " axis=" + (diagnostics.longOnX() ? "X" : "Z");
        return new StickInspectSnapshot(diagnostics, message, debugLine);
    }

    private void highlightTableDiagnostics(Player viewer, TableInspectSnapshot diagnostics) {
        Location centerMarker = diagnostics.diagnostics().tableCenter().clone().add(0.0D, 1.02D, 0.0D);
        Location anchorMarker = diagnostics.diagnostics().visualAnchor().clone().add(0.0D, 1.02D, 0.0D);
        this.spawnMarker(viewer, centerMarker, Color.fromRGB(0, 255, 80), 18);
        this.spawnMarker(viewer, anchorMarker, Color.fromRGB(255, 70, 70), 18);

        double minX = diagnostics.diagnostics().visualAnchor().getX() - diagnostics.diagnostics().borderSpanX() / 2.0D;
        double maxX = diagnostics.diagnostics().visualAnchor().getX() + diagnostics.diagnostics().borderSpanX() / 2.0D;
        double minZ = diagnostics.diagnostics().visualAnchor().getZ() - diagnostics.diagnostics().borderSpanZ() / 2.0D;
        double maxZ = diagnostics.diagnostics().visualAnchor().getZ() + diagnostics.diagnostics().borderSpanZ() / 2.0D;
        World world = diagnostics.diagnostics().visualAnchor().getWorld();
        if (world == null) {
            return;
        }
        this.spawnMarker(viewer, new Location(world, minX, centerMarker.getY(), minZ), Color.fromRGB(255, 180, 60), 10);
        this.spawnMarker(viewer, new Location(world, maxX, centerMarker.getY(), minZ), Color.fromRGB(255, 180, 60), 10);
        this.spawnMarker(viewer, new Location(world, minX, centerMarker.getY(), maxZ), Color.fromRGB(255, 180, 60), 10);
        this.spawnMarker(viewer, new Location(world, maxX, centerMarker.getY(), maxZ), Color.fromRGB(255, 180, 60), 10);
    }

    private void highlightStickDiagnostics(Player viewer, StickInspectSnapshot diagnostics) {
        Color color = diagnostics.diagnostics().riichi() ? Color.fromRGB(255, 255, 255) : switch (diagnostics.diagnostics().stick()) {
            case P100 -> Color.fromRGB(255, 70, 70);
            case P5000 -> Color.fromRGB(255, 210, 60);
            case P10000 -> Color.fromRGB(60, 220, 120);
            default -> Color.fromRGB(240, 240, 240);
        };
        this.spawnMarker(viewer, diagnostics.diagnostics().center().clone().add(0.0D, 0.04D, 0.0D), color, 12);
        for (double offset = -1.0D; offset <= 1.0D; offset += 0.25D) {
            Location point = diagnostics.diagnostics().center().clone().add(
                diagnostics.diagnostics().longOnX() ? 0.20D * offset : 0.0D,
                0.04D,
                diagnostics.diagnostics().longOnX() ? 0.0D : 0.20D * offset
            );
            this.spawnMarker(viewer, point, color, 4);
        }
    }

    private void spawnMarker(Player viewer, Location location, Color color, int count) {
        viewer.spawnParticle(
            Particle.DUST,
            location,
            count,
            0.015D,
            0.015D,
            0.015D,
            0.0D,
            new Particle.DustOptions(color, 1.1F)
        );
    }

    private String formatLocation(Location location) {
        return formatDecimal(location.getX()) + "," + formatDecimal(location.getY()) + "," + formatDecimal(location.getZ());
    }

    private static String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private record InspectRenderSnapshot(
        TableInspectSnapshot table,
        List<StickInspectSnapshot> sticks,
        Component summaryMessage,
        List<String> debugLines
    ) {
    }

    private record TableInspectSnapshot(
        TableRenderer.TableDiagnostics diagnostics,
        Component summaryMessage,
        String debugLine
    ) {
    }

    private record StickInspectSnapshot(
        TableRenderer.StickDiagnostics diagnostics,
        Component message,
        String debugLine
    ) {
    }
}


