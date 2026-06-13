# PowerShell Script to Build and Deploy Haval Shisuku to Car MMI
#
# This script:
# 1. Builds the project using the correct JDK.
# 2. Detects the Car MMI IP from the ARP table (192.168.33.x).
# 3. Connects via ADB and installs the APK.

$ErrorActionPreference = "Stop"

# 1. Resolve Paths
$sdkDir = "C:\Users\vanes\AppData\Local\Android\Sdk"
$adbPath = Join-Path $sdkDir "platform-tools\adb.exe"
$jbrPath = "C:\Program Files\Android\Android Studio\jbr"
$gradlew = Join-Path $PSScriptRoot "..\gradlew.bat"
$apkPath = Join-Path $PSScriptRoot "..\app\build\outputs\apk\debug\app-debug.apk"

Write-Host "--- Building Project ---" -ForegroundColor Cyan
$env:JAVA_HOME = $jbrPath
& $gradlew assembleDebug

if (-not (Test-Path $apkPath)) {
    Write-Error "APK not found at $apkPath"
}

Write-Host ""
Write-Host "--- Detecting Car MMI IP ---" -ForegroundColor Cyan
$mmiIp = ""
# Scan 192.168.33.x subnet in ARP table
$arp = arp -a
$candidates = @()
foreach ($line in $arp) {
    if ($line -match '192\.168\.33\.(\d{1,3})') {
        $lastOctet = $Matches[1]
        if ($lastOctet -ne "255" -and $lastOctet -ne "1") {
            $candidates += "192.168.33.$lastOctet"
        }
    }
}
$candidates = $candidates | Select-Object -Unique

foreach ($ip in $candidates) {
    Write-Host "Testing $ip..." -ForegroundColor DarkGray
    $isActive = $false
    foreach ($port in @(5555, 23)) {
        try {
            $t = New-Object System.Net.Sockets.TcpClient
            $async = $t.BeginConnect($ip, $port, $null, $null)
            if ($async.AsyncWaitHandle.WaitOne(200)) {
                $t.EndConnect($async)
                $isActive = $true
            }
            $t.Close()
        } catch {}
        if ($isActive) { break }
    }
    if ($isActive) {
        $mmiIp = $ip
        Write-Host "[+] Found Car MMI at $mmiIp" -ForegroundColor Green
        break
    }
}

if (-not $mmiIp) {
    Write-Host "[!] No active Car MMI detected in ARP table. Defaulting to 192.168.33.225" -ForegroundColor Yellow
    $mmiIp = "192.168.33.225"
}

Write-Host ""
Write-Host "--- Deploying to Car ---" -ForegroundColor Cyan
Write-Host "Connecting to $mmiIp..."
& $adbPath connect "${mmiIp}:5555"

Write-Host "Installing APK..."
& $adbPath -s "${mmiIp}:5555" install -r $apkPath

Write-Host ""
Write-Host "Deployment Complete!" -ForegroundColor Green
