"""Compile/run the shared actual Java lifetime barrier without Android or OEM."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

PATCHES = Path(__file__).resolve().parents[1]
ROOT = PATCHES.parents[1]
SOURCE = ROOT / "app/src/main/java/br/com/redesurftank/havalshisuku/api/ClusterLeaseBarrier.java"


class ClusterLeaseBarrierJvmTest(unittest.TestCase):
    def test_actual_shared_java_lifetime_barrier(self):
        java = shutil.which("java")
        self.assertIsNotNone(java, "JDK required; missing validation is not a skipped pass")
        javac = shutil.which("javac")
        compiler = [javac] if javac else [java, "-m", "jdk.compiler/com.sun.tools.javac.Main"]
        with tempfile.TemporaryDirectory(prefix="cluster-lease-tests-") as output:
            build = subprocess.run(compiler + ["-source", "8", "-target", "8", "-Xlint:-options", "-d", output,
                                   str(SOURCE), str(PATCHES / "integration/tests/ClusterLeaseBarrierTest.java")],
                                   capture_output=True, text=True, timeout=60)
            self.assertEqual(build.returncode, 0, build.stdout + build.stderr)
            run = subprocess.run([java, "-cp", output, "br.com.redesurftank.havalshisuku.api.ClusterLeaseBarrierTest"],
                                 capture_output=True, text=True, timeout=60)
            self.assertEqual(run.returncode, 0, run.stdout + run.stderr)
            self.assertIn("PASS total=18 lease barrier checks", run.stdout)


if __name__ == "__main__":
    unittest.main()
