package doublemoon.mahjongcraft.paper.i18n

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

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
        assertEquals("荣和", messages.plain(Locale.forLanguageTag("zh-TW"), "command.action.ron"))
        assertEquals("荣和", messages.plain(Locale.forLanguageTag("zh-HK"), "command.action.ron"))
        assertEquals("荣和", messages.plain(Locale.forLanguageTag("zh-MO"), "command.action.ron"))
    }
}
