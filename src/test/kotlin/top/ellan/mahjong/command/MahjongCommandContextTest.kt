package top.ellan.mahjong.command

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.lenient
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import top.ellan.mahjong.debug.DebugService
import top.ellan.mahjong.i18n.MessageService
import top.ellan.mahjong.model.MahjongVariant
import top.ellan.mahjong.table.core.MahjongTableManager
import top.ellan.mahjong.table.core.MahjongTableSession
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the small decision helpers on MahjongCommandContext that subcommand
 * implementations rely on. We do not exercise every branch of the rendering
 * helpers; those are dominated by Adventure component plumbing better suited
 * to integration tests.
 */
class MahjongCommandContextTest {
    private lateinit var messages: MessageService
    private lateinit var tableManager: MahjongTableManager
    private lateinit var debug: DebugService
    private var reloadResult: String? = null
    private lateinit var context: MahjongCommandContext

    @BeforeEach
    fun setUp() {
        messages = mock(MessageService::class.java)
        tableManager = mock(MahjongTableManager::class.java)
        debug = mock(DebugService::class.java)
        reloadResult = null
        context = MahjongCommandContext(
            messages,
            tableManager,
            debug,
            mock(top.ellan.mahjong.runtime.AsyncService::class.java),
            mock(top.ellan.mahjong.runtime.ServerScheduler::class.java),
            { null },
            { reloadResult }
        )
    }

    @Test
    fun `requireAdmin returns true when sender has admin permission`() {
        val sender = mock(CommandSender::class.java)
        `when`(sender.hasPermission(MahjongCommandContext.ADMIN_PERMISSION)).thenReturn(true)

        assertTrue(context.requireAdmin(sender))
        // No admin_required message should be sent.
        verify(messages, org.mockito.Mockito.never()).send(sender, "command.admin_required")
    }

    @Test
    fun `requireAdmin returns false and notifies when sender lacks permission`() {
        val sender = mock(CommandSender::class.java)
        `when`(sender.hasPermission(MahjongCommandContext.ADMIN_PERMISSION)).thenReturn(false)

        assertFalse(context.requireAdmin(sender))
        verify(messages).send(sender, "command.admin_required")
    }

    @Test
    fun `requireTable returns the table when player is seated`() {
        val player = playerMock()
        val table = mock(MahjongTableSession::class.java)
        `when`(tableManager.tableFor(player.uniqueId)).thenReturn(table)

        assertEquals(table, context.requireTable(player))
        verify(messages, org.mockito.Mockito.never()).send(player, "command.not_in_table")
    }

    @Test
    fun `requireTable returns null and notifies when player is not seated`() {
        val player = playerMock()
        `when`(tableManager.tableFor(player.uniqueId)).thenReturn(null)

        assertNull(context.requireTable(player))
        verify(messages).send(player, "command.not_in_table")
    }

    @Test
    fun `requireTableManager rejects non-owner non-admin players`() {
        val player = playerMock()
        val table = mock(MahjongTableSession::class.java)
        `when`(player.hasPermission(MahjongCommandContext.ADMIN_PERMISSION)).thenReturn(false)
        `when`(table.isOwner(player.uniqueId)).thenReturn(false)

        assertFalse(context.requireTableManager(player, table))
        verify(messages).send(player, "command.table_owner_required")
    }

    @Test
    fun `requireTableManager allows table owner without admin`() {
        val player = playerMock()
        val table = mock(MahjongTableSession::class.java)
        `when`(player.hasPermission(MahjongCommandContext.ADMIN_PERMISSION)).thenReturn(false)
        `when`(table.isOwner(player.uniqueId)).thenReturn(true)

        assertTrue(context.requireTableManager(player, table))
    }

    @Test
    fun `requireTableManager allows admin who is not the owner`() {
        val player = playerMock()
        val table = mock(MahjongTableSession::class.java)
        `when`(player.hasPermission(MahjongCommandContext.ADMIN_PERMISSION)).thenReturn(true)
        `when`(table.isOwner(player.uniqueId)).thenReturn(false)

        assertTrue(context.requireTableManager(player, table))
    }

