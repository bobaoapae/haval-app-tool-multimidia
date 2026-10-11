"""Source regressions for the host trust boundary; no Android/OEM execution."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / "app/src/main/java/br/com/redesurftank/havalshisuku"


class ClusterHostTrustTest(unittest.TestCase):
    def test_protocol_keeps_exact_reviewed_service_allowlist(self):
        source = (JAVA / "api/AaClusterProtocol.java").read_text(encoding="utf-8")
        pins = re.findall(r'"([0-9a-f]{64})"', source)
        self.assertEqual(pins, ["7be3a99482e3f2f7f4f411f0a5a571ac97a505e500f9e05863fa8574e00baeb0",
                                "3c7d703011f11ea2a4baa35ba2c522d6b03e3af011d70dcb95c1331f11ad0f65"])
        self.assertRegex(source, r"public static final String\[\] OEM_SIGNER_SHA256\s*=\s*\{")
        self.assertEqual(source.count("OEM_SIGNER_SHA256"), 1)

    def test_service_verification_keeps_single_current_signer_and_allowlist(self):
        source = (JAVA / "managers/AndroidAutoClusterClient.java").read_text(encoding="utf-8")
        verify = source.split("private int verifyService(", 1)[1].split("private void query(", 1)[0]
        self.assertIn("getApkContentsSigners()", verify)
        self.assertIn("!AaClusterProtocol.SERVICE_PACKAGE.equals(name.getPackageName())", verify)
        self.assertIn('!"com.ts.androidauto.sdk.aidl.LinkCommand".equals(binder.getInterfaceDescriptor())', verify)
        self.assertIn("signers==null||signers.length!=1||info.applicationInfo==null", verify)
        self.assertIn('if(!java.util.Arrays.asList(AaClusterProtocol.OEM_SIGNER_SHA256).contains(hex.toString()))throw new SecurityException("AA Service signer mismatch: "+hex);', verify)
        self.assertEqual(verify.count("OEM_SIGNER_SHA256"), 1)
        self.assertLess(verify.index("java.util.Arrays.asList(AaClusterProtocol.OEM_SIGNER_SHA256)"), verify.index("return info.applicationInfo.uid;"))

    def test_stream_constants_are_preserved(self):
        source = (JAVA / "api/AaClusterProtocol.java").read_text(encoding="utf-8")
        constants = dict(re.findall(r'public static final int (STREAM_\w+)\s*=\s*(\d+)\s*;', source))
        self.assertEqual(constants, {"STREAM_WIDTH": "1920", "STREAM_HEIGHT": "1080", "STREAM_HEIGHT_MARGIN": "360"})


if __name__ == "__main__":
    unittest.main()
