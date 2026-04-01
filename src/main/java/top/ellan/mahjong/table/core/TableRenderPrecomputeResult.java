package top.ellan.mahjong.table.core;

import top.ellan.mahjong.render.layout.TableRenderLayout;
import java.util.Map;

public record TableRenderPrecomputeResult(
    TableRenderSnapshot snapshot,
    Map<String, Long> regionFingerprints,
    TableRenderLayout.LayoutPlan layout
) {
}

