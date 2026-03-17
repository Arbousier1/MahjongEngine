package doublemoon.mahjongcraft.paper.table

import doublemoon.mahjongcraft.paper.table.presentation.SettlementFeedbackGate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettlementFeedbackGateTest {
    @Test
    fun `blank fingerprint is never treated as new settlement`() {
        assertFalse(SettlementFeedbackGate.isNewSettlement("", "old"))
        assertFalse(SettlementFeedbackGate.isNewSettlement("   ", "old"))
        assertFalse(SettlementFeedbackGate.isNewSettlement(null, "old"))
    }

    @Test
    fun `same fingerprint is not reprocessed`() {
        assertFalse(SettlementFeedbackGate.isNewSettlement("abc", "abc"))
    }

    @Test
    fun `different fingerprint is processed once`() {
        assertTrue(SettlementFeedbackGate.isNewSettlement("abc", ""))
        assertTrue(SettlementFeedbackGate.isNewSettlement("def", "abc"))
    }
}
