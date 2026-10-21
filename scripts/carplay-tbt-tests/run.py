#!/usr/bin/env python3
"""Compile and run the actual pure Kotlin preparation sources. No Android, download or OEM code."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "br/com/redesurftank/havalshisuku/navigation"
CLASSES = ["CarPlayGuidanceTest", "PreparedNavigationArbiterTest"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--deps-dir", type=Path, default=os.environ.get("KOTLIN_TEST_DEPS_DIR"),
                        help="Existing Kotlin 2.0.21 compiler/stdlib and JUnit 4.13.2 jars directory")
    args = parser.parse_args()
    if args.deps_dir is None:
        parser.error("--deps-dir or KOTLIN_TEST_DEPS_DIR is required; no dependencies are downloaded")
    deps = Path(args.deps_dir).resolve()
    required = ["kotlin-compiler-embeddable-2.0.21.jar", "kotlin-stdlib-2.0.21.jar",
                "kotlin-script-runtime-2.0.21.jar", "kotlin-reflect-1.6.10.jar",
                "kotlin-daemon-embeddable-2.0.21.jar", "trove4j-1.0.20200330.jar",
                "kotlinx-coroutines-core-jvm-1.6.4.jar", "annotations-13.0.jar",
                "junit-4.13.2.jar", "hamcrest-core-1.3.jar"]
    for name in required:
        if not (deps / name).is_file():
            parser.error(f"Missing preinstalled dependency: {deps / name}")
    java = shutil.which("java")
    if not java:
        parser.error("java (JDK 11+) is required")
    classpath = os.pathsep.join(str(deps / name) for name in required)
    sources = sorted((ROOT / "app/src/main/java" / PACKAGE).glob("*.kt"))
    tests = sorted((ROOT / "app/src/test/java" / PACKAGE).glob("*.kt"))
    if len(sources) != 2 or len(tests) != 2:
        parser.error("Expected exactly two production Kotlin files and two test files")
    with tempfile.TemporaryDirectory(prefix="carplay-tbt-jvm-") as temporary:
        output = Path(temporary) / "classes"
        output.mkdir()
        subprocess.run([java, "-cp", classpath, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                        "-no-stdlib", "-no-reflect", "-Werror", "-jvm-target", "11",
                        "-classpath", classpath, "-d", str(output)] +
                       [str(path) for path in sources + tests], check=True)
        subprocess.run([java, "-ea", "-cp", str(output) + os.pathsep + classpath,
                        "org.junit.runner.JUnitCore"] +
                       [PACKAGE.replace("/", ".") + "." + name for name in CLASSES], check=True)
    print("PASS production Kotlin compiled with warnings as errors; synthetic JVM tests only.")


if __name__ == "__main__":
    main()
