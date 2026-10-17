"""Run actual shared release ledger and lifetime barrier, without Android/OEM."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

PATCHES = Path(__file__).resolve().parents[1]
API = PATCHES.parents[1] / "app/src/main/java/br/com/redesurftank/havalshisuku/api"


class ClusterReleaseLedgerJvmTest(unittest.TestCase):
    def test_actual_shared_java_release_ledger(self):
        java = shutil.which("java")
        self.assertIsNotNone(java, "JDK required; missing validation is not a skipped pass")
        javac = shutil.which("javac")
        compiler = [javac] if javac else [java, "-m", "jdk.compiler/com.sun.tools.javac.Main"]
        sources = [API / "ClusterLeaseBarrier.java", API / "ClusterReleaseLedger.java",
                   PATCHES / "integration/tests/ClusterReleaseLedgerTest.java"]
        with tempfile.TemporaryDirectory(prefix="cluster-release-tests-") as output:
            build = subprocess.run(compiler + ["-source", "8", "-target", "8", "-Xlint:-options", "-d", output] + [str(p) for p in sources],
                                   capture_output=True, text=True, timeout=60)
            self.assertEqual(build.returncode, 0, build.stdout + build.stderr)
            run = subprocess.run([java, "-cp", output, "br.com.redesurftank.havalshisuku.api.ClusterReleaseLedgerTest"],
                                 capture_output=True, text=True, timeout=60)
            self.assertEqual(run.returncode, 0, run.stdout + run.stderr)
            self.assertIn("PASS total=21 release ledger checks", run.stdout)


if __name__ == "__main__":
    unittest.main()
