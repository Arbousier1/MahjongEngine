package doublemoon.mahjongcraft.paper.table.presentation

import doublemoon.mahjongcraft.paper.bootstrap.MahjongPaperPlugin
import doublemoon.mahjongcraft.paper.i18n.MessageService
import doublemoon.mahjongcraft.paper.table.core.MahjongTableSession
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.util.Locale
import java.util.UUID
import kotlin.test.Test

class TableViewerPresentationCoordinatorTest {
    @Test
    fun `overlay refresh is throttled between periodic polls`() {
        val session = mock(MahjongTableSession::class.java)
        val coordinator = TableViewerPresentationCoordinator(session)
        setField(coordinator, "viewerHudDirty", false)

        val viewerId = UUID.fromString("00000000-0000-0000-0000-000000000101")
        val viewer = mock(Player::class.java)
        `when`(viewer.uniqueId).thenReturn(viewerId)

        val snapshot = MahjongTableSession.ViewerOverlaySnapshot(
            viewerId,
            "viewer-overlay:$viewerId",
            false,
            Component.empty(),
            emptyList(),
            "overlay-fingerprint"
        )

        `when`(session.viewers()).thenReturn(listOf(viewer))
        `when`(session.captureViewerOverlaySnapshot(viewer)).thenReturn(snapshot)
        `when`(session.viewerOverlayRegionKeys()).thenReturn(listOf(snapshot.regionKey()))

        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<Int> { Bukkit.getCurrentTick() }.thenReturn(100, 105, 120)

            coordinator.flushIfNeeded()
            coordinator.flushIfNeeded()
            coordinator.flushIfNeeded()
        }

        verify(session, times(2)).updateViewerOverlayRegion(snapshot)
    }

    @Test
    fun `hud refresh is throttled between periodic polls`() {
        val session = mock(MahjongTableSession::class.java)
        val plugin = mock(MahjongPaperPlugin::class.java)
        val messages = mock(MessageService::class.java)
        val coordinator = TableViewerPresentationCoordinator(session)
        setField(coordinator, "viewerOverlayDirty", false)
        setField(coordinator, "viewerHudDirty", false)

        val viewerId = UUID.fromString("00000000-0000-0000-0000-000000000202")
        val viewer = mock(Player::class.java)
        `when`(viewer.uniqueId).thenReturn(viewerId)

        val hudSnapshot = MahjongTableSession.ViewerHudSnapshot(
            Component.text("hud"),
            0.75F,
            BossBar.Color.BLUE,
            "hud-state"
        )
        val existingBar = mock(BossBar::class.java)

        @Suppress("UNCHECKED_CAST")
        val bars = getField(coordinator, "viewerHudBars") as MutableMap<UUID, BossBar>
        bars[viewerId] = existingBar

        `when`(session.viewers()).thenReturn(listOf(viewer))
        `when`(session.plugin()).thenReturn(plugin)
        `when`(plugin.messages()).thenReturn(messages)
        `when`(messages.resolveLocale(viewer)).thenReturn(Locale.SIMPLIFIED_CHINESE)
        `when`(session.captureViewerHudSnapshot(Locale.SIMPLIFIED_CHINESE, viewerId)).thenReturn(hudSnapshot)

        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<Int> { Bukkit.getCurrentTick() }.thenReturn(200, 205, 220)

            coordinator.flushIfNeeded()
            coordinator.flushIfNeeded()
            coordinator.flushIfNeeded()
        }

        verify(session, times(2)).captureViewerHudSnapshot(Locale.SIMPLIFIED_CHINESE, viewerId)
    }

    private fun setField(target: Any, fieldName: String, value: Any) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun getField(target: Any, fieldName: String): Any? {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(target)
    }
}
