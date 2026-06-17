package top.ellan.mahjong.gb.jni

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GbMahjongRealWorldFanCoverageTest {
    @Test
    fun `MCR source-backed official fan catalog matches JNI fan keys`() {
        // Source baseline: Chinese Official Mahjong Competition Rules - MCR fan names.
        val jniKeys = extractJniFanKeys()

        assertEquals(MCR_OFFICIAL_FAN_KEYS, jniKeys)
    }

    @Test
    fun `MCR source-backed representative hands score through the native bridge`() {
        val bridge = GbMahjongNativeBridge()
        assumeTrue(bridge.isAvailable(), bridge.availabilityDetail())

        assertFan(
            bridge,
            "big four winds",
            hand = listOf("F1", "F1", "F1", "F2", "F2", "F2", "F3", "F3", "F3", "F4", "F4", "F4", "J1"),
            winningTile = "J1",
            expected = "DASIXI"
        )
        assertFan(
            bridge,
            "big three dragons",
            hand = listOf("J1", "J1", "J1", "J2", "J2", "J2", "J3", "J3", "J3", "W2", "W3", "W4", "B9"),
            winningTile = "B9",
            expected = "DASANYUAN"
        )
        assertFan(
            bridge,
            "thirteen orphans",
            hand = listOf("W1", "W9", "T1", "T9", "B1", "B9", "F1", "F2", "F3", "F4", "J1", "J2", "J3"),
            winningTile = "W1",
            expected = "SHISANYAO"
        )
        assertFan(
            bridge,
            "seven pairs",
            hand = listOf("W1", "W1", "W2", "W2", "W3", "W3", "B4", "B4", "B5", "B5", "T6", "T6", "T7"),
            winningTile = "T7",
            expected = "QIDUI"
        )
        assertFan(
            bridge,
            "pure straight",
            hand = listOf("W1", "W2", "W3", "W4", "W5", "W6", "W7", "W8", "B1", "B1", "B1", "T2", "T2"),
            winningTile = "W9",
            expected = "QINGLONG"
        )
        assertFan(
            bridge,
            "all green",
            hand = listOf("T2", "T3", "T4", "T2", "T3", "T4", "T6", "T6", "T6", "T8", "T8", "T8", "J2"),
            winningTile = "J2",
            expected = "LVYISE"
        )
        assertFan(
            bridge,
            "self draw",
            hand = listOf("W2", "W3", "W4", "B2", "B3", "B4", "T2", "T3", "T4", "W6", "W7", "W8", "B9"),
            winningTile = "B9",
            expected = "BUQIUREN",
            winType = "SELF_DRAW"
        )
    }

    private fun assertFan(
        bridge: GbMahjongNativeBridge,
        caseName: String,
        hand: List<String>,
        winningTile: String,
        expected: String,
        winType: String = "DISCARD",
        flags: List<String> = emptyList()
    ) {
        val response = bridge.evaluateFan(
            GbFanRequest(
                handTiles = hand,
                winningTile = winningTile,
                winType = winType,
                seatWind = "EAST",
                roundWind = "EAST",
                flags = flags
            )
        )

        assertTrue(response.valid, "$caseName should be a valid MCR hand: ${response.error}")
        assertContains(response.fans.map { it.name }, expected, "$caseName should include $expected")
    }

    private fun extractJniFanKeys(): List<String> {
        val source = Files.readString(Path.of("native/gbmahjong/src/gbmahjong_jni.cpp"))
        val arrayBody = source
            .substringAfter("static const char* KEYS[] = {")
            .substringBefore("};")
        return arrayBody
            .lineSequence()
            .map { it.trim().removeSuffix(",") }
            .filter { it.startsWith("\"") }
            .map { it.trim('"') }
            .filter { it != "INVALID" }
            .toList()
    }

    private companion object {
        val MCR_OFFICIAL_FAN_KEYS = listOf(
            "DASIXI",
            "DASANYUAN",
            "LVYISE",
            "JIULIANBAODENG",
            "SIGANG",
            "LIANQIDUI",
            "SHISANYAO",
            "QINGYAOJIU",
            "XIAOSIXI",
            "XIAOSANYUAN",
            "ZIYISE",
            "SIANKE",
            "YISESHUANGLONGHUI",
            "YISESITONGSHUN",
            "YISESIJIEGAO",
            "YISESIBUGAO",
            "SANGANG",
            "HUNYAOJIU",
            "QIDUI",
            "QIXINGBUKAO",
            "QUANSHUANGKE",
            "QINGYISE",
            "YISESANTONGSHUN",
            "YISESANJIEGAO",
            "QUANDA",
            "QUANZHONG",
            "QUANXIAO",
            "QINGLONG",
            "SANSESHUANGLONGHUI",
            "YISESANBUGAO",
            "QUANDAIWU",
            "SANTONGKE",
            "SANANKE",
            "QUANBUKAO",
            "ZUHELONG",
            "DAYUWU",
            "XIAOYUWU",
            "SANFENGKE",
            "HUALONG",
            "TUIBUDAO",
            "SANSESANTONGSHUN",
            "SANSESANJIEGAO",
            "WUFANHU",
            "MIAOSHOUHUICHUN",
            "HAIDILAOYUE",
            "GANGSHANGKAIHUA",
            "QIANGGANGHU",
            "PENGPENGHU",
            "HUNYISE",
            "SANSESANBUGAO",
            "WUMENQI",
            "QUANQIUREN",
            "SHUANGANGANG",
            "SHUANGJIANKE",
            "QUANDAIYAO",
            "BUQIUREN",
            "SHUANGMINGGANG",
            "HUJUEZHANG",
            "JIANKE",
            "QUANFENGKE",
            "MENFENGKE",
            "MENQIANQING",
            "PINGHU",
            "SIGUIYI",
            "SHUANGTONGKE",
            "SHUANGANKE",
            "ANGANG",
            "DUANYAO",
            "YIBANGAO",
            "XIXIANGFENG",
            "LIANLIU",
            "LAOSHAOFU",
            "YAOJIUKE",
            "MINGGANG",
            "QUEYIMEN",
            "WUZI",
            "BIANZHANG",
            "KANZHANG",
            "DANDIAOJIANG",
            "ZIMO",
            "HUAPAI",
            "MINGANGANG"
        )
    }
}
