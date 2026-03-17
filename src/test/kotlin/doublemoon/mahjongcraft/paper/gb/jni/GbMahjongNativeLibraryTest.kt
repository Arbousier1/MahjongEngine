package doublemoon.mahjongcraft.paper.gb.jni

import kotlin.test.Test
import kotlin.test.assertEquals

class GbMahjongNativeLibraryTest {
    @Test
    fun `windows library name uses dll`() {
        assertEquals("mahjongpaper_gb.dll", GbMahjongNativeLibrary.platformLibraryFileName("Windows 11"))
    }

    @Test
    fun `linux library name uses so`() {
        assertEquals("libmahjongpaper_gb.so", GbMahjongNativeLibrary.platformLibraryFileName("Linux"))
    }

    @Test
    fun `mac library name uses dylib`() {
        assertEquals("libmahjongpaper_gb.dylib", GbMahjongNativeLibrary.platformLibraryFileName("Mac OS X"))
    }
}
