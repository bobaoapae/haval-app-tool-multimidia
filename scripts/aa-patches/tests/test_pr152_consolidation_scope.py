"""Keep unrelated Home access behavior identical to preview 681bf61.

Only AA trust/loading integration is in scope. No Android or shell execution.
"""
from pathlib import Path
import hashlib
import unittest

ROOT = Path(__file__).resolve().parents[3]
HOME_BASELINE = {'app/src/main/java/br/com/redesurftank/App.java': '6cda19f93a342c7d0ad83fc357226ad27c95153d', 'app/src/main/res/xml/file_paths.xml': '658b2a4d8163c3024630ad20d610fbba65069fe2', 'app/src/main/java/br/com/redesurftank/havalshisuku/ui/screens/ImpulseHomeSetupDialog.kt': '3b45e493aac5a127bd8bc8247dd0bd578e401028', 'app/src/main/java/br/com/redesurftank/havalshisuku/utils/ViewerFirstRun.kt': '035eef4bb1bc960b3f73e146c4a8bd44e1d4f795', 'app/src/main/java/br/com/redesurftank/havalshisuku/utils/SilentApkInstall.kt': '8e5c628e4eec6c45defd9915a6c8e6a0cb2a691e', 'app/src/main/java/br/com/redesurftank/havalshisuku/utils/SessionApkCache.kt': '63c31d9a60c62150a8e87db68301cf035c81d25e', 'app/src/main/java/br/com/redesurftank/havalshisuku/utils/ApkInstallReturn.kt': '4d13e30718501db1c06303bece8a67f3fec941d4'}


class ConsolidationScopeTest(unittest.TestCase):
    def test_existing_home_helpers_and_entrypoints_are_unchanged(self):
        for path, expected in HOME_BASELINE.items():
            with self.subTest(path=path):
                data = (ROOT / path).read_bytes()
                # Git blob identity normalizes checkout line endings for Windows CI.
                data = data.replace(b"\r\n", b"\n")
                actual = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
                self.assertEqual(actual, expected)

    def test_screen_does_not_add_v9_home_autogrant_triggers(self):
        source = (ROOT / "app/src/main/java/br/com/redesurftank/havalshisuku/ui/screens/InstallAppsScreen.kt").read_text(encoding="utf-8")
        self.assertNotIn("ViewerFirstRun", source)
        self.assertNotIn("homeGrantedVersion", source)
        self.assertIn("SilentApkInstall.install(file, IMPULSE_HOME_PACKAGE)", source)
        self.assertIn("ImpulseHomeSetupDialog(", source)
        self.assertIn("AndroidAutoPatchManager.ensureClusterServiceAutoMount()", source)


if __name__ == "__main__":
    unittest.main()
