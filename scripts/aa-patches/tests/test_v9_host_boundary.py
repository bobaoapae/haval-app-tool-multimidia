"""Preserve the pre-existing v9 host boundary during the bounded source port.

These guards describe the target branch, not approval of an installed identity.
They do not execute Android/OEM code or authorize loading the Service.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / "app/src/main/java/br/com/redesurftank/havalshisuku"


class V9HostBoundaryTest(unittest.TestCase):
    def test_existing_target_service_pin_array_is_unchanged(self):
        source = (JAVA / "api/AaClusterProtocol.java").read_text(encoding="utf-8")
        pins = re.findall(r'"([0-9a-f]{64})"', source)
        self.assertEqual(pins, [
            "7be3a99482e3f2f7f4f411f0a5a571ac97a505e500f9e05863fa8574e00baeb0",
            "3c7d703011f11ea2a4baa35ba2c522d6b03e3af011d70dcb95c1331f11ad0f65",
        ])
        self.assertRegex(source, r"public static final String\[\] OEM_SIGNER_SHA256\s*=\s*\{")
        self.assertEqual(source.count("OEM_SIGNER_SHA256"), 1)

    def test_pm_still_requires_one_current_signer_and_checks_target_array(self):
        source = (JAVA / "managers/AndroidAutoClusterClient.java").read_text(encoding="utf-8")
        verify = source.split("private int verifyService(", 1)[1].split("private void query(", 1)[0]
        self.assertIn("getApkContentsSigners()", verify)
        self.assertIn("signers==null||signers.length!=1||info.applicationInfo==null", verify)
        check = "java.util.Arrays.asList(AaClusterProtocol.OEM_SIGNER_SHA256).contains(hex.toString())"
        self.assertIn('if(!' + check + ')throw new SecurityException("AA Service signer mismatch: "+hex);', verify)
        self.assertEqual(verify.count("OEM_SIGNER_SHA256"), 1)
        self.assertLess(verify.index(check), verify.index("return info.applicationInfo.uid;"))

    def test_existing_target_stream_constants_are_unchanged(self):
        source = (JAVA / "api/AaClusterProtocol.java").read_text(encoding="utf-8")
        constants = dict(re.findall(r"public static final int (STREAM_\w+)\s*=\s*(\d+)\s*;", source))
        self.assertEqual(constants, {
            "STREAM_WIDTH": "1920", "STREAM_HEIGHT": "1080", "STREAM_HEIGHT_MARGIN": "360",
        })


if __name__ == "__main__":
    unittest.main()
