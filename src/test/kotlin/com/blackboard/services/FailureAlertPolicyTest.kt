package com.blackboard.services

import com.blackboard.services.FailureAlertPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class FailureAlertPolicyTest {

    private fun failed(n: Int) = SaveStatus.Failed(IOException("disk full"), duringLoad = false, consecutiveFailures = n)

    @Test
    fun `single failures do not notify`() {
        val policy = FailureAlertPolicy(threshold = 3)
        assertEquals(Decision.NONE, policy.onStatus(failed(1)))
        assertEquals(Decision.NONE, policy.onStatus(SaveStatus.Saving))
        assertEquals(Decision.NONE, policy.onStatus(failed(2)))
    }

    @Test
    fun `notifies once per streak`() {
        val policy = FailureAlertPolicy(threshold = 3)
        assertEquals(Decision.NOTIFY, policy.onStatus(failed(3)))
        assertEquals(Decision.NONE, policy.onStatus(SaveStatus.Saving))
        assertEquals(Decision.NONE, policy.onStatus(failed(4)))
        assertEquals(Decision.NONE, policy.onStatus(failed(5)))
    }

    @Test
    fun `skipped intermediate states still notify`() {
        assertEquals(Decision.NOTIFY, FailureAlertPolicy(threshold = 3).onStatus(failed(7)))
    }

    @Test
    fun `success expires the alert and a new streak can notify again`() {
        val policy = FailureAlertPolicy(threshold = 3)
        policy.onStatus(failed(3))
        assertEquals(Decision.EXPIRE, policy.onStatus(SaveStatus.Saved))
        assertEquals(Decision.NONE, policy.onStatus(SaveStatus.Saved))
        assertEquals(Decision.NONE, policy.onStatus(failed(1)))
        assertEquals(Decision.NOTIFY, policy.onStatus(failed(3)))
    }

    @Test
    fun `new streak observed without the success in between resets the alert`() {
        val policy = FailureAlertPolicy(threshold = 3)
        policy.onStatus(failed(3))
        assertEquals(Decision.EXPIRE, policy.onStatus(failed(1)))
        assertEquals(Decision.NOTIFY, policy.onStatus(failed(3)))
    }

    @Test
    fun `success without an alert does nothing`() {
        assertEquals(Decision.NONE, FailureAlertPolicy().onStatus(SaveStatus.Saved))
    }
}
