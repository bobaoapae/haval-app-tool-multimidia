# PowerShell Script to Wait for Haval Car MMI on LAN / Tailscale and Deploy Latest Build
[CmdletBinding()]
param(
    [int]$MaxWaitMinutes = 60,
    [int]$IntervalSeconds = 5
)

$ErrorActionPreference = "Continue"

# 1. Environment and tool resolution
$jdk = "C:\Users\marce\.jdks\jdk-21.0.7+6"
if (Test-Path $jdk) {
    $env:JAVA_HOME = $jdk
}

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { $adb = $onPath.Source }
}
if (-not (Test-Path $adb)) {
    throw "adb not found at $adb. Ensure Android SDK is installed."
}

$tailscale = "C:\Program Files\Tailscale\tailscale.exe"
if (-not (Test-Path $tailscale)) {
    $tailscale = "C:\Program Files (x86)\Tailscale IPN\tailscale.exe"
}

$apkPath = Join-Path $PSScriptRoot "..\app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apkPath)) {
    throw "APK not found at $apkPath. Please build it first."
}

function Test-Tcp([string]$ip, [int]$port, [int]$timeoutMs = 500) {
    $t = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $t.BeginConnect($ip, $port, $null, $null)
        if ($async.AsyncWaitHandle.WaitOne($timeoutMs)) {
            $t.EndConnect($async)
            return $true
        }
    } catch {} finally {
        $t.Close()
    }
    return $false
}

function Verify-CarAdb([string]$serial) {
    try {
        $out = & $adb -s $serial shell "getprop | grep -iE 'haval|product.model'; pm path br.com.redesurftank.havalshisuku" 2>$null
        if ($out -match 'br\.com\.redesurftank\.havalshisuku|haval' -or $out.Length -gt 0) {
            return $true
        }
    } catch {}
    return $false
}

Write-Host "==========================================================================" -ForegroundColor Cyan
Write-Host " Waiting for Haval Car MMI to become available on LAN / Tailscale..." -ForegroundColor Green
Write-Host "==========================================================================" -ForegroundColor Cyan
Write-Host "Started at: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor DarkGray
Write-Host "APK to install: $apkPath ($( [math]::Round((Get-Item $apkPath).Length / 1MB, 2) ) MB)" -ForegroundColor DarkGray
Write-Host "Max wait time: $MaxWaitMinutes minutes (polling every $IntervalSeconds seconds)" -ForegroundColor DarkGray
Write-Host ""

$startTime = Get-Date
$foundTarget = ""
$foundMethod = ""
$iteration = 0

while ((Get-Date) -lt $startTime.AddMinutes($MaxWaitMinutes)) {
    $iteration++
    $timeStr = Get-Date -Format "HH:mm:ss"

    # --- Check 1: Already attached in ADB (excluding emulator-5554) ---
    $devicesOut = & $adb devices -l 2>&1
    foreach ($line in ($devicesOut -split "`r?`n")) {
        if ($line -match '^(\S+)\s+device\b' -and $Matches[1] -notlike "emulator*") {
            $dev = $Matches[1]
            Write-Host "[$timeStr] Detected active ADB device: $dev" -ForegroundColor Green
            $foundTarget = if ($dev -match ':\d+$') { ($dev -split ':')[0] } else { $dev }
            $foundMethod = "ADB devices list ($dev)"
            break
        }
    }
    if ($foundTarget) { break }

    # --- Check 2: Tailscale haval-headunit status ---
    if (Test-Path $tailscale) {
        try {
            $tsJson = & $tailscale status --json 2>$null | ConvertFrom-Json
            if ($tsJson -and $tsJson.Peer) {
                $headunit = $null
                foreach ($prop in $tsJson.Peer.PSObject.Properties) {
                    if ($prop.Value.HostName -like "*headunit*") {
                        $headunit = $prop.Value
                        break
                    }
                }
                if ($headunit -and $headunit.Online) {
                    $tsIp = $headunit.TailscaleIPs | Where-Object { $_ -match '^\d+\.\d+\.\d+\.\d+$' } | Select-Object -First 1
                    Write-Host "[$timeStr] haval-headunit is ONLINE on Tailscale ($tsIp)!" -ForegroundColor Green

                    # Check if CurAddr has a local LAN IP
                    if ($headunit.CurAddr -match '^(\d+\.\d+\.\d+\.\d+):') {
                        $lanIp = $Matches[1]
                        Write-Host "[$timeStr] Checking direct LAN address $lanIp from Tailscale endpoint..." -ForegroundColor Cyan
                        if (Test-Tcp $lanIp 5555 800) {
                            $foundTarget = $lanIp
                            $foundMethod = "Tailscale direct LAN ($lanIp)"
                            break
                        }
                    }

                    # Otherwise try Tailscale IP on 5555
                    if (Test-Tcp $tsIp 5555 2000) {
                        $foundTarget = $tsIp
                        $foundMethod = "Tailscale IP ($tsIp)"
                        break
                    }
                }
            }
        } catch {}
    }
    if ($foundTarget) { break }

    # --- Check 3: Known car LAN IP candidate (192.168.1.43) ---
    if (Test-Tcp "192.168.1.43" 5555 300) {
        Write-Host "[$timeStr] 192.168.1.43:5555 answered TCP probe!" -ForegroundColor Green
        & $adb connect "192.168.1.43:5555" | Out-Null
        Start-Sleep -Milliseconds 400
        if (Verify-CarAdb "192.168.1.43:5555") {
            $foundTarget = "192.168.1.43"
            $foundMethod = "LAN direct (192.168.1.43)"
            break
        }
    }

    # --- Check 4: Car Hotspot Subnet (192.168.33.x) ---
    $arp = arp -a
    $hotspotCandidates = @()
    foreach ($line in $arp) {
        if ($line -match '192\.168\.33\.(\d{1,3})') {
            $lastOctet = $Matches[1]
            if ($lastOctet -ne "255" -and $lastOctet -ne "1" -and $lastOctet -ne "0") {
                $hotspotCandidates += "192.168.33.$lastOctet"
            }
        }
    }
    foreach ($ip in ($hotspotCandidates | Select-Object -Unique)) {
        if ((Test-Tcp $ip 5555 250) -or (Test-Tcp $ip 23 250)) {
            Write-Host "[$timeStr] Car hotspot candidate found at $ip!" -ForegroundColor Green
            $foundTarget = $ip
            $foundMethod = "Car Wi-Fi Hotspot ($ip)"
            break
        }
    }
    if ($foundTarget) { break }

    # --- Check 5: Fast ARP sweep on home LAN (192.168.1.x) every 6 iterations ---
    if ($iteration % 6 -eq 0) {
        $lanCandidates = @()
        foreach ($line in $arp) {
            if ($line -match '192\.168\.1\.(\d{1,3})') {
                $lastOctet = $Matches[1]
                # Exclude gateway (.1, .254) and this PC (.12, .67)
                if ($lastOctet -ne "255" -and $lastOctet -ne "1" -and $lastOctet -ne "254" -and $lastOctet -ne "12" -and $lastOctet -ne "67" -and $lastOctet -ne "27" -and $lastOctet -ne "28") {
                    $lanCandidates += "192.168.1.$lastOctet"
                }
            }
        }
        foreach ($ip in ($lanCandidates | Select-Object -Unique)) {
            if (Test-Tcp $ip 5555 150) {
                Write-Host "[$timeStr] Potential ADB port open on LAN candidate $ip" -ForegroundColor Yellow
                & $adb connect "${ip}:5555" | Out-Null
                Start-Sleep -Milliseconds 400
                if (Verify-CarAdb "${ip}:5555") {
                    $foundTarget = $ip
                    $foundMethod = "LAN sweep ($ip)"
                    break
                }
            }
        }
        if ($foundTarget) { break }
    }

    if ($iteration % 6 -eq 1) {
        $elapsed = [math]::Round(((Get-Date) - $startTime).TotalMinutes, 1)
        Write-Host "[$timeStr] Still waiting... (elapsed: $elapsed min). Checking Tailscale, LAN and Hotspot..." -ForegroundColor DarkGray
    }

    Start-Sleep -Seconds $IntervalSeconds
}

