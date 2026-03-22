package top.ellan.mahjong.db

import top.ellan.mahjong.riichi.model.MahjongRule
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MahjongSoulRankRulesTest {
    private val playerId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `room parse falls back to silver when blank`() {
        assertEquals(MahjongSoulRankRules.Room.SILVER, MahjongSoulRankRules.Room.parse(""))
        assertEquals(MahjongSoulRankRules.Room.SILVER, MahjongSoulRankRules.Room.parse("   "))
        assertEquals(MahjongSoulRankRules.Room.SILVER, MahjongSoulRankRules.Room.parse(null))
    }

    @Test
    fun `room for chooses configured room by match length`() {
        assertEquals(
            MahjongSoulRankRules.Room.JADE,
            MahjongSoulRankRules.roomFor(MahjongSoulRankRules.MatchLength.EAST, "jade", "throne")
        )
        assertEquals(
            MahjongSoulRankRules.Room.THRONE,
            MahjongSoulRankRules.roomFor(MahjongSoulRankRules.MatchLength.SOUTH, "jade", "throne")
        )
    }

    @Test
    fun `match length maps east and south style games`() {
        assertEquals(MahjongSoulRankRules.MatchLength.EAST, MahjongSoulRankRules.matchLength(MahjongRule.GameLength.EAST))
        assertEquals(MahjongSoulRankRules.MatchLength.SOUTH, MahjongSoulRankRules.matchLength(MahjongRule.GameLength.TWO_WIND))
    }

    @Test
    fun `apply match can promote novice across multiple stages`() {
        val profile = MahjongSoulRankProfile.defaultProfile(playerId, "Player")

        val result = MahjongSoulRankRules.applyMatch(
            profile,
            MahjongSoulRankRules.Room.GOLD,
            MahjongSoulRankRules.MatchLength.SOUTH,
            1,
            45000,
            false
        )

        assertEquals(115, result.rankPointChange())
        assertEquals(MahjongSoulRankRules.Tier.ADEPT, result.updated().tier())
        assertEquals(1, result.updated().level())
        assertEquals(395, result.updated().rankPoints())
        assertEquals(1, result.updated().totalMatches())
        assertEquals(1, result.updated().firstPlaces())
    }

    @Test
    fun `apply celestial match demotes saint 3 when points drop below zero at level one`() {
        val profile = MahjongSoulRankProfile(
            playerId,
            "Player",
            MahjongSoulRankRules.Tier.CELESTIAL,
            1,
            1,
            10,
            3,
            3,
            2,
            2
        )

        val result = MahjongSoulRankRules.applyMatch(
            profile,
            MahjongSoulRankRules.Room.THRONE,
            MahjongSoulRankRules.MatchLength.SOUTH,
            4,
            0,
            false
        )

        assertEquals(-5, result.rankPointChange())
        assertEquals(MahjongSoulRankRules.Tier.SAINT, result.updated().tier())
        assertEquals(3, result.updated().level())
        assertEquals(0, result.updated().rankPoints())
        assertEquals(11, result.updated().totalMatches())
        assertEquals(3, result.updated().firstPlaces())
        assertEquals(3, result.updated().secondPlaces())
        assertEquals(2, result.updated().thirdPlaces())
        assertEquals(3, result.updated().fourthPlaces())
    }

    @Test
    fun `room parse rejects unsupported names`() {
        assertFailsWith<IllegalArgumentException> {
            MahjongSoulRankRules.Room.parse("diamond")
        }
    }
}

