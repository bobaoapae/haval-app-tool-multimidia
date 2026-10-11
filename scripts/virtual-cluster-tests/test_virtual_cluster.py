"""JVM host behavior plus narrowly labeled Kotlin source-wiring guards.

The source checks do not compile Kotlin or validate Compose/WebView rendering.
"""
from pathlib import Path
import re
import subprocess
import sys
import unittest

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
JAVA_ROOT = ROOT / "app/src/main/java/br/com/redesurftank/havalshisuku"


class ProductionHostLifecycleTest(unittest.TestCase):
    def test_actual_java_host_with_lightweight_fakes(self):
        result = subprocess.run(
            [sys.executable, str(HERE / "run.py")],
            cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        )
        print(result.stdout, end="", flush=True)
        self.assertEqual(result.returncode, 0, result.stdout)


class KotlinSourceWiringTest(unittest.TestCase):
    """Textual regression checks only, not Kotlin execution or device validation."""

    def read(self, relative):
        return (JAVA_ROOT / relative).read_text()

    def test_ui_bounds_projector_and_existing_telemetry_use_same_effective_value(self):
        paths = (
            "ui/screens/TelasScreen.kt",
            "managers/DisplayAppLauncher.kt",
            "projectors/InstrumentProjector2.kt",
            "diagnostics/AnonymousTelemetryCollector.kt",
        )
        for path in paths:
            with self.subTest(path=path):
                source = self.read(path)
                self.assertIn("VirtualClusterPreferences.isEnabled(", source)
                self.assertNotRegex(source, r"getBoolean\(\s*SharedPreferencesKeys\.ENABLE_VIRTUAL_CLUSTER\.key")

    def test_native_mask_off_gate_precedes_theme_or_geometry_checks(self):
        source = self.read("projectors/InstrumentProjector2.kt")
        prefix = source.split("fun updateNativeMaskViews()", 1)[1].split("if (!isThemeLiveOnDisplay3())", 1)[0]
        self.assertIn("if (!VirtualClusterPreferences.isEnabled(preferences))", prefix)
        self.assertIn("nativeMaskContainer?.isVisible = false", prefix)
        self.assertIn("setDisplayedGlobalMask(null)", prefix)
        self.assertIn("return", prefix)

    def test_view_visibility_has_an_explicit_cluster_off_gate(self):
        source = self.read("projectors/InstrumentProjector2.kt")
        self.assertRegex(source, r"val hidden\s*=\s*!VirtualClusterPreferences\.isEnabled\(preferences\)\s*\|\|")
        self.assertRegex(source, r"root\.isVisible\s*=\s*visible\s*&&\s*!hidden")
        self.assertRegex(source, r"private fun shouldShowProjector\(\): Boolean\s*\{\s*return VirtualClusterPreferences\.isEnabled\(preferences\)")

    def test_off_control_invalidates_cached_true_state_before_dispatch(self):
        source = self.read("projectors/InstrumentProjector2.kt")
        off_calls = list(re.finditer(r'evaluateJsIfReady\(webView, "control\(\x27clusterEnabled\x27, false\)"\)', source))
        self.assertGreaterEqual(len(off_calls), 2, "preference and app-state off paths both notify the theme")
        for call in off_calls:
            with self.subTest(offset=call.start()):
                prefix = source[max(0, call.start() - 180):call.start()]
                self.assertIn("lastAppInDashJsKey = null", prefix, "rapid re-enable must not retain cached true state")

    def test_switch_off_preserves_selected_theme_and_color_preferences(self):
        source = self.read("ui/screens/TelasScreen.kt")
        self.assertNotIn("resetToStockDefaultTheme", source)
        self.assertIn("mutableStateOf(VirtualClusterPreferences.isEnabled(prefs))", source)
        off_writes = list(re.finditer(r"putBoolean\(SharedPreferencesKeys\.ENABLE_VIRTUAL_CLUSTER\.key, false\)", source))
        self.assertGreaterEqual(len(off_writes), 2, "master off and individual off paths remain covered")
        for write in off_writes:
            with self.subTest(offset=write.start()):
                prefix = source[max(0, write.start() - 500):write.start()]
                self.assertNotIn('selectedTheme = "Default"', prefix)
                self.assertNotIn("ACTIVE_CUSTOM_THEME", prefix)
                self.assertNotIn("theme_config_", prefix)


if __name__ == "__main__":
    unittest.main()
