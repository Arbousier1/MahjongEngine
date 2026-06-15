package top.ellan.mahjong.command

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.lenient
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import top.ellan.mahjong.command.subcommand.AddBotSubcommand
import top.ellan.mahjong.command.subcommand.BotMatchSubcommand
import top.ellan.mahjong.command.subcommand.CreateSubcommand
import top.ellan.mahjong.command.subcommand.HelpSubcommand
import top.ellan.mahjong.command.subcommand.LeaderboardSubcommand
import top.ellan.mahjong.command.subcommand.RankSubcommand
import top.ellan.mahjong.command.subcommand.RemoveBotSubcommand
import top.ellan.mahjong.command.subcommand.ReloadSubcommand
import top.ellan.mahjong.debug.DebugService
import top.ellan.mahjong.i18n.MessageService
import top.ellan.mahjong.model.MahjongVariant
import top.ellan.mahjong.runtime.AsyncService
import top.ellan.mahjong.runtime.ServerScheduler
import top.ellan.mahjong.table.core.MahjongTableManager
import top.ellan.mahjong.table.core.MahjongTableSession
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioural coverage for the most decision-heavy subcommands. We bypass the
 * Bukkit dispatch layer (already covered by MahjongCommandDispatchTest) and
 * invoke each MahjongSubcommand's executor directly with a mocked sender +
 * player so we can verify the message keys and table-manager calls each
 * branch produces.
 *
 * Trivial pass-through subcommands such as ReloadSubcommand and RankSubcommand
 * are exercised here as well to lock down the contract that they delegate
 * unchanged to MahjongCommandContext.
 */
class SubcommandBehaviourTest {
    private lateinit var messages: MessageService
    private lateinit var tableManager: MahjongTableManager
    private lateinit var debug: DebugService
    private lateinit var contextSpy: MahjongCommandContext
    private lateinit var senderPlayer: Player
    private lateinit var consoleSender: CommandSender

    @BeforeEach
    fun setUp() {
        messages = mock(MessageService::class.java)
        tableManager = mock(MahjongTableManager::class.java)
        debug = mock(DebugService::class.java)
        val real = MahjongCommandContext(
            messages,
            tableManager,
            debug,
            mock(AsyncService::class.java),
            mock(ServerScheduler::class.java),
            { null },
            { null }
        )
        contextSpy = Mockito.spy(real)
        senderPlayer = mock(Player::class.java)
        consoleSender = mock(CommandSender::class.java)
        lenient().`when`(senderPlayer.uniqueId).thenReturn(UUID.randomUUID())
        lenient().`when`(senderPlayer.name).thenReturn("tester")
    }

    @Test
    fun `HelpSubcommand delegates to context sendHelp`() {
        val helpCalled = AtomicBoolean()
        Mockito.doAnswer { helpCalled.set(true); null }.`when`(contextSpy).sendHelp(senderPlayer)

        HelpSubcommand(contextSpy).create().executor().execute(senderPlayer, senderPlayer, arrayOf("help"))

        assertTrue(helpCalled.get())
    }

    @Test
    fun `ReloadSubcommand delegates to context handleReload`() {
        val reloadCalled = AtomicBoolean()
        Mockito.doAnswer { reloadCalled.set(true); null }.`when`(contextSpy).handleReload(consoleSender)

        ReloadSubcommand(contextSpy).create().executor().execute(consoleSender, null, arrayOf("reload"))

        assertTrue(reloadCalled.get())
    }

    @Test
    fun `RankSubcommand delegates to context showRank`() {
        val shown = AtomicBoolean()
        Mockito.doAnswer { shown.set(true); null }.`when`(contextSpy).showRank(senderPlayer)

        RankSubcommand(contextSpy).create().executor().execute(senderPlayer, senderPlayer, arrayOf("rank"))

        assertTrue(shown.get())
    }

