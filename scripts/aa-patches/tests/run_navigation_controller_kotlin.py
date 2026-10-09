"""Optional host-only check of the complete production controller with synthetic adapters.

Usage: python run_navigation_controller_kotlin.py --dependencies /path/to/kotlin-jars
Uses an existing Kotlin compiler only: no downloads and no silently skipped tests.
The dependency directory must have a sibling dependency-manifest.json containing
the SHA256 and Maven Central URL of each compiler/runtime jar (Kotlin 2.0.21).
This is controller wiring validation, not Android, Binder, rendering or vehicle QA.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[3]
MANAGERS = ROOT / "app/src/main/java/br/com/redesurftank/havalshisuku/managers"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dependencies", type=Path, required=True)
    parser.add_argument("--controller", type=Path, default=MANAGERS / "AndroidAutoClusterController.kt")
    args = parser.parse_args()
    deps = args.dependencies.resolve()
    records = json.loads((deps.parent / "dependency-manifest.json").read_text())
    classpath = []
    for record in records:
        assert record["url"].startswith("https://repo.maven.apache.org/maven2/"), "Unrecognized dependency origin"
        jar = deps / record["url"].rsplit("/", 1)[1]
        assert hashlib.sha256(jar.read_bytes()).hexdigest() == record["sha256"], f"Dependency mismatch: {jar.name}"
        classpath.append(str(jar))
    java = shutil.which("java")
    assert java, "JDK required"
    build = ROOT / "scripts/.build/navigation-controller"
    classes = build / "classes"
    classes.mkdir(parents=True, exist_ok=True)
    policy = MANAGERS / "AndroidAutoClusterNavigationDebouncer.java"
    sources = [args.controller.resolve(), policy]
    hashes = {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in sources}
    (build / "source-before.json").write_text(json.dumps(hashes, indent=2) + "\n")
    compiler = [shutil.which("javac")] if shutil.which("javac") else [java, "-m", "jdk.compiler/com.sun.tools.javac.Main"]
    subprocess.run(compiler + ["-source", "11", "-target", "11", "-Xlint:-options", "-d", str(classes), str(policy)], check=True, timeout=60)
    kotlin_cp = os.pathsep.join(classpath)
    runtime_cp = os.pathsep.join([str(classes), str(deps / "kotlin-stdlib-2.0.21.jar")])
    stubs = sorted(Path(__file__).with_name("navigation-controller").glob("*.kt"))
    subprocess.run([java, "-cp", kotlin_cp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect",
                    "-jvm-target", "11", "-classpath", runtime_cp, "-d", str(classes), str(args.controller)] + [str(path) for path in stubs], check=True, timeout=90)
    after = {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in sources}
    assert hashes == after, "Production input sources changed during compilation"
    (build / "source-after.json").write_text(json.dumps(after, indent=2) + "\n")
    subprocess.run([java, "-cp", runtime_cp, "harness.ControllerHarnessKt"], check=True, timeout=60)


if __name__ == "__main__":
    main()
