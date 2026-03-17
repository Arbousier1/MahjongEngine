package doublemoon.mahjongcraft.paper.gb.jni

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class GbMeldInput(
    val type: String,
    val tiles: List<String>,
    val claimedTile: String? = null,
    val fromSeat: String? = null,
    val open: Boolean = true
)

@Serializable
data class GbFanRequest(
    val ruleProfile: String = "GB_MAHJONG",
    val handTiles: List<String>,
    val melds: List<GbMeldInput> = emptyList(),
    val winningTile: String,
    val winType: String,
    val seatWind: String? = null,
    val roundWind: String? = null,
    val flowerTiles: List<String> = emptyList(),
    val flags: List<String> = emptyList()
)

@Serializable
data class GbFanEntry(
    val name: String,
    val fan: Int,
    val count: Int = 1
)

@Serializable
data class GbFanResponse(
    val valid: Boolean,
    val totalFan: Int = 0,
    val fans: List<GbFanEntry> = emptyList(),
    val error: String? = null
)

@Serializable
data class GbTingRequest(
    val ruleProfile: String = "GB_MAHJONG",
    val handTiles: List<String>,
    val melds: List<GbMeldInput> = emptyList(),
    val seatWind: String? = null,
    val roundWind: String? = null,
    val flowerTiles: List<String> = emptyList(),
    val flags: List<String> = emptyList()
)

@Serializable
data class GbTingCandidate(
    val tile: String,
    val totalFan: Int? = null,
    val fans: List<GbFanEntry> = emptyList()
)

@Serializable
data class GbTingResponse(
    val valid: Boolean,
    val waits: List<GbTingCandidate> = emptyList(),
    val error: String? = null
)

@Serializable
data class GbSeatPointsInput(
    val seat: String,
    val points: Int
)

@Serializable
data class GbWinRequest(
    val ruleProfile: String = "GB_MAHJONG",
    val handTiles: List<String>,
    val melds: List<GbMeldInput> = emptyList(),
    val winningTile: String,
    val winType: String,
    val winnerSeat: String,
    val discarderSeat: String? = null,
    val seatWind: String? = null,
    val roundWind: String? = null,
    val seatPoints: List<GbSeatPointsInput> = emptyList(),
    val flowerTiles: List<String> = emptyList(),
    val flags: List<String> = emptyList()
)

@Serializable
data class GbScoreDelta(
    val seat: String,
    val delta: Int
)

@Serializable
data class GbWinResponse(
    val valid: Boolean,
    val title: String = "WIN",
    val totalFan: Int = 0,
    val fans: List<GbFanEntry> = emptyList(),
    val scoreDeltas: List<GbScoreDelta> = emptyList(),
    val error: String? = null
)

object GbMahjongNativeJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @JvmStatic
    fun encodeFanRequest(request: GbFanRequest): String = json.encodeToString(request)

    @JvmStatic
    fun decodeFanResponse(payload: String): GbFanResponse = json.decodeFromString(payload)

    @JvmStatic
    fun encodeTingRequest(request: GbTingRequest): String = json.encodeToString(request)

    @JvmStatic
    fun decodeTingResponse(payload: String): GbTingResponse = json.decodeFromString(payload)

    @JvmStatic
    fun encodeWinRequest(request: GbWinRequest): String = json.encodeToString(request)

    @JvmStatic
    fun decodeWinResponse(payload: String): GbWinResponse = json.decodeFromString(payload)
}
