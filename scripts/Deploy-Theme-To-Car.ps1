<#
.SYNOPSIS
    Side-loads a built cluster theme straight onto the car, bypassing the
    GitHub catalogue.
.DESCRIPTION
    Themes normally arrive by download: ThemeManager fetches them from the
    preview branch on GitHub into the app's private storage. That means an
    uncommitted theme cannot be tested on the car through the normal flow.

    This copies the built package directly into that same directory instead:

        /data/data/br.com.redesurftank.havalshisuku/files/themes/<folder>

    adb shell runs as uid 2000 and cannot write there, but the installed app is
    a debug build, so `run-as` can. Files go to /sdcard first, then run-as
    copies them into place and the app is restarted to pick them up.

    Build the theme before running this:
        cd cluster-widgets/source/v1.0/minimalist ; npm run build

.PARAMETER Theme
    Theme folder name as it exists on the device. Default 'minimalist'.
.PARAMETER Source
    Directory holding the built package (app.html, theme.xml, ...).
    Defaults to cluster-widgets/Themes/v1.0/<Theme>.
.PARAMETER Serial
    adb serial. Defaults to the only attached device. The head unit often
    appears twice (two Wi-Fi NICs, one box); either transport works.
.PARAMETER NoRestart
    Skip the app restart. The theme only reloads on the next cluster start.
.EXAMPLE
    .\scripts\Deploy-Theme-To-Car.ps1
.EXAMPLE
    .\scripts\Deploy-Theme-To-Car.ps1 -Theme minimalist -NoRestart
#>
[CmdletBinding()]
param(
    [string]$Theme = "minimalist",
    [string]$Source,
    [string]$Serial,
    [switch]$NoRestart
)

$ErrorActionPreference = "Stop"

$pkg = "br.com.redesurftank.havalshisuku"
$adb = "C:\Users\vanes\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb not found at $adb" }

if (-not $Source) {
    $Source = Join-Path $PSScriptRoot "..\cluster-widgets\Themes\v1.0\$Theme"
}
if (-not (Test-Path $Source)) {
    throw "Built theme not found at $Source. Run 'npm run build' in the theme source first."
}

# @() matters: a single match comes back as a bare string, and $lines[0] would
# then index its first CHARACTER instead of the first row.
if (-not $Serial) {
    $lines = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice$" })
    if ($lines.Count -eq 0) { throw "No adb device attached. Is the car on the network?" }
    $Serial = ($lines[0] -split "\s+")[0]
}
Write-Host "Device $Serial" -ForegroundColor Cyan

$files = Get-ChildItem -Path $Source -File
if ($files.Count -eq 0) { throw "No files in $Source" }

$staging = "/sdcard/impulse-theme-stage"
$dest = "files/themes/$Theme"

& $adb -s $Serial shell rm -rf $staging | Out-Null
& $adb -s $Serial shell mkdir -p $staging | Out-Null

foreach ($f in $files) {
    Write-Host ("  push {0,-16} {1,8:N0} bytes" -f $f.Name, $f.Length) -ForegroundColor DarkGray
    & $adb -s $Serial push $f.FullName "$staging/$($f.Name)" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "push failed for $($f.Name)" }
}

# run-as is the only writer with access to the app's private storage. mkdir -p
# is harmless if the theme was already downloaded once.
& $adb -s $Serial shell run-as $pkg mkdir -p $dest
foreach ($f in $files) {
    & $adb -s $Serial shell run-as $pkg cp "$staging/$($f.Name)" "$dest/$($f.Name)"
    if ($LASTEXITCODE -ne 0) { throw "run-as copy failed for $($f.Name)" }
}

& $adb -s $Serial shell rm -rf $staging | Out-Null

Write-Host "Installed into $dest" -ForegroundColor Green
& $adb -s $Serial shell run-as $pkg ls -la $dest

if (-not $NoRestart) {
    # The cluster WebView caches the theme for the life of the process, so a
    # restart is what actually makes the new app.html load.
    Write-Host "Restarting $pkg ..." -ForegroundColor Cyan
    & $adb -s $Serial shell am force-stop $pkg
    Start-Sleep -Seconds 2
    # monkey chatters on stderr even when it succeeds, and PowerShell scores a
    # native command's stderr as failure under $ErrorActionPreference = Stop.
    # The launch either worked or it did not; the pidof check below is the test.
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & $adb -s $Serial shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 2>&1 | Out-Null
    $ErrorActionPreference = $prev
    # The app can take longer than one sleep to come back, and pidof returns
    # nothing at all until it does — so poll, and never call .Trim() on null.
    $appPid = ""
    foreach ($attempt in 1..10) {
        Start-Sleep -Seconds 2
        $appPid = "$(& $adb -s $Serial shell pidof $pkg)".Trim()
        if ($appPid) { break }
    }
    if ($appPid) {
        Write-Host "Restarted (pid $appPid). Give the cluster ~15s to rebuild the projector." -ForegroundColor Green
    } else {
        Write-Warning "App did not come back up. Start it from the head unit, or re-run with -NoRestart."
    }
}
