"""Compile/run the real pure Java pair/guard/Annex-B sources, never Android/OEM."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

INTEGRATION = Path(__file__).resolve().parents[1] / "integration"
SOURCE = INTEGRATION / "src/com/ts/androidauto/impulse/cluster"


class IntegrationCoreJvmTest(unittest.TestCase):
    def test_actual_java_pair_registration_native_guard_and_annex_b(self):
        java = shutil.which("java")
        self.assertIsNotNone(java, "JDK required; do not treat missing validation as a skipped pass")
        javac = shutil.which("javac")
        compiler = [javac] if javac else [java, "-m", "jdk.compiler/com.sun.tools.javac.Main"]
        with tempfile.TemporaryDirectory(prefix="cluster-core-tests-") as output:
            sources = [SOURCE / name for name in ("PairedRegistration.java", "GuardedSink.java", "H264AccessUnit.java")]
            sources.append(INTEGRATION / "tests/ClusterCoreTest.java")
            build = subprocess.run(compiler + ["-source", "8", "-target", "8", "-Xlint:-options", "-d", output] + [str(p) for p in sources],
                                   capture_output=True, text=True, timeout=60)
            self.assertEqual(build.returncode, 0, build.stdout + build.stderr)
            run = subprocess.run([java, "-cp", output, "com.ts.androidauto.impulse.cluster.ClusterCoreTest"],
                                 capture_output=True, text=True, timeout=60)
            self.assertEqual(run.returncode, 0, run.stdout + run.stderr)
            self.assertIn("PASS total=33 core checks", run.stdout)


if __name__ == "__main__":
    unittest.main()
