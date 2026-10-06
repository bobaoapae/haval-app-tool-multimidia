"""Compile and run the real Java research prototype; no Android/OEM execution."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

PROTOTYPE = Path(__file__).resolve().parents[1] / "prototype"


class ClusterFramePumpJvmTest(unittest.TestCase):
    def test_real_java_lifecycle_and_ownership_regressions(self):
        java = shutil.which("java")
        self.assertIsNotNone(java, "JDK required to validate CLUSTER prototype; do not treat as a skipped pass")
        javac = shutil.which("javac")
        compiler = [javac] if javac else [java, "-m", "jdk.compiler/com.sun.tools.javac.Main"]
        # -source/-target also work on trimmed JDK images without ct.sym.
        # This is a JVM prototype check, NOT Android API/DEX compatibility proof.
        with tempfile.TemporaryDirectory(prefix="cluster-frame-pump-") as output:
            command = compiler + ["-source", "8", "-target", "8", "-Xlint:-options", "-d", output,
                                  str(PROTOTYPE / "ClusterFramePump.java"),
                                  str(PROTOTYPE / "ClusterFramePumpTest.java")]
            build = subprocess.run(command, capture_output=True, text=True, timeout=60)
            self.assertEqual(build.returncode, 0, build.stdout + build.stderr)
            run = subprocess.run([java, "-cp", output, "impulse.cluster.prototype.ClusterFramePumpTest"],
                                 capture_output=True, text=True, timeout=60)
            self.assertEqual(run.returncode, 0, run.stdout + run.stderr)
            self.assertIn("PASS total=25 prototype checks", run.stdout)


if __name__ == "__main__":
    unittest.main()
