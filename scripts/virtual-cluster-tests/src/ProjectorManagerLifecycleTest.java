import android.app.Presentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import br.com.redesurftank.App;
import br.com.redesurftank.havalshisuku.managers.ProjectorManager;
import br.com.redesurftank.havalshisuku.managers.ServiceManager;
import br.com.redesurftank.havalshisuku.managers.StealthModeManager;
import br.com.redesurftank.havalshisuku.models.CarConstants;
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys;
import br.com.redesurftank.havalshisuku.utils.VirtualClusterPreferences;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Behavioral tests of actual production host code, with deterministic collaborator fakes. */
public final class ProjectorManagerLifecycleTest {
    private static final String ENABLE = SharedPreferencesKeys.ENABLE_VIRTUAL_CLUSTER.getKey();
    private static int passed;
    private static int failed;

    private interface Test { void run() throws Exception; }

    private static final class FakePreferences implements SharedPreferences {
        final Map<String, Object> values = new HashMap<>();
        final Set<OnSharedPreferenceChangeListener> listeners = new LinkedHashSet<>();
        int registrations;
        int unregistrations;
        @Override public boolean getBoolean(String key, boolean fallback) {
            return (boolean) values.getOrDefault(key, fallback);
        }
        @Override public String getString(String key, String fallback) {
            return (String) values.getOrDefault(key, fallback);
        }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            registrations++;
            listeners.add(listener);
        }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            unregistrations++;
            listeners.remove(listener);
        }
        void put(String key, boolean value) {
            Object previous = values.put(key, value);
            if (!Boolean.valueOf(value).equals(previous)) notifyKey(key);
        }
        void clear() { values.clear(); notifyKey(null); }
        void notifyKey(String key) {
            // Snapshot models an already-dispatched callback; no claim of Android concurrency fidelity.
            for (OnSharedPreferenceChangeListener listener : new ArrayList<>(listeners)) {
                listener.onSharedPreferenceChanged(this, key);
            }
        }
    }

    private static final class Fixture {
        final FakePreferences preferences = new FakePreferences();
        final DisplayManager displays;
        final ProjectorManager manager;
        Fixture(Boolean enabled, int... displayIds) throws Exception {
            Looper.getMainLooper(); // Bind the fake main looper before any worker starts.
            Handler.reset();
            Presentation.reset();
            ServiceManager.getInstance().listeners.clear();
            StealthModeManager.active = false;
            if (enabled != null) preferences.values.put(ENABLE, enabled);
            displays = new DisplayManager(displayIds);
            App.context = new Context(preferences, displays);
            Field instance = ProjectorManager.class.getDeclaredField("instance");
            instance.setAccessible(true);
            instance.set(null, null); // Test isolation only; production singleton code is unmodified.
            manager = ProjectorManager.getInstance();
        }
        void start() { manager.initialize(); assertListeners(1); }
        void toggle(boolean enabled) { preferences.put(ENABLE, enabled); Handler.drain(); }
        void assertListeners(int active) {
            equal(active, preferences.listeners.size(), "active preference listeners");
            equal(active, displays.listenerCount(), "active display listeners");
            // Existing service listener is intentionally registered once for singleton lifetime.
            equal(1, ServiceManager.getInstance().listeners.size(), "car-data listener count");
        }
    }

    private static long created(int displayId) {
        return Presentation.CREATED.stream().filter(p -> p.displayId == displayId).count();
    }
    private static long live(int displayId) {
        return Presentation.CREATED.stream().filter(p -> p.displayId == displayId && p.isShowing()).count();
    }
    private static Presentation latest(int displayId) {
        return Presentation.CREATED.stream().filter(p -> p.displayId == displayId).reduce((a, b) -> b).orElseThrow();
    }
    private static void equal(long expected, long actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
    private static void truth(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void background(Runnable operation) throws Exception {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { operation.run(); } catch (Throwable error) { thrown.set(error); }
        }, "fake-preference-writer");
        worker.start();
        worker.join();
        if (thrown.get() != null) throw new AssertionError("Worker failed", thrown.get());
    }
    private static void run(String name, Test test) {
        try {
            test.run();
            truth(Presentation.OFF_MAIN_OPERATIONS.isEmpty(), "Off-main window operations: " + Presentation.OFF_MAIN_OPERATIONS);
            equal(0, Handler.pendingCount(), "unconsumed handler callbacks");
            System.out.println("PASS " + name);
            passed++;
        } catch (Throwable error) {
            System.err.println("FAIL " + name + ": " + error);
            error.printStackTrace(System.err);
            failed++;
        }
    }

    public static void main(String[] args) {
        run("missing key defaults enabled without seeding preferences", () -> {
            Fixture f = new Fixture(null, 1, 3);
            truth(VirtualClusterPreferences.isEnabled(f.preferences), "missing key must be enabled");
            f.start();
            equal(1, live(1), "D1 live");
            equal(1, live(3), "D3 live");
            truth(!f.preferences.values.containsKey(ENABLE), "reading default must not write preference");
            equal(1, Presentation.CREATED.get(0).displayId, "D1 must be constructed first");
            equal(3, Presentation.CREATED.get(1).displayId, "D3 must be constructed second");
        });
        run("explicit false survives cold start and refresh restart", () -> {
            Fixture f = new Fixture(false, 1, 3);
            truth(!VirtualClusterPreferences.isEnabled(f.preferences), "stored false must be respected");
            f.start();
            for (int i = 0; i < 3; i++) f.manager.initialize();
            equal(0, created(3), "disabled D3 must not even be constructed");
            equal(1, live(1), "normal D1 behavior survives false");
            f.manager.stopProjectors();
            f.assertListeners(0);
            f.manager.refresh();
            f.assertListeners(1);
            equal(0, created(3), "restart must not revive disabled D3");
            truth(!VirtualClusterPreferences.isEnabled(f.preferences), "restart must preserve stored false");
            equal(1, live(1), "D1 restored after restart");
        });
        run("repeated on off on affects only D3 with one listener each", () -> {
            Fixture f = new Fixture(true, 1, 3);
            f.start();
            Presentation d1 = latest(1);
            for (int i = 0; i < 6; i++) {
                Presentation previous = latest(3);
                f.toggle(false);
                equal(0, live(3), "off removes D3 window");
                equal(1, previous.dismissals, "previous D3 dismissed once");
                f.toggle(true);
                equal(1, live(3), "on restores one D3 window");
                truth(previous != latest(3), "on creates a fresh D3 instance");
                equal(1, live(1), "D1 remains live");
                truth(d1 == latest(1), "toggle must preserve D1 identity");
                equal(0, d1.dismissals, "toggle must not dismiss D1");
                f.assertListeners(1);
            }
            equal(7, created(3), "one D3 creation per enabled session");
            equal(1, f.preferences.registrations, "no repeated preference registration");
            equal(1, f.displays.registrations, "no repeated display registration");
        });
        run("repeated initialize and duplicate same-value callbacks are idempotent", () -> {
            Fixture f = new Fixture(true, 1, 3);
            f.start();
            for (int i = 0; i < 5; i++) {
                f.manager.initialize();
                f.preferences.notifyKey(ENABLE); // Defensive duplicate notification, beyond real no-op writes.
                Handler.drain();
            }
            equal(1, created(1), "D1 created once");
            equal(1, created(3), "D3 created once");
            f.toggle(false);
            for (int i = 0; i < 5; i++) { f.preferences.notifyKey(ENABLE); Handler.drain(); }
            equal(1, created(3), "duplicate disabled callback cannot create D3");
            equal(1, latest(3).dismissals, "duplicate disabled callback cannot dismiss twice");
            f.assertListeners(1);
        });
        run("rapid writes coalesce and reconcile latest value", () -> {
            Fixture f = new Fixture(true, 1, 3);
            f.start();
            Presentation original = latest(3);
            f.preferences.put(ENABLE, false);
            f.preferences.put(ENABLE, true);
            f.preferences.put(ENABLE, false);
            f.preferences.put(ENABLE, true);
            equal(1, Handler.pendingCount(), "one coalesced reconciliation");
            equal(0, original.dismissals, "callback does not synchronously touch window");
            Handler.drain();
            truth(original == latest(3), "latest true keeps original live D3");
            equal(0, original.dismissals, "intermediate false must not cause flicker");
            f.preferences.put(ENABLE, false);
            f.preferences.put(ENABLE, true);
            f.preferences.put(ENABLE, false);
            equal(1, Handler.pendingCount(), "latest false also coalesces");
            Handler.drain();
            equal(0, live(3), "latest false wins");
            equal(1, original.dismissals, "only final reconciliation dismisses");
        });
        run("background preference callback touches windows only on main", () -> {
            Fixture f = new Fixture(true, 1, 3);
            f.start();
            background(() -> f.preferences.put(ENABLE, false));
            equal(1, live(3), "worker callback leaves window alone until main dispatch");
            equal(1, Handler.pendingCount(), "worker callback schedules main reconciliation");
            Handler.drain();
            equal(0, live(3), "main dispatch dismisses D3");
            background(() -> f.preferences.put(ENABLE, true));
            equal(0, live(3), "worker does not create presentation");
            Handler.drain();
            equal(1, live(3), "main dispatch recreates presentation");
        });
        run("initialize refresh and stop dispatch from background to main", () -> {
            Fixture f = new Fixture(true, 1, 3);
            background(f.manager::initialize);
            equal(0, Presentation.CREATED.size(), "background initialize defers construction");
            Handler.drain();
            f.assertListeners(1);
            equal(1, live(3), "main initializes D3");
            background(f.manager::refresh);
            equal(1, created(3), "background refresh defers replacement");
            Handler.drain();
            equal(2, created(3), "main refresh replaces D3 once");
            equal(1, live(3), "refresh leaves one D3 window");
            background(f.manager::stopProjectors);
            equal(1, live(3), "background stop defers dismissal");
            Handler.drain();
            equal(0, live(3), "main stop dismisses D3");
            f.assertListeners(0);
        });
        run("stop cancels pending preference work and unregisters listeners", () -> {
            Fixture f = new Fixture(false, 1, 3);
            f.start();
            f.preferences.put(ENABLE, true);
            equal(1, Handler.pendingCount(), "enable is queued before stop");
            f.manager.stopProjectors();
            equal(0, Handler.pendingCount(), "stop removes queued preference reconciliation");
            equal(1, f.preferences.unregistrations, "stop unregisters preference listener");
            equal(1, f.displays.unregistrations, "stop unregisters display listener");
            f.assertListeners(0);
            f.preferences.put(ENABLE, false);
            f.preferences.put(ENABLE, true);
            Handler.drain();
            equal(0, created(3), "writes after stop cannot resurrect host");
            equal(0, live(1), "D1 remains stopped");
            f.manager.refresh();
            f.assertListeners(1);
            equal(1, live(3), "explicit refresh restarts host with latest preference");
            f.toggle(false);
            equal(0, live(3), "listener works after restart");
        });
        run("already captured preference callback after stop cannot resurrect host", () -> {
            Fixture f = new Fixture(false, 1, 3);
            f.start();
            SharedPreferences.OnSharedPreferenceChangeListener captured =
                    f.preferences.listeners.iterator().next();
            f.manager.stopProjectors();
            f.preferences.values.put(ENABLE, true);
            background(() -> captured.onSharedPreferenceChanged(f.preferences, ENABLE));
            equal(0, Handler.pendingCount(), "late snapshot callback cannot schedule after stop");
            Handler.drain();
            f.assertListeners(0);
            equal(0, created(3), "late callback cannot create D3");
            equal(0, live(1), "late callback cannot restart D1");
        });
        run("null-key clear notification restores missing-key default", () -> {
            Fixture f = new Fixture(false, 1, 3);
            f.start();
            f.preferences.clear();
            equal(1, Handler.pendingCount(), "clear/null key queues reconciliation");
            equal(0, created(3), "clear does not create inline");
            Handler.drain();
            equal(1, live(3), "clear restores default-enabled D3");
            truth(!f.preferences.values.containsKey(ENABLE), "clear does not seed preference");
            equal(1, created(1), "clear keeps D1 instance");
        });
        run("unrelated preferences produce no scheduled or display work", () -> {
            Fixture f = new Fixture(true, 1, 3);
            f.start();
            int scans = f.displays.scans;
            f.preferences.put("unrelatedThemeSetting", true);
            f.preferences.notifyKey(SharedPreferencesKeys.ENABLE_INSTRUMENT_PROJECTOR.getKey());
            equal(0, Handler.pendingCount(), "unrelated preference cannot queue host work");
            equal(scans, f.displays.scans, "unrelated preference cannot scan displays");
            equal(1, created(3), "unrelated preference cannot rebuild D3");
        });
        run("display absent then re-added while off never creates D3", () -> {
            Fixture f = new Fixture(false, 1);
            f.start();
            f.displays.addDisplay(3);
            Handler.drain();
            equal(0, created(3), "adding disabled display cannot create D3");
            f.toggle(true);
            equal(1, live(3), "enabling on present display creates D3");
            f.displays.removeDisplay(3);
            Handler.drain();
            f.toggle(false);
            f.displays.addDisplay(3);
            Handler.drain();
            f.displays.cancelAndChangeDisplay(3);
            Handler.drain();
            equal(1, created(3), "removed/re-added/changed disabled display cannot resurrect D3");
            equal(0, live(3), "disabled D3 remains absent");
            equal(1, created(1), "display events preserve live D1");
            f.assertListeners(1);
        });
        run("enabled missing display recovers and cancelled presentation is replaced", () -> {
            Fixture f = new Fixture(true, 1);
            f.start();
            equal(0, created(3), "missing display waits for callback");
            f.displays.addDisplay(3);
            Handler.drain();
            equal(1, live(3), "display callback creates enabled D3");
            f.displays.cancelAndChangeDisplay(3);
            Handler.drain();
            equal(2, created(3), "cancelled presentation is replaced");
            equal(1, live(3), "recovery leaves exactly one live D3");
            equal(1, created(1), "recovery preserves D1");
        });
        run("car data listener remains single and targets current windows", () -> {
            Fixture f = new Fixture(true, 1, 3);
            f.start();
            Presentation oldD3 = latest(3);
            f.toggle(false);
            f.toggle(true);
            f.manager.refresh();
            f.assertListeners(1);
            ServiceManager.getInstance().emit(CarConstants.CAR_BASIC_ENGINE_STATE.getValue(), false);
            ServiceManager.getInstance().emit(CarConstants.CAR_BASIC_ENGINE_STATE.getValue(), true);
            equal(1, latest(1).screenOffCalls, "one off event to current D1");
            equal(1, latest(1).screenOnCalls, "one on event to current D1");
            equal(1, latest(3).screenOffCalls, "one off event to current D3");
            equal(1, latest(3).screenOnCalls, "one on event to current D3");
            equal(0, oldD3.screenOffCalls, "dismissed D3 is not an event target");
        });
        run("normal D1 policy and existing stealth suppression stay intact", () -> {
            Fixture f = new Fixture(false, 1, 3);
            f.preferences.values.put(SharedPreferencesKeys.ENABLE_INSTRUMENT_PROJECTOR.getKey(), false);
            f.start();
            equal(1, live(1), "normal D1 exists even when wallpaper setting is false");
            f.toggle(true);
            f.toggle(false);
            equal(1, created(1), "cluster toggle does not change D1 policy");
            StealthModeManager.active = true;
            f.manager.initialize();
            equal(0, live(1), "existing stealth D1 suppression retained");
            equal(0, live(3), "disabled D3 remains off in stealth");
        });
        System.out.println("\nHost lifecycle: " + passed + " passed, " + failed + " failed (JVM collaborator fakes; not Android/device validation)");
        if (failed != 0) System.exit(1);
    }
}
