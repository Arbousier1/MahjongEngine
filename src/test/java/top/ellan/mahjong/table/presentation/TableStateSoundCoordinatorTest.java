package top.ellan.mahjong.table.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import top.ellan.mahjong.model.MahjongVariant;
import top.ellan.mahjong.table.core.TableSessionContext;
import top.ellan.mahjong.table.core.round.TableRoundController;

class TableStateSoundCoordinatorTest {
    @Test
    void playsDrawSoundWhenWallCountDropsAfterInitialSync() {
        TableSessionContext session = mock(TableSessionContext.class);
        TableRoundController controller = mock(TableRoundController.class);
        Player viewer = mock(Player.class);
        Location location = mock(Location.class);
        TableStateSoundCoordinator coordinator = new TableStateSoundCoordinator(session);

        when(session.hasRoundController()).thenReturn(true);
        when(session.roundControllerInternal()).thenReturn(controller);
        when(session.viewers()).thenReturn(List.of(viewer));
        when(session.isStarted()).thenReturn(true);
        when(session.currentSeat()).thenReturn(null);
        when(session.pendingReactionFingerprint()).thenReturn("");
        when(session.riichiFingerprintValue()).thenReturn("");
        when(session.lastResolution()).thenReturn(null);
        when(controller.started()).thenReturn(true);
        when(viewer.getLocation()).thenReturn(location);

        when(controller.remainingWallCount()).thenReturn(70);
        coordinator.syncStateSounds();
        verify(viewer, never()).playSound(location, "mahjong:tile_draw", 0.65F, 1.05F);

        when(controller.remainingWallCount()).thenReturn(69);
        coordinator.syncStateSounds();

        verify(viewer).playSound(location, "mahjong:tile_draw", 0.65F, 1.05F);
    }

    @Test
    void playsDiscardSoundOnDemand() {
        TableSessionContext session = mock(TableSessionContext.class);
        Player viewer = mock(Player.class);
        Location location = mock(Location.class);
        TableStateSoundCoordinator coordinator = new TableStateSoundCoordinator(session);

        when(session.viewers()).thenReturn(List.of(viewer));
        when(viewer.getLocation()).thenReturn(location);

        coordinator.playDiscardSound();

        verify(viewer).playSound(location, "mahjong:tile_discard", 0.75F, 1.05F);
    }

    @Test
    void routesDrawSoundByGbVariant() {
        TableSessionContext session = mock(TableSessionContext.class);
        TableRoundController controller = mock(TableRoundController.class);
        Player viewer = mock(Player.class);
        Location location = mock(Location.class);
        TableStateSoundCoordinator coordinator = new TableStateSoundCoordinator(session);

        when(session.currentVariant()).thenReturn(MahjongVariant.GB);
        when(session.hasRoundController()).thenReturn(true);
        when(session.roundControllerInternal()).thenReturn(controller);
        when(session.viewers()).thenReturn(List.of(viewer));
        when(session.isStarted()).thenReturn(true);
        when(session.currentSeat()).thenReturn(null);
        when(session.pendingReactionFingerprint()).thenReturn("");
        when(session.riichiFingerprintValue()).thenReturn("");
        when(session.lastResolution()).thenReturn(null);
        when(controller.started()).thenReturn(true);
        when(viewer.getLocation()).thenReturn(location);

        when(controller.remainingWallCount()).thenReturn(70);
        coordinator.syncStateSounds();
        when(controller.remainingWallCount()).thenReturn(69);
        coordinator.syncStateSounds();

        verify(viewer).playSound(location, "mahjong:gb_tile_draw", 0.65F, 1.05F);
    }

    @Test
    void routesDiscardSoundBySichuanVariant() {
        TableSessionContext session = mock(TableSessionContext.class);
        Player viewer = mock(Player.class);
        Location location = mock(Location.class);
        TableStateSoundCoordinator coordinator = new TableStateSoundCoordinator(session);

        when(session.currentVariant()).thenReturn(MahjongVariant.SICHUAN);
        when(session.viewers()).thenReturn(List.of(viewer));
        when(viewer.getLocation()).thenReturn(location);

        coordinator.playDiscardSound();

        verify(viewer).playSound(location, "mahjong:sichuan_tile_discard", 0.75F, 1.05F);
    }
}
