package top.ellan.mahjong.riichi

import top.ellan.mahjong.table.core.round.OpeningDiceRoll
import top.ellan.mahjong.riichi.model.ScoringStick
import top.ellan.mahjong.riichi.model.MahjongRule
import top.ellan.mahjong.riichi.model.MahjongTile
import top.ellan.mahjong.riichi.model.MahjongRound
import top.ellan.mahjong.riichi.model.Fuuro
import top.ellan.mahjong.riichi.model.ClaimTarget
import top.ellan.mahjong.riichi.model.MeldType
import top.ellan.mahjong.riichi.model.SettlementPaymentType
import top.ellan.mahjong.riichi.model.TileInstance
import top.ellan.mahjong.riichi.model.Wind
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RiichiRoundEngineTest {
    @Test
    fun `start round deals dealer 14 and others 13`() {
        val players = listOf(
            RiichiPlayerState("A", "a"),
            RiichiPlayerState("B", "b"),
            RiichiPlayerState("C", "c"),
            RiichiPlayerState("D", "d")
        )
        val engine = RiichiRoundEngine(players, MahjongRule())

        engine.startRound()

        assertTrue(engine.started)
        assertEquals(4, engine.seats.size)
        assertEquals(1, engine.doraIndicators.size)
        assertEquals(14, engine.currentPlayer.hands.size)
        assertEquals(3, engine.seats.count { it.hands.size == 13 })
        assertEquals(1, engine.seats.count { it.hands.size == 14 })
        assertFalse(engine.gameFinished)
    }

    @Test
    fun `engine initializes starting points for all seats`() {
        val rule = MahjongRule(startingPoints = 32000)
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("A", "a"),
                RiichiPlayerState("B", "b"),
                RiichiPlayerState("C", "c"),
                RiichiPlayerState("D", "d")
            ),
            rule
        )

        assertTrue(engine.seats.all { it.points == 32000 })
    }

    @Test
    fun `engine uses pending opening dice roll when provided`() {
        val players = listOf(
            RiichiPlayerState("A", "a"),
            RiichiPlayerState("B", "b"),
            RiichiPlayerState("C", "c"),
            RiichiPlayerState("D", "d")
        )
        val engine = RiichiRoundEngine(players, MahjongRule())
        engine.setPendingDiceRoll(OpeningDiceRoll(3, 4))

        engine.startRound()

        assertEquals(7, engine.dicePoints)
    }

    @Test
    fun `dora indicators stay empty before dead wall is assigned`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("A", "a"),
                RiichiPlayerState("B", "b"),
                RiichiPlayerState("C", "c"),
                RiichiPlayerState("D", "d")
            ),
            MahjongRule()
        )

        assertTrue(engine.doraIndicators.isEmpty())
        assertTrue(engine.uraDoraIndicators.isEmpty())
    }

    @Test
    fun `houtei only applies when live wall is exhausted`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("A", "a"),
                RiichiPlayerState("B", "b"),
                RiichiPlayerState("C", "c"),
                RiichiPlayerState("D", "d")
            ),
            MahjongRule()
        )
        engine.startRound()
        engine.wall.clear()
        engine.wall += tiles(MahjongTile.M1, MahjongTile.M2, MahjongTile.M3)

        assertFalse(engine.isHoutei)

        engine.wall.clear()
        assertTrue(engine.isHoutei)
    }

    @Test
    fun `engine preserves input seat order`() {
        val players = listOf(
            RiichiPlayerState("East", "east"),
            RiichiPlayerState("South", "south"),
            RiichiPlayerState("West", "west"),
            RiichiPlayerState("North", "north")
        )

        val engine = RiichiRoundEngine(players, MahjongRule())

        assertEquals(listOf("east", "south", "west", "north"), engine.seats.map { it.uuid })
    }

    @Test
    fun `normal draw with all players tenpai does not divide by zero`() {
        val players = listOf(
            RiichiPlayerState("A", "a"),
            RiichiPlayerState("B", "b"),
            RiichiPlayerState("C", "c"),
            RiichiPlayerState("D", "d")
        )
        val engine = RiichiRoundEngine(players, MahjongRule())

        engine.startRound()
        val tenpaiHand = listOf(
            MahjongTile.M1,
            MahjongTile.M1,
            MahjongTile.M2,
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.M3,
            MahjongTile.P4,
            MahjongTile.P4,
            MahjongTile.P5,
            MahjongTile.P5,
            MahjongTile.S6,
            MahjongTile.S6,
            MahjongTile.EAST
        )
        engine.seats.forEach { player ->
            player.hands.clear()
            tenpaiHand.forEach { tile ->
                player.hands += TileInstance(mahjongTile = tile)
            }
            if (player == engine.currentPlayer) {
                player.hands += TileInstance(mahjongTile = MahjongTile.WHITE_DRAGON)
            }
        }
        engine.wall.clear()

        val result = engine.discard(engine.currentPlayer.uuid, engine.currentPlayer.hands.lastIndex)

        assertTrue(result)
        val scoreChanges = engine.lastResolution?.scoreSettlement?.scoreList?.map { it.scoreChange }
        if (scoreChanges != null) {
            assertEquals(0, scoreChanges.sum())
        }
    }

    @Test
    fun `discard uses the exact selected tile instance`() {
        val players = listOf(
            RiichiPlayerState("A", "a"),
            RiichiPlayerState("B", "b"),
            RiichiPlayerState("C", "c"),
            RiichiPlayerState("D", "d")
        )
        val engine = RiichiRoundEngine(players, MahjongRule())

        engine.startRound()

        val player = engine.currentPlayer
        player.resetRoundState()
        val selected = TileInstance(mahjongTile = MahjongTile.M1)
        val otherSameKind = TileInstance(mahjongTile = MahjongTile.M1)
        player.hands += listOf(selected, otherSameKind, TileInstance(mahjongTile = MahjongTile.P5))

        assertTrue(engine.discard(player.uuid, 0))
        assertSame(selected, engine.discards.last())
        assertFalse(player.hands.contains(selected))
        assertTrue(player.hands.contains(otherSameKind))
    }

    @Test
    fun `turn advances east south west north after a discard with no reactions`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("East", "east"),
                RiichiPlayerState("South", "south"),
                RiichiPlayerState("West", "west"),
                RiichiPlayerState("North", "north")
            ),
            MahjongRule()
        )
        engine.startRound()
        val east = engine.seats[0]
        val south = engine.seats[1]
        val west = engine.seats[2]
        val north = engine.seats[3]
        east.resetRoundState()
        south.resetRoundState()
        west.resetRoundState()
        north.resetRoundState()
        east.hands += tiles(
            MahjongTile.M9,
            MahjongTile.P1,
            MahjongTile.P2,
            MahjongTile.P3,
            MahjongTile.S1,
            MahjongTile.S2,
            MahjongTile.S3,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.NORTH,
            MahjongTile.WHITE_DRAGON,
            MahjongTile.GREEN_DRAGON,
            MahjongTile.RED_DRAGON
        )
        south.hands += tiles(
            MahjongTile.M1,
            MahjongTile.M4,
            MahjongTile.M7,
            MahjongTile.P1,
            MahjongTile.P4,
            MahjongTile.P7,
            MahjongTile.S1,
            MahjongTile.S4,
            MahjongTile.S7,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.NORTH
        )
        west.hands += tiles(
            MahjongTile.M2,
            MahjongTile.M5,
            MahjongTile.M8,
            MahjongTile.P2,
            MahjongTile.P5,
            MahjongTile.P8,
            MahjongTile.S2,
            MahjongTile.S5,
            MahjongTile.S8,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.WHITE_DRAGON
        )
        north.hands += tiles(
            MahjongTile.M3,
            MahjongTile.M6,
            MahjongTile.M9,
            MahjongTile.P3,
            MahjongTile.P6,
            MahjongTile.P9,
            MahjongTile.S3,
            MahjongTile.S6,
            MahjongTile.S9,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.NORTH
        )
        engine.wall.clear()
        engine.wall += TileInstance(mahjongTile = MahjongTile.M1)

        assertTrue(engine.discard(east.uuid, 0))
        assertEquals("south", engine.currentPlayer.uuid)
        assertEquals(14, south.hands.size)
    }

    @Test
    fun `only the next player may chii a discard in east south west north flow`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("East", "east"),
                RiichiPlayerState("South", "south"),
                RiichiPlayerState("West", "west"),
                RiichiPlayerState("North", "north")
            ),
            MahjongRule()
        )
        engine.startRound()
        val east = engine.seats[0]
        val south = engine.seats[1]
        val west = engine.seats[2]
        val north = engine.seats[3]
        east.resetRoundState()
        south.resetRoundState()
        west.resetRoundState()
        north.resetRoundState()
        east.hands += tiles(
            MahjongTile.M2,
            MahjongTile.P1,
            MahjongTile.P2,
            MahjongTile.P3,
            MahjongTile.S1,
            MahjongTile.S2,
            MahjongTile.S3,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.NORTH,
            MahjongTile.WHITE_DRAGON,
            MahjongTile.GREEN_DRAGON,
            MahjongTile.RED_DRAGON
        )
        south.hands += tiles(
            MahjongTile.M1,
            MahjongTile.M3,
            MahjongTile.P4,
            MahjongTile.P5,
            MahjongTile.P6,
            MahjongTile.S4,
            MahjongTile.S5,
            MahjongTile.S6,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.NORTH,
            MahjongTile.WHITE_DRAGON
        )
        west.hands += tiles(
            MahjongTile.M4,
            MahjongTile.M5,
            MahjongTile.M6,
            MahjongTile.P1,
            MahjongTile.P4,
            MahjongTile.P7,
            MahjongTile.S1,
            MahjongTile.S4,
            MahjongTile.S7,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.NORTH
        )
        north.hands += tiles(
            MahjongTile.M1,
            MahjongTile.M3,
            MahjongTile.P7,
            MahjongTile.P8,
            MahjongTile.P9,
            MahjongTile.S7,
            MahjongTile.S8,
            MahjongTile.S9,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.NORTH,
            MahjongTile.GREEN_DRAGON
        )

        assertTrue(engine.discard(east.uuid, 0))
        assertEquals(listOf(MahjongTile.M1 to MahjongTile.M3), engine.availableReactions(south.uuid)?.chiiPairs)
        assertEquals(null, engine.availableReactions(west.uuid))
        assertEquals(null, engine.availableReactions(north.uuid))
    }

    @Test
    fun `riichi declaration requires at least four live-wall tiles`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("East", "east"),
                RiichiPlayerState("South", "south"),
                RiichiPlayerState("West", "west"),
                RiichiPlayerState("North", "north")
            ),
            MahjongRule(riichiProfile = MahjongRule.RiichiProfile.MAJSOUL)
        )
        engine.startRound()
        val east = engine.currentPlayer
        east.resetRoundState()
        east.hands += tiles(
            MahjongTile.M1,
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.P1,
            MahjongTile.P2,
            MahjongTile.P3,
            MahjongTile.S1,
            MahjongTile.S2,
            MahjongTile.S3,
            MahjongTile.EAST,
            MahjongTile.EAST,
            MahjongTile.M4,
            MahjongTile.M5,
            MahjongTile.M9
        )
        val riichiTile = east.tilePairsForRiichi.firstOrNull()?.first
        assertTrue(riichiTile != null)
        val riichiIndex = east.hands.indexOfFirst { it.mahjongTile == riichiTile }
        assertTrue(riichiIndex >= 0)

        engine.wall.clear()
        engine.wall += tiles(MahjongTile.M7, MahjongTile.P7, MahjongTile.S7)
        assertFalse(engine.declareRiichi(east.uuid, riichiIndex))

        engine.wall += TileInstance(mahjongTile = MahjongTile.EAST)
        assertTrue(engine.declareRiichi(east.uuid, riichiIndex))
    }

    @Test
    fun `chii caller does not draw and must discard next`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("East", "east"),
                RiichiPlayerState("South", "south"),
                RiichiPlayerState("West", "west"),
                RiichiPlayerState("North", "north")
            ),
            MahjongRule()
        )
        engine.startRound()
        val east = engine.seats[0]
        val south = engine.seats[1]
        val west = engine.seats[2]
        val north = engine.seats[3]
        east.resetRoundState()
        south.resetRoundState()
        west.resetRoundState()
        north.resetRoundState()
        east.hands += tiles(
            MahjongTile.M2,
            MahjongTile.P1,
            MahjongTile.P2,
            MahjongTile.P3,
            MahjongTile.S1,
            MahjongTile.S2,
            MahjongTile.S3,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.NORTH,
            MahjongTile.WHITE_DRAGON,
            MahjongTile.GREEN_DRAGON,
            MahjongTile.RED_DRAGON
        )
        south.hands += tiles(
            MahjongTile.M1,
            MahjongTile.M3,
            MahjongTile.P4,
            MahjongTile.P5,
            MahjongTile.P6,
            MahjongTile.S4,
            MahjongTile.S5,
            MahjongTile.S6,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.NORTH,
            MahjongTile.WHITE_DRAGON
        )
        west.hands += tiles(
            MahjongTile.M4,
            MahjongTile.M5,
            MahjongTile.M6,
            MahjongTile.P1,
            MahjongTile.P4,
            MahjongTile.P7,
            MahjongTile.S1,
            MahjongTile.S4,
            MahjongTile.S7,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.NORTH
        )
        north.hands += tiles(
            MahjongTile.M7,
            MahjongTile.M8,
            MahjongTile.M9,
            MahjongTile.P7,
            MahjongTile.P8,
            MahjongTile.P9,
            MahjongTile.S7,
            MahjongTile.S8,
            MahjongTile.S9,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.GREEN_DRAGON
        )
        engine.wall.clear()
        engine.wall += TileInstance(mahjongTile = MahjongTile.P9)

        assertTrue(engine.discard(east.uuid, 0))
        val wallSizeBeforeChii = engine.wall.size
        assertTrue(engine.react(south.uuid, ReactionResponse(ReactionType.CHII, MahjongTile.M1 to MahjongTile.M3)))
        assertEquals("south", engine.currentPlayer.uuid)
        assertEquals(11, south.hands.size)
        assertEquals(wallSizeBeforeChii, engine.wall.size)
        assertEquals(MeldType.CHII, south.fuuroList.last().type)
        assertEquals(ClaimTarget.LEFT, south.fuuroList.last().claimTarget)
        assertFalse(engine.discard(west.uuid, 0))
    }

    @Test
    fun `called discard leaves display river but still counts as discarder history`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("East", "east"),
                RiichiPlayerState("South", "south"),
                RiichiPlayerState("West", "west"),
                RiichiPlayerState("North", "north")
            ),
            MahjongRule()
        )
        engine.startRound()
        val east = engine.seats[0]
        val south = engine.seats[1]
        val west = engine.seats[2]
        val north = engine.seats[3]
        east.resetRoundState()
        south.resetRoundState()
        west.resetRoundState()
        north.resetRoundState()
        east.hands += tiles(
            MahjongTile.M2,
            MahjongTile.P1,
            MahjongTile.P2,
            MahjongTile.P3,
            MahjongTile.S1,
            MahjongTile.S2,
            MahjongTile.S3,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.NORTH,
            MahjongTile.WHITE_DRAGON,
            MahjongTile.GREEN_DRAGON,
            MahjongTile.RED_DRAGON
        )
        south.hands += tiles(
            MahjongTile.M1,
            MahjongTile.M3,
            MahjongTile.P4,
            MahjongTile.P5,
            MahjongTile.P6,
            MahjongTile.S4,
            MahjongTile.S5,
            MahjongTile.S6,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.NORTH,
            MahjongTile.WHITE_DRAGON
        )
        west.hands += tiles(
            MahjongTile.M4,
            MahjongTile.M5,
            MahjongTile.M6,
            MahjongTile.P1,
            MahjongTile.P4,
            MahjongTile.P7,
            MahjongTile.S1,
            MahjongTile.S4,
            MahjongTile.S7,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.NORTH
        )
        north.hands += tiles(
            MahjongTile.M7,
            MahjongTile.M8,
            MahjongTile.M9,
            MahjongTile.P7,
            MahjongTile.P8,
            MahjongTile.P9,
            MahjongTile.S7,
            MahjongTile.S8,
            MahjongTile.S9,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.GREEN_DRAGON
        )

        assertTrue(engine.discard(east.uuid, 0))
        val calledTile = engine.discards.last()
        assertTrue(engine.react(south.uuid, ReactionResponse(ReactionType.CHII, MahjongTile.M1 to MahjongTile.M3)))

        assertTrue(east.discardedTiles.contains(calledTile))
        assertFalse(east.discardedTilesForDisplay.contains(calledTile))
    }

    @Test
    fun `pon takes priority over chii on the same discard`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("East", "east"),
                RiichiPlayerState("South", "south"),
                RiichiPlayerState("West", "west"),
                RiichiPlayerState("North", "north")
            ),
            MahjongRule()
        )
        engine.startRound()
        val east = engine.seats[0]
        val south = engine.seats[1]
        val west = engine.seats[2]
        val north = engine.seats[3]
        east.resetRoundState()
        south.resetRoundState()
        west.resetRoundState()
        north.resetRoundState()
        east.hands += tiles(
            MahjongTile.M2,
            MahjongTile.P1,
            MahjongTile.P2,
            MahjongTile.P3,
            MahjongTile.S1,
            MahjongTile.S2,
            MahjongTile.S3,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.NORTH,
            MahjongTile.WHITE_DRAGON,
            MahjongTile.GREEN_DRAGON,
            MahjongTile.RED_DRAGON
        )
        south.hands += tiles(
            MahjongTile.M1,
            MahjongTile.M3,
            MahjongTile.P4,
            MahjongTile.P5,
            MahjongTile.P6,
            MahjongTile.S4,
            MahjongTile.S5,
            MahjongTile.S6,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.NORTH,
            MahjongTile.WHITE_DRAGON
        )
        west.hands += tiles(
            MahjongTile.M2,
            MahjongTile.M2,
            MahjongTile.P1,
            MahjongTile.P4,
            MahjongTile.P7,
            MahjongTile.S1,
            MahjongTile.S4,
            MahjongTile.S7,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.NORTH,
            MahjongTile.GREEN_DRAGON
        )
        north.hands += tiles(
            MahjongTile.M7,
            MahjongTile.M8,
            MahjongTile.M9,
            MahjongTile.P7,
            MahjongTile.P8,
            MahjongTile.P9,
            MahjongTile.S7,
            MahjongTile.S8,
            MahjongTile.S9,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.RED_DRAGON
        )

        assertTrue(engine.discard(east.uuid, 0))
        assertEquals(listOf(MahjongTile.M1 to MahjongTile.M3), engine.availableReactions(south.uuid)?.chiiPairs)
        assertTrue(engine.availableReactions(west.uuid)?.canPon == true)
        assertTrue(engine.react(south.uuid, ReactionResponse(ReactionType.CHII, MahjongTile.M1 to MahjongTile.M3)))
        assertTrue(engine.react(west.uuid, ReactionResponse(ReactionType.PON)))
        assertEquals("west", engine.currentPlayer.uuid)
        assertTrue(west.fuuroList.any { it.type == MeldType.PON })
        assertTrue(south.fuuroList.none { it.type == MeldType.CHII })
    }

    @Test
    fun `last live-wall discard only allows ron responses`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("East", "east"),
                RiichiPlayerState("South", "south"),
                RiichiPlayerState("West", "west"),
                RiichiPlayerState("North", "north")
            ),
            MahjongRule(riichiProfile = MahjongRule.RiichiProfile.TOURNAMENT)
        )
        engine.startRound()
        setupDualRedDragonRonReaction(engine)
        val east = engine.seats[0]
        val south = engine.seats[1]
        val north = engine.seats[3]
        engine.wall.clear()

        assertTrue(engine.discard(east.uuid, 0))
        val southOptions = engine.availableReactions(south.uuid)
        val northOptions = engine.availableReactions(north.uuid)
        assertTrue(southOptions != null)
        assertTrue(northOptions != null)
        assertTrue(southOptions.canRon)
        assertFalse(southOptions.canPon)
        assertFalse(southOptions.canMinkan)
        assertTrue(southOptions.chiiPairs.isEmpty())
    }

    @Test
    fun `suufon renda only triggers on first four opening discards`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("East", "east"),
                RiichiPlayerState("South", "south"),
                RiichiPlayerState("West", "west"),
                RiichiPlayerState("North", "north")
            ),
            MahjongRule()
        )
        engine.discards += tiles(
            MahjongTile.M1,
            MahjongTile.EAST,
            MahjongTile.EAST,
            MahjongTile.EAST,
            MahjongTile.EAST
        )

        assertFalse(engine.isSuufonRenda)
    }

    @Test
    fun `placement order uses current seat wind for tie breaks`() {
        val players = listOf(
            RiichiPlayerState("East", "east"),
            RiichiPlayerState("South", "south"),
            RiichiPlayerState("West", "west"),
            RiichiPlayerState("North", "north")
        )
        val engine = RiichiRoundEngine(players, MahjongRule())
        engine.round.round = 2
        engine.seats.forEach { it.points = 25000 }

        assertEquals(listOf("west", "north", "east", "south"), engine.placementOrder().map { it.uuid })
    }

    @Test
    fun `bankruptcy ends the match and awards leftover riichi sticks to first place`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("A", "a"),
                RiichiPlayerState("B", "b"),
                RiichiPlayerState("C", "c"),
                RiichiPlayerState("D", "d")
            ),
            MahjongRule()
        )
        engine.seats[0].points = 35000
        engine.seats[1].points = 24000
        engine.seats[2].points = -1000
        engine.seats[3].points = 22000
        engine.seats[1].sticks += ScoringStick.P1000
        engine.seats[2].sticks += ScoringStick.P1000

        finishRound(engine, dealerRemaining = false, clearRiichiSticks = false)

        assertTrue(engine.gameFinished)
        assertEquals(37000, engine.seats[0].points)
        assertTrue(engine.seats.all { it.sticks.isEmpty() })
    }

    @Test
    fun `all last dealer does not stop unless dealer is first`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("East", "east"),
                RiichiPlayerState("South", "south"),
                RiichiPlayerState("West", "west"),
                RiichiPlayerState("North", "north")
            ),
            MahjongRule(length = MahjongRule.GameLength.EAST)
        )
        engine.round = MahjongRound(wind = Wind.EAST, round = 3, honba = 0)
        setSpentRounds(engine.round, 3)
        engine.seats[0].points = 32000
        engine.seats[1].points = 33000
        engine.seats[2].points = 20000
        engine.seats[3].points = 15000

        finishRound(engine, dealerRemaining = true, clearRiichiSticks = false)

        assertFalse(engine.gameFinished)
        assertEquals(1, engine.round.honba)
        assertEquals(Wind.EAST, engine.round.wind)
        assertEquals(3, engine.round.round)
    }

    @Test
    fun `open kan reveals extra dora immediately in majsoul profile`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("East", "east"),
                RiichiPlayerState("South", "south"),
                RiichiPlayerState("West", "west"),
                RiichiPlayerState("North", "north")
            ),
            MahjongRule()
        )
        engine.startRound()
        val east = engine.seats[0]
        val south = engine.seats[1]
        val west = engine.seats[2]
        val north = engine.seats[3]
        east.resetRoundState()
        south.resetRoundState()
        west.resetRoundState()
        north.resetRoundState()
        east.hands += tiles(
            MahjongTile.EAST,
            MahjongTile.M1,
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.P1,
            MahjongTile.P2,
            MahjongTile.P3,
            MahjongTile.S1,
            MahjongTile.S2,
            MahjongTile.S3,
            MahjongTile.WHITE_DRAGON,
            MahjongTile.GREEN_DRAGON,
            MahjongTile.RED_DRAGON,
            MahjongTile.NORTH
        )
        south.hands += tiles(
            MahjongTile.EAST,
            MahjongTile.EAST,
            MahjongTile.EAST,
            MahjongTile.M4,
            MahjongTile.M5,
            MahjongTile.M6,
            MahjongTile.P4,
            MahjongTile.P5,
            MahjongTile.P6,
            MahjongTile.S4,
            MahjongTile.S5,
            MahjongTile.S6,
            MahjongTile.SOUTH
        )
        west.hands += tiles(
            MahjongTile.M7,
            MahjongTile.M8,
            MahjongTile.M9,
            MahjongTile.P7,
            MahjongTile.P8,
            MahjongTile.P9,
            MahjongTile.S7,
            MahjongTile.S8,
            MahjongTile.S9,
            MahjongTile.WHITE_DRAGON,
            MahjongTile.WHITE_DRAGON,
            MahjongTile.GREEN_DRAGON,
            MahjongTile.GREEN_DRAGON
        )
        north.hands += tiles(
            MahjongTile.M1,
            MahjongTile.M1,
            MahjongTile.M2,
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.M3,
            MahjongTile.P1,
            MahjongTile.P1,
            MahjongTile.P2,
            MahjongTile.P2,
            MahjongTile.S1,
            MahjongTile.S1,
            MahjongTile.RED_DRAGON
        )

        assertEquals(1, engine.doraIndicators.size)
        assertTrue(engine.discard(east.uuid, 0))
        assertTrue(engine.availableReactions(south.uuid)?.canMinkan == true)
        assertTrue(engine.react(south.uuid, ReactionResponse(ReactionType.MINKAN)))
        assertEquals(2, engine.doraIndicators.size)
    }

    @Test
    fun `open kan reveals extra dora after discard in tournament profile`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("East", "east"),
                RiichiPlayerState("South", "south"),
                RiichiPlayerState("West", "west"),
                RiichiPlayerState("North", "north")
            ),
            MahjongRule(riichiProfile = MahjongRule.RiichiProfile.TOURNAMENT)
        )
        engine.startRound()
        val east = engine.seats[0]
        val south = engine.seats[1]
        val west = engine.seats[2]
        val north = engine.seats[3]
        east.resetRoundState()
        south.resetRoundState()
        west.resetRoundState()
        north.resetRoundState()
        east.hands += tiles(
            MahjongTile.EAST,
            MahjongTile.M1,
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.P1,
            MahjongTile.P2,
            MahjongTile.P3,
            MahjongTile.S1,
            MahjongTile.S2,
            MahjongTile.S3,
            MahjongTile.WHITE_DRAGON,
            MahjongTile.GREEN_DRAGON,
            MahjongTile.RED_DRAGON,
            MahjongTile.NORTH
        )
        south.hands += tiles(
            MahjongTile.EAST,
            MahjongTile.EAST,
            MahjongTile.EAST,
            MahjongTile.M4,
            MahjongTile.M5,
            MahjongTile.M6,
            MahjongTile.P4,
            MahjongTile.P5,
            MahjongTile.P6,
            MahjongTile.S4,
            MahjongTile.S5,
            MahjongTile.S6,
            MahjongTile.SOUTH
        )
        west.hands += tiles(
            MahjongTile.M7,
            MahjongTile.M8,
            MahjongTile.M9,
            MahjongTile.P7,
            MahjongTile.P8,
            MahjongTile.P9,
            MahjongTile.S7,
            MahjongTile.S8,
            MahjongTile.S9,
            MahjongTile.WHITE_DRAGON,
            MahjongTile.WHITE_DRAGON,
            MahjongTile.GREEN_DRAGON,
            MahjongTile.GREEN_DRAGON
        )
        north.hands += tiles(
            MahjongTile.M1,
            MahjongTile.M1,
            MahjongTile.M2,
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.M3,
            MahjongTile.P1,
            MahjongTile.P1,
            MahjongTile.P2,
            MahjongTile.P2,
            MahjongTile.S1,
            MahjongTile.S1,
            MahjongTile.RED_DRAGON
        )

        assertEquals(1, engine.doraIndicators.size)
        assertTrue(engine.discard(east.uuid, 0))
        assertTrue(engine.availableReactions(south.uuid)?.canMinkan == true)
        assertTrue(engine.react(south.uuid, ReactionResponse(ReactionType.MINKAN)))
        assertEquals(1, engine.doraIndicators.size)
        assertTrue(engine.discard(south.uuid, south.hands.lastIndex))
        resolveAllPendingWithSkip(engine)
        assertEquals(2, engine.doraIndicators.size)
    }

    @Test
    fun `ankan is rejected when no rinshan draw is available`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("East", "east"),
                RiichiPlayerState("South", "south"),
                RiichiPlayerState("West", "west"),
                RiichiPlayerState("North", "north")
            ),
            MahjongRule()
        )
        engine.startRound()
        val east = engine.currentPlayer
        east.resetRoundState()
        east.hands += tiles(
            MahjongTile.EAST,
            MahjongTile.EAST,
            MahjongTile.EAST,
            MahjongTile.EAST,
            MahjongTile.M1,
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.P1,
            MahjongTile.P2,
            MahjongTile.P3,
            MahjongTile.S1,
            MahjongTile.S2,
            MahjongTile.S3,
            MahjongTile.WHITE_DRAGON
        )
        engine.wall.clear()

        assertFalse(engine.tryAnkanOrKakan(east.uuid, MahjongTile.EAST))
        assertEquals(4, east.hands.count { it.mahjongTile == MahjongTile.EAST })
        assertTrue(engine.pendingReaction == null)
    }

    @Test
    fun `pao ron splits yakuman payment between discarder and liable player`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("East", "east"),
                RiichiPlayerState("South", "south"),
                RiichiPlayerState("West", "west"),
                RiichiPlayerState("North", "north")
            ),
            MahjongRule()
        )
        val east = engine.seats[0]
        val south = engine.seats[1]
        val west = engine.seats[2]
        configureOpenDaisangenWait(south)
        setPaoLiability(engine, south.uuid, "DAISANGEN", east.uuid)

        resolveRon(engine, listOf(south), west, TileInstance(mahjongTile = MahjongTile.RED_DRAGON), false)

        assertEquals(57000, south.points)
        assertEquals(9000, east.points)
        assertEquals(9000, west.points)
        val settlement = engine.lastResolution!!.yakuSettlements.single()
        assertTrue(settlement.paymentBreakdown.any { it.payerUuid == west.uuid && it.amount == 16000 && it.type == SettlementPaymentType.RON })
        assertTrue(settlement.paymentBreakdown.any { it.payerUuid == east.uuid && it.amount == 16000 && it.type == SettlementPaymentType.PAO })
    }

    @Test
    fun `head bump ron mode resolves to nearest claimant only after all ron candidates respond`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("East", "east"),
                RiichiPlayerState("South", "south"),
                RiichiPlayerState("West", "west"),
                RiichiPlayerState("North", "north")
            ),
            MahjongRule(ronMode = MahjongRule.RonMode.HEAD_BUMP)
        )
        engine.startRound()
        setupDualRedDragonRonReaction(engine)
        val east = engine.seats[0]
        val south = engine.seats[1]
        val north = engine.seats[3]

        assertTrue(engine.discard(east.uuid, 0))
        assertTrue(engine.availableReactions(south.uuid)?.canRon == true)
        assertTrue(engine.availableReactions(north.uuid)?.canRon == true)

        assertTrue(engine.react(south.uuid, ReactionResponse(ReactionType.RON)))
        assertTrue(engine.pendingReaction != null)
        assertTrue(engine.lastResolution == null)

        assertTrue(engine.react(north.uuid, ReactionResponse(ReactionType.RON)))
        assertTrue(engine.pendingReaction == null)
        assertEquals(listOf(south.uuid), engine.lastResolution!!.yakuSettlements.map { it.uuid })
        assertEquals(25000, north.points)
    }

    @Test
    fun `multi ron mode resolves all ron claimants after all ron candidates respond`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("East", "east"),
                RiichiPlayerState("South", "south"),
                RiichiPlayerState("West", "west"),
                RiichiPlayerState("North", "north")
            ),
            MahjongRule(ronMode = MahjongRule.RonMode.MULTI_RON)
        )
        engine.startRound()
        setupDualRedDragonRonReaction(engine)
        val east = engine.seats[0]
        val south = engine.seats[1]
        val north = engine.seats[3]

        assertTrue(engine.discard(east.uuid, 0))
        assertTrue(engine.availableReactions(south.uuid)?.canRon == true)
        assertTrue(engine.availableReactions(north.uuid)?.canRon == true)

        assertTrue(engine.react(south.uuid, ReactionResponse(ReactionType.RON)))
        assertTrue(engine.pendingReaction != null)
        assertTrue(engine.lastResolution == null)

        assertTrue(engine.react(north.uuid, ReactionResponse(ReactionType.RON)))
        val winnerUuids = engine.lastResolution!!.yakuSettlements.map { it.uuid }
        assertEquals(2, winnerUuids.size)
        assertTrue(south.uuid in winnerUuids)
        assertTrue(north.uuid in winnerUuids)
    }

    @Test
    fun `pao tsumo charges the liable player the full yakuman value`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("East", "east"),
                RiichiPlayerState("South", "south"),
                RiichiPlayerState("West", "west"),
                RiichiPlayerState("North", "north")
            ),
            MahjongRule()
        )
        val east = engine.seats[0]
        val south = engine.seats[1]
        val west = engine.seats[2]
        val north = engine.seats[3]
        configureOpenDaisangenWait(south)
        setPaoLiability(engine, south.uuid, "DAISANGEN", east.uuid)
        val winningTile = TileInstance(mahjongTile = MahjongTile.RED_DRAGON)
        south.drawTile(winningTile)

        resolveTsumo(engine, south, winningTile, false)

        assertEquals(57000, south.points)
        assertEquals(-7000, east.points)
        assertEquals(25000, west.points)
        assertEquals(25000, north.points)
        val settlement = engine.lastResolution!!.yakuSettlements.single()
        assertTrue(settlement.paymentBreakdown.any { it.payerUuid == east.uuid && it.amount == 32000 && it.type == SettlementPaymentType.PAO })
        assertFalse(settlement.paymentBreakdown.any { it.payerUuid == west.uuid && it.amount > 0 })
    }

    @Test
    fun `composite yakuman tsumo keeps pao and normal shares separated`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("East", "east"),
                RiichiPlayerState("South", "south"),
                RiichiPlayerState("West", "west"),
                RiichiPlayerState("North", "north")
            ),
            MahjongRule()
        )
        val east = engine.seats[0]
        val south = engine.seats[1]
        val west = engine.seats[2]
        val north = engine.seats[3]
        configureOpenDaisuushiTsuuiisouWait(south)
        setPaoLiability(engine, south.uuid, "DAISUUSHI", east.uuid)
        val winningTile = TileInstance(mahjongTile = MahjongTile.NORTH)
        south.drawTile(winningTile)

        resolveTsumo(engine, south, winningTile, false)

        val settlement = engine.lastResolution!!.yakuSettlements.single()
        assertEquals(121000, south.points)
        assertEquals(-55000, east.points)
        assertEquals(17000, west.points)
        assertEquals(17000, north.points)
        assertTrue(settlement.paymentBreakdown.any { it.payerUuid == east.uuid && it.amount == 64000 && it.type == SettlementPaymentType.PAO })
        assertTrue(settlement.paymentBreakdown.any { it.payerUuid == east.uuid && it.amount == 16000 && it.type == SettlementPaymentType.TSUMO })
        assertTrue(settlement.paymentBreakdown.any { it.payerUuid == west.uuid && it.amount == 8000 && it.type == SettlementPaymentType.TSUMO })
        assertTrue(settlement.paymentBreakdown.any { it.payerUuid == north.uuid && it.amount == 8000 && it.type == SettlementPaymentType.TSUMO })
    }

    @Test
    fun `multiple ron keeps pao scoped to the liable winner and riichi pool goes to atamahane`() {
        val engine = RiichiRoundEngine(
            listOf(
                RiichiPlayerState("East", "east"),
                RiichiPlayerState("South", "south"),
                RiichiPlayerState("West", "west"),
                RiichiPlayerState("North", "north")
            ),
            MahjongRule()
        )
        val east = engine.seats[0]
        val south = engine.seats[1]
        val west = engine.seats[2]
        val north = engine.seats[3]
        configureOpenDaisangenWait(south)
        configureClosedRedDragonRonWait(north)
        setPaoLiability(engine, south.uuid, "DAISANGEN", east.uuid)
        west.sticks += ScoringStick.P1000

        resolveRon(engine, listOf(south, north), west, TileInstance(mahjongTile = MahjongTile.RED_DRAGON), false)

        val southSettlement = engine.lastResolution!!.yakuSettlements.first { it.uuid == south.uuid }
        val northSettlement = engine.lastResolution!!.yakuSettlements.first { it.uuid == north.uuid }
        assertEquals(25000 + southSettlement.score, south.points)
        assertEquals(25000 + northSettlement.score + 1000, north.points)
        assertEquals(9000, east.points)
        assertTrue(southSettlement.paymentBreakdown.any { it.payerUuid == east.uuid && it.amount == 16000 && it.type == SettlementPaymentType.PAO })
        assertFalse(southSettlement.paymentBreakdown.any { it.type == SettlementPaymentType.RIICHI_POOL })
        assertTrue(northSettlement.paymentBreakdown.any { it.payerUuid == west.uuid && it.type == SettlementPaymentType.RON })
        assertTrue(northSettlement.paymentBreakdown.any { it.type == SettlementPaymentType.RIICHI_POOL && it.amount == 1000 })
        assertFalse(northSettlement.paymentBreakdown.any { it.payerUuid == east.uuid && it.type == SettlementPaymentType.PAO })
    }

    private fun tiles(vararg tiles: MahjongTile): List<TileInstance> =
        tiles.map { TileInstance(mahjongTile = it) }

    private fun configureOpenDaisangenWait(player: RiichiPlayerState) {
        player.resetRoundState()
        player.hands += tiles(
            MahjongTile.RED_DRAGON,
            MahjongTile.RED_DRAGON,
            MahjongTile.M1,
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.S5,
            MahjongTile.S5
        )
        player.fuuroList += openPon(MahjongTile.WHITE_DRAGON)
        player.fuuroList += openPon(MahjongTile.GREEN_DRAGON)
    }

    private fun openPon(tile: MahjongTile): Fuuro {
        val claim = TileInstance(mahjongTile = tile)
        return Fuuro(
            MeldType.PON,
            listOf(claim, TileInstance(mahjongTile = tile), TileInstance(mahjongTile = tile)),
            ClaimTarget.RIGHT,
            claim
        )
    }

    private fun configureOpenDaisuushiTsuuiisouWait(player: RiichiPlayerState) {
        player.resetRoundState()
        player.hands += tiles(
            MahjongTile.NORTH,
            MahjongTile.NORTH,
            MahjongTile.WHITE_DRAGON,
            MahjongTile.WHITE_DRAGON
        )
        player.fuuroList += openPon(MahjongTile.EAST)
        player.fuuroList += openPon(MahjongTile.SOUTH)
        player.fuuroList += openPon(MahjongTile.WEST)
    }

    private fun configureClosedRedDragonRonWait(player: RiichiPlayerState) {
        player.resetRoundState()
        player.hands += tiles(
            MahjongTile.M1,
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.M4,
            MahjongTile.M5,
            MahjongTile.M6,
            MahjongTile.P1,
            MahjongTile.P2,
            MahjongTile.P3,
            MahjongTile.S2,
            MahjongTile.S2,
            MahjongTile.RED_DRAGON,
            MahjongTile.RED_DRAGON
        )
    }

    private fun setupDualRedDragonRonReaction(engine: RiichiRoundEngine) {
        val east = engine.seats[0]
        val south = engine.seats[1]
        val west = engine.seats[2]
        val north = engine.seats[3]

        east.resetRoundState()
        south.resetRoundState()
        west.resetRoundState()
        north.resetRoundState()

        east.hands += tiles(
            MahjongTile.RED_DRAGON,
            MahjongTile.M9,
            MahjongTile.P1,
            MahjongTile.P2,
            MahjongTile.P3,
            MahjongTile.S1,
            MahjongTile.S2,
            MahjongTile.S3,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.NORTH,
            MahjongTile.WHITE_DRAGON,
            MahjongTile.GREEN_DRAGON
        )
        configureOpenDaisangenWait(south)
        west.hands += tiles(
            MahjongTile.M1,
            MahjongTile.M4,
            MahjongTile.M7,
            MahjongTile.P1,
            MahjongTile.P4,
            MahjongTile.P7,
            MahjongTile.S1,
            MahjongTile.S4,
            MahjongTile.S7,
            MahjongTile.EAST,
            MahjongTile.SOUTH,
            MahjongTile.WEST,
            MahjongTile.NORTH
        )
        configureClosedRedDragonRonWait(north)
    }

    private fun resolveAllPendingWithSkip(engine: RiichiRoundEngine) {
        while (true) {
            val pending = engine.pendingReaction ?: return
            val undecided = pending.options.keys.filter { it !in pending.responses.keys }
            if (undecided.isEmpty()) {
                return
            }
            undecided.forEach { responderUuid ->
                assertTrue(engine.react(responderUuid, ReactionResponse(ReactionType.SKIP)))
            }
        }
    }

    private fun setPaoLiability(engine: RiichiRoundEngine, winnerUuid: String, key: String, liableUuid: String) {
        @Suppress("UNCHECKED_CAST")
        val liabilities = paoField.get(engine) as MutableMap<String, MutableMap<String, String>>
        liabilities.getOrPut(winnerUuid) { linkedMapOf() }[key] = liableUuid
    }

    private fun finishRound(engine: RiichiRoundEngine, dealerRemaining: Boolean, clearRiichiSticks: Boolean) {
        finishRoundMethod.invoke(engine, dealerRemaining, clearRiichiSticks)
    }

    private fun resolveRon(
        engine: RiichiRoundEngine,
        winners: List<RiichiPlayerState>,
        target: RiichiPlayerState,
        tile: TileInstance,
        isChankan: Boolean
    ) {
        resolveRonMethod.invoke(engine, winners, target, tile, isChankan)
    }

    private fun resolveTsumo(engine: RiichiRoundEngine, winner: RiichiPlayerState, tile: TileInstance, isRinshanKaihou: Boolean) {
        resolveTsumoMethod.invoke(engine, winner, tile, isRinshanKaihou)
    }

    private fun setSpentRounds(round: MahjongRound, value: Int) {
        spentRoundsField.setInt(round, value)
    }

    private companion object {
        val finishRoundMethod: Method =
            RiichiRoundEngine::class.java.getDeclaredMethod("finishRound", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }

        val resolveRonMethod: Method =
            RiichiRoundEngine::class.java.getDeclaredMethod(
                "resolveRon",
                List::class.java,
                RiichiPlayerState::class.java,
                TileInstance::class.java,
                Boolean::class.javaPrimitiveType
            ).apply { isAccessible = true }

        val resolveTsumoMethod: Method =
            RiichiRoundEngine::class.java.getDeclaredMethod(
                "resolveTsumo",
                RiichiPlayerState::class.java,
                TileInstance::class.java,
                Boolean::class.javaPrimitiveType
            ).apply { isAccessible = true }

        val spentRoundsField =
            MahjongRound::class.java.getDeclaredField("spentRounds").apply { isAccessible = true }

        val paoField: Field =
            RiichiRoundEngine::class.java.getDeclaredField("paoLiabilityByWinner").apply { isAccessible = true }
    }
}

