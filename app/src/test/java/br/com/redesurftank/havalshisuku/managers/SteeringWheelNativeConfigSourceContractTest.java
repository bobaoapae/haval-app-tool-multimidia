package br.com.redesurftank.havalshisuku.managers;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Source-contract checks complement policy tests; not a claim of on-device UI testing. */
public class SteeringWheelNativeConfigSourceContractTest {
    private static String source(String relative) throws Exception {
        for (String prefix : new String[]{"src/main/", "app/src/main/"}) {
            Path path = Paths.get(prefix + relative);
            if (Files.isRegularFile(path)) return new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
        }
        throw new AssertionError("Missing checkout source: " + relative);
    }
    private static String manager() throws Exception {
        return source("java/br/com/redesurftank/havalshisuku/managers/ServiceManager.java");
    }
    private static String between(String text, String start, String end) {
        int a = text.indexOf(start), b = text.indexOf(end, a);
        assertTrue(a >= 0 && b >= a);
        return text.substring(a, b);
    }
    private static String sha256(String text) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte value : hash) result.append(String.format("%02x", value & 255));
        return result.toString();
    }

    @Test public void policyApiAndResultOrderAreStable() throws Exception {
        assertEquals(Arrays.asList("PRESERVED", "CAPTURED", "RESTORED", "ALREADY_CAPTURED", "UNKNOWN", "CHANGED"),
                Arrays.asList(Arrays.stream(SteeringWheelNativeConfigPolicy.Result.values()).map(Enum::name).toArray(String[]::new)));
        assertEquals(0, SteeringWheelNativeConfigPolicy.Store.class.getMethod("readBackup").getExceptionTypes().length);
    }

    @Test public void stealthAndDeferredTapDispatchRemainByteIdenticalToPinnedBaseline() throws Exception {
        String text = manager();
        assertEquals("0d931d2ff963c3d1a8b3648e1cc6548f21af6a277c3a6526783e0a13bbc2c1f2", sha256(between(text,
                "    public void ensureSteeringWheelButtonIntegration() {", "        boolean enabled = sharedPreferences.getBoolean(")));
        assertEquals("ac687bc64f4d7676d1d83a29460a0f255647d9de41a93ad3534fa7a5d17ba67e", sha256(between(text,
                "    // ===== Toques nos botoes personalizados do volante", "    public void enableSteeringWheelButton1Integration() {")));
    }

    @Test public void productionAdapterRequiresCommitAndConfirmsSystemWrite() throws Exception {
        String adapter = between(manager(), "    private synchronized void reconcileSteeringWheelNativeSetting(", "    private void sendClusterIntMsg(");
        assertTrue(adapter.contains("new SteeringWheelNativeConfigPolicy.Store()"));
        assertFalse(adapter.contains("if (value.equals(readBackup())) return;"));
        assertTrue(adapter.contains("if (!sharedPreferences.edit().putString(backupKey.getKey(), value).commit())"));
        assertTrue(adapter.contains("if (!value.equals(readCurrent()))"));
        assertFalse(adapter.contains(".apply()"));
        assertTrue(adapter.contains("Could not persist native steering choice"));
        assertTrue(adapter.contains("Native steering setting write not confirmed"));
    }

    @Test public void bothButtonWarningsUseDefaultKeyAndNameSemantics() throws Exception {
        String ui = source("java/br/com/redesurftank/havalshisuku/ui/screens/BasicSettingsScreen.kt");
        assertTrue(ui.contains("action == SteeringWheelCustomActionType.DEFAULT.key ||"));
        assertTrue(ui.contains("action == SteeringWheelCustomActionType.DEFAULT.name"));
        assertTrue(ui.contains("if (!isSteeringNativeDefault(shortAction)) return"));
        assertTrue(ui.contains("customDouble && customLong -> R.string.steering_native_preservation_double_long_warning"));
        for (int button : new int[]{1, 2}) {
            String section = ui.substring(ui.indexOf("\"Botão " + button + "\""));
            int warning = section.indexOf("SteeringNativePreservationWarning(");
            int picker = section.indexOf("SteeringActionPicker(");
            assertTrue(warning >= 0 && picker > warning);
            String call = section.substring(warning, picker);
            assertTrue(call.contains("shortAction = steeringWheelButton" + button + "Action,"));
            assertTrue(call.contains("doubleAction = steeringWheelButton" + button + "ActionDouble,"));
            assertTrue(call.contains("longAction = steeringWheelButton" + button + "ActionLong"));
        }
        String strings = source("res/values/strings.xml");
        for (String suffix : new String[]{"double", "long", "double_long"})
            assertTrue(strings.contains("name=\"steering_native_preservation_" + suffix + "_warning\""));
        assertTrue(strings.contains("duas vezes a ação nativa"));
        assertTrue(strings.contains("menu original do carro"));
    }
}
