package top.ellan.mahjong.db

import top.ellan.mahjong.bootstrap.MahjongPaperPlugin
import top.ellan.mahjong.config.PluginSettings
import top.ellan.mahjong.debug.DebugService
import top.ellan.mahjong.model.MahjongTile as DisplayMahjongTile
import top.ellan.mahjong.riichi.RoundResolution
import top.ellan.mahjong.riichi.model.DoubleYakuman
import top.ellan.mahjong.riichi.model.ExhaustiveDraw
import top.ellan.mahjong.riichi.model.MahjongRule
import top.ellan.mahjong.riichi.model.MahjongTile
import top.ellan.mahjong.riichi.model.ScoreItem
import top.ellan.mahjong.riichi.model.ScoreSettlement
import top.ellan.mahjong.riichi.model.YakuSettlement
import top.ellan.mahjong.runtime.AsyncService
import top.ellan.mahjong.table.core.MahjongVariant
import top.ellan.mahjong.table.core.MahjongTableSession
import top.ellan.mahjong.table.core.TableFinalStanding
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.nio.file.Files
import java.util.UUID
import java.util.logging.Logger
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DatabaseIntegrationTest {
    private lateinit var tempDir: java.nio.file.Path
    private lateinit var plugin: MahjongPaperPlugin
    private lateinit var settings: PluginSettings
    private lateinit var service: DatabaseService

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("mahjongpaper-db-test")
        plugin = mock(MahjongPaperPlugin::class.java)
        settings = mock(PluginSettings::class.java)

        `when`(plugin.getDataFolder()).thenReturn(tempDir.toFile())
        `when`(plugin.getLogger()).thenReturn(Logger.getLogger("DatabaseIntegrationTest"))
        `when`(plugin.settings()).thenReturn(settings)
        `when`(plugin.debug()).thenReturn(mock(DebugService::class.java))
        `when`(plugin.async()).thenReturn(AsyncService(Logger.getLogger("DatabaseIntegrationTest-Async")))
        `when`(settings.rankingEnabled()).thenReturn(true)
        `when`(settings.rankingEastRoom()).thenReturn("SILVER")
        `when`(settings.rankingSouthRoom()).thenReturn("GOLD")

        val config = YamlConfiguration()
        config.set("database.pool.maxSize", 2)
        config.set("database.pool.minIdle", 1)
        config.set("database.connection.type", "h2")
        config.set("database.h2.path", "data/test-db")
        service = DatabaseService(plugin, PluginSettings.from(config).database())
    }

    @AfterEach
    fun tearDown() {
        service.close()
        plugin.async().close()
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `persist round result sync writes round and player settlement rows`() {
        val session = mock(MahjongTableSession::class.java)
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000011")
        `when`(session.id()).thenReturn("TABLE01")
        `when`(session.roundDisplay()).thenReturn("East 1")
        `when`(session.dealerName()).thenReturn("Dealer")
        `when`(session.remainingWallCount()).thenReturn(42)
        `when`(session.dicePoints()).thenReturn(7)
        `when`(session.doraIndicators()).thenReturn(listOf(DisplayMahjongTile.M1, DisplayMahjongTile.P1))
        `when`(session.uraDoraIndicators()).thenReturn(listOf(DisplayMahjongTile.S1))
        `when`(session.displayName(playerId)).thenReturn("Alice")

        val resolution = RoundResolution(
            title = "Ron",
            scoreSettlement = ScoreSettlement(
                "Ron",
                listOf(ScoreItem("Alice", playerId.toString(), 25000, 8000))
            ),
            yakuSettlements = listOf(
                YakuSettlement(
                    displayName = "Alice",
                    uuid = playerId.toString(),
                    yakuList = listOf("reach"),
                    yakumanList = emptyList(),
                    doubleYakumanList = listOf(DoubleYakuman.SUANKO_TANKI),
                    riichi = true,
                    winningTile = MahjongTile.M1,
                    hands = listOf(MahjongTile.M1, MahjongTile.M2, MahjongTile.M3),
                    fuuroList = listOf(true to listOf(MahjongTile.P1, MahjongTile.P2, MahjongTile.P3)),
                    doraIndicators = listOf(MahjongTile.M1),
                    uraDoraIndicators = listOf(MahjongTile.P1),
                    fu = 40,
                    han = 3,
                    score = 7700
                )
            ),
            draw = ExhaustiveDraw.NORMAL
        )

        service.persistRoundResultSync(session, resolution)

        withConnection(service) { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT table_id, resolution_title, round_display, dealer_name, wall_count, dice_points, dora_indicators FROM round_history").use { result ->
                    assertTrue(result.next())
                    assertEquals("TABLE01", result.getString("table_id"))
                    assertEquals("Ron", result.getString("resolution_title"))
                    assertEquals("East 1", result.getString("round_display"))
                    assertEquals("Dealer", result.getString("dealer_name"))
                    assertEquals(42, result.getInt("wall_count"))
                    assertEquals(7, result.getInt("dice_points"))
                    assertEquals("m1 p1", result.getString("dora_indicators"))
                }
                statement.executeQuery("SELECT player_uuid, display_name, score_total, winning, yaku_summary, meld_summary FROM round_player_result").use { result ->
                    assertTrue(result.next())
                    assertEquals(playerId.toString(), result.getString("player_uuid"))
                    assertEquals("Alice", result.getString("display_name"))
                    assertEquals(33000, result.getInt("score_total"))
                    assertEquals(true, result.getBoolean("winning"))
                    assertEquals("reach, SUANKO_TANKI", result.getString("yaku_summary"))
                    assertEquals("open:p1 p2 p3", result.getString("meld_summary"))
                }
            }
        }
    }

    @Test
    fun `persist match ranks sync updates profile and writes history`() {
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000021")

        service.persistMatchRanksSync(
            "TABLE99",
            MahjongRule.GameLength.TWO_WIND,
            listOf(
                TableFinalStanding(playerId, "Alice", 1, 42000, 57.0, false)
            )
        )

        val profile = service.loadRankProfile(playerId, "Alice")
        assertNotNull(profile)
        assertEquals(MahjongSoulRankRules.Tier.ADEPT, profile.tier())
        assertEquals(1, profile.level())
        assertEquals(392, profile.rankPoints())
        assertEquals(1, profile.totalMatches())

        withConnection(service) { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT table_id, room_code, match_length, place, rank_point_change FROM rank_history").use { result ->
                    assertTrue(result.next())
                    assertEquals("TABLE99", result.getString("table_id"))
                    assertEquals("GOLD", result.getString("room_code"))
                    assertEquals("SOUTH", result.getString("match_length"))
                    assertEquals(1, result.getInt("place"))
                    assertEquals(112, result.getInt("rank_point_change"))
                }
            }
        }
    }

    @Test
    fun `replace and load persistent tables round-trips through database`() {
        val rule = MahjongRule().apply {
            length = MahjongRule.GameLength.SOUTH
            thinkingTime = MahjongRule.ThinkingTime.LONG
            startingPoints = 30000
            minPointsToWin = 35000
            minimumHan = MahjongRule.MinimumHan.TWO
            spectate = false
            redFive = MahjongRule.RedFive.FOUR
            openTanyao = true
            localYaku = true
            ronMode = MahjongRule.RonMode.MULTI_RON
            riichiProfile = MahjongRule.RiichiProfile.TOURNAMENT
        }
        service.replacePersistentTables(
            listOf(
                DatabaseService.PersistentTableRecord(
                    "TABLE88",
                    "world",
                    100.5,
                    64.0,
                    -22.25,
                    MahjongVariant.GB,
                    rule,
                    true
                )
            )
        )

        val loaded = service.loadPersistentTables()

        assertEquals(1, loaded.size)
        val table = loaded.single()
        assertEquals("TABLE88", table.id())
        assertEquals("world", table.worldName())
        assertEquals(100.5, table.x())
        assertEquals(64.0, table.y())
        assertEquals(-22.25, table.z())
        assertEquals(MahjongVariant.GB, table.variant())
        assertTrue(table.botMatch())
        assertEquals(MahjongRule.GameLength.SOUTH, table.rule().length)
        assertEquals(MahjongRule.ThinkingTime.LONG, table.rule().thinkingTime)
        assertEquals(30000, table.rule().startingPoints)
        assertEquals(35000, table.rule().minPointsToWin)
        assertEquals(MahjongRule.MinimumHan.TWO, table.rule().minimumHan)
        assertEquals(false, table.rule().spectate)
        assertEquals(MahjongRule.RedFive.FOUR, table.rule().redFive)
        assertEquals(true, table.rule().openTanyao)
        assertEquals(true, table.rule().localYaku)
        assertEquals(MahjongRule.RonMode.MULTI_RON, table.rule().ronMode)
        assertEquals(MahjongRule.RiichiProfile.TOURNAMENT, table.rule().riichiProfile)
    }

    private fun withConnection(service: DatabaseService, block: (java.sql.Connection) -> Unit) {
        val field = DatabaseService::class.java.getDeclaredField("dataSource")
        field.isAccessible = true
        val dataSource = field.get(service) as DataSource
        dataSource.connection.use(block)
    }
}


