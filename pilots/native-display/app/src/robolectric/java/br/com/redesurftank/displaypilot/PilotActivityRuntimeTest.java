package br.com.redesurftank.displaypilot;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowDisplayManager;
import org.robolectric.util.ReflectionHelpers;

/**
 * Android 9 framework/shadow integration tests, not an OEM compositor or parked-car safety test.
 * Simulated displays cannot prove that native instruments remain visible on actual hardware.
 * Reflection is confined to this test because the programmatic UI deliberately has no test IDs.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w1280dp-h720dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class PilotActivityRuntimeTest {
    private static final String OPEN = "Abrir SIMULAÇÃO por até 15 segundos";
    private static final String STOP = "PARAR PROJEÇÃO AGORA";
    private ActivityController<PilotActivity> controller;
    private PilotActivity activity;
    private int secondaryId;

    @Before public void setUp() {
        secondaryId = ShadowDisplayManager.addDisplay("w1920dp-h720dp-land-mdpi", "Bench secondary");
        controller = Robolectric.buildActivity(PilotActivity.class).setup();
        activity = controller.get();
        idle();
    }

    @After public void tearDown() {
        if (controller != null) controller.close();
        idle();
    }

    @Test public void launchingActivityEnumeratesButNeverProjectsOrArms() {
        assertNoProjection();
        assertEquals(0, spinner().getSelectedItemPosition());
        assertDisarmed();
        assertFalse(candidates().isEmpty());
        assertTrue(text("inventory").contains("ID " + secondaryId));
        assertEquals(0, shownPilotCount());
    }

    @Test public void confirmationsWithoutManualDisplaySelectionCannotOpen() {
        confirm();
        click(OPEN);
        assertNoProjection();
        assertEquals(0, shownPilotCount());
    }

    @Test public void manualSelectionWithoutBothConfirmationsCannotOpen() {
        select(secondaryId);
        click(OPEN);
        assertNoProjection();
        check("parked").setChecked(true);
        click(OPEN);
        assertNoProjection();
        check("parked").setChecked(false);
        check("clearArea").setChecked(true);
        click(OPEN);
        assertNoProjection();
        assertEquals(0, shownPilotCount());
    }

    @Test public void selectingDisplayClearsEarlierConfirmations() {
        confirm();
        select(secondaryId);
        assertDisarmed();
        click(OPEN);
        assertNoProjection();
    }

    @Test public void localPreviewDoesNotCreateSecondaryWindow() {
        MockNavigationView preview = findView(activity.getWindow().getDecorView(), MockNavigationView.class);
        assertNotNull(preview);
        assertEquals(View.GONE, preview.getVisibility());
        click("Ver desenho simulado aqui");
        assertEquals(View.VISIBLE, preview.getVisibility());
        assertNoProjection();
        click("Ver desenho simulado aqui");
        assertEquals(View.GONE, preview.getVisibility());
        assertEquals(0, shownPilotCount());
    }

    @Test public void chosenDisplayAndBoundedNoninteractiveWindowAreUsed() {
        int otherId = ShadowDisplayManager.addDisplay("w1280dp-h720dp-land-mdpi", "Chosen secondary");
        PilotPresentation presentation = openOn(otherId);
        assertEquals(otherId, presentation.getDisplay().getDisplayId());
        assertNotEquals(Display.DEFAULT_DISPLAY, presentation.getDisplay().getDisplayId());
        assertEquals(1, showingPilotPresentations().size());

        Point size = new Point();
        display(otherId).getSize(size);
        Window window = presentation.getWindow();
        assertNotNull(window);
        WindowManager.LayoutParams params = window.getAttributes();
        // Independent expectations for the default 40%, 25%, 20%, 40% form values.
        assertEquals(size.x * 40 / 100, params.x);
        assertEquals(size.y * 25 / 100, params.y);
        assertEquals(size.x * 60 / 100 - params.x, params.width);
        assertEquals(size.y * 65 / 100 - params.y, params.height);
        assertTrue(params.width > 0 && params.width < size.x);
        assertTrue(params.height > 0 && params.height < size.y);
        assertTrue(params.x + params.width <= size.x);
        assertTrue(params.y + params.height <= size.y);
        assertEquals(Gravity.TOP | Gravity.LEFT, params.gravity);
        // Android 9's Presentation assigns type 2037 internally; the constant is SDK-hidden.
        assertEquals(2037, params.type);
        assertFlag(params.flags, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        assertFlag(params.flags, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        assertEquals(0, params.flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        assertEquals(0f, params.dimAmount, 0f);
        assertEquals(0f, params.horizontalMargin, 0f);
        assertEquals(0f, params.verticalMargin, 0f);
        assertEquals(0f, params.horizontalWeight, 0f);
        assertEquals(0f, params.verticalWeight, 0f);
        assertEquals(PixelFormat.TRANSLUCENT, params.format);
        assertEquals(0, params.windowAnimations);
        assertEquals(0f, window.getDecorView().getElevation(), 0f);
        assertEquals(0, window.getDecorView().getPaddingLeft());
        assertEquals(0, window.getDecorView().getPaddingTop());
        assertEquals(0, window.getDecorView().getPaddingRight());
        assertEquals(0, window.getDecorView().getPaddingBottom());
        assertTrue(window.getDecorView().getBackground() instanceof ColorDrawable);
        assertEquals(Color.TRANSPARENT,
                ((ColorDrawable) window.getDecorView().getBackground()).getColor());
        assertNotNull(findView(window.getDecorView(), MockNavigationView.class));
    }

    @Test public void stopStaysOutsideScrollableControlsAndDismissesImmediately() {
        PilotPresentation presentation = openOn(secondaryId);
        Button stop = button(STOP);
        ScrollView scroll = findView(activity.getWindow().getDecorView(), ScrollView.class);
        assertNotNull(scroll);
        for (ViewParent parent = stop.getParent(); parent != null; parent = parent.getParent()) {
            assertFalse("STOP must never scroll with the form", parent instanceof ScrollView);
        }
        assertSame(stop.getParent(), scroll.getParent());
        LinearLayout root = (LinearLayout) stop.getParent();
        assertEquals(LinearLayout.VERTICAL, root.getOrientation());
        assertTrue(root.indexOfChild(stop) < root.indexOfChild(scroll));
        assertEquals(1f, ((LinearLayout.LayoutParams) scroll.getLayoutParams()).weight, 0f);
        int stopTop = stop.getTop();
        scroll.scrollTo(0, 10000);
        idle();
        assertEquals(stopTop, stop.getTop());
        assertTrue(stop.isEnabled());
        assertEquals(View.VISIBLE, stop.getVisibility());
        click(STOP);
        assertFalse(presentation.isShowing());
        assertNoProjection();
        assertDisarmed();
    }

    @Test public void timeoutDismissesAtFifteenSecondsWithoutAutoReopening() {
        PilotPresentation presentation = openOn(secondaryId);
        advance(14999);
        assertTrue(presentation.isShowing());
        assertTrue(session().isOpen());
        advance(1);
        assertFalse(presentation.isShowing());
        assertNoProjection();
        advance(30000);
        assertNoProjection();
        assertEquals(1, shownPilotCount());
    }

    @Test public void repeatedOpenReplacesWindowAndOldTimeoutCannotCloseReplacement() {
        PilotPresentation first = openOn(secondaryId);
        advance(1000);
        click(OPEN);
        PilotPresentation replacement = onlyPresentation();
        assertNotSame(first, replacement);
        assertFalse(first.isShowing());
        advance(14000); // The first window's original deadline.
        assertTrue(replacement.isShowing());
        assertTrue(session().isOpen());
        advance(1000); // The replacement's own deadline.
        assertNoProjection();
        assertEquals(2, shownPilotCount());
    }

    @Test public void repeatedStopIsSafeAndDoesNotReopen() {
        PilotPresentation presentation = openOn(secondaryId);
        click(STOP);
        click(STOP);
        advance(30000);
        assertFalse(presentation.isShowing());
        assertNoProjection();
        assertEquals(1, shownPilotCount());
    }

    @Test public void removingEitherConfirmationClosesAnOpenWindow() {
        openOn(secondaryId);
        check("parked").setChecked(false);
        idle();
        assertNoProjection();
        confirm();
        click(OPEN);
        onlyPresentation();
        check("clearArea").setChecked(false);
        idle();
        assertNoProjection();
    }

    @Test public void editingBoundsDismissesAndRequiresNewConfirmations() {
        openOn(secondaryId);
        edit("left").setText("41");
        idle();
        assertNoProjection();
        assertDisarmed();
        click(OPEN);
        assertNoProjection();
    }

    @Test public void malformedAndOutOfBoundsGeometryFailClosed() {
        select(secondaryId);
        edit("width").setText("");
        confirm();
        click(OPEN);
        assertNoProjection();
        assertTrue(text("status").contains("Área inválida"));
        edit("width").setText("41");
        confirm();
        click(OPEN);
        assertNoProjection();
        assertEquals(0, shownPilotCount());
    }

    @Test public void refreshingClosesAndRequiresExplicitReselection() {
        PilotPresentation presentation = openOn(secondaryId);
        click("Atualizar lista de displays");
        assertFalse(presentation.isShowing());
        assertNoProjection();
        assertDisarmed();
        assertEquals(0, spinner().getSelectedItemPosition());
    }

    @Test public void pauseClosesAndResumeNeverRestoresProjectionOrConsent() {
        PilotPresentation presentation = openOn(secondaryId);
        controller.pause();
        idle();
        assertFalse(presentation.isShowing());
        assertNoProjection();
        assertDisarmed();
        controller.resume();
        idle();
        assertNoProjection();
        assertDisarmed();
        assertEquals(0, spinner().getSelectedItemPosition());
        advance(30000);
        assertEquals(1, shownPilotCount());
    }

    @Test public void rotationRecreatesActivityWithoutRestoringWindowSelectionOrConsent() {
        PilotPresentation presentation = openOn(secondaryId);
        PilotActivity previous = activity;
        PilotSession previousSession = session();
        Configuration rotated = new Configuration(activity.getResources().getConfiguration());
        rotated.orientation = Configuration.ORIENTATION_PORTRAIT;
        rotated.screenWidthDp = 720;
        rotated.screenHeightDp = 1280;
        controller.configurationChange(rotated).visible();
        activity = controller.get();
        idle();
        assertNotSame(previous, activity);
        assertFalse(previousSession.isOpen());
        assertFalse(presentation.isShowing());
        assertNoProjection();
        assertDisarmed();
        assertEquals(0, spinner().getSelectedItemPosition());
        advance(30000);
        assertEquals(1, shownPilotCount());
    }

    @Test public void disconnectClosesAndReconnectDoesNotSelectOrReopen() {
        PilotPresentation presentation = openOn(secondaryId);
        ShadowDisplayManager.removeDisplay(secondaryId);
        idle();
        assertFalse(presentation.isShowing());
        assertNoProjection();
        assertDisarmed();
        assertTrue(candidates().isEmpty());
        ShadowDisplayManager.addDisplay("w1920dp-h720dp-land-mdpi", "Reconnected secondary");
        idle();
        assertNoProjection();
        assertDisarmed();
        assertEquals(0, spinner().getSelectedItemPosition());
        assertEquals(1, shownPilotCount());
    }

    @Test public void secondaryDisplayConfigurationChangeClosesAndDisarms() {
        PilotPresentation presentation = openOn(secondaryId);
        ShadowDisplayManager.changeDisplay(secondaryId, "w720dp-h1920dp-port-mdpi");
        idle();
        assertFalse(presentation.isShowing());
        assertNoProjection();
        assertDisarmed();
        assertEquals(0, spinner().getSelectedItemPosition());
    }

    @Test public void systemDismissalClearsSessionAndNeverReopens() {
        PilotPresentation presentation = openOn(secondaryId);
        presentation.dismiss();
        idle();
        assertNoProjection();
        advance(30000);
        assertEquals(1, shownPilotCount());
    }

    @Test public void failedShowCleanupUnregistersStartedPresentationExactlyOnce() {
        PilotPresentation presentation = new PilotPresentation(activity, display(secondaryId),
                new Viewport.Pixels(40, 25, 200, 140));
        int before = displayListenerCount();
        // Reproduce the Android 9 lifecycle point after onStart but before addView succeeds.
        // This isolates cleanup; it does not emulate an OEM WindowManager permission denial.
        presentation.onStart();
        try {
            assertFalse(presentation.isShowing());
            assertEquals(before + 1, displayListenerCount());
            presentation.dismiss();
            idle();
            assertEquals("dismiss alone cannot clean up a never-shown dialog",
                    before + 1, displayListenerCount());
            presentation.cleanupFailedShow();
            assertEquals(before, displayListenerCount());
            presentation.cleanupFailedShow();
            assertEquals(before, displayListenerCount());
        } finally {
            presentation.cleanupFailedShow();
        }
    }

    @Test public void failedShowCleanupDoesNotInterfereWithShowingPresentation() {
        PilotPresentation presentation = openOn(secondaryId);
        int before = displayListenerCount();
        presentation.cleanupFailedShow();
        assertTrue(presentation.isShowing());
        assertTrue(session().isOpen());
        assertEquals(before, displayListenerCount());
        click(STOP);
        assertNoProjection();
        assertTrue(displayListenerCount() < before);
    }

    @Test public void primaryPrivateAndPoweredOffDisplaysAreNotCandidates() {
        int privateId = ShadowDisplayManager.addDisplay("w480dp-h240dp-land-mdpi", "Private display");
        shadowOf(display(privateId)).setFlags(Display.FLAG_PRIVATE);
        int offId = ShadowDisplayManager.addDisplay("w1280dp-h720dp-land-mdpi", "Off display");
        shadowOf(display(offId)).setState(Display.STATE_OFF);
        idle();
        click("Atualizar lista de displays");
        assertEquals(1, candidates().size());
        assertEquals(secondaryId, candidates().get(0).id);
        assertNoProjection();
    }

    private void select(int displayId) {
        List<DisplayTarget> targets = candidates();
        for (int i = 0; i < targets.size(); i++) {
            if (targets.get(i).id == displayId) {
                // The two-argument API also lays out the selection before checking consent.
                spinner().setSelection(i + 1, false);
                idle();
                assertEquals(i + 1, spinner().getSelectedItemPosition());
                return;
            }
        }
        fail("Expected eligible test display " + displayId);
    }

    private PilotPresentation openOn(int displayId) {
        select(displayId);
        confirm();
        click(OPEN);
        assertTrue("Opening failed: " + text("status"), session().isOpen());
        return onlyPresentation();
    }

    private void confirm() {
        check("parked").setChecked(true);
        check("clearArea").setChecked(true);
    }

    private void assertDisarmed() {
        assertFalse(check("parked").isChecked());
        assertFalse(check("clearArea").isChecked());
    }

    private void assertNoProjection() {
        assertFalse(session().isOpen());
        assertTrue(showingPilotPresentations().isEmpty());
    }

    private PilotPresentation onlyPresentation() {
        List<PilotPresentation> windows = showingPilotPresentations();
        assertEquals("Exactly one showing pilot Presentation", 1, windows.size());
        return windows.get(0);
    }

    private List<PilotPresentation> showingPilotPresentations() {
        List<PilotPresentation> windows = new ArrayList<>();
        for (Dialog dialog : ShadowDialog.getShownDialogs()) {
            if (dialog instanceof PilotPresentation && dialog.isShowing()) {
                windows.add((PilotPresentation) dialog);
            }
        }
        return windows;
    }

    private int shownPilotCount() {
        int count = 0;
        for (Dialog dialog : ShadowDialog.getShownDialogs()) {
            if (dialog instanceof PilotPresentation) count++;
        }
        return count;
    }

    private Display display(int id) {
        DisplayManager manager = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
        assertNotNull(manager);
        Display display = manager.getDisplay(id);
        assertNotNull(display);
        return display;
    }

    private int displayListenerCount() {
        // Android-28-only framework inspection in tests, never a production hidden-API call.
        DisplayManager manager = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
        Object global = ReflectionHelpers.getField(manager, "mGlobal");
        List<?> listeners = ReflectionHelpers.getField(global, "mDisplayListeners");
        return listeners.size();
    }

    private void click(String label) {
        assertTrue(button(label).performClick());
        idle();
    }

    private Button button(String label) {
        Button found = findButton(activity.getWindow().getDecorView(), label);
        assertNotNull("Missing button: " + label, found);
        return found;
    }

    private static Button findButton(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button) view).getText())) {
            return (Button) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = findButton(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T extends View> T findView(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = findView(group.getChildAt(i), type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void assertFlag(int flags, int expected) { assertEquals(expected, flags & expected); }
    private static void idle() { shadowOf(Looper.getMainLooper()).idle(); }
    private static void advance(long millis) { shadowOf(Looper.getMainLooper()).idleFor(millis, MILLISECONDS); }
    private PilotSession session() { return ReflectionHelpers.getField(activity, "session"); }
    private Spinner spinner() { return ReflectionHelpers.getField(activity, "spinner"); }
    private CheckBox check(String name) { return ReflectionHelpers.getField(activity, name); }
    private EditText edit(String name) { return ReflectionHelpers.getField(activity, name); }
    private String text(String name) {
        TextView view = ReflectionHelpers.getField(activity, name);
        return view.getText().toString();
    }
    private List<DisplayTarget> candidates() { return ReflectionHelpers.getField(activity, "candidates"); }
}
