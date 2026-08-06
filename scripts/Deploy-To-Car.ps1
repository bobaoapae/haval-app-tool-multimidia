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
# AVG HTTPS scanning MITMs TLS; JBR cacerts does not trust AVG's root. Use the
# user truststore created by scripts/Setup-JavaSslForAvg.ps1 when present.
$avgTrust = Join-Path $env:USERPROFILE ".gradle\haval-ssl\cacerts-with-avg"
if (Test-Path $avgTrust) {
    $sslOpts = "-Djavax.net.ssl.trustStore=`"$avgTrust`" -Djavax.net.ssl.trustStorePassword=changeit"
    $env:JAVA_OPTS = if ($env:JAVA_OPTS) { "$env:JAVA_OPTS $sslOpts" } else { $sslOpts }
    $env:GRADLE_OPTS = if ($env:GRADLE_OPTS) { "$env:GRADLE_OPTS $sslOpts" } else { $sslOpts }
    Write-Host "Using AVG-aware Java truststore: $avgTrust" -ForegroundColor DarkGray
} else {
    Write-Host "[!] AVG truststore missing - if build fails with PKIX, run scripts\Setup-JavaSslForAvg.ps1" -ForegroundColor Yellow
}
& $gradlew assembleDebug
if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle build failed (exit $LASTEXITCODE) - aborting deploy so a stale APK is not installed."
    exit $LASTEXITCODE
}

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

Write-Host "Syncing Minimalist Theme files to Car internal storage..."
& $adbPath -s "${mmiIp}:5555" push "$PSScriptRoot\..\cluster-widgets\source\v1.0\minimalist\src\assets\car-bg.png" "/data/local/tmp/minimalist_car-bg.png"
& $adbPath -s "${mmiIp}:5555" push "$PSScriptRoot\..\cluster-widgets\Themes\v1.0\minimalist\theme.xml" "/data/local/tmp/minimalist_theme.xml"
& $adbPath -s "${mmiIp}:5555" push "$PSScriptRoot\..\cluster-widgets\Themes\v1.0\minimalist\app.html" "/data/local/tmp/minimalist_app.html"
& $adbPath -s "${mmiIp}:5555" shell run-as br.com.redesurftank.havalshisuku mkdir -p files/themes/minimalist
& $adbPath -s "${mmiIp}:5555" shell run-as br.com.redesurftank.havalshisuku cp /data/local/tmp/minimalist_car-bg.png files/themes/minimalist/car-bg.png
& $adbPath -s "${mmiIp}:5555" shell run-as br.com.redesurftank.havalshisuku cp /data/local/tmp/minimalist_theme.xml files/themes/minimalist/theme.xml
& $adbPath -s "${mmiIp}:5555" shell run-as br.com.redesurftank.havalshisuku cp /data/local/tmp/minimalist_app.html files/themes/minimalist/app.html

Write-Host "Syncing Default Theme files to Car internal storage..."
& $adbPath -s "${mmiIp}:5555" push "$PSScriptRoot\..\cluster-widgets\Themes\v1.0\Default\theme.xml" "/data/local/tmp/default_theme.xml"
& $adbPath -s "${mmiIp}:5555" push "$PSScriptRoot\..\cluster-widgets\Themes\v1.0\Default\index.html" "/data/local/tmp/default_index.html"
& $adbPath -s "${mmiIp}:5555" shell run-as br.com.redesurftank.havalshisuku mkdir -p files/themes/Default
& $adbPath -s "${mmiIp}:5555" shell run-as br.com.redesurftank.havalshisuku cp /data/local/tmp/default_theme.xml files/themes/Default/theme.xml
& $adbPath -s "${mmiIp}:5555" shell run-as br.com.redesurftank.havalshisuku cp /data/local/tmp/default_index.html files/themes/Default/index.html

Write-Host "Cleaning up debug HTML override and temp files on Car..."
& $adbPath -s "${mmiIp}:5555" shell rm -f /data/local/tmp/app.html /data/local/tmp/minimalist_* /data/local/tmp/default_*

Write-Host ""
Write-Host "Deployment Complete!" -ForegroundColor Green
