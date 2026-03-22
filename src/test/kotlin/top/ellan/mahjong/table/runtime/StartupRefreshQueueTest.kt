package top.ellan.mahjong.table.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StartupRefreshQueueTest {
    @Test
    fun `enqueue deduplicates ids case insensitively and preserves order`() {
        val queue = StartupRefreshQueue()

        queue.enqueue("abc123")
        queue.enqueue("ABC123")
        queue.enqueue("def456")

        assertEquals("ABC123", queue.poll())
        assertEquals("DEF456", queue.poll())
        assertTrue(queue.isEmpty())
    }

    @Test
    fun `polled id can be enqueued again later`() {
        val queue = StartupRefreshQueue()

        queue.enqueue("table01")
        assertEquals("TABLE01", queue.poll())
        queue.enqueue("table01")

        assertEquals("TABLE01", queue.poll())
        assertTrue(queue.isEmpty())
    }
}

