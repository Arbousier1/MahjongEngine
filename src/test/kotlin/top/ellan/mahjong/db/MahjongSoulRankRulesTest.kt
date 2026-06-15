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
    fun `stronger competitive field increases rank reward`() {
        val profile = MahjongSoulRankProfile(
            playerId,
            "Player",
            MahjongSoulRankRules.Tier.ADEPT,
            1,
            300,
            20,
            5,
            5,
            5,
            5
        )
        val field = listOf(
            profile,
            rankProfile("00000000-0000-0000-0000-000000000002", MahjongSoulRankRules.Tier.EXPERT, 1, 600),
            rankProfile("00000000-0000-0000-0000-000000000003", MahjongSoulRankRules.Tier.EXPERT, 1, 600),
            rankProfile("00000000-0000-0000-0000-000000000004", MahjongSoulRankRules.Tier.EXPERT, 1, 600)
        )

        val result = MahjongSoulRankRules.applyMatch(
            profile,
            MahjongSoulRankRules.Room.GOLD,
            MahjongSoulRankRules.MatchLength.SOUTH,
            1,
            25000,
            false,
            field
        )

        assertEquals(113, result.rankPointChange())
        assertEquals(413, result.updated().rankPoints())
    }

    @Test
    fun `weaker competitive field reduces rank reward`() {
        val profile = MahjongSoulRankProfile(
            playerId,
            "Player",
            MahjongSoulRankRules.Tier.EXPERT,
            1,
            600,
            20,
            5,
            5,
            5,
            5
        )
        val field = listOf(
            profile,
            rankProfile("00000000-0000-0000-0000-000000000002", MahjongSoulRankRules.Tier.ADEPT, 1, 300),
            rankProfile("00000000-0000-0000-0000-000000000003", MahjongSoulRankRules.Tier.ADEPT, 1, 300),
            rankProfile("00000000-0000-0000-0000-000000000004", MahjongSoulRankRules.Tier.ADEPT, 1, 300)
        )

        val result = MahjongSoulRankRules.applyMatch(
            profile,
            MahjongSoulRankRules.Room.GOLD,
            MahjongSoulRankRules.MatchLength.SOUTH,
            1,
            25000,
            false,
            field
        )

        assertEquals(77, result.rankPointChange())
        assertEquals(677, result.updated().rankPoints())
    }

    @Test
    fun `rank profile formats competitive summary metrics`() {
        val profile = MahjongSoulRankProfile(
            playerId,
            "Player",
            MahjongSoulRankRules.Tier.EXPERT,
            2,
            650,
            10,
            3,
            2,
            4,
            1
        )

        assertEquals("2.30", MahjongSoulRankRules.formatAveragePlace(profile))
        assertEquals("30.0%", MahjongSoulRankRules.formatFirstRate(profile))
        assertEquals("50.0%", MahjongSoulRankRules.formatTopTwoRate(profile))
        assertEquals("10.0%", MahjongSoulRankRules.formatFourthRate(profile))
        assertEquals(750, MahjongSoulRankRules.promotionRemaining(profile))
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

    private fun rankProfile(
        playerId: String,
        tier: MahjongSoulRankRules.Tier,
        level: Int,
        rankPoints: Int
    ): MahjongSoulRankProfile {
        return MahjongSoulRankProfile(
            UUID.fromString(playerId),
            "Player",
            tier,
            level,
            rankPoints,
            20,
            5,
            5,
            5,
            5
        )
    }
}

