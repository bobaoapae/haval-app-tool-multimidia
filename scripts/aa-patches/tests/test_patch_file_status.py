"""Actual Java file-evidence/poll model plus supplementary UI wiring guards."""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / "app/src/main/java/br/com/redesurftank/havalshisuku"
MODEL = JAVA / "managers/AndroidAutoPatchStatus.java"
MANAGER = JAVA / "managers/AndroidAutoPatchManager.kt"
SCREEN = JAVA / "ui/screens/InstallAppsScreen.kt"


class PatchFileStatusTest(unittest.TestCase):
    def test_production_model_and_poll_generation_on_jvm(self):
        java = shutil.which("java")
        self.assertIsNotNone(java, "JDK required; missing validation is not a skipped pass")
        javac = shutil.which("javac")
        compiler = [javac] if javac else [java, "-m", "jdk.compiler/com.sun.tools.javac.Main"]
        with tempfile.TemporaryDirectory(prefix="aa-patch-status-") as output:
            build = subprocess.run(
                compiler + ["-encoding", "UTF-8", "-source", "11", "-target", "11", "-Xlint:-options", "-d", output,
                            str(MODEL), str(Path(__file__).with_name("AaPatchStatusHarness.java"))],
                capture_output=True, text=True, timeout=60,
            )
            self.assertEqual(build.returncode, 0, build.stdout + build.stderr)
            run = subprocess.run(
                [java, "-cp", output, "br.com.redesurftank.havalshisuku.managers.AaPatchStatusHarness"],
                capture_output=True, text=True, timeout=60,
            )
            self.assertEqual(run.returncode, 0, run.stdout + run.stderr)
            self.assertRegex(run.stdout, r"PASS total=\d+ patch status checks")

    def test_aa_initial_state_is_pure_and_status_reads_are_in_io_poll(self):
        source = SCREEN.read_text(encoding="utf-8")
        initial = source[:source.index("    LaunchedEffect(Unit)")]
        self.assertIn("AndroidAutoPatchStatus.PollState.initial()", initial)
        self.assertNotIn("AndroidAutoPatchManager.", initial)
        self.assertRegex(source, r"withContext\(Dispatchers.IO\)\s*\{\s*AndroidAutoPatchManager.readPatchStatus\(\)")
        self.assertIn("aaPatchState.accept(generation, status)", source)
        self.assertNotIn("AndroidAutoPatchManager.isMounted()", source)

    def test_aa_card_labels_independent_file_evidence_and_unverified_runtime(self):
        source = SCREEN.read_text(encoding="utf-8")
        card = source[source.index('title = "Android Auto Patch"'):source.index('title = "Apple CarPlay Patch"')]
        self.assertIn('"App visual: ${aaPatchStatus.app.summary}"', card)
        self.assertIn("status = null", card)
        self.assertIn('"Service: ${aaPatchStatus.service.summary}"', card)
        self.assertIn("AndroidAutoPatchStatus.RUNTIME_NOTICE", card)
        self.assertNotIn('-> "Ativo"', card)
        for call in ("installPatches(context)", "applyMounts()", "removeMounts()"):
            self.assertRegex(card, r"aaPatchState = aaPatchState.reset\(\)\s+AndroidAutoPatchManager\." + re.escape(call))

    def test_status_does_not_replace_v9_service_automount_controls(self):
        source = SCREEN.read_text(encoding="utf-8")
        self.assertIn('label = "Mapa no cluster"', source)
        self.assertIn(".AA_CLUSTER_SERVICE_AUTO_MOUNT", source)
        self.assertEqual(source.count("AndroidAutoPatchManager.ensureClusterServiceAutoMount()"), 1)
        self.assertNotIn("Auto-montagem: somente App.", source)
        source = MANAGER.read_text(encoding="utf-8")
        legacy = source[source.index("    fun isServiceClusterPatchMounted()"):source.index("    fun applyServiceMountWithoutForceStop()")]
        self.assertIn("installedPatchMd5(SERVICE_APK)", legacy)
        self.assertNotIn("readComponentStatus", legacy)

    def test_visual_automount_and_diagnostics_do_not_claim_runtime(self):
        source = MANAGER.read_text(encoding="utf-8")
        automount = source[source.index("    fun ensureMounted()"):source.index("    private fun isClusterServiceAutoMountEnabled()")]
        self.assertIn("installAppPatch(context)", automount)
        self.assertIn("applyAppMount()", automount)
        for forbidden in ("applyServiceMount", "applyMounts()", "installPatches("):
            self.assertNotIn(forbidden, automount)
        diagnostics = source[source.index("    fun getDiagnostics()") :]
        self.assertIn("AndroidAutoPatchStatus.RUNTIME_NOTICE", diagnostics)
        self.assertIn("UNKNOWN (missing or invalid checksum)", diagnostics)
        for overclaim in ("MATCH (Mounted)", "STOCK (OK)", "EMPTY (OK)"):
            self.assertNotIn(overclaim, diagnostics)


if __name__ == "__main__":
    unittest.main()
