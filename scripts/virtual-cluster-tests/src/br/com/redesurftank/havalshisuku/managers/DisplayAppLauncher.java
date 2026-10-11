package br.com.redesurftank.havalshisuku.managers;

import br.com.redesurftank.havalshisuku.models.DisplayAppConfig;
import java.util.Collections;
import java.util.List;

public final class DisplayAppLauncher {
    public static final DisplayAppLauncher INSTANCE = new DisplayAppLauncher();
    public List<DisplayAppConfig> getAllConfigs() { return Collections.emptyList(); }
    public TaskInfo findTaskForPackage(String packageName) { return null; }
    public static void killAppAsync(String packageName) { }
    public static final class TaskInfo { public int getDisplayId() { return 0; } }
}