    @Test
    fun `AddBotSubcommand requires a current table and table-manager rights`() {
        // No table → requireTable sends not_in_table and returns null.
        `when`(tableManager.tableFor(senderPlayer.uniqueId)).thenReturn(null)

        AddBotSubcommand(contextSpy).create().executor().execute(senderPlayer, senderPlayer, arrayOf("addbot"))

        verify(messages).send(senderPlayer, "command.not_in_table")
        verify(messages, never()).send(senderPlayer, "command.bot_added")
        verify(messages, never()).send(senderPlayer, "command.bot_add_failed")
    }

    @Test
    fun `AddBotSubcommand reports success when table accepts the bot`() {
        val table = tableMockForOwner(senderPlayer)
        `when`(table.addBot()).thenReturn(true)

        AddBotSubcommand(contextSpy).create().executor().execute(senderPlayer, senderPlayer, arrayOf("addbot"))

        verify(messages).send(senderPlayer, "command.bot_added")
        verify(messages, never()).send(senderPlayer, "command.bot_add_failed")
    }

    @Test
    fun `AddBotSubcommand reports failure when table rejects the bot`() {
        val table = tableMockForOwner(senderPlayer)
        `when`(table.addBot()).thenReturn(false)

        AddBotSubcommand(contextSpy).create().executor().execute(senderPlayer, senderPlayer, arrayOf("addbot"))

        verify(messages).send(senderPlayer, "command.bot_add_failed")
    }

    @Test
    fun `RemoveBotSubcommand reports failure when table rejects the removal`() {
        val table = tableMockForOwner(senderPlayer)
        `when`(table.removeBot()).thenReturn(false)

        RemoveBotSubcommand(contextSpy).create().executor().execute(senderPlayer, senderPlayer, arrayOf("removebot"))

        verify(messages).send(senderPlayer, "command.bot_remove_failed")
    }

    @Test
    fun `BotMatchSubcommand falls back to MAJSOUL_HANCHAN when no preset is supplied`() {
        val newTable = mock(MahjongTableSession::class.java)
        lenient().`when`(newTable.id()).thenReturn("T-7")
        `when`(tableManager.createBotMatch(senderPlayer, "MAJSOUL_HANCHAN")).thenReturn(newTable)

        BotMatchSubcommand(contextSpy).create().executor().execute(senderPlayer, senderPlayer, arrayOf("botmatch"))

        verify(tableManager).createBotMatch(senderPlayer, "MAJSOUL_HANCHAN")
    }

    @Test
    fun `BotMatchSubcommand passes through the supplied preset`() {
        val newTable = mock(MahjongTableSession::class.java)
        lenient().`when`(newTable.id()).thenReturn("T-2")
        `when`(tableManager.createBotMatch(senderPlayer, "tonpuu")).thenReturn(newTable)

        BotMatchSubcommand(contextSpy).create().executor().execute(senderPlayer, senderPlayer, arrayOf("botmatch", "tonpuu"))

        verify(tableManager).createBotMatch(senderPlayer, "tonpuu")
    }

    @Test
    fun `BotMatchSubcommand notifies player when manager rejects the request`() {
        `when`(tableManager.createBotMatch(senderPlayer, "MAJSOUL_HANCHAN")).thenReturn(null)

        BotMatchSubcommand(contextSpy).create().executor().execute(senderPlayer, senderPlayer, arrayOf("botmatch"))

        verify(messages).send(senderPlayer, "command.botmatch_failed_in_table")
    }

    @Test
    fun `BotMatchSubcommand suggestions list known presets only on the second arg`() {
        val sub = BotMatchSubcommand(contextSpy).create()
        // No suggestions for the first arg slot (which is the subcommand keyword itself).
        assertTrue(sub.suggestions().suggest(senderPlayer, arrayOf("botmatch")).isEmpty())
        val matches = sub.suggestions().suggest(senderPlayer, arrayOf("botmatch", "ton"))
        assertEquals(listOf("tonpuu"), matches)
    }

