package top.ellan.mahjong.riichi

import mahjongutils.shanten.shanten
import top.ellan.mahjong.riichi.model.ClaimTarget
import top.ellan.mahjong.riichi.model.Fuuro
import top.ellan.mahjong.riichi.model.GeneralSituation
import top.ellan.mahjong.riichi.model.MahjongTile
import top.ellan.mahjong.riichi.model.MeldType
import top.ellan.mahjong.riichi.model.MahjongRule
import top.ellan.mahjong.riichi.model.PersonalSituation
import top.ellan.mahjong.riichi.model.TileInstance
import top.ellan.mahjong.riichi.model.Wind
import java.lang.reflect.Field
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RiichiPlayerStateTest {
    @Test
    fun `best-only shanten crash falls back to full analysis when Java no such element is thrown`() {
        val player = RiichiPlayerState("Alice", "alice")
        player.hands += tiles(
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.M4,
            MahjongTile.M3,
            MahjongTile.M4,
            MahjongTile.M5,
            MahjongTile.P4,
            MahjongTile.P5,
            MahjongTile.P6,
            MahjongTile.S6,
            MahjongTile.S7,
            MahjongTile.S8,
            MahjongTile.P6,
            MahjongTile.M9
        )

        val originalCalculator = RiichiPlayerState.shantenCalculator
        var bestOnlyCalls = 0
        var fullCalls = 0
        try {
            RiichiPlayerState.shantenCalculator = { tiles, furo, bestShantenOnly ->
                if (bestShantenOnly) {
                    bestOnlyCalls++
                    throw java.util.NoSuchElementException("forced failure for regression test")
                }
                fullCalls++
                shanten(
                    tiles = tiles,
                    furo = furo,
                    bestShantenOnly = false
                )
            }

            val suggestions = player.discardSuggestions()

            assertTrue(suggestions.isNotEmpty())
            assertEquals(1, bestOnlyCalls)
            assertEquals(1, fullCalls)
        } finally {
            RiichiPlayerState.shantenCalculator = originalCalculator
        }
    }

    @Test
    fun `shanten no such element in both attempts recovers via util stable args`() {
        val player = RiichiPlayerState("Alice", "alice")
        player.hands += tiles(
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.M4,
            MahjongTile.M3,
            MahjongTile.M4,
            MahjongTile.M5,
            MahjongTile.P4,
            MahjongTile.P5,
            MahjongTile.P6,
            MahjongTile.S6,
            MahjongTile.S7,
            MahjongTile.S8,
            MahjongTile.P6,
            MahjongTile.M9
        )

        val originalCalculator = RiichiPlayerState.shantenCalculator
        var calculatorCalls = 0
        try {
            RiichiPlayerState.shantenCalculator = { _, _, _ ->
                calculatorCalls++
                throw java.util.NoSuchElementException("forced failure for stable util fallback test")
            }

            val suggestions = player.discardSuggestions()

            assertTrue(suggestions.isNotEmpty())
            assertEquals(2, calculatorCalls)
        } finally {
            RiichiPlayerState.shantenCalculator = originalCalculator
        }
    }

    @Test
    fun `can win pre-check reuses shanten fallback chain`() {
        val player = RiichiPlayerState("Alice", "alice")
        player.hands += tiles(
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.M4,
            MahjongTile.M3,
            MahjongTile.M4,
            MahjongTile.M5,
            MahjongTile.P4,
            MahjongTile.P5,
            MahjongTile.P6,
            MahjongTile.S6,
            MahjongTile.S7,
            MahjongTile.S8,
            MahjongTile.P6
        )

        val originalCalculator = RiichiPlayerState.shantenCalculator
        var bestOnlyCalls = 0
        var fullCalls = 0
        try {
            RiichiPlayerState.shantenCalculator = { tiles, furo, bestShantenOnly ->
                if (bestShantenOnly) {
                    bestOnlyCalls++
                    throw java.util.NoSuchElementException("forced pre-check failure")
                }
                fullCalls++
                shanten(
                    tiles = tiles,
                    furo = furo,
                    bestShantenOnly = false
                )
            }

            val canWin = player.canWin(
                winningTile = MahjongTile.P6,
                isWinningTileInHands = false,
                rule = MahjongRule(),
                generalSituation = defaultGeneralSituation(),
                personalSituation = defaultPersonalSituation()
            )

            assertTrue(canWin)
            assertEquals(1, bestOnlyCalls)
            assertEquals(1, fullCalls)
        } finally {
            RiichiPlayerState.shantenCalculator = originalCalculator
        }
    }

    @Test
    fun `invalid hand size does not throw during shanten checks`() {
        val player = RiichiPlayerState("Alice", "alice")
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
            MahjongTile.S1,
            MahjongTile.S2,
            MahjongTile.S3
        )

        assertFalse(player.isTenpai)
        assertTrue(player.discardSuggestions().isEmpty())
    }

    @Test
    fun `tile pairs for riichi cache invalidates when hand changes`() {
        val player = RiichiPlayerState("Alice", "alice")
        player.drawTile(TileInstance(mahjongTile = MahjongTile.M1))
        player.tilePairsForRiichi
        val cachedVersionBefore = cachedTilePairsVersion(player)
        assertEquals(analysisVersion(player), cachedVersionBefore)

        player.discardTile(MahjongTile.M1)

        assertTrue(analysisVersion(player) > cachedVersionBefore)

        player.tilePairsForRiichi

        assertEquals(analysisVersion(player), cachedTilePairsVersion(player))
    }

    @Test
    fun `reset round state clears cached hand analysis`() {
        val player = RiichiPlayerState("Alice", "alice")
        player.drawTile(TileInstance(mahjongTile = MahjongTile.M1))
        player.riichi = true
        player.doubleRiichi = true
        player.tilePairsForRiichi
        val cachedVersionBefore = cachedTilePairsVersion(player)

        player.resetRoundState()

        assertTrue(analysisVersion(player) > cachedVersionBefore)
        assertTrue(player.hands.isEmpty())
        assertTrue(player.fuuroList.isEmpty())
        assertFalse(player.riichi)
        assertFalse(player.doubleRiichi)

        player.tilePairsForRiichi

        assertEquals(analysisVersion(player), cachedTilePairsVersion(player))
    }

    @Test
    fun `pon removes two matching hand tiles and creates open meld`() {
        val player = RiichiPlayerState("Alice", "alice")
        val target = RiichiPlayerState("Bob", "bob")
        val discard = TileInstance(mahjongTile = MahjongTile.M5)
        player.hands += listOf(
            TileInstance(mahjongTile = MahjongTile.M5),
            TileInstance(mahjongTile = MahjongTile.M5_RED),
            TileInstance(mahjongTile = MahjongTile.P1)
        )
        target.discardedTilesForDisplay += discard

        player.pon(discard, ClaimTarget.RIGHT, target)

        assertEquals(1, player.hands.size)
        assertEquals(MahjongTile.P1, player.hands.single().mahjongTile)
        assertEquals(1, player.fuuroList.size)
        assertEquals(MeldType.PON, player.fuuroList.single().type)
        assertTrue(player.fuuroList.single().isOpen)
        assertFalse(target.discardedTilesForDisplay.contains(discard))
    }

    @Test
    fun `ankan removes four matching tiles and creates concealed kan`() {
        val player = RiichiPlayerState("Alice", "alice")
        val tile = TileInstance(mahjongTile = MahjongTile.EAST)
        player.hands += listOf(
            tile,
            TileInstance(mahjongTile = MahjongTile.EAST),
            TileInstance(mahjongTile = MahjongTile.EAST),
            TileInstance(mahjongTile = MahjongTile.EAST)
        )

        player.ankan(tile)

        assertTrue(player.hands.isEmpty())
        assertEquals(1, player.fuuroList.size)
        assertEquals(MeldType.ANKAN, player.fuuroList.single().type)
        assertTrue(player.fuuroList.single().isKan)
        assertFalse(player.fuuroList.single().isOpen)
    }

    @Test
    fun `riichi ankan requires the freshly drawn tile to complete the quad`() {
        val player = RiichiPlayerState("Alice", "alice")
        player.riichi = true
        player.hands += tiles(
            MahjongTile.EAST,
            MahjongTile.EAST,
            MahjongTile.EAST,
            MahjongTile.EAST,
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.M4,
            MahjongTile.P2,
            MahjongTile.P3,
            MahjongTile.P4,
            MahjongTile.S2,
            MahjongTile.S3,
            MahjongTile.S4,
            MahjongTile.WHITE_DRAGON
        )
        player.lastDrawnTile = TileInstance(mahjongTile = MahjongTile.WHITE_DRAGON)

        assertTrue(player.tilesCanAnkan.isEmpty())
    }

    @Test
    fun `drawing winning tile refreshes riichi availability`() {
        val player = RiichiPlayerState("Alice", "alice")
        player.hands += tiles(MahjongTile.M1, MahjongTile.M2, MahjongTile.M3)
        player.tilePairsForRiichi
        val cachedVersionBefore = cachedTilePairsVersion(player)

        player.drawTile(TileInstance(mahjongTile = MahjongTile.M4))

        assertTrue(analysisVersion(player) > cachedVersionBefore)
        assertEquals(cachedVersionBefore, cachedTilePairsVersion(player))
    }

    @Test
    fun `discard suggestion cache invalidates when hand changes`() {
        val player = RiichiPlayerState("Alice", "alice")
        player.hands += tiles(
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.M4,
            MahjongTile.M3,
            MahjongTile.M4,
            MahjongTile.M5,
            MahjongTile.P4,
            MahjongTile.P5,
            MahjongTile.P6,
            MahjongTile.S6,
            MahjongTile.S7,
            MahjongTile.S8,
            MahjongTile.P6,
            MahjongTile.M9
        )

        player.discardSuggestions()
        val cachedVersionBefore = cachedDiscardSuggestionsVersion(player)
        assertEquals(analysisVersion(player), cachedVersionBefore)

        player.discardTile(MahjongTile.M9)

        assertTrue(analysisVersion(player) > cachedVersionBefore)
        assertEquals(cachedVersionBefore, cachedDiscardSuggestionsVersion(player))
    }

    @Test
    fun `best discard suggestions mirror detailed discard suggestions`() {
        val player = RiichiPlayerState("Alice", "alice")
        player.hands += tiles(
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.M4,
            MahjongTile.M3,
            MahjongTile.M4,
            MahjongTile.M5,
            MahjongTile.P4,
            MahjongTile.P5,
            MahjongTile.P6,
            MahjongTile.S6,
            MahjongTile.S7,
            MahjongTile.S8,
            MahjongTile.P6,
            MahjongTile.M9
        )

        val details = player.discardSuggestions()

        assertTrue(details.isNotEmpty())
        assertEquals(details.map { it.tile }, player.bestDiscardSuggestions())
        assertTrue(details.first().advanceTiles.isNotEmpty())
        assertTrue(details.first().advanceCount > 0)
    }

    @Test
    fun `furiten checks start from the actual last discard instance`() {
        val player = RiichiPlayerState("Alice", "alice")
        player.hands += tiles(
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
        val earlierSameKind = TileInstance(mahjongTile = MahjongTile.M9)
        val waitedTileFromEarlierTurn = TileInstance(mahjongTile = MahjongTile.EAST)
        val ownLastDiscard = TileInstance(mahjongTile = MahjongTile.M9)
        val currentWinningDiscard = TileInstance(mahjongTile = MahjongTile.EAST)
        player.discardedTiles += ownLastDiscard

        assertFalse(
            player.isFuriten(
                currentWinningDiscard,
                listOf(earlierSameKind, waitedTileFromEarlierTurn, ownLastDiscard, currentWinningDiscard)
            )
        )
    }

    @Test
    fun `discarding a selected tile removes the exact tile instance`() {
        val player = RiichiPlayerState("Alice", "alice")
        val selected = TileInstance(mahjongTile = MahjongTile.M1)
        val otherSameKind = TileInstance(mahjongTile = MahjongTile.M1)
        player.hands += listOf(selected, otherSameKind, TileInstance(mahjongTile = MahjongTile.P5))

        val discarded = player.discardTile(selected)

        assertSame(selected, discarded)
        assertFalse(player.hands.contains(selected))
        assertTrue(player.hands.contains(otherSameKind))
    }

    @Test
    fun `available chii pairs come from mahjong utils furo analysis`() {
        val player = RiichiPlayerState("Alice", "alice")
        player.hands += tiles(
            MahjongTile.M1,
            MahjongTile.M3,
            MahjongTile.M3,
            MahjongTile.M4,
            MahjongTile.P1,
            MahjongTile.P1,
            MahjongTile.P2,
            MahjongTile.P2,
            MahjongTile.P3,
            MahjongTile.P3,
            MahjongTile.S4,
            MahjongTile.S5,
            MahjongTile.S6
        )

        val pairs = player.availableChiiPairs(TileInstance(mahjongTile = MahjongTile.M2))

        assertEquals(
            setOf(MahjongTile.M1 to MahjongTile.M3, MahjongTile.M3 to MahjongTile.M4),
            pairs.toSet()
        )
    }

    @Test
    fun `closed tanyao hand can win`() {
        val player = RiichiPlayerState("Alice", "alice")
        player.hands += tiles(
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.M4,
            MahjongTile.M3,
            MahjongTile.M4,
            MahjongTile.M5,
            MahjongTile.P4,
            MahjongTile.P5,
            MahjongTile.P6,
            MahjongTile.S6,
            MahjongTile.S7,
            MahjongTile.S8,
            MahjongTile.P6
        )

        val canWin = player.canWin(
            winningTile = MahjongTile.P6,
            isWinningTileInHands = false,
            rule = MahjongRule(),
            generalSituation = defaultGeneralSituation(),
            personalSituation = defaultPersonalSituation()
        )
        val settlement = player.calcYakuSettlementForWin(
            winningTile = MahjongTile.P6,
            isWinningTileInHands = false,
            rule = MahjongRule(),
            generalSituation = defaultGeneralSituation(),
            personalSituation = defaultPersonalSituation(),
            doraIndicators = emptyList(),
            uraDoraIndicators = emptyList()
        )

        assertTrue(canWin)
        assertContains(settlement.yakuList, "PINFU")
        assertContains(settlement.yakuList, "TANYAO")
        assertEquals(2, settlement.han)
    }

    @Test
    fun `dora adds han when hand already has a yaku`() {
        val player = RiichiPlayerState("Alice", "alice")
        player.hands += tiles(
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.M4,
            MahjongTile.M3,
            MahjongTile.M4,
            MahjongTile.M5,
            MahjongTile.P4,
            MahjongTile.P5,
            MahjongTile.P6,
            MahjongTile.S6,
            MahjongTile.S7,
            MahjongTile.S8,
            MahjongTile.P6
        )

        val settlement = player.calcYakuSettlementForWin(
            winningTile = MahjongTile.P6,
            isWinningTileInHands = false,
            rule = MahjongRule(),
            generalSituation = defaultGeneralSituation(doraIndicators = listOf(MahjongTile.P5)),
            personalSituation = defaultPersonalSituation(),
            doraIndicators = listOf(MahjongTile.P5),
            uraDoraIndicators = emptyList()
        )

        assertContains(settlement.yakuList, "PINFU")
        assertContains(settlement.yakuList, "TANYAO")
        assertEquals(3, settlement.yakuList.count { it == "DORA" })
        assertEquals(5, settlement.han)
    }

    @Test
    fun `ittsu loses one han after opening the hand`() {
        val closed = RiichiPlayerState("Closed", "closed")
        closed.hands += tiles(
            MahjongTile.M1,
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.M4,
            MahjongTile.M5,
            MahjongTile.M6,
            MahjongTile.M7,
            MahjongTile.M8,
            MahjongTile.P1,
            MahjongTile.P1,
            MahjongTile.P1,
            MahjongTile.S7,
            MahjongTile.S7
        )
        val closedSettlement = closed.calcYakuSettlementForWin(
            winningTile = MahjongTile.M9,
            isWinningTileInHands = false,
            rule = MahjongRule(),
            generalSituation = defaultGeneralSituation(),
            personalSituation = defaultPersonalSituation(),
            doraIndicators = emptyList(),
            uraDoraIndicators = emptyList()
        )
        assertContains(closedSettlement.yakuList, "ITTSU")
        assertEquals(2, closedSettlement.han)

        val open = RiichiPlayerState("Open", "open")
        open.hands += tiles(
            MahjongTile.M4,
            MahjongTile.M5,
            MahjongTile.M6,
            MahjongTile.M7,
            MahjongTile.M8,
            MahjongTile.P1,
            MahjongTile.P1,
            MahjongTile.P1,
            MahjongTile.S7,
            MahjongTile.S7
        )
        open.fuuroList += openChii(MahjongTile.M1, MahjongTile.M2, MahjongTile.M3)
        val openSettlement = open.calcYakuSettlementForWin(
            winningTile = MahjongTile.M9,
            isWinningTileInHands = false,
            rule = MahjongRule(),
            generalSituation = defaultGeneralSituation(),
            personalSituation = defaultPersonalSituation(),
            doraIndicators = emptyList(),
            uraDoraIndicators = emptyList()
        )
        assertContains(openSettlement.yakuList, "ITTSU")
        assertEquals(1, openSettlement.han)
    }

    @Test
    fun `chinitsu loses one han after opening the hand`() {
        val closed = RiichiPlayerState("Closed", "closed")
        closed.hands += tiles(
            MahjongTile.M1,
            MahjongTile.M1,
            MahjongTile.M1,
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.M4,
            MahjongTile.M4,
            MahjongTile.M5,
            MahjongTile.M6,
            MahjongTile.M7,
            MahjongTile.M7,
            MahjongTile.M9,
            MahjongTile.M9
        )
        val closedSettlement = closed.calcYakuSettlementForWin(
            winningTile = MahjongTile.M7,
            isWinningTileInHands = false,
            rule = MahjongRule(),
            generalSituation = defaultGeneralSituation(),
            personalSituation = defaultPersonalSituation(),
            doraIndicators = emptyList(),
            uraDoraIndicators = emptyList()
        )
        assertContains(closedSettlement.yakuList, "CHINITSU")
        assertEquals(6, closedSettlement.han)

        val open = RiichiPlayerState("Open", "open")
        open.hands += tiles(
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.M4,
            MahjongTile.M4,
            MahjongTile.M5,
            MahjongTile.M6,
            MahjongTile.M7,
            MahjongTile.M7,
            MahjongTile.M9,
            MahjongTile.M9
        )
        open.fuuroList += openPon(MahjongTile.M1)
        val openSettlement = open.calcYakuSettlementForWin(
            winningTile = MahjongTile.M7,
            isWinningTileInHands = false,
            rule = MahjongRule(),
            generalSituation = defaultGeneralSituation(),
            personalSituation = defaultPersonalSituation(),
            doraIndicators = emptyList(),
            uraDoraIndicators = emptyList()
        )
        assertContains(openSettlement.yakuList, "CHINITSU")
        assertEquals(5, openSettlement.han)
    }

    @Test
    fun `dora alone does not make a hand winnable`() {
        val player = RiichiPlayerState("Alice", "alice")
        player.hands += tiles(
            MahjongTile.M1,
            MahjongTile.M2,
            MahjongTile.M3,
            MahjongTile.P3,
            MahjongTile.P4,
            MahjongTile.P5,
            MahjongTile.S3,
            MahjongTile.S4,
            MahjongTile.S5,
            MahjongTile.M7,
            MahjongTile.M8,
            MahjongTile.M9,
            MahjongTile.EAST
        )

        val canWin = player.canWin(
            winningTile = MahjongTile.EAST,
            isWinningTileInHands = false,
            rule = MahjongRule(),
            generalSituation = defaultGeneralSituation(doraIndicators = listOf(MahjongTile.NORTH)),
            personalSituation = defaultPersonalSituation(jikaze = Wind.WEST)
        )
        val settlement = player.calcYakuSettlementForWin(
            winningTile = MahjongTile.EAST,
            isWinningTileInHands = false,
            rule = MahjongRule(),
            generalSituation = defaultGeneralSituation(doraIndicators = listOf(MahjongTile.NORTH)),
            personalSituation = defaultPersonalSituation(jikaze = Wind.WEST),
            doraIndicators = listOf(MahjongTile.NORTH),
            uraDoraIndicators = emptyList()
        )

        assertFalse(canWin)
        assertTrue(settlement.yakuList.isEmpty())
        assertEquals(0, settlement.han)
        assertEquals(0, settlement.score)
    }

    @Test
    fun `double riichi still enables ippatsu when no one calls`() {
        val player = RiichiPlayerState("Alice", "alice")
        val south = RiichiPlayerState("South", "south")
        val west = RiichiPlayerState("West", "west")
        val north = RiichiPlayerState("North", "north")
        val riichiDiscard = TileInstance(mahjongTile = MahjongTile.M1)
        val discards = listOf(
            riichiDiscard,
            TileInstance(mahjongTile = MahjongTile.P1),
            TileInstance(mahjongTile = MahjongTile.S1)
        )
        player.doubleRiichi = true
        player.riichiSengenTile = riichiDiscard

        assertTrue(player.isIppatsu(listOf(player, south, west, north), discards))
    }

    private fun analysisVersion(player: RiichiPlayerState): Long =
        playerField("analysisStateVersion").getLong(player)

    private fun cachedTilePairsVersion(player: RiichiPlayerState): Long =
        playerField("cachedTilePairsForRiichiVersion").getLong(player)

    private fun cachedDiscardSuggestionsVersion(player: RiichiPlayerState): Long =
        playerField("cachedDiscardSuggestionsVersion").getLong(player)

    private fun playerField(name: String): Field =
        RiichiPlayerState::class.java.getDeclaredField(name).apply { isAccessible = true }

    private fun tiles(vararg tiles: MahjongTile): List<TileInstance> =
        tiles.map { TileInstance(mahjongTile = it) }

    private fun openChii(first: MahjongTile, second: MahjongTile, third: MahjongTile): Fuuro {
        val claim = TileInstance(mahjongTile = first)
        return Fuuro(
            type = MeldType.CHII,
            tileInstances = listOf(claim, TileInstance(mahjongTile = second), TileInstance(mahjongTile = third)),
            claimTarget = ClaimTarget.RIGHT,
            claimTile = claim
        )
    }

    private fun openPon(tile: MahjongTile): Fuuro {
        val claim = TileInstance(mahjongTile = tile)
        return Fuuro(
            type = MeldType.PON,
            tileInstances = listOf(claim, TileInstance(mahjongTile = tile), TileInstance(mahjongTile = tile)),
            claimTarget = ClaimTarget.RIGHT,
            claimTile = claim
        )
    }

    private fun defaultGeneralSituation(
        doraIndicators: List<MahjongTile> = emptyList(),
        uraDoraIndicators: List<MahjongTile> = emptyList()
    ): GeneralSituation = GeneralSituation(
        isFirstRound = false,
        isHoutei = false,
        bakaze = Wind.SOUTH,
        doraIndicators = doraIndicators,
        uraDoraIndicators = uraDoraIndicators
    )

    private fun defaultPersonalSituation(
        jikaze: Wind = Wind.WEST
    ): PersonalSituation = PersonalSituation(
        isTsumo = false,
        isIppatsu = false,
        isRiichi = false,
        isDoubleRiichi = false,
        isChankan = false,
        isRinshanKaihoh = false,
        jikaze = jikaze
    )
}

