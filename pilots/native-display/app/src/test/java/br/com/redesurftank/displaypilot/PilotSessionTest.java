package br.com.redesurftank.displaypilot;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class PilotSessionTest {
    private final DisplayTarget target = new DisplayTarget(7, "Test display", 1920, 720, 0, 0, true, true, false);
    private final Viewport viewport = new Viewport(40, 25, 20, 40);
    private FakeRenderer renderer;
    private PilotSession session;
    private String status;

    private static class FakeRenderer implements PilotSession.Renderer {
        int opens, closes;
        boolean fail, dismissDuringOpen, failClose;
        Runnable callback;
        @Override public PilotSession.Output open(DisplayTarget target, Viewport viewport, Runnable dismissed) {
            ++opens;
            callback = dismissed;
            if (fail) throw new SecurityException("OEM denial");
            if (dismissDuringOpen) dismissed.run();
            return () -> {
                ++closes;
                dismissed.run();
                if (failClose) throw new IllegalStateException("Already removed");
            };
        }
    }
    @Before public void setup() {
        renderer = new FakeRenderer();
        session = new PilotSession(renderer, message -> status = message);
        session.resume();
    }
    private void open() { session.open(target, viewport, true, true); }
    @Test public void validExplicitRequestOpens() { open(); assertTrue(session.isOpen()); assertEquals(1, renderer.opens); }
    @Test public void missingParkedConfirmationRefuses() { session.open(target, viewport, false, true); assertEquals(0, renderer.opens); }
    @Test public void missingAreaConfirmationRefuses() { session.open(target, viewport, true, false); assertEquals(0, renderer.opens); }
    @Test public void missingTargetRefuses() { session.open(null, viewport, true, true); assertEquals(0, renderer.opens); }
    @Test public void missingViewportRefuses() { session.open(target, null, true, true); assertEquals(0, renderer.opens); }
    @Test public void primaryDisplayRefuses() {
        session.open(new DisplayTarget(0, "Main", 1920, 720, 0, 0, true, true, false), viewport, true, true);
        assertEquals(0, renderer.opens);
    }
    @Test public void privateDisplayRefuses() {
        session.open(new DisplayTarget(4096, "Private", 480, 240, 0, 4, true, true, true), viewport, true, true);
        assertEquals(0, renderer.opens);
    }
    @Test public void permissionFailureHasNoFallback() {
        renderer.fail = true; open(); assertFalse(session.isOpen()); assertEquals(1, renderer.opens);
        assertTrue(status.contains("SecurityException"));
    }
    @Test public void pauseClosesAndDisarms() { open(); session.pause(); assertFalse(session.isOpen()); assertEquals(1, renderer.closes); }
    @Test public void queuedOpenAfterPauseRefuses() { session.pause(); open(); assertEquals(0, renderer.opens); }
    @Test public void resumeDoesNotReopen() { open(); session.pause(); session.resume(); assertEquals(1, renderer.opens); assertFalse(session.isOpen()); }
    @Test public void repeatedOpenReplacesExactlyOneWindow() { open(); open(); assertEquals(2, renderer.opens); assertEquals(1, renderer.closes); assertTrue(session.isOpen()); }
    @Test public void repeatedCloseIsIdempotent() { open(); session.stop("stop"); session.pause(); session.stop("destroy"); assertEquals(1, renderer.closes); }
    @Test public void selectedDisplayChangeCloses() { open(); session.displayChanged(7); assertFalse(session.isOpen()); assertEquals(1, renderer.closes); }
    @Test public void unrelatedDisplayChangeDoesNotMutateSession() { open(); session.displayChanged(9); assertTrue(session.isOpen()); }
    @Test public void displayChangeDoesNotAutoReopen() { open(); session.displayChanged(7); session.displayChanged(7); assertEquals(1, renderer.opens); }
    @Test public void systemDismissReleasesReference() { open(); renderer.callback.run(); assertFalse(session.isOpen()); assertEquals(1, renderer.closes); }
    @Test public void staleDismissCannotCloseNewWindow() {
        open(); Runnable old = renderer.callback; open(); old.run(); assertTrue(session.isOpen()); assertEquals(1, renderer.closes);
    }
    @Test public void synchronousDismissDuringShowCannotResurrectWindow() {
        renderer.dismissDuringOpen = true; open(); assertFalse(session.isOpen()); assertEquals(1, renderer.closes);
    }
    @Test public void closeFailureStillDisarms() { open(); renderer.failClose = true; session.pause(); assertFalse(session.isOpen()); }
    @Test public void closeFailureBlocksNewOpen() {
        open(); renderer.failClose = true; session.stop("stop"); renderer.failClose = false;
        open(); assertEquals(1, renderer.opens); assertFalse(session.isOpen());
        assertTrue(status.contains("bloqueadas"));
    }
    @Test public void closeFailureIsNotClearedByResume() {
        open(); renderer.failClose = true; session.pause(); session.resume(); open();
        assertEquals(1, renderer.opens); assertTrue(status.contains("Falha ao encerrar"));
    }
    @Test public void invalidNewRequestClosesExistingWindow() { open(); session.open(null, viewport, true, true); assertFalse(session.isOpen()); assertEquals(1, renderer.closes); }
    @Test public void hundredOpenCloseCyclesRetainNothing() {
        for (int i = 0; i < 100; i++) { open(); session.stop("stop"); }
        assertFalse(session.isOpen()); assertEquals(100, renderer.opens); assertEquals(100, renderer.closes);
    }
}