    @Test
    fun `parseMode parses canonical variant names case insensitively`() {
        assertEquals(MahjongVariant.RIICHI, context.parseMode("riichi"))
        assertEquals(MahjongVariant.GB, context.parseMode("GB"))
        assertEquals(MahjongVariant.SICHUAN, context.parseMode(" sichuan "))
    }

    @Test
    fun `parseMode returns null for unknown or blank input`() {
        assertNull(context.parseMode(""))
        assertNull(context.parseMode("   "))
        assertNull(context.parseMode(null))
        assertNull(context.parseMode("not_a_mode"))
    }

    @Test
    fun `parseIndex returns Optional containing the parsed integer`() {
        val parsed = context.parseIndex("12")
        assertTrue(parsed.isPresent)
        assertEquals(12, parsed.asInt)
    }

    @Test
    fun `parseIndex returns empty Optional for non-numeric input`() {
        assertFalse(context.parseIndex("abc").isPresent)
        assertFalse(context.parseIndex("").isPresent)
    }

    @Test
    fun `matchPrefix is case insensitive and respects insertion order`() {
        val matches = context.matchPrefix("Hi", listOf("hello", "high", "hint", "HIATUS", "low"))
        assertEquals(listOf("high", "hint", "HIATUS"), matches)
    }

    @Test
    fun `suggestModes returns variants matching the given prefix`() {
        val matches = context.suggestModes("g")
        assertEquals(listOf("GB"), matches)
    }

    @Test
    fun `handleReload denies non-admin senders before invoking reload`() {
        val sender = mock(CommandSender::class.java)
        `when`(sender.hasPermission(MahjongCommandContext.ADMIN_PERMISSION)).thenReturn(false)
        var invoked = false
        val tracking = MahjongCommandContext(
            messages,
            tableManager,
            debug,
            mock(top.ellan.mahjong.runtime.AsyncService::class.java),
            mock(top.ellan.mahjong.runtime.ServerScheduler::class.java),
            { null },
            { invoked = true; null }
        )

        tracking.handleReload(sender)

        verify(messages).send(sender, "command.admin_required")
        assertFalse(invoked, "reload supplier must not be invoked for non-admin senders")
    }

    @Test
    fun `handleReload announces success when reload supplier returns null`() {
        val sender = mock(CommandSender::class.java)
        `when`(sender.hasPermission(MahjongCommandContext.ADMIN_PERMISSION)).thenReturn(true)

        context.handleReload(sender)

        verify(messages).send(sender, "command.reload_success")
    }

    @Test
    fun `handleReload announces failure with the reason from supplier`() {
        val sender = mock(CommandSender::class.java)
        `when`(sender.hasPermission(MahjongCommandContext.ADMIN_PERMISSION)).thenReturn(true)
        reloadResult = "broken"
        // We just want to verify the failure branch executes; capturing the
        // varargs tag would require deeper Adventure-specific stubbing.
        val placeholder = mock(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver::class.java)
        `when`(messages.tag(org.mockito.ArgumentMatchers.eq("reason") ?: "reason", org.mockito.ArgumentMatchers.eq("broken") ?: "broken"))
            .thenReturn(placeholder)

        context.handleReload(sender)

        verify(messages).send(
            org.mockito.ArgumentMatchers.eq(sender) ?: sender,
            org.mockito.ArgumentMatchers.eq("command.reload_failed") ?: "command.reload_failed",
            org.mockito.ArgumentMatchers.any()
        )
    }

    private fun playerMock(): Player {
        val player = mock(Player::class.java)
        lenient().`when`(player.uniqueId).thenReturn(UUID.randomUUID())
        lenient().`when`(player.name).thenReturn("tester")
        return player
    }
}
