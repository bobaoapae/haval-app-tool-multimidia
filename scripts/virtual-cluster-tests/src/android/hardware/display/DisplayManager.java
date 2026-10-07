package android.hardware.display;

import android.app.Presentation;
import android.os.Handler;
import android.view.Display;
import java.util.LinkedHashMap;
import java.util.Map;

/** Models listener registration and selected display events only, not Android display behavior. */
public class DisplayManager {
    private final Map<Integer, Display> displays = new LinkedHashMap<>();
    private final Map<DisplayListener, Handler> listeners = new LinkedHashMap<>();
    public int registrations;
    public int unregistrations;
    public int scans;
    public DisplayManager(int... ids) { for (int id : ids) displays.put(id, new Display(id)); }
    public Display[] getDisplays() { scans++; return displays.values().toArray(new Display[0]); }
    public void registerDisplayListener(DisplayListener listener, Handler handler) {
        registrations++;
        listeners.put(listener, handler);
    }
    public void unregisterDisplayListener(DisplayListener listener) {
        unregistrations++;
        listeners.remove(listener);
    }
    public int listenerCount() { return listeners.size(); }
    public void addDisplay(int id) {
        displays.put(id, new Display(id));
        listeners.forEach((listener, handler) -> handler.post(() -> listener.onDisplayAdded(id)));
    }
    public void removeDisplay(int id) {
        displays.remove(id);
        Presentation.cancelDisplay(id);
        listeners.forEach((listener, handler) -> handler.post(() -> listener.onDisplayRemoved(id)));
    }
    public void cancelAndChangeDisplay(int id) {
        Presentation.cancelDisplay(id);
        listeners.forEach((listener, handler) -> handler.post(() -> listener.onDisplayChanged(id)));
    }
    public interface DisplayListener {
        void onDisplayAdded(int id);
        void onDisplayRemoved(int id);
        void onDisplayChanged(int id);
    }
}
