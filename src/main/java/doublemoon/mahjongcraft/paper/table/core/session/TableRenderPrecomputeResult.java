package doublemoon.mahjongcraft.paper.table.core;

import doublemoon.mahjongcraft.paper.render.layout.TableRenderLayout;
import java.util.Map;

public record TableRenderPrecomputeResult(
    TableRenderSnapshot snapshot,
    Map<String, String> regionFingerprints,
    TableRenderLayout.LayoutPlan layout
) {
}
