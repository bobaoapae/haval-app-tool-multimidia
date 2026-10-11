package br.com.redesurftank.havalshisuku.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Collects host listener subscriptions; no vehicle service or ignition implementation. */
public final class ServiceManager {
    private static final ServiceManager INSTANCE = new ServiceManager();
    public final List<BiConsumer<String, Object>> listeners = new ArrayList<>();
    public static ServiceManager getInstance() { return INSTANCE; }
    public void addDataChangedListener(BiConsumer<String, Object> listener) { listeners.add(listener); }
    public void emit(String key, Object value) { for (BiConsumer<String, Object> listener : listeners) listener.accept(key, value); }
}
