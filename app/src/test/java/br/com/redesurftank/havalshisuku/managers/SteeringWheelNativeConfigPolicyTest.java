package br.com.redesurftank.havalshisuku.managers;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import static br.com.redesurftank.havalshisuku.managers.SteeringWheelNativeConfigPolicy.*;

public class SteeringWheelNativeConfigPolicyTest {
    private static final class Disk {
        String current, backup;
        Disk(String current, String backup) { this.current = current; this.backup = backup; }
    }
    private static class Store implements SteeringWheelNativeConfigPolicy.Store {
        final Disk disk;
        final List<String> writes = new ArrayList<>();
        int reads;
        String changeOnSecondRead;
        boolean failSave, failRead, failWrite;
        Store(Disk disk) { this.disk = disk; }
        public String readCurrent() throws Exception {
            if (failRead) throw new Exception("read failed");
            if (++reads == 2 && changeOnSecondRead != null) disk.current = changeOnSecondRead;
            return disk.current;
        }
        public String readBackup() { return disk.backup; }
        public void saveBackup(String value) throws Exception {
            if (failSave) throw new Exception("disk full");
            disk.backup = value;
        }
        public void writeCurrent(String value) throws Exception {
            if (failWrite) throw new Exception("write failed");
            writes.add(value); disk.current = value;
        }
    }

    @Test public void onlyEnabledCustomShortRequiresCapture() {
        assertFalse(shouldCapture(true, false));
        assertTrue(shouldCapture(true, true));
        assertFalse(shouldCapture(false, true));
        assertFalse(shouldCapture(false, false));
    }

    @Test public void bothButtonsPreserveTheirOwnLatestChoiceAcrossReinstantiation() throws Exception {
        Disk[] buttons = {new Disk("6", "3"), new Disk("0", "2")};
        for (Disk disk : buttons) {
            String choice = disk.current;
            for (int boot = 0; boot < 3; boot++) {
                Store store = new Store(disk); // process-local state discarded; persisted settings retained
                assertEquals(Result.PRESERVED, reconcile(store, shouldCapture(true, false)));
                assertEquals(choice, disk.backup);
                assertEquals(choice, disk.current);
                assertTrue(store.writes.isEmpty());
            }
        }
    }

    @Test public void restoreLegacyCaptureOnlyOnceForEitherButton() throws Exception {
        for (String backup : new String[]{"-1", "0", "3", "6", "9", "100"}) {
            Disk disk = new Disk("99", backup);
            Store firstBoot = new Store(disk);
            assertEquals(Result.RESTORED, reconcile(firstBoot, false));
            assertEquals(backup, disk.current);
            assertEquals(1, firstBoot.writes.size());
            Store reboot = new Store(disk);
            assertEquals(Result.PRESERVED, reconcile(reboot, false));
            assertTrue(reboot.writes.isEmpty());
        }
    }

    @Test public void reconfigurationUpdatesBackupAndNeverReplacesNewOemSelection() throws Exception {
        Disk disk = new Disk("3", "6");
        Store store = new Store(disk);
        assertEquals(Result.PRESERVED, reconcile(store, false));
        assertEquals("3", disk.backup);
        disk.current = "-1"; // choice in the native menu, including unassigned/menu default
        assertEquals(Result.PRESERVED, reconcile(new Store(disk), false));
        assertEquals("-1", disk.backup);
        assertEquals("-1", disk.current);
    }

    @Test public void customShortCaptureAndDisableRestoreWithoutReplacingBackupWith99() throws Exception {
        Disk disk = new Disk("2", "6");
        assertEquals(Result.CAPTURED, reconcile(new Store(disk), true));
        assertEquals("2", disk.backup);
        assertEquals("99", disk.current);
        assertEquals(Result.ALREADY_CAPTURED, reconcile(new Store(disk), true));
        assertEquals("2", disk.backup);
        assertEquals(Result.RESTORED, reconcile(new Store(disk), shouldCapture(false, true)));
        assertEquals("2", disk.current);
    }