    @Test
    fun `CreateSubcommand reports invalid_location when manager rejects with that reason`() {
        val failure = MahjongTableManager.CreateTableFailure(
            MahjongTableManager.CreateTableFailureReason.INVALID_LOCATION,
            null, null, null, null
        )
        `when`(tableManager.createTable(senderPlayer))
            .thenReturn(MahjongTableManager.CreateTableResult(null, failure))

        CreateSubcommand(contextSpy).create().executor().execute(senderPlayer, senderPlayer, arrayOf("create"))

        verify(messages).send(senderPlayer, "command.create_failed_invalid_location")
    }

    @Test
    fun `CreateSubcommand reports too_close failure with the offending table id`() {
        val failure = MahjongTableManager.CreateTableFailure(
            MahjongTableManager.CreateTableFailureReason.TOO_CLOSE_TO_TABLE,
            "T-99", null, null, null
        )
        `when`(tableManager.createTable(senderPlayer))
            .thenReturn(MahjongTableManager.CreateTableResult(null, failure))

        CreateSubcommand(contextSpy).create().executor().execute(senderPlayer, senderPlayer, arrayOf("create"))

        verify(messages).send(
            org.mockito.ArgumentMatchers.eq(senderPlayer) ?: senderPlayer,
            org.mockito.ArgumentMatchers.eq("command.create_failed_too_close") ?: "command.create_failed_too_close",
            org.mockito.ArgumentMatchers.any()
        )
    }

    @Test
    fun `CreateSubcommand announces table id on success`() {
        val table = mock(MahjongTableSession::class.java)
        lenient().`when`(table.id()).thenReturn("T-1")
        `when`(tableManager.createTable(senderPlayer))
            .thenReturn(MahjongTableManager.CreateTableResult(table, null))

        CreateSubcommand(contextSpy).create().executor().execute(senderPlayer, senderPlayer, arrayOf("create"))

        verify(messages).send(
            org.mockito.ArgumentMatchers.eq(senderPlayer) ?: senderPlayer,
            org.mockito.ArgumentMatchers.eq("command.created_table") ?: "command.created_table",
            org.mockito.ArgumentMatchers.any()
        )
    }

    @Test
    fun `LeaderboardSubcommand reports usage when the supplied mode is invalid`() {
        LeaderboardSubcommand(contextSpy).create().executor().execute(senderPlayer, senderPlayer, arrayOf("leaderboard", "not_a_mode"))

        verify(messages).send(senderPlayer, "command.leaderboard_usage")
    }

    @Test
    fun `LeaderboardSubcommand passes parsed mode to context showLeaderboard`() {
        val captured = AtomicBoolean()
        Mockito.doAnswer { invocation ->
            val mode = invocation.getArgument<MahjongVariant?>(1)
            assertEquals(MahjongVariant.GB, mode)
            captured.set(true)
            null
        }.`when`(contextSpy).showLeaderboard(any(), any())

        LeaderboardSubcommand(contextSpy).create().executor().execute(senderPlayer, senderPlayer, arrayOf("leaderboard", "gb"))

        assertTrue(captured.get())
    }

    @Test
    fun `LeaderboardSubcommand passes a null mode when no argument is provided`() {
        val nullMode = AtomicBoolean()
        Mockito.doAnswer { invocation ->
            val mode = invocation.getArgument<MahjongVariant?>(1)
            assertEquals(null, mode)
            nullMode.set(true)
            null
        }.`when`(contextSpy).showLeaderboard(any(), any())

        LeaderboardSubcommand(contextSpy).create().executor().execute(senderPlayer, senderPlayer, arrayOf("leaderboard"))

        assertTrue(nullMode.get())
    }

    /**
     * Wire the table mock so that requireTable returns it and requireTableManager
     * accepts the calling player as the owner. This is the common precondition
     * for every subcommand that touches a table.
     */
    private fun tableMockForOwner(player: Player): MahjongTableSession {
        val table = mock(MahjongTableSession::class.java)
        `when`(tableManager.tableFor(player.uniqueId)).thenReturn(table)
        `when`(table.isOwner(player.uniqueId)).thenReturn(true)
        return table
    }
}
