package br.com.redesurftank.displaypilot;

import org.junit.Test;
import static org.junit.Assert.*;

public class DisplayTargetTest {
    private DisplayTarget target(int id, int rotation, boolean valid, boolean on, boolean privateDisplay) {
        return new DisplayTarget(id, "Display", 1920, 720, rotation, 0, valid, on, privateDisplay);
    }
    @Test public void arbitrarySecondaryIdAccepted() { assertNull(target(71, 0, true, true, false).rejection()); }
    @Test public void defaultDisplayRejected() { assertNotNull(target(0, 0, true, true, false).rejection()); }
    @Test public void negativeIdRejected() { assertNotNull(target(-1, 0, true, true, false).rejection()); }
    @Test public void invalidDisplayRejected() { assertNotNull(target(3, 0, false, true, false).rejection()); }
    @Test public void offOrUnknownDisplayRejected() { assertNotNull(target(3, 0, true, false, false).rejection()); }
    @Test public void privateDisplayRejected() { assertNotNull(target(4096, 0, true, true, true).rejection()); }
    @Test public void missingDimensionsRejected() { assertNotNull(new DisplayTarget(7, "X", 0, 720, 0, 0, true, true, false).rejection()); }
    @Test public void rotation180InvalidatesEvenWithSameSize() { assertFalse(target(7, 0, true, true, false).sameConfiguration(target(7, 2, true, true, false))); }
    @Test public void missingDisplayInvalidates() { assertFalse(target(7, 0, true, true, false).sameConfiguration(null)); }
    @Test public void unchangedSnapshotAccepted() { assertTrue(target(7, 0, true, true, false).sameConfiguration(target(7, 0, true, true, false))); }
    @Test public void reusedIdWithDifferentSizeInvalidates() { assertFalse(target(7, 0, true, true, false).sameConfiguration(new DisplayTarget(7, "X", 1280, 720, 0, 0, true, true, false))); }
}
