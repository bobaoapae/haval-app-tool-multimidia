package android.app;

import android.content.Context;
import android.os.Looper;
import android.view.Display;
import java.util.ArrayList;
import java.util.List;

/** Records fake window lifetime; creates no Android window, WebView, or rendering surface. */
public class Presentation {
    public static final List<Presentation> CREATED = new ArrayList<>();
    public static final List<String> OFF_MAIN_OPERATIONS = new ArrayList<>();
    public final int displayId;
    public int shows;
    public int dismissals;
    public int screenOnCalls;
    public int screenOffCalls;
    private boolean showing;
    public Presentation(Context context, Display display) {
        displayId = display.getDisplayId();
        checkMain("construct");
        CREATED.add(this);
    }
    private void checkMain(String operation) {
        if (Looper.myLooper() != Looper.getMainLooper()) OFF_MAIN_OPERATIONS.add(operation + ":" + displayId);
    }
    public void show() { checkMain("show"); shows++; showing = true; }
    public void dismiss() { checkMain("dismiss"); dismissals++; showing = false; }
    public boolean isShowing() { return showing; }
    public void carMainScreenOn() { screenOnCalls++; }
    public void carMainScreenOff() { screenOffCalls++; }
    public static void cancelDisplay(int id) {
        for (Presentation presentation : CREATED) if (presentation.displayId == id) presentation.showing = false;
    }
    public static void reset() { CREATED.clear(); OFF_MAIN_OPERATIONS.clear(); }
}
