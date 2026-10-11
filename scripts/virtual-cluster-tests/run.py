#!/usr/bin/env python3
"""Compile real host sources against small test fakes; requires Python 3 and JDK 17+."""
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
JAVA_ROOT = ROOT / "app/src/main/java/br/com/redesurftank/havalshisuku"


def compiler_command():
    """Some JDK images expose java but omit the javac launcher."""
    javac = shutil.which("javac")
    if javac:
        return [javac]
    java = shutil.which("java")
    if java:
        command = [java, "-m", "jdk.compiler/com.sun.tools.javac.Main"]
        probe = subprocess.run(command + ["-version"], capture_output=True, text=True)
        if probe.returncode == 0:
            return command
    raise RuntimeError("A JDK 17+ with javac (or jdk.compiler module) is required; no dependency download is attempted")


def check_fake_keys():
    actual = (JAVA_ROOT / "models/SharedPreferencesKeys.kt").read_text()
    fake = (HERE / "src/br/com/redesurftank/havalshisuku/models/SharedPreferencesKeys.java").read_text()
    for name, key in re.findall(r'([A-Z_]+)\("([^"]+)"\)', fake):
        match = re.search(rf'\b{re.escape(name)}\s*\(\s*"([^"]+)"', actual)
        if match is None or match.group(1) != key:
            raise AssertionError(f"SharedPreferencesKeys fake drifted from production: {name}")
    print("PASS fake preference keys match production Kotlin enum", flush=True)


def main():
    check_fake_keys()
    production = [
        JAVA_ROOT / "managers/ProjectorManager.java",
        JAVA_ROOT / "utils/VirtualClusterPreferences.java",
    ]
    for source in production:
        if not source.is_file():
            raise RuntimeError(f"Missing production source: {source.relative_to(ROOT)}")
    java = shutil.which("java")
    if not java:
        raise RuntimeError("java is required")
    sources = [str(source) for source in sorted((HERE / "src").rglob("*.java")) + production]
    with tempfile.TemporaryDirectory(prefix="haval-virtual-cluster-") as temporary:
        classes = Path(temporary) / "classes"
        classes.mkdir()
        subprocess.run(compiler_command() + ["-source", "17", "-target", "17", "-Xlint:-options", "-encoding", "UTF-8", "-d", str(classes)] + sources, check=True)
        subprocess.run([java, "-ea", "-cp", str(classes), "ProjectorManagerLifecycleTest"], check=True)
    print("PASS actual production Java compiled and tested without Gradle, Android SDK, or network", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        sys.exit(1)
