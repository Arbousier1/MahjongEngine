package top.ellan.mahjong.table.presentation

import top.ellan.mahjong.bootstrap.MahjongPaperPlugin
import top.ellan.mahjong.i18n.MessageService
import top.ellan.mahjong.model.SeatWind
import top.ellan.mahjong.table.core.MahjongTableSession
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class TablePublicTextFactoryTest {
    @Test
    fun `seat display name follows dealer rotation`() {
        val plugin = mock(MahjongPaperPlugin::class.java)
        val session = mock(MahjongTableSession::class.java)
        val messages = MessageService()
        val factory = TablePublicTextFactory(session)

        `when`(session.plugin()).thenReturn(plugin)
        `when`(plugin.messages()).thenReturn(messages)
        `when`(session.hasRoundController()).thenReturn(true)
        `when`(session.dealerSeat()).thenReturn(SeatWind.SOUTH)

        assertEquals("North", factory.seatDisplayName(SeatWind.EAST, Locale.ENGLISH))
        assertEquals("East", factory.seatDisplayName(SeatWind.SOUTH, Locale.ENGLISH))
        assertEquals("South", factory.seatDisplayName(SeatWind.WEST, Locale.ENGLISH))
        assertEquals("West", factory.seatDisplayName(SeatWind.NORTH, Locale.ENGLISH))
    }

    @Test
    fun `seat display name stays unchanged before a round controller exists`() {
        val plugin = mock(MahjongPaperPlugin::class.java)
        val session = mock(MahjongTableSession::class.java)
        val messages = MessageService()
        val factory = TablePublicTextFactory(session)

        `when`(session.plugin()).thenReturn(plugin)
        `when`(plugin.messages()).thenReturn(messages)
        `when`(session.hasRoundController()).thenReturn(false)

        assertEquals("East", factory.seatDisplayName(SeatWind.EAST, Locale.ENGLISH))
        assertEquals("South", factory.seatDisplayName(SeatWind.SOUTH, Locale.ENGLISH))
        assertEquals("West", factory.seatDisplayName(SeatWind.WEST, Locale.ENGLISH))
        assertEquals("North", factory.seatDisplayName(SeatWind.NORTH, Locale.ENGLISH))
    }
}

