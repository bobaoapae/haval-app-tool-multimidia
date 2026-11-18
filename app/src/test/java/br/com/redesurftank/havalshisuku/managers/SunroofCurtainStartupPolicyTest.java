package br.com.redesurftank.havalshisuku.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SunroofCurtainStartupPolicyTest {
    @Test
    public void readyEventKeepsFullSettle() {
        assertEquals(20_000L, SunroofCurtainStartupPolicy.settleMsFor(
                SunroofCurtainStartupPolicy.Trigger.DRIVING_READY_EVENT));
    }

    @Test
    public void fallbackKeepsFullSettle() {
        assertEquals(20_000L, SunroofCurtainStartupPolicy.settleMsFor(
                SunroofCurtainStartupPolicy.Trigger.FALLBACK_TIMEOUT));
    }

    @Test
    public void readyPollSkipsSettleBecauseReadyWasAlreadyOnForTheCheckDelay() {
        assertEquals(0L, SunroofCurtainStartupPolicy.settleMsFor(
                SunroofCurtainStartupPolicy.Trigger.DRIVING_READY_POLL));
    }

    @Test
    public void curtainIsNeverCommandedBeforeSettleAfterKnownReady() {
        for (SunroofCurtainStartupPolicy.Trigger trigger : SunroofCurtainStartupPolicy.Trigger.values()) {
            long sinceReady = SunroofCurtainStartupPolicy.minimumReadyOnMs(trigger)
                    + SunroofCurtainStartupPolicy.settleMsFor(trigger);
            assertTrue(trigger.traceName + " sinceReady=" + sinceReady,
                    sinceReady >= SunroofCurtainStartupPolicy.SETTLE_AFTER_READY_MS);
        }
    }

    @Test
    public void coldBootWithReadyAlreadyOnEvaluatesAtReadyCheckDelay() {
        // Carro READY antes do app armar: nenhum evento chega, o poll consome o armamento.
        long firstEvaluationAfterArm = SunroofCurtainStartupPolicy.READY_CHECK_DELAY_MS
                + SunroofCurtainStartupPolicy.settleMsFor(
                        SunroofCurtainStartupPolicy.Trigger.DRIVING_READY_POLL);
        assertEquals(30_000L, firstEvaluationAfterArm);
    }

    @Test
    public void traceNamesMatchExistingCurtainTraces() {
        assertEquals("driving_ready_event", SunroofCurtainStartupPolicy.Trigger.DRIVING_READY_EVENT.traceName);
        assertEquals("driving_ready_poll", SunroofCurtainStartupPolicy.Trigger.DRIVING_READY_POLL.traceName);
        assertEquals("fallback_timeout", SunroofCurtainStartupPolicy.Trigger.FALLBACK_TIMEOUT.traceName);
    }
}
