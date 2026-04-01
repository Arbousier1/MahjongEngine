package top.ellan.mahjong.table.render

import top.ellan.mahjong.bootstrap.MahjongPaperPlugin
import top.ellan.mahjong.metrics.InMemoryMetricsCollector
import top.ellan.mahjong.model.MahjongTile
import top.ellan.mahjong.model.SeatWind
import top.ellan.mahjong.render.display.DisplayEntities
import top.ellan.mahjong.render.layout.TableRenderLayout
import top.ellan.mahjong.render.scene.TableRenderer
import top.ellan.mahjong.table.core.MahjongTableSession
import top.ellan.mahjong.table.core.TableRenderPrecomputeResult
import top.ellan.mahjong.table.core.TableRenderSnapshot
import top.ellan.mahjong.table.core.TableSeatRenderSnapshot
import org.bukkit.entity.Entity
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.EnumMap
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TableRegionDisplayCoordinatorTest {
    @Test
    fun `applyRenderPrecompute prioritizes reaction and hand regions before turn and board regions`() {
        val session = mock(MahjongTableSession::class.java)
        val plugin = mock(MahjongPaperPlugin::class.java)
        val renderer = mock(TableRenderer::class.java)
        val fingerprintService = mock(TableRegionFingerprintService::class.java)
        val metrics = InMemoryMetricsCollector()
        val calls = mutableListOf<String>()

        `when`(session.plugin()).thenReturn(plugin)
        `when`(plugin.metrics()).thenReturn(metrics)
        `when`(session.renderer()).thenReturn(renderer)

        `when`(
            renderer.renderCenterLabelSpecs(
                eq(session),
                any(TableRenderSnapshot::class.java),
                any(TableRenderLayout.LayoutPlan::class.java)
            )
        ).thenAnswer {
            calls.add("reaction:center")
            emptySpecs()
        }
        `when`(
            renderer.renderSeatLabelSpecs(
                eq(session),
                any(TableSeatRenderSnapshot::class.java),
                any(TableRenderLayout.SeatLayoutPlan::class.java)
            )
        ).thenAnswer {
            val seat = it.getArgument<TableSeatRenderSnapshot>(1)
            calls.add("reaction:label:${seat.wind().name}")
            emptySpecs()
        }
        `when`(
            renderer.renderHandPublicTileSpecs(
                eq(session),
                any(TableRenderSnapshot::class.java),
                any(TableSeatRenderSnapshot::class.java),
                any(TableRenderLayout.SeatLayoutPlan::class.java),
                eq(0)
            )
        ).thenAnswer {
            calls.add("hand:public")
            emptySpecs()
        }
        `when`(
            renderer.renderHandPrivateTileSpecs(
                eq(session),
                any(TableSeatRenderSnapshot::class.java),
                any(TableRenderLayout.SeatLayoutPlan::class.java),
                eq(0)
            )
        ).thenAnswer {
            calls.add("hand:private")
            emptySpecs()
        }
        `when`(
            renderer.renderSticks(
                eq(session),
                any(TableSeatRenderSnapshot::class.java),
                any(TableRenderLayout.SeatLayoutPlan::class.java)
            )
        ).thenAnswer {
            calls.add("turn:sticks")
            emptyEntities()
        }
        `when`(renderer.renderDoraSpecs(eq(session), any(TableRenderLayout.LayoutPlan::class.java))).thenAnswer {
            calls.add("board:dora")
            emptySpecs()
        }
        `when`(renderer.renderTableStructure(eq(session), any(TableRenderLayout.LayoutPlan::class.java))).thenAnswer {
            calls.add("board:table")
            emptyEntities()
        }
        `when`(renderer.renderSeatVisual(eq(session), any(SeatWind::class.java))).thenAnswer {
            calls.add("background:visual")
            emptyEntities()
        }

        `when`(
            fingerprintService.handPublicTileFingerprint(
                any(TableRenderSnapshot::class.java),
                any(TableSeatRenderSnapshot::class.java),
                any(TableRenderLayout.SeatLayoutPlan::class.java),
                eq(0)
            )
        ).thenReturn(101L)
        `when`(
            fingerprintService.handPrivateTileFingerprint(
                any(TableSeatRenderSnapshot::class.java),
                any(TableRenderLayout.SeatLayoutPlan::class.java),
                eq(0)
            )
        ).thenReturn(102L)

        val coordinator = TableRegionDisplayCoordinator(session, fingerprintService, 7, 64)
        val deferred = coordinator.applyRenderPrecompute(precomputeResult())

        assertTrue(deferred, "Budget should defer lower-priority regions.")
        assertEquals(7, calls.size)
        assertTrue(calls.take(5).all { it.startsWith("reaction:") })
        assertTrue(calls.drop(5).all { it.startsWith("hand:") })
        assertFalse(calls.any { it.startsWith("turn:") || it.startsWith("board:") || it.startsWith("background:") })

        assertEquals(1L, metrics.counterValue("table.render.region.apply.calls"))
        assertEquals(7L, metrics.counterValue("table.render.region.apply.processed"))
        assertEquals(1L, metrics.counterValue("table.render.region.apply.deferred"))
        assertTrue(metrics.gaugeValue("table.render.region.queue.size") >= 7L)
        assertTrue(metrics.timerCount("table.render.region.apply.nanos") >= 1L)
    }

    private fun precomputeResult(): TableRenderPrecomputeResult {
        val eastPlayerId = UUID.fromString("00000000-0000-0000-0000-00000000e001")
        val seatSnapshots = EnumMap<SeatWind, TableSeatRenderSnapshot>(SeatWind::class.java)
        val seatPlans = EnumMap<SeatWind, TableRenderLayout.SeatLayoutPlan>(SeatWind::class.java)
        for (wind in SeatWind.values()) {
            val occupied = wind == SeatWind.EAST
            seatSnapshots[wind] = TableSeatRenderSnapshot(
                wind,
                if (occupied) eastPlayerId else null,
                if (occupied) "east-player" else "",
                "",
                0,
                false,
                false,
                false,
                true,
                "",
                -1,
                -1,
                0,
                emptyList(),
                if (occupied) listOf(MahjongTile.M1) else emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList()
            )
            seatPlans[wind] = TableRenderLayout.SeatLayoutPlan(
                wind,
                point(),
                point(),
                point(),
                point(),
                0.0F,
                if (occupied) listOf(point()) else emptyList(),
                if (occupied) listOf(point()) else emptyList(),
                emptyList(),
                emptyList(),
                emptyList()
            )
        }

        val snapshot = TableRenderSnapshot(
            1L,
            0L,
            "world",
            0.0,
            0.0,
            0.0,
            true,
            false,
            0,
            0,
            2,
            0,
            0,
            SeatWind.EAST,
            SeatWind.EAST,
            SeatWind.EAST,
            "",
            "",
            "",
            null,
            null,
            emptyList(),
            seatSnapshots
        )
        val layout = TableRenderLayout.LayoutPlan(
            point(),
            point(),
            point(),
            0.0,
            0.0,
            seatPlans,
            emptyList(),
            emptyList()
        )
        return TableRenderPrecomputeResult(snapshot, emptyMap(), layout)
    }

    private fun point() = TableRenderLayout.Point(0.0, 0.0, 0.0)

    private fun emptySpecs(): List<DisplayEntities.EntitySpec> = emptyList()

    private fun emptyEntities(): List<Entity> = emptyList()
}
