package top.ellan.mahjong.table.action;

import java.util.List;

public record PlayerActionSnapshot(
    PlayerActionPhase phase,
    String menuState,
    int selectedCount,
    int selectedTarget,
    boolean waitingOnOthers,
    List<PlayerActionEntry> actions
) {
    public PlayerActionSnapshot {
        menuState = menuState == null ? "" : menuState;
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public boolean hasActions() {
        return !this.actions.isEmpty();
    }
}
