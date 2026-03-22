package top.ellan.mahjong.gb.jni

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GbMahjongNativeLibraryTest {
    @Test
    fun `windows library name uses dll`() {
        assertEquals("mahjongpaper_gb.dll", GbMahjongNativeLibrary.platformLibraryFileName("Windows 11"))
    }

    @Test
    fun `linux library name uses so`() {
        assertEquals("mahjongpaper_gb.so", GbMahjongNativeLibrary.platformLibraryFileName("Linux"))
    }

    @Test
    fun `mac library name uses dylib`() {
        assertEquals("mahjongpaper_gb.dylib", GbMahjongNativeLibrary.platformLibraryFileName("Mac OS X"))
    }

    @Test
    fun `platform resource key normalizes windows amd64`() {
        assertEquals("windows-x86_64", GbMahjongNativeLibrary.platformResourceKey("Windows 11", "AMD64"))
    }

    @Test
    fun `platform resource key normalizes linux arm64`() {
        assertEquals("linux-aarch64", GbMahjongNativeLibrary.platformResourceKey("Linux", "arm64"))
    }

    @Test
    fun `development candidates include build native root`() {
        val expected = GbMahjongNativeLibrary.developmentLibraryCandidates("mahjongpaper_gb.dll")
            .map { it.toString().replace('\\', '/') }
        assertTrue(expected.any { it.endsWith("/build/native/gbmahjong/mahjongpaper_gb.dll") })
    }
}

