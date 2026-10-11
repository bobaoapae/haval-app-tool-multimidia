"""Execute the production navigation-demand policy; supplementary controller wiring guards."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
MANAGERS = ROOT / "app/src/main/java/br/com/redesurftank/havalshisuku/managers"
POLICY = MANAGERS / "AndroidAutoClusterNavigationDebouncer.java"
CONTROLLER = MANAGERS / "AndroidAutoClusterController.kt"


class ClusterNavigationDebounceTest(unittest.TestCase):
    def test_production_policy_with_controlled_scheduler_on_jvm(self):
        java = shutil.which("java")
        self.assertIsNotNone(java, "JDK required; missing validation is not a skipped pass")
        javac = shutil.which("javac")
        compiler = [javac] if javac else [java, "-m", "jdk.compiler/com.sun.tools.javac.Main"]
        build_root = ROOT / "scripts/.build"
        build_root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix="aa-navigation-", dir=build_root) as output:
            build = subprocess.run(
                compiler + ["-encoding", "UTF-8", "-source", "11", "-target", "11", "-Xlint:-options", "-d", output,
                            str(POLICY), str(Path(__file__).with_name("AaClusterNavigationDebouncerHarness.java"))],
                capture_output=True, text=True, timeout=60,
            )
            self.assertEqual(build.returncode, 0, build.stdout + build.stderr)
            run = subprocess.run(
                [java, "-cp", output, "br.com.redesurftank.havalshisuku.managers.AaClusterNavigationDebouncerHarness"],
                capture_output=True, text=True, timeout=60,
            )
            self.assertEqual(run.returncode, 0, run.stdout + run.stderr)
            self.assertRegex(run.stdout, r"PASS total=\d+ navigation debounce checks in 9 scenarios")

    def test_controller_uses_policy_for_navigation_updates_and_output_demand(self):
        source = CONTROLLER.read_text(encoding="utf-8")
        self.assertIn("private val navigationDebouncer = AndroidAutoClusterNavigationDebouncer(", source)
        self.assertRegex(source, r"NAVIGATION_HIDE_DEBOUNCE_MS\s*=\s*5_000L")
        self.assertIn("mainHandler.postDelayed(callback, delayMs)", source)
        self.assertIn("mainHandler.removeCallbacks(callback)", source)
        self.assertIn("navigationDebouncer.onNavigationActive(update.active)", source)
        self.assertIn("clusterRequested.get() && isSessionActive() && navigationDebouncer.isActive", source)
        self.assertNotIn("navigationEndedRunnable", source)
        self.assertIn('attachIfSessionAllows("navigation_started")', source)
        self.assertIn('attachIfSessionAllows("navigation_ended")', source)

    def test_v9_session_poll_and_dock_publication_are_preserved(self):
        source = CONTROLLER.read_text(encoding="utf-8")
        self.assertIn("AndroidAutoSessionTelemetry.statusForPoll(", source)
        for field in ("linkStatus = status", "dcmEvidenceRecent = dcmEvidenceRecent", "sessionActive = isSessionActive()"):
            self.assertIn(field, source)
        self.assertIn("BottomBarState.publishAndroidAutoLinked(", source)
        publish = source.split("    private fun publishSession(", 1)[1].split("    private fun onSessionStopped()", 1)[0]
        self.assertIn("value == AndroidAutoTelemetryKeys.SESSION_ACTIVE", publish)
        self.assertIn("mainHandler.postDelayed(this, 1_500L)", source)

    def test_session_baseline_resets_policy_and_preserves_demand_and_telemetry_contract(self):
        source = CONTROLLER.read_text(encoding="utf-8")
        start = source.split("    fun start() {", 1)[1].split("    fun onLinkStatus", 1)[0]
        self.assertIn("publishSession(AndroidAutoTelemetryKeys.SESSION_STOPPED, force = true)", start)
        stop = source.split("    private fun onSessionStopped() {", 1)[1].split("    private fun publishDirections", 1)[0]
        self.assertIn("navigationDebouncer.reset()", stop)
        self.assertIn("clusterRequested.getAndSet(false)", stop)
        self.assertIn('detachSurface("session_stopped")', stop)
        self.assertIn("directionsPublisher.reset()", stop)
        self.assertIn("AndroidAutoNavigationTelemetry.inactive().toJson()", stop)
        self.assertIn("sessionDebouncer.reset()", stop)


if __name__ == "__main__":
    unittest.main()
