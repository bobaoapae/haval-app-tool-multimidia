# Scripts Directory

This directory contains all scripts and tools for the Haval MMI project.

> 📋 **If you're an agent (human or LLM) about to do build/decompile/patch work
> here**, read the **[Working conventions](#working-conventions-for-agents)**
> section at the bottom first. It's short and binding: where intermediate
> artifacts must go, what gets committed vs. discarded, how to talk to the
> car, and the cleanup checklist before pushing.

## Directory Structure

```
scripts/
├── README.md                          # This file
├── Deploy-To-Car.ps1                  # Impulse app (Haval Shisuku) deployment
├── Share-Internet-And-Connect.ps1     # PC → Car internet sharing via Wi-Fi
├── Get-Car-IP.ps1                     # MMI dynamic IP auto-discovery utility
│
├── aa-patches/                        # ★ Android Auto patching pipeline
│   ├── README.md                      #   Full build/deploy documentation
│   ├── patch_v19_focus.py             #   Focus-bypass patch script
│   ├── deploy_to_car.py              #   Push + mount deployment
│   └── stock/                         #   Stock APK cache
│       └── AndroidAutoApp_stock.apk
│
├── milestones/                        # Validated, car-confirmed versions
│   └── (promoted after car validation)
│
├── experimental/                      # Historical experiments & future work
│   ├── README.md                      #   Index with resume instructions
│   ├── patches/                       #   V9–V18 patch history
│   ├── resize/                        #   Display resize experiments
│   ├── Frida/                         #   Legacy Frida hooks (deprecated)
│   └── telnet/                        #   Telnet deployment & diagnostics
│
└── utils/                             # Car diagnostics helpers
    ├── check_app_maps.py
    ├── check_service_maps.py
    ├── dump_maps_telnet.py
    ├── dumpsys_telnet.py
    ├── extract_vdex.py
    └── poll_app_maps.py
```

## Key Workflows

### Build & Deploy Patched Android Auto
See [`aa-patches/README.md`](aa-patches/README.md) for the full pipeline.

### Deploy Impulse App to Car
```powershell
.\scripts\Deploy-To-Car.ps1
```

### Share Internet with Car MMI
```powershell
.\scripts\Share-Internet-And-Connect.ps1
```

### Auto-Detect Car MMI IP
```powershell
# Pretty prints detected MMI details and active ports (ADB/Telnet)
.\scripts\Get-Car-IP.ps1

# Prints only the raw IP address string (ideal for programmatic use)
.\scripts\Get-Car-IP.ps1 -Raw
```

---

# Working conventions for agents

Short, binding rules for any agent (human or LLM) doing build / decompile /
patch work under `scripts/`. These are committed to the repo so every clone
has them — separate from per-developer notes in `.agents/` (which is
gitignored).

## TL;DR

- **All build / decompile output goes in `scripts/.build/`** (or
  `scripts/.tmp/` for short-lived scratch).
- Both paths are **gitignored**. Anything outside them is committable by
  default, so don't leak intermediate artifacts elsewhere.
- The historical `build_v19/` at the repo root is also gitignored and existing
  scripts still use it (see "Legacy paths" below) — don't churn that yet, but
  **do not introduce new code that writes to the repo root**.

## Where intermediate artifacts go

| Kind of artifact | Path | Why |
|---|---|---|
| Disassembled APK trees (apktool output) | `scripts/.build/<apk-name>/` | Large, regeneratable, never commit. |
| Reassembled / aligned / signed APKs (intermediates) | `scripts/.build/*.apk` | Validated builds get promoted to a milestone folder; intermediates stay here. |
| Pulled binaries from the car for analysis (e.g. `AndroidAutoService_pulled.apk`) | `scripts/.build/` | Same — large, derived from device state. |
| Short-lived scratch (logs, parsed dumps, screencaps) | `scripts/.tmp/` | Same idea, even more disposable. |
| Downloaded vendor tools (apktool, dexer, etc.) | `tools/` (repo root) | Already gitignored. Keep there — many docs reference it. |

If you find yourself wanting to write somewhere outside these three roots,
that's a smell — pause and ask whether the file should be a tracked artifact
(commit it to its proper home) or a temp file (route it through `.build/` /
`.tmp/`).

## What goes in `scripts/milestones/`

Only **validated, releasable** artifacts. A milestone folder is a tagged
state of "this APK was tested on the car and works." Specifically:

- The **signed** APK (no unsigned/aligned-only intermediates)
- A **snapshot** of the `patch_logic.py` (or equivalent) that produced it
- The matching `deploy_to_car.py` (or `.ps1`) script
- A `README.md` documenting the patch layers, MD5, build commands

The build pipeline that *produced* the milestone APK runs in `scripts/.build/`.
Once the result is signed and validated, copy the signed APK and the patch
script snapshot into the milestone folder.

## What goes in `app/src/main/assets/aa_patches/` and `app/src/main/assets/carplay_patches/`

The signed, validated APKs that the Impulse app will bundle and bind-mount on
the car. These ARE committed (yes, binary APKs). Treat them as the
release-channel artifact for end users. Updating them requires:

1. Building a new patched APK in `scripts/.build/`
2. Signing it
3. Validating on the car (don't ship without an on-vehicle test)
4. Copying the signed APK to the assets folder
5. Rebuilding the Impulse app

## Legacy paths (don't churn for now)

The `aa-patches` workflow currently uses `build_v19/` at the repo root. Many
docs and milestone READMEs reference it. We're leaving this as-is to avoid
churning a working pipeline — but **new** scripts, new patch efforts, and any
ad-hoc one-off work should use `scripts/.build/`. If you're refactoring the
aa-patches pipeline anyway, migrating `build_v19/` → `scripts/.build/aa/` is
welcome but make sure to update every README and milestone snapshot at the
same time.

## Connecting to the Car (ADB & Telnet)

The car's multimedia system (MMI) connects to the developer machine via
Wi-Fi and supports both **ADB** and **Telnet** connections.

### Subnet and IP discovery

- **Subnet:** the MMI operates on the `192.168.33.x` subnet.
- **Dynamic IP:** the car's IP address can change between sessions.
- **Auto-discovery:**
  ```powershell
  .\scripts\Get-Car-IP.ps1                # human-readable
  $ip = .\scripts\Get-Car-IP.ps1 -Raw     # just the IP, for piping
  ```
  This pings the subnet to refresh the ARP table, probes the active ports
  (ADB/Telnet), and outputs the current MMI IP.
- **Connection + Internet routing:** for "find the MMI, share my PC's
  internet with it, keep the connection alive":
  ```powershell
  .\scripts\Share-Internet-And-Connect.ps1
  ```

### ADB (port 5555)

```powershell
adb connect <CAR_IP>:5555
adb devices                          # verify
adb shell id                         # check root — must be uid=0(root)
```

If ADB connects but lacks root, route through Telnet instead.

### Telnet (port 23)

Direct root shell, no password. Used for deployment scripting and as a
fallback when ADB is unavailable or non-root.

```powershell
telnet <CAR_IP> 23
```

Many scripts (`Share-Internet-And-Connect.ps1`,
`tools/headunit-dev/headunit.sh`) automatically fall back to Telnet/Base64
transmission if ADB connectivity fails or lacks root.

### Common deployment workflows

| Target | How |
|---|---|
| Impulse / Haval Shisuku app | `.\scripts\Deploy-To-Car.ps1` (build + install) |
| Android Auto patches | `python scripts/aa-patches/deploy_to_car.py --ip <CAR_IP>` — pushes the APK to `/data/local/tmp/` and bind-mounts over `/vendor/app/AndroidAutoApp/AndroidAutoApp.apk` via root telnet/adb. |

## Cleanup on the way out

When you finish a piece of work:

1. **Verify gitignore is doing its job.** Run `git status --short`. Nothing
   under `scripts/.build/`, `scripts/.tmp/`, `tools/`, or `build_*/` should
   appear. If something does, fix the gitignore before committing.
2. **Wipe stale intermediates.** Remove APKs in `scripts/.build/` that aren't
   the latest. PowerShell: `Remove-Item scripts/.build/*.apk`. Bash:
   `rm scripts/.build/*.apk`.
3. **Clean `$env:TEMP` files** you created (logcat dumps, dumpsys captures,
   screencaps). Common patterns: `car_log*.txt`, `sf_*.txt`, `top_*.txt`,
   `ps_*.txt`, `cluster*.png`, `bundled_*.apk`, `extracted_*.apk`.

## When pushing to git

- **Stage selectively.** `git add <specific files>` — never `git add -A` or
  `git add .` blindly, because parallel work from other agents (or your own
  scratch) may have left untracked files you don't intend to commit.
- **Commit messages** follow the existing style:
  `feat(patches): integrate CarPlay & AndroidAuto patches, milestones, …`
- **Re-check** with `git diff --cached --stat` before committing. If you see
  a file you don't recognize, investigate first.

## Apktool, signing tools, keystore — known paths

These are referenced verbatim in many of the README files. Keep them stable.

| Tool | Path |
|------|------|
| apktool | `tools/apktool_3.0.2.jar` (gitignored — download from `iBotPeaches/Apktool` releases if missing) |
| zipalign | `C:\Users\vanes\AppData\Local\Android\Sdk\build-tools\36.1.0\zipalign.exe` |
| apksigner | `C:\Users\vanes\AppData\Local\Android\Sdk\build-tools\36.1.0\apksigner.bat` |
| Debug keystore | `C:\Users\vanes\.android\debug.keystore` (password `android`) |
| ADB | `C:\Users\vanes\AppData\Local\Android\Sdk\platform-tools\adb.exe` |
