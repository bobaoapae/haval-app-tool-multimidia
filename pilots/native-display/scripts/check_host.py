#!/usr/bin/env python3
"""Compile real sources against the public API28 jar, then execute pure-Java JUnit tests.
This does NOT emulate WindowManager, install an APK or establish vehicle compatibility.
Dependencies are supplied by the caller; this script downloads or signs nothing.
"""
import argparse
import pathlib
import subprocess
import os

p = argparse.ArgumentParser()
p.add_argument('--android-jar', type=pathlib.Path, required=True)
p.add_argument('--junit-jar', type=pathlib.Path, required=True)
p.add_argument('--hamcrest-jar', type=pathlib.Path, required=True)
a = p.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
classes = root / 'build' / 'host-check'
classes.mkdir(parents=True, exist_ok=True)
sources = sorted((root / 'app/src/main/java').rglob('*.java'))
tests = sorted((root / 'app/src/test/java').rglob('*.java'))
cp = os.pathsep.join(str(f.resolve()) for f in [a.android_jar, a.junit_jar, a.hamcrest_jar])
subprocess.run(['java', 'com.sun.tools.javac.Main', '-source', '8', '-target', '8', '-Xlint:all,-options', '-Werror',
                '-classpath', cp, '-d', str(classes), *map(str, sources + tests)], check=True)
cp = os.pathsep.join([str(classes), str(a.junit_jar.resolve()), str(a.hamcrest_jar.resolve())])
subprocess.run(['java', '-cp', cp, 'org.junit.runner.JUnitCore',
                *['br.com.redesurftank.displaypilot.' + f.stem for f in tests]], check=True)
print('PASS: actual API 28 source compilation and pure-Java tests; Android runtime not exercised.')
