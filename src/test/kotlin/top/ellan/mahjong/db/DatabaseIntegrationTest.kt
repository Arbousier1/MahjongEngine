package top.ellan.mahjong.db

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
import top.ellan.mahjong.model.MahjongVariant
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DatabaseIntegrationTest {
    private lateinit var tempDir: java.nio.file.Path
    private lateinit var async: AsyncService
    private lateinit var service: DatabaseService

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("mahjongpaper-db-test")
        async = AsyncService(Logger.getLogger("DatabaseIntegrationTest-Async"))

        val config = YamlConfiguration()
        config.set("database.pool.maxSize", 2)
        config.set("database.pool.minIdle", 1)
        config.set("database.connection.type", "h2")
        config.set("database.h2.path", "data/test-db")
        service = DatabaseService(
            PluginSettings.from(config).database(),
            mock(DebugService::class.java),
            async,
            Logger.getLogger("DatabaseIntegrationTest"),
            tempDir,
            true,
            "SILVER",
            "GOLD"
        )
    }

    @AfterEach
    fun tearDown() {
        service.close()
        async.close()
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
        val secondId = UUID.fromString("00000000-0000-0000-0000-000000000022")
        val thirdId = UUID.fromString("00000000-0000-0000-0000-000000000023")
        val fourthId = UUID.fromString("00000000-0000-0000-0000-000000000024")

        service.persistMatchRanksSync(
            "TABLE99",
            MahjongRule.GameLength.TWO_WIND,
            listOf(
                TableFinalStanding(playerId, "Alice", 1, 42000, 57.0, false),
                TableFinalStanding(secondId, "Bob", 2, 30000, 10.0, false),
                TableFinalStanding(thirdId, "Carol", 3, 20000, -20.0, false),
                TableFinalStanding(fourthId, "Dave", 4, 8000, -47.0, false)
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
                statement.executeQuery("SELECT table_id, mode_code, room_code, match_length, place, rank_point_change FROM rank_history WHERE player_uuid = '$playerId'").use { result ->
                    assertTrue(result.next())
                    assertEquals("TABLE99", result.getString("table_id"))
                    assertEquals("RIICHI", result.getString("mode_code"))
                    assertEquals("GOLD", result.getString("room_code"))
                    assertEquals("SOUTH", result.getString("match_length"))
                    assertEquals(1, result.getInt("place"))
                    assertEquals(112, result.getInt("rank_point_change"))
                }
            }
        }
    }

    @Test
    fun `persist match ranks keeps separate profiles per mode`() {
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000029")
        val secondId = UUID.fromString("00000000-0000-0000-0000-000000000030")
        val thirdId = UUID.fromString("00000000-0000-0000-0000-000000000031")
        val fourthId = UUID.fromString("00000000-0000-0000-0000-000000000032")

        service.persistMatchRanksSync(
            "TABLE-RIICHI",
            MahjongVariant.RIICHI,
            MahjongRule.GameLength.TWO_WIND,
            listOf(
                TableFinalStanding(playerId, "Alice", 1, 42000, 57.0, false),
                TableFinalStanding(secondId, "Bob", 2, 30000, 10.0, false),
                TableFinalStanding(thirdId, "Carol", 3, 20000, -20.0, false),
                TableFinalStanding(fourthId, "Dave", 4, 8000, -47.0, false)
            )
        )
        service.persistMatchRanksSync(
            "TABLE-GB",
            MahjongVariant.GB,
            MahjongRule.GameLength.TWO_WIND,
            listOf(
                TableFinalStanding(secondId, "Bob", 1, 42000, 57.0, false),
                TableFinalStanding(thirdId, "Carol", 2, 30000, 10.0, false),
                TableFinalStanding(fourthId, "Dave", 3, 20000, -20.0, false),
                TableFinalStanding(playerId, "Alice", 4, 8000, -47.0, false)
            )
        )

        val riichiProfile = service.loadRankProfile(playerId, "Alice", MahjongVariant.RIICHI)
        val gbProfile = service.loadRankProfile(playerId, "Alice", MahjongVariant.GB)
        val sichuanProfile = service.loadRankProfile(playerId, "Alice", MahjongVariant.SICHUAN)

        assertEquals(1, riichiProfile.totalMatches())
        assertEquals(1, riichiProfile.firstPlaces())
        assertEquals(392, riichiProfile.rankPoints())
        assertEquals(1, gbProfile.totalMatches())
        assertEquals(1, gbProfile.fourthPlaces())
        assertEquals(0, gbProfile.rankPoints())
        assertEquals(0, sichuanProfile.totalMatches())
        assertEquals(
            setOf(MahjongVariant.RIICHI, MahjongVariant.GB, MahjongVariant.SICHUAN),
            service.loadRankProfiles(playerId, "Alice").keys
        )

        withConnection(service) { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT mode_code, COUNT(*) AS rows FROM rank_history WHERE player_uuid = '$playerId' GROUP BY mode_code").use { result ->
                    val counts = mutableMapOf<String, Int>()
                    while (result.next()) {
                        counts[result.getString("mode_code")] = result.getInt("rows")
                    }
                    assertEquals(1, counts["RIICHI"])
                    assertEquals(1, counts["GB"])
                }
            }
        }
    }

    @Test
    fun `leaderboard returns ranked profiles for requested mode`() {
        val firstId = UUID.fromString("00000000-0000-0000-0000-000000000041")
        val secondId = UUID.fromString("00000000-0000-0000-0000-000000000042")
        val thirdId = UUID.fromString("00000000-0000-0000-0000-000000000043")
        val fourthId = UUID.fromString("00000000-0000-0000-0000-000000000044")

        service.persistMatchRanksSync(
            "TABLE-LB",
            MahjongVariant.RIICHI,
            MahjongRule.GameLength.TWO_WIND,
            listOf(
                TableFinalStanding(firstId, "Alice", 1, 52000, 87.0, false),
                TableFinalStanding(secondId, "Bob", 2, 30000, 10.0, false),
                TableFinalStanding(thirdId, "Carol", 3, 18000, -22.0, false),
                TableFinalStanding(fourthId, "Dave", 4, 0, -75.0, false)
            )
        )

        val leaderboard = service.loadLeaderboard(MahjongVariant.RIICHI, 3)
        val gbLeaderboard = service.loadLeaderboard(MahjongVariant.GB, 3)

        assertEquals(3, leaderboard.size)
        assertEquals(1, leaderboard[0].position())
        assertEquals("Alice", leaderboard[0].profile().displayName())
        assertEquals(firstId, leaderboard[0].profile().playerId())
        assertEquals(0, gbLeaderboard.size)
    }

    @Test
    fun `persist match ranks ignores incomplete human tables`() {
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000025")

        service.persistMatchRanksSync(
            "TABLE-BOT",
            MahjongRule.GameLength.TWO_WIND,
            listOf(
                TableFinalStanding(playerId, "Alice", 1, 42000, 57.0, false),
                TableFinalStanding(UUID.fromString("00000000-0000-0000-0000-000000000026"), "Bot-1", 2, 30000, 10.0, true),
                TableFinalStanding(UUID.fromString("00000000-0000-0000-0000-000000000027"), "Bot-2", 3, 20000, -20.0, true),
                TableFinalStanding(UUID.fromString("00000000-0000-0000-0000-000000000028"), "Bot-3", 4, 8000, -47.0, true)
            )
        )

        val profile = service.loadRankProfile(playerId, "Alice")
        assertEquals(0, profile.totalMatches())
        withConnection(service) { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT player_uuid FROM rank_history").use { result ->
                    assertFalse(result.next())
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
                    UUID.fromString("00000000-0000-0000-0000-000000000088"),
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
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000088"), table.ownerId())
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


