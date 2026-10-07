# Impulse native-display pilot (Android 9)

A standalone, original Android application to answer one question: **can an ordinary app place a
small native window on a manually selected, publicly accessible secondary display on this firmware?**
The drawing is clearly marked **SIMULAÇÃO · SEM WAZE**. Navigation values are invented.
It is not Waze, a map engine, screen capture, an OEM integration, or a vehicle-control feature.

Package: `br.com.redesurftank.displaypilot` · version `0.1.0-pilot` · minimum/target API 28.
The production Impulse application and its root Gradle project are unchanged. This project uses
its own Gradle settings; it is not included in the production build or release workflow.
PR #152 / HAV-24, Android Auto and CarPlay are independent and untouched.

## Safety and scope

- Test only on a bench or **while parked**, with direct access to the central's persistent Stop button
- Every opening automatically dismisses after **15 seconds**, provided Android's UI thread is responsive
- No auto-start, boot receiver, background service, reopen, persistence of consent, or vehicle connection
- No requested Android permissions, network, location, captures, private display access, root, Shizuku,
  Frida, hidden APIs, task manipulation, display-resolution changes, security grants or fallback Activity
- No proprietary binaries, assets, extracted source, Waze IPC, or signature bypass
- All visible displays are enumerated; no fixed display ID is used. ID 0, private, invalid and non-ON
  displays are refused. A public candidate may still be refused by the OEM; this is a valid test result
- Selecting the destination is not enough: the operator must confirm the vehicle is parked and that
  the chosen rectangle is clear of ADAS, speedometer, warning lights and other critical instruments
- Coordinates are integer percentages of `Display.getSize()`; width is limited to 10–40%, height
  to 10–60%. These limits and example coordinates are **not a certified safe cluster layout**
- The native window is bounded, transparent outside its own drawing, nonfocusable, nontouchable,
  undimmed, unanimated, and does not change brightness, fullscreen mode or the underlying apps

**Important:** on Android 9, adding any own content to a secondary display can stop existing mirroring.
Even a tiny transparent window may consequently leave other parts black. OEM composition, geometry,
continued native rendering and ADAS visibility cannot be guaranteed by these flags. Any change to
critical native content is a failed test: press Stop and do not continue on that configuration.
A timeout is cleanup, not a safety guarantee if Android is unresponsive. No on-road testing.

## Build

From the repository root, using the existing wrapper, JDK 17, Android SDK platform 36 and Build Tools 36:

```sh
./gradlew -p pilots/native-display :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

The default result is `pilots/native-display/app/build/outputs/apk/debug/app-debug-unsigned.apk`.
**Unsigned APKs are not installable.** The build intentionally disables implicit debug-key generation.
Use only an explicitly approved pilot-only signing credential for any installable test candidate;
never the production/release key. No installation is performed by this project or its CI.

The `Native display pilot checks` workflow builds the exact pull-request head, runs checks, verifies
that no Android permission was packaged and that the APK is unsigned, and attaches APK/check reports
as an Actions artifact. It does not publish a release or modify the car.

A lightweight local check is also available with preexisting official API 28 and JUnit jars:

```sh
python3 pilots/native-display/scripts/check_host.py \
  --android-jar /path/to/sdk/platforms/android-28/android.jar \
  --junit-jar /path/to/junit-4.13.2.jar \
  --hamcrest-jar /path/to/hamcrest-core-1.3.jar
```

This compiles every production source against the actual public Android 28 SDK, with Java 8
bytecode and warnings as errors, then runs pure-Java state/geometry tests. It does not execute Android.
The full Gradle suite additionally runs Robolectric at SDK 28; simulated framework tests cannot prove
OEM permission, physical display composition, display recovery or road safety.

## Design

`DisplayTarget` is an immutable, public-SDK-only snapshot. `Viewport` validates overflow-safe bounds.
`PilotSession` owns at most one output on the main thread; generation tokens discard stale dismissal
callbacks. `PilotActivity` enumerates displays, asks for explicit selection/acknowledgements, reacquires
and checks the target immediately before opening, and owns a bounded timeout. `PilotPresentation`
uses the public Presentation constructor, without changing its window type. `MockNavigationView`
only draws original static vectors/text; it has no animation loop or data connection.

Pause/Stop/Destroy, target removal/change/rotation and external dismissal release ownership.
Returning to the foreground, reconnecting, or rotating never reopens output. The UI conservatively
clears selection and confirmations on any display inventory change. A window-close failure blocks
further openings for that Activity instance and tells the operator to close the app through Android. Editing geometry or changing
destination also closes/disarms existing output. The Stop button remains outside the scroll area.

See [TEST-GUIDE.md](TEST-GUIDE.md) for the parked-car checklist and feedback template.
Native Waze is a later, separate stage conditional on these results; this pilot establishes neither
an authorized data interface nor native Waze rendering.

## Android 9 references

Version-pinned sources matter: newer Android versions changed Presentation eligibility.

- [Presentation](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-9.0.0_r1/core/java/android/app/Presentation.java)
- [DisplayManager](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-9.0.0_r1/core/java/android/hardware/display/DisplayManager.java)
- [WindowManager.LayoutParams](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-9.0.0_r1/core/java/android/view/WindowManager.java)
- [DisplayManagerService, display mirroring/content selection](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-9.0.0_r1/services/core/java/com/android/server/display/DisplayManagerService.java)

This original pilot follows the repository's existing AGPL-3.0 license.

## SDK-only unsigned assembly (cloud fallback)

When a full Gradle environment is unavailable, the original Java/resources can also be assembled with
preexisting official Android tools. This does not replace lint or simulated-runtime tests:

```sh
python3 pilots/native-display/scripts/build_unsigned.py \
  --android-jar /path/to/sdk/platforms/android-28/android.jar \
  --build-tools /path/to/sdk/build-tools/36.0.0 \
  --r8-jar /path/to/r8-9.1.31.jar \
  --output /new/path/Impulse-Display-Pilot-0.1.0-UNSIGNED.apk
```

The script refuses to overwrite an existing output, compiles the actual API 28 sources, performs D8
Java-8 desugaring, packages only original pilot resources/classes, aligns the APK, checks its package
and lack of permissions, and writes source/tool SHA-256 provenance beside it. It neither downloads
dependencies nor signs anything. SDK-only and Gradle outputs are distinct artifacts, with different
compile-SDK metadata and hashes; test/deployment reports must identify the exact artifact used.

Tool provenance for the initial Linux build:
- [Official Build Tools 36.0.0 archive](https://dl.google.com/android/repository/build-tools_r36_linux.zip),
  SHA-256 `5d9ac77fb6ff43d9da518a337b4fcf8f9097113df531d99ccefe80ef7ce8250b`
- Android 28 public `android.jar`: `96c0b5750ea7715f1db5005085d0b0a46c98680cfd28c88c370540327ebbc862`
- R8 9.1.31 jar: `3b4de3053885da105e39c15212261d22653d6d1b5eb92323dd04ae913cc8286f`