if (-not $foundTarget) {
    Write-Error "Timeout: Car MMI did not appear within $MaxWaitMinutes minutes."
    exit 1
}

Write-Host ""
Write-Host "==========================================================================" -ForegroundColor Green
Write-Host " [+] CAR MMI DETECTED via $foundMethod!" -ForegroundColor Green
Write-Host " Target IP: $foundTarget" -ForegroundColor Yellow
Write-Host "==========================================================================" -ForegroundColor Green
Write-Host ""

# Give ADB daemon on car a moment to stabilize
Write-Host "Connecting ADB to $foundTarget..." -ForegroundColor Cyan
$serial = if ($foundTarget -match ':\d+$') { $foundTarget } else { "${foundTarget}:5555" }

$connected = $false
for ($i = 1; $i -le 5; $i++) {
    & $adb connect $serial | Write-Host
    Start-Sleep -Seconds 1
    $devCheck = & $adb devices -l
    if ($devCheck -match [regex]::Escape($serial)) {
        $connected = $true
        Write-Host "[+] ADB connected successfully to $serial!" -ForegroundColor Green
        break
    }
    Write-Host "Retrying connection ($i/5)..." -ForegroundColor Yellow
    Start-Sleep -Seconds 2
}

if (-not $connected) {
    Write-Error "Failed to establish ADB connection to $serial"
    exit 1
}

# Run the deployment using Deploy-To-Car.ps1 with -SkipBuild
Write-Host ""
Write-Host "--- Running Deploy-To-Car.ps1 ---" -ForegroundColor Cyan
& "$PSScriptRoot\Deploy-To-Car.ps1" -Target $foundTarget -SkipBuild

if ($LASTEXITCODE -ne 0) {
    Write-Error "Deploy-To-Car.ps1 failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "--- Verifying Installation on Car ---" -ForegroundColor Cyan
$pkgInfo = & $adb -s $serial shell "dumpsys package br.com.redesurftank.havalshisuku | grep -E 'versionName|versionCode'" 2>&1
$pkgInfo | Write-Host

Write-Host ""
Write-Host "--- Launching App on Car ---" -ForegroundColor Cyan
& $adb -s $serial shell "am start -n br.com.redesurftank.havalshisuku/.MainActivity" 2>&1 | Write-Host

Write-Host ""
Write-Host "==========================================================================" -ForegroundColor Green
Write-Host " DEPLOYMENT TO CAR COMPLETED SUCCESSFULLY AT $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')!" -ForegroundColor Green
Write-Host "==========================================================================" -ForegroundColor Green
