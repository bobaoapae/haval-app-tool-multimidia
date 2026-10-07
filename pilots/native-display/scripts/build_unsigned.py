#!/usr/bin/env python3
"""SDK-only fallback: build original pilot sources without Gradle, signing or downloads.
Use the Gradle build for lint/Robolectric validation. This produces a separate unsigned artifact.
"""
import argparse
import hashlib
import json
import pathlib
import shutil
import subprocess
import tempfile
import zipfile
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--android-jar', type=pathlib.Path, required=True)
p.add_argument('--build-tools', type=pathlib.Path, required=True)
p.add_argument('--r8-jar', type=pathlib.Path, required=True)
p.add_argument('--output', type=pathlib.Path, required=True)
a = p.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
android = a.android_jar.resolve()
tools = a.build_tools.resolve()
r8 = a.r8_jar.resolve()
output = a.output.resolve()
if output.exists():
    p.error('Output already exists; choose a fresh destination so evidence is not overwritten.')
for dependency in [android, r8, tools / 'aapt2', tools / 'zipalign']:
    if not dependency.is_file():
        p.error('Missing dependency: ' + str(dependency))
output.parent.mkdir(parents=True, exist_ok=True)

def run(args):
    subprocess.run([str(x) for x in args], check=True)

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

with tempfile.TemporaryDirectory(prefix='native-display-pilot-') as temp:
    temp = pathlib.Path(temp)
    classes = temp / 'classes'
    classes.mkdir()
    sources = sorted((root / 'app/src/main/java').rglob('*.java'))
    run(['java', 'com.sun.tools.javac.Main', '-source', '8', '-target', '8', '-Xlint:all,-options',
         '-Werror', '-classpath', android, '-d', classes, *sources])
    bytecode = temp / 'classes.jar'
    with zipfile.ZipFile(bytecode, 'w', zipfile.ZIP_DEFLATED) as archive:
        for source in sorted(classes.rglob('*.class')):
            archive.write(source, source.relative_to(classes).as_posix())
    dex = temp / 'dex'
    dex.mkdir()
    run(['java', '-cp', r8, 'com.android.tools.r8.D8', '--debug', '--min-api', '28',
         '--lib', android, '--output', dex, bytecode])
    manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml')
    manifest.getroot().set('package', 'br.com.redesurftank.displaypilot')
    manifest_path = temp / 'AndroidManifest.xml'
    ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
    manifest.write(manifest_path, encoding='utf-8', xml_declaration=True)
    resources = temp / 'resources.zip'
    run([tools / 'aapt2', 'compile', '--dir', root / 'app/src/main/res', '-o', resources])
    apk = temp / 'pilot-unaligned.apk'
    run([tools / 'aapt2', 'link', '-I', android, '--manifest', manifest_path, '--debug-mode',
         '--min-sdk-version', '28', '--target-sdk-version', '28', '--version-code', '1',
         '--version-name', '0.1.0-pilot', '-o', apk, resources])
    with zipfile.ZipFile(apk, 'a', zipfile.ZIP_DEFLATED) as archive:
        for source in sorted(dex.glob('*.dex')):
            archive.write(source, source.name)
    aligned = temp / 'pilot-unsigned.apk'
    run([tools / 'zipalign', '-p', '4', apk, aligned])
    run([tools / 'zipalign', '-c', '-p', '4', aligned])
    badging = subprocess.check_output([str(tools / 'aapt2'), 'dump', 'badging', str(aligned)], text=True)
    if 'uses-permission:' in badging:
        raise RuntimeError('Pilot must not request any permission')
    if "package: name='br.com.redesurftank.displaypilot'" not in badging:
        raise RuntimeError('Unexpected package name')
    print(badging)
    with zipfile.ZipFile(aligned) as archive:
        if any(n.startswith('META-INF/') for n in archive.namelist()):
            raise RuntimeError('Unexpected signing or metadata entry')
    shutil.copyfile(aligned, output)
    report = {
        'artifact': output.name, 'sha256': digest(output), 'bytes': output.stat().st_size,
        'signed': False, 'vehicle_validated': False, 'gradle_build': False,
        'android_api': 28, 'package': 'br.com.redesurftank.displaypilot',
        'inputs_sha256': {str(f.relative_to(root)): digest(f) for f in sorted(
            [*sources, root / 'app/src/main/AndroidManifest.xml', root / 'app/src/main/res/values/strings.xml'])},
        'tools_sha256': {name: digest(path) for name, path in
            [('android.jar', android), ('r8.jar', r8), ('aapt2', tools / 'aapt2'), ('zipalign', tools / 'zipalign')]}
    }
    output.with_suffix('.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))
