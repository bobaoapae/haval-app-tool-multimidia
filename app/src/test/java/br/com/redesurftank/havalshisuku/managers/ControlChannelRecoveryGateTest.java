package br.com.redesurftank.havalshisuku.managers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ControlChannelRecoveryGateTest {
    @Test
    public void concurrentTriggerIsCoalescedUntilRelease() {
        ControlChannelRecoveryGate gate = new ControlChannelRecoveryGate();
        assertTrue(gate.tryAcquire());
        assertFalse(gate.tryAcquire());
        gate.release();
        assertTrue(gate.tryAcquire());
    }
}
