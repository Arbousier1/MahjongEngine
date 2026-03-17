package doublemoon.mahjongcraft.paper.i18n

import java.util.Locale
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MessageServiceTest {
    private val messages = LocalizedMessages()

    @Test
    fun `plain returns english text for english locale`() {
        assertContains(messages.plain(Locale.ENGLISH, "command.action.ron"), "Ron")
    }

    @Test
    fun `plain returns chinese text for zh CN locale`() {
        val chinese = messages.plain(Locale.forLanguageTag("zh-CN"), "command.action.ron")
        assertEquals("荣和", chinese)
    }

    @Test
    fun `normalize locale matches supported language and region variants`() {
        assertEquals(Locale.ENGLISH, messages.normalizeLocale("en-US"))
        assertEquals(Locale.forLanguageTag("zh-TW"), messages.normalizeLocale("zh-Hant"))
        assertEquals(Locale.forLanguageTag("zh-TW"), messages.normalizeLocale("zh-TW"))
        assertEquals(Locale.forLanguageTag("zh-HK"), messages.normalizeLocale("zh-HK"))
        assertEquals(Locale.forLanguageTag("zh-MO"), messages.normalizeLocale("zh-MO"))
        assertEquals(Locale.forLanguageTag("zh-CN"), messages.normalizeLocale("zh-SG"))
        assertEquals(Locale.forLanguageTag("zh-CN"), messages.normalizeLocale("de-DE"))
    }

    @Test
    fun `render keeps command help text available`() {
        val rendered = messages.plain(Locale.ENGLISH, "command.help.create")
        assertContains(rendered, "/mahjong create")
        assertContains(rendered, "Create a new table")
    }

    @Test
    fun `yaku labels are localized`() {
        assertEquals("Riichi", messages.plain(Locale.ENGLISH, "yaku.reach"))
        assertEquals("立直", messages.plain(Locale.forLanguageTag("zh-CN"), "yaku.reach"))
        assertEquals("国士无双", messages.plain(Locale.forLanguageTag("zh-CN"), "yakuman.kokushimuso"))
    }

    @Test
    fun `traditional region bundles are addressable`() {
        assertEquals("榮和", messages.plain(Locale.forLanguageTag("zh-TW"), "command.action.ron"))
        assertEquals("榮和", messages.plain(Locale.forLanguageTag("zh-HK"), "command.action.ron"))
        assertEquals("榮和", messages.plain(Locale.forLanguageTag("zh-MO"), "command.action.ron"))
    }
    @Test
    fun `render caches static components without placeholders`() {
        val first = messages.render(Locale.ENGLISH, "command.inspect_sent")
        val second = messages.render(Locale.ENGLISH, "command.inspect_sent")

        assertSame(first, second)
    }

    @Test
    fun `render keeps placeholder substitutions dynamic`() {
        val first = messages.plain(
            Locale.ENGLISH,
            "command.inspect_summary",
            messages.tag("table_id", "A"),
            messages.tag("center", "1"),
            messages.tag("anchor", "2"),
            messages.tag("span_x", "3"),
            messages.tag("span_z", "4")
        )
        val second = messages.plain(
            Locale.ENGLISH,
            "command.inspect_summary",
            messages.tag("table_id", "B"),
            messages.tag("center", "5"),
            messages.tag("anchor", "6"),
            messages.tag("span_x", "7"),
            messages.tag("span_z", "8")
        )

        assertContains(first, "A")
        assertContains(second, "B")
    }

    @Test
    fun `number formats integers with locale aware grouping`() {
        val english = messages.number(Locale.ENGLISH, "value", 25000)
        val chinese = messages.number(Locale.forLanguageTag("zh-CN"), "value", 25000)

        val englishRendered = messages.plain(Locale.ENGLISH, "ui.score.total", english)
        val chineseRendered = messages.plain(Locale.forLanguageTag("zh-CN"), "ui.score.total", chinese)

        assertContains(englishRendered, "25,000")
        assertContains(chineseRendered, "25,000")
    }

    @Test
    fun `message bundle index points to language resources`() {
        val stream = javaClass.classLoader.getResourceAsStream("i18n/_index.json")
        assertNotNull(stream)

        val text = stream.bufferedReader().use { it.readText() }
        assertContains(text, "language/messages.properties")
        assertContains(text, "language/messages_zh_CN.properties")
    }

    @Test
    fun `localized bundles contain every english message key`() {
        val english = loadBundle("language/messages.properties").stringPropertyNames()
        val localizedBundles = listOf(
            "language/messages_zh_CN.properties",
            "language/messages_zh_TW.properties",
            "language/messages_zh_HK.properties",
            "language/messages_zh_MO.properties"
        )

        localizedBundles.forEach { resource ->
            val localized = loadBundle(resource).stringPropertyNames()
            val missing = english - localized
            assertTrue(missing.isEmpty(), "$resource is missing keys: $missing")
        }
    }

    private fun loadBundle(resource: String): Properties {
        val stream = javaClass.classLoader.getResourceAsStream(resource)
        assertNotNull(stream, "Missing resource $resource")
        return Properties().apply {
            stream.reader(Charsets.UTF_8).use { load(it) }
        }
    }
}
