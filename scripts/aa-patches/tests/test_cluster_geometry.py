"""Production geometry JVM execution plus supplementary Android wiring guards."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / "app/src/main/java/br/com/redesurftank/havalshisuku"
HOST = JAVA / "projectors/AaClusterVideoHost.kt"
PROJECTOR = JAVA / "projectors/InstrumentProjector2.kt"
BRIDGE = JAVA / "bridge/ThemeBridgeImpl.kt"


def method(source, signature):
    """Read an unchanged source method for guards / the optional Kotlin harness."""
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    for end in range(opening + 1, len(source)):
        depth += (source[end] == "{") - (source[end] == "}")
        if depth == 0:
            return source[start:end + 1]
    raise AssertionError("unterminated method: " + signature)


class ClusterGeometryTest(unittest.TestCase):
    def test_production_geometry_on_jvm(self):
        java = shutil.which("java")
        self.assertIsNotNone(java, "JDK required; missing validation is not a skipped pass")
        javac = shutil.which("javac")
        compiler = [javac] if javac else [java, "-m", "jdk.compiler/com.sun.tools.javac.Main"]
        build_root = ROOT / "scripts/.build"
        build_root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix="aa-cluster-geometry-", dir=build_root) as output:
            build = subprocess.run(compiler + ["-source", "11", "-target", "11", "-Xlint:-options",
                "-d", output, str(JAVA / "projectors/AaClusterGeometry.java"),
                str(Path(__file__).with_name("AaClusterGeometryHarness.java"))],
                capture_output=True, text=True, timeout=60)
            self.assertEqual(build.returncode, 0, build.stdout + build.stderr)
            run = subprocess.run([java, "-cp", output,
                "br.com.redesurftank.havalshisuku.projectors.AaClusterGeometryHarness"],
                capture_output=True, text=True, timeout=60)
            self.assertEqual(run.returncode, 0, run.stdout + run.stderr)
            self.assertRegex(run.stdout, r"PASS total=\d+ production geometry checks")

    def test_custom_theme_and_default_share_final_policy(self):
        source = HOST.read_text(encoding="utf-8")
        bounds = method(source, "fun mapBounds()")
        self.assertIn("return AaClusterGeometry.resolve(custom, theme, nativeCardShown)", bounds)
        self.assertNotIn("return it", bounds)
        refresh = method(source, "fun refreshWindow(")
        self.assertIn("applyStreamTransform(it, bounds)", refresh)
        self.assertIn("onBoundsApplied?.invoke(bounds)", refresh)
        self.assertLess(refresh.index("applyStreamTransform(it, bounds)"), refresh.index("onBoundsApplied?.invoke(bounds)"))
        self.assertIn("Looper.myLooper() == Looper.getMainLooper()", refresh)

    def test_v9_transform_fades_and_virtual_cluster_guards_are_preserved(self):
        source = HOST.read_text(encoding="utf-8")
        self.assertIn("private const val VEHICLE_CENTER_SHIFT = 175", source)
        self.assertIn("private const val RIGHT_FADE_LEFT = 1400", source)
        self.assertIn("private const val LEFT_FADE_WIDTH = 520", source)
        self.assertIn("matrix.postTranslate(VEHICLE_CENTER_SHIFT.toFloat(), 0f)", source)
        self.assertIn("syncFades()", method(source, "fun setNativeCardShown("))
        self.assertIn("if (shown && !nativeCardShown)", method(source, "private fun syncFades()"))
        ensure = method(source, "private fun ensureView(")
        self.assertIn("leftFade = fade(", ensure)
        self.assertIn("rightFade = fade(", ensure)
        projector = PROJECTOR.read_text(encoding="utf-8")
        self.assertIn("VirtualClusterPreferences.isEnabled(preferences)", method(projector, "private fun shouldShowProjector()"))
        self.assertIn("!VirtualClusterPreferences.isEnabled(preferences)", method(projector, "fun updateNativeMaskViews()"))

    def test_aa_hole_ownership_and_empty_rect_are_preserved(self):
        source = PROJECTOR.read_text(encoding="utf-8")
        visibility = method(source, "private fun updateVirtualClusterVisibility(")
        self.assertIn("var appRectIsAaCluster = false", visibility)
        self.assertIn("display3AppRectIsAaCluster = appRectIsAaCluster", visibility)
        aa = visibility[visibility.index("} else if (isAaClusterInDash())") :]
        self.assertIn("appRectIsAaCluster = true", aa)
        prep = method(source, "fun prepareDisplay3AppHole(")
        self.assertIn("display3AppRectIsAaCluster = false", prep)
        event = source[source.index("ServiceManagerEventType.AA_CLUSTER_SURFACE ->"):
                       source.index("ServiceManagerEventType.RAW_KEY_EVENT ->")]
        self.assertNotIn("prepareDisplay3AppHole", event)
        self.assertIn("updateVirtualClusterVisibility", event)
        hole = method(source, "private fun updateAaClusterAppHole(")
        self.assertIn("!display3AppRectIsAaCluster || !isAaClusterInDash()", hole)
        self.assertIn("if (rect == display3AppRect) return", hole)
        for widening_or_feedback in ("max(", "coerce", "syncSecondaryDisplayApps", "evaluateJs", "Event", "resize"):
            self.assertNotIn(widening_or_feedback, hole)

    def test_theme_and_card_refresh_sync_without_reentering_resize(self):
        source = PROJECTOR.read_text(encoding="utf-8")
        refresh = method(source, "override fun refreshDisplayBounds()")
        self.assertIn("AaClusterVideoHost.refreshWindow { updateAaClusterAppHole(it) }", refresh)
        self.assertIn("if (hasManagedSecondaryDisplayWork(displayId))", refresh)
        self.assertNotIn("lastAppliedConfigs.clear()", refresh)
        self.assertNotIn("updateVirtualClusterVisibility", refresh)
        card = method(source, "private fun syncAaClusterMapWindow(")
        self.assertIn("updateAaClusterAppHole(it, redrawMasks)", card)
        card_change = method(source, "private fun handleClusterCardChanged(")
        self.assertIn("syncAaClusterMapWindow(redrawMasks = false)", card_change)
        bridge = method(BRIDGE.read_text(encoding="utf-8"), "fun setAppDefaultDimensions(")
        self.assertIn("prev[3] == next[3]", bridge)
        self.assertIn("context.refreshDisplayBounds()", bridge)
        self.assertNotIn("AaClusterVideoHost.refreshWindow", bridge)
        app_sync = method(source, "private fun syncSecondaryDisplayApps(")
        self.assertIn("notifyGeometry = false", app_sync)


if __name__ == "__main__":
    unittest.main()