    @Test public void changeFromCustomShortToDefaultRestoresThenBootPreserves() throws Exception {
        Disk disk = new Disk("4", null);
        reconcile(new Store(disk), shouldCapture(true, true));
        reconcile(new Store(disk), shouldCapture(true, false));
        Store boot = new Store(disk);
        reconcile(boot, shouldCapture(true, false));
        assertEquals("4", disk.current);
        assertTrue(boot.writes.isEmpty());
    }

    @Test public void disableDoesNotRestoreStaleBackupOverCurrentOemValue() throws Exception {
        for (String current : new String[]{"-1", "0", "9", "101"}) {
            Disk disk = new Disk(current, "6");
            Store store = new Store(disk);
            reconcile(store, shouldCapture(false, true));
            assertEquals(current, disk.current);
            assertEquals(current, disk.backup);
            assertTrue(store.writes.isEmpty());
        }
    }

    @Test public void noBackupOrInvalidBackupLeaves99Untouched() throws Exception {
        for (String backup : new String[]{null, "", "99", "null", "undefined", "-2", "bad", "2147483648"}) {
            Store store = new Store(new Disk("99", backup));
            assertEquals(Result.UNKNOWN, reconcile(store, false));
            assertEquals("99", store.disk.current);
            assertTrue(store.writes.isEmpty());
        }
    }

    @Test public void unreadableCurrentNeverUsesBackupOrCaptures() throws Exception {
        for (String current : new String[]{null, "", "null", "undefined", "Permission denied", "-2"}) {
            for (boolean capture : new boolean[]{false, true}) {
                Store store = new Store(new Disk(current, "6"));
                assertEquals(Result.UNKNOWN, reconcile(store, capture));
                assertEquals("6", store.disk.backup);
                assertTrue(store.writes.isEmpty());
            }
        }
    }

    @Test public void changedSelectionDuringRestoreOrCaptureIsNotOverwritten() throws Exception {
        for (boolean capture : new boolean[]{false, true}) {
            Store store = new Store(new Disk(capture ? "3" : "99", "6"));
            store.changeOnSecondRead = "9";
            assertEquals(Result.CHANGED, reconcile(store, capture));
            assertEquals("9", store.disk.current);
            assertTrue(store.writes.isEmpty());
        }
    }

    @Test public void failedPersistencePreventsDestructiveCapture() throws Exception {
        Store store = new Store(new Disk("0", null));
        store.failSave = true;
        try { reconcile(store, true); fail("Expected save failure"); }
        catch (Exception expected) { assertEquals("disk full", expected.getMessage()); }
        assertEquals("0", store.disk.current);
        assertTrue(store.writes.isEmpty());
    }

    @Test public void dealerForcedCaptureAndSeparateButtonOwnershipRemainAvailable() throws Exception {
        Disk first = new Disk("-1", null), second = new Disk("0", null);
        reconcile(new Store(first), true); // dealer path explicitly captures even if normal prefs disabled
        assertEquals("99", first.current);
        assertEquals("-1", first.backup);
        assertEquals("0", second.current);
        assertNull(second.backup);
        reconcile(new Store(second), true);
        reconcile(new Store(first), false);
        assertEquals("-1", first.current);
        assertEquals("99", second.current);
        assertEquals("0", second.backup);
    }
    @Test public void readFailureNeverMutatesCurrentOrBackup() throws Exception {
        Store store = new Store(new Disk("3", "6"));
        store.failRead = true;
        try { reconcile(store, true); fail("Expected read failure"); }
        catch (Exception expected) { assertEquals("read failed", expected.getMessage()); }
        assertEquals("3", store.disk.current);
        assertEquals("6", store.disk.backup);
        assertTrue(store.writes.isEmpty());
    }

    @Test public void writeFailureRetainsBackupForRecoveryAndIsNotReportedSuccessful() throws Exception {
        for (boolean capture : new boolean[]{false, true}) {
            Store store = new Store(new Disk(capture ? "0" : "99", "0"));
            store.failWrite = true;
            try { reconcile(store, capture); fail("Expected write failure"); }
            catch (Exception expected) { assertEquals("write failed", expected.getMessage()); }
            assertEquals(capture ? "0" : "99", store.disk.current);
            assertEquals("0", store.disk.backup);
            assertTrue(store.writes.isEmpty());
        }
    }

}
