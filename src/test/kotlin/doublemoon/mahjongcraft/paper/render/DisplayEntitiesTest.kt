package doublemoon.mahjongcraft.paper.render

import org.bukkit.Location
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DisplayEntitiesTest {
    @Test
    fun `tile display yaw follows upstream renderer`() {
        assertEquals(-90.0f, DisplayEntities.tileDisplayYaw(-90.0f))
        assertEquals(-180.0f, DisplayEntities.tileDisplayYaw(0.0f))
        assertEquals(90.0f, DisplayEntities.tileDisplayYaw(90.0f))
        assertEquals(0.0f, DisplayEntities.tileDisplayYaw(180.0f))
    }

    @Test
    fun `standing hand pose lifts tile by half height`() {
        val location = DisplayEntities.tileRenderLocation(Location(null, 1.0, 2.0, 3.0), -90.0f, DisplayEntities.TileRenderPose.STANDING)
        assertEquals(1.0, location.x, 1.0E-6)
        assertEquals(2.075, location.y, 1.0E-6)
        assertEquals(3.0, location.z, 1.0E-6)
    }

    @Test
    fun `flat poses keep renderer offset vertical after x rotation`() {
        val faceUp = DisplayEntities.tileRenderLocation(Location(null, 0.0, 0.0, 0.0), 0.0f, DisplayEntities.TileRenderPose.FLAT_FACE_UP)
        val faceDown = DisplayEntities.tileRenderLocation(Location(null, 0.0, 0.0, 0.0), 180.0f, DisplayEntities.TileRenderPose.FLAT_FACE_DOWN)
        assertEquals(0.0375, faceUp.y, 1.0E-6)
        assertEquals(0.0375, faceDown.y, 1.0E-6)
        assertTrue(kotlin.math.abs(faceUp.x) < 1.0E-6 && kotlin.math.abs(faceUp.z) < 1.0E-6)
        assertTrue(kotlin.math.abs(faceDown.x) < 1.0E-6 && kotlin.math.abs(faceDown.z) < 1.0E-6)
    }
}
