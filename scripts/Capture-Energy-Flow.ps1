<#
.SYNOPSIS
    Records energy-flow telemetry from the car while you drive, so the
    Minimalist powertrain icon can be built against real values.
.DESCRIPTION
    The H6 3D viewer (com.havalh6.viewer) logs every car-data push as
        W/H6Viewer: CarSignal com.haval.vehicle.EVENT_CHANGED key=<k> value=<v>
    which is the only live probe available without rebuilding the app. This
    script tails that tag, keeps the powertrain keys, and writes a timestamped
    CSV plus a summary of every distinct energy_drive_state seen.

    Run it, drive normally for 10-15 minutes (include: pulling away, steady
    cruise, hard acceleration, coasting/regen, a stop with the engine running,
    and an engine start under load), then Ctrl+C.

    Decode the states with the OEM table in
    docs/reference/energy-drive-state.md.
.PARAMETER Serial
    adb serial of the head unit. Defaults to the only attached device.
    NOTE: the head unit often appears TWICE in `adb devices` (two Wi-Fi NICs,
    one physical box). Either transport works.
.PARAMETER OutDir
    Where to write the capture. Defaults to .\captures.
.PARAMETER Snapshot
    Take one full snapshot and exit instead of recording continuously.
.EXAMPLE
    .\scripts\Capture-Energy-Flow.ps1 -Snapshot
.EXAMPLE
    .\scripts\Capture-Energy-Flow.ps1 -OutDir D:\drives
#>
[CmdletBinding()]
param(
    [string]$Serial,
    [string]$OutDir = (Join-Path $PSScriptRoot "..\captures"),
    [switch]$Snapshot
)

$adb = "C:\Users\vanes\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb not found at $adb" }

if (-not $Serial) {
    # @() matters: a single match comes back as a bare string, and $lines[0]
    # would then index its first CHARACTER instead of the first row.
    $lines = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice$" })
    if ($lines.Count -eq 0) { throw "No adb device attached. Is the car on the network?" }
    $Serial = ($lines[0] -split "\s+")[0]
    if ($lines.Count -gt 1) {
        Write-Host "Multiple transports listed (normally ONE head unit with two Wi-Fi NICs); using $Serial" -ForegroundColor DarkYellow
    }
}
Write-Host "Using device $Serial" -ForegroundColor Cyan

# Powertrain keys the icon needs. energy_drive_state carries the axle/ICE
# decomposition on its own; the rest are cross-checks.
$keys = @(
    "car.ev_info.energy_drive_state",
    "car.ev_info.motor_speed",
    "car.ev_info.rear_motor_speed",
    "car.ev_info.motor_power",
    "car.ev_info.energy_output_percentage",
    "car.ev_info.charging_state",
    "car.ev_info.power_battery_current",
    "car.ev_info.power_battery_voltage",
    "car.configure.ev_drive_architecture",
    "car.basic.engine_state",
    "car.basic.engine_speed",
    "car.basic.vehicle_speed",
    "car.ipk_light.brake_energe_recycle"
)
# Anchor on "key=<k> value=" so car.basic.vehicle_speed does not also swallow
# car.basic.vehicle_speed_since_reset.
$pattern = ($keys | ForEach-Object { "key=" + [regex]::Escape($_) + "\s+value=" }) -join "|"

if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Force -Path $OutDir | Out-Null }
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$csv = Join-Path $OutDir "energy-flow-$stamp.csv"

# Force a full snapshot so the capture opens with every key's current value
# rather than waiting for each one to change.
& $adb -s $Serial logcat -c 2>$null
& $adb -s $Serial shell am broadcast -a br.com.redesurftank.havalshisuku.ACTION_DISPATCH_ALL_DATAS | Out-Null
Start-Sleep -Seconds 3

if ($Snapshot) {
    # Filter before trimming: $pattern anchors on "key=", which the split removes.
    & $adb -s $Serial logcat -d -s H6Viewer |
        Where-Object { $_ -match "CarSignal" -and $_ -match $pattern } |
        ForEach-Object { ($_ -split "key=")[-1].Trim() } |
        Sort-Object -Unique
    return
}

"timestamp,key,value" | Set-Content -Path $csv -Encoding utf8
Write-Host "Recording to $csv - drive now, Ctrl+C when done." -ForegroundColor Green
Write-Host "Cover: pull away / cruise / hard accel / coast (regen) / stop with engine on." -ForegroundColor DarkGray

$seen = @{}
& $adb -s $Serial logcat -v time -s H6Viewer | ForEach-Object {
    if ($_ -notmatch "CarSignal") { return }
    if ($_ -notmatch $pattern) { return }
    if ($_ -notmatch "^(?<t>\S+\s+\S+).*key=(?<k>\S+)\s+value=(?<v>.*)$") { return }

    $t = $Matches['t']; $k = $Matches['k']; $v = $Matches['v'].Trim()
    "$t,$k,$v" | Add-Content -Path $csv -Encoding utf8

    # -99999 (motor speed) and -1001 (motor power) are the car's
    # "value unavailable" sentinels, not readings. Never let them into a tally.
    if ($v -eq "-99999" -or $v -eq "-1001") { return }

    if ($k -eq "car.ev_info.energy_drive_state" -and -not $seen.ContainsKey($v)) {
        $seen[$v] = $true
        Write-Host ("  new energy_drive_state = {0}   (states so far: {1})" -f $v, (($seen.Keys | Sort-Object) -join ", ")) -ForegroundColor Yellow
    }
}
