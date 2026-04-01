package top.ellan.mahjong.db

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.AfterAll
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MariaDBContainer
import top.ellan.mahjong.bootstrap.MahjongPaperPlugin
import top.ellan.mahjong.config.PluginSettings
import top.ellan.mahjong.debug.DebugService
import top.ellan.mahjong.riichi.model.MahjongRule
import top.ellan.mahjong.runtime.AsyncService
import top.ellan.mahjong.table.core.MahjongVariant
import top.ellan.mahjong.table.core.TableFinalStanding
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.nio.file.Files
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID
import java.util.logging.Logger
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DatabaseCrossDialectIntegrationTest {
    @Test
    fun `persist match ranks stays consistent between h2 and mariadb`() {
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000031")
        val snapshots = mutableMapOf<Dialect, RankSnapshot>()

        forEachDialect { dialect, service ->
            service.persistMatchRanksSync(
                "TABLE-CROSS",
                MahjongRule.GameLength.TWO_WIND,
                listOf(TableFinalStanding(playerId, "Alice", 1, 42000, 57.0, false))
            )

            val profile = service.loadRankProfile(playerId, "Alice")
            assertNotNull(profile)
            val history = withConnection(service) { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(
                        "SELECT room_code, place, rank_point_change FROM rank_history WHERE player_uuid = '$playerId'"
                    ).use { result ->
                        assertTrue(result.next())
                        RankHistoryRow(
                            roomCode = result.getString("room_code"),
                            place = result.getInt("place"),
                            rankPointChange = result.getInt("rank_point_change")
                        )
                    }
                }
            }
            snapshots[dialect] = RankSnapshot(
                tier = profile.tier().name,
                level = profile.level(),
                rankPoints = profile.rankPoints(),
                totalMatches = profile.totalMatches(),
                roomCode = history.roomCode,
                place = history.place,
                rankPointChange = history.rankPointChange
            )
        }

        val h2 = snapshots.getValue(Dialect.H2)
        assertEquals("ADEPT", h2.tier)
        assertEquals(1, h2.level)
        assertEquals(392, h2.rankPoints)
        assertEquals(1, h2.totalMatches)
        assertEquals("GOLD", h2.roomCode)
        assertEquals(1, h2.place)
        assertEquals(112, h2.rankPointChange)

        snapshots[Dialect.MARIADB]?.let { mariadb ->
            assertEquals(h2, mariadb)
        }
    }

    @Test
    fun `persistent table round-trip stays consistent between h2 and mariadb`() {
        val snapshots = mutableMapOf<Dialect, PersistentTableSnapshot>()

        forEachDialect { dialect, service ->
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
                        "TABLE-GB",
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
            val row = loaded.single()
            snapshots[dialect] = PersistentTableSnapshot(
                id = row.id(),
                worldName = row.worldName(),
                x = row.x(),
                y = row.y(),
                z = row.z(),
                variant = row.variant().name,
                botMatch = row.botMatch(),
                length = row.rule().length.name,
                thinkingTime = row.rule().thinkingTime.name,
                startingPoints = row.rule().startingPoints,
                minPointsToWin = row.rule().minPointsToWin,
                minimumHan = row.rule().minimumHan.name,
                spectate = row.rule().spectate,
                redFive = row.rule().redFive.name,
                openTanyao = row.rule().openTanyao,
                localYaku = row.rule().localYaku,
                ronMode = row.rule().ronMode.name,
                riichiProfile = row.rule().riichiProfile.name
            )
        }

        val h2 = snapshots.getValue(Dialect.H2)
        assertEquals("TABLE-GB", h2.id)
        assertEquals("GB", h2.variant)
        assertTrue(h2.botMatch)
        assertEquals("SOUTH", h2.length)
        assertEquals("LONG", h2.thinkingTime)
        assertEquals("MULTI_RON", h2.ronMode)
        assertEquals("TOURNAMENT", h2.riichiProfile)

        snapshots[Dialect.MARIADB]?.let { mariadb ->
            assertEquals(h2, mariadb)
        }
    }

    private fun forEachDialect(block: (Dialect, DatabaseService) -> Unit) {
        withDialect(Dialect.H2, block)
        if (dockerAvailable()) {
            withDialect(Dialect.MARIADB, block)
        }
    }

    private fun withDialect(dialect: Dialect, block: (Dialect, DatabaseService) -> Unit) {
        val tempDir = Files.createTempDirectory("mahjongpaper-db-${dialect.name.lowercase()}-")
        val plugin = mock(MahjongPaperPlugin::class.java)
        val settings = mock(PluginSettings::class.java)
        val async = AsyncService(Logger.getLogger("DatabaseCrossDialect-${dialect.name}-Async"))
        val logger = Logger.getLogger("DatabaseCrossDialect-${dialect.name}")

        `when`(plugin.getDataFolder()).thenReturn(tempDir.toFile())
        `when`(plugin.getLogger()).thenReturn(logger)
        `when`(plugin.settings()).thenReturn(settings)
        `when`(plugin.debug()).thenReturn(mock(DebugService::class.java))
        `when`(plugin.async()).thenReturn(async)
        `when`(settings.rankingEnabled()).thenReturn(true)
        `when`(settings.rankingEastRoom()).thenReturn("SILVER")
        `when`(settings.rankingSouthRoom()).thenReturn("GOLD")

        val config = YamlConfiguration()
        config.set("database.pool.maxSize", 2)
        config.set("database.pool.minIdle", 1)
        config.set("database.pool.connectionTimeoutMillis", 10000L)
        when (dialect) {
            Dialect.H2 -> {
                config.set("database.connection.type", "h2")
                config.set("database.h2.path", "data/test-db")
            }

            Dialect.MARIADB -> {
                val container = mariaDbContainer()
                config.set("database.connection.type", "mariadb")
                config.set("database.connection.host", container.host)
                config.set("database.connection.port", container.getMappedPort(3306))
                config.set("database.connection.name", container.databaseName)
                config.set("database.connection.parameters", "useUnicode=true&characterEncoding=UTF-8")
                config.set("database.credentials.username", container.username)
                config.set("database.credentials.password", container.password)
            }
        }

        val service = DatabaseService(plugin, PluginSettings.from(config).database())
        try {
            block(dialect, service)
        } finally {
            service.close()
            async.close()
            tempDir.toFile().deleteRecursively()
        }
    }

    private fun <T> withConnection(service: DatabaseService, block: (Connection) -> T): T {
        val field = DatabaseService::class.java.getDeclaredField("dataSource")
        field.isAccessible = true
        val dataSource = field.get(service) as DataSource
        return dataSource.connection.use(block)
    }

    private fun dockerAvailable(): Boolean {
        return try {
            DockerClientFactory.instance().isDockerAvailable
        } catch (_: Throwable) {
            false
        }
    }

    private fun mariaDbContainer(): MariaDBContainer<*> {
        synchronized(containerLock) {
            if (mariadbContainer == null) {
                mariadbContainer = MariaDBContainer("mariadb:11.4").apply {
                    withDatabaseName("mahjongpaper_test")
                    withUsername("mahjong")
                    withPassword("mahjong")
                    start()
                }
            }
            return mariadbContainer!!
        }
    }

    private enum class Dialect {
        H2,
        MARIADB
    }

    private data class RankSnapshot(
        val tier: String,
        val level: Int,
        val rankPoints: Int,
        val totalMatches: Int,
        val roomCode: String,
        val place: Int,
        val rankPointChange: Int
    )

    private data class RankHistoryRow(
        val roomCode: String,
        val place: Int,
        val rankPointChange: Int
    )

    private data class PersistentTableSnapshot(
        val id: String,
        val worldName: String?,
        val x: Double,
        val y: Double,
        val z: Double,
        val variant: String,
        val botMatch: Boolean,
        val length: String,
        val thinkingTime: String,
        val startingPoints: Int,
        val minPointsToWin: Int,
        val minimumHan: String,
        val spectate: Boolean,
        val redFive: String,
        val openTanyao: Boolean,
        val localYaku: Boolean,
        val ronMode: String,
        val riichiProfile: String
    )

    companion object {
        private val containerLock = Any()
        private var mariadbContainer: MariaDBContainer<*>? = null

        @AfterAll
        @JvmStatic
        fun stopContainer() {
            synchronized(containerLock) {
                mariadbContainer?.stop()
                mariadbContainer = null
            }
        }
    }
}
