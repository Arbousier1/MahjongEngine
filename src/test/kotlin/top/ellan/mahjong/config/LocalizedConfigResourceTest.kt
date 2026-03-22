package top.ellan.mahjong.config

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LocalizedConfigResourceTest {
    @Test
    fun `resolve resource name follows chinese locale variants`() {
        assertEquals("config_zh_CN.yml", LocalizedConfigResource.resolveResourceName(java.util.Locale.forLanguageTag("zh-CN")))
        assertEquals("config_zh_CN.yml", LocalizedConfigResource.resolveResourceName(java.util.Locale.forLanguageTag("zh-Hans-SG")))
        assertEquals("config_zh_CN.yml", LocalizedConfigResource.resolveResourceName(java.util.Locale.forLanguageTag("zh")))
        assertEquals("config_zh_TW.yml", LocalizedConfigResource.resolveResourceName(java.util.Locale.forLanguageTag("zh-TW")))
        assertEquals("config_zh_TW.yml", LocalizedConfigResource.resolveResourceName(java.util.Locale.forLanguageTag("zh-HK")))
        assertEquals("config_zh_TW.yml", LocalizedConfigResource.resolveResourceName(java.util.Locale.forLanguageTag("zh-Hant")))
    }

    @Test
    fun `resolve resource name falls back to english template for unsupported locales`() {
        assertEquals("config.yml", LocalizedConfigResource.resolveResourceName(java.util.Locale.forLanguageTag("en-US")))
        assertEquals("config.yml", LocalizedConfigResource.resolveResourceName(java.util.Locale.forLanguageTag("ja-JP")))
        assertEquals("config.yml", LocalizedConfigResource.resolveResourceName(java.util.Locale.forLanguageTag("fr-FR")))
    }

    @Test
    fun `localized config templates are bundled`() {
        val simplified = javaClass.classLoader.getResourceAsStream("config_zh_CN.yml")
        val traditional = javaClass.classLoader.getResourceAsStream("config_zh_TW.yml")
        assertNotNull(simplified)
        assertNotNull(traditional)

        val simplifiedText = simplified.bufferedReader().use { it.readText() }
        val traditionalText = traditional.bufferedReader().use { it.readText() }
        assertContains(simplifiedText, "# MahjongPaper 闁板秶鐤嗛弬鍥︽")
        assertContains(traditionalText, "# MahjongPaper 鐟奉厼鐣惧?)
    }
}

