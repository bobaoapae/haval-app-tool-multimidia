# PowerShell Script to Build and Deploy Haval Shisuku to Car MMI
#
# This script:
# 1. Builds the project using the correct JDK.
# 2. Resolves the head unit: -Target, then a Tailscale machine name if given,
#    then the local 192.168.33.x ARP scan.
# 3. Connects via ADB and installs the APK.
#
# Nothing here is machine- or network-specific: paths come from the standard SDK
# and JDK environment variables, and the head unit address comes from a parameter
# or an environment variable. Set these once per dev box if the defaults miss:
#   ANDROID_SDK_ROOT / ANDROID_HOME  - Android SDK (adb)
#   JAVA_HOME                        - JDK 17+ for the Gradle build
#   HAVAL_ADB_TARGET                 - head unit as IP, host, or host:port
#   HAVAL_TAILSCALE_HOST             - Tailscale machine name of the head unit
#
# Usage:
#   .\scripts\Deploy-To-Car.ps1                      # resolve, build, install
#   .\scripts\Deploy-To-Car.ps1 -Target 192.168.33.10
#   .\scripts\Deploy-To-Car.ps1 -TailscaleHost my-headunit
#   .\scripts\Deploy-To-Car.ps1 -SkipBuild           # install the current APK

[CmdletBinding()]
param(
    [string]$Target = $env:HAVAL_ADB_TARGET,
    [string]$TailscaleHost = $env:HAVAL_TAILSCALE_HOST,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

# 1. Resolve tools from the environment, falling back to the SDK's default
# location and then to PATH.
$adbPath = @(
    $env:ANDROID_SDK_ROOT,
    $env:ANDROID_HOME,
    (Join-Path $env:LOCALAPPDATA "Android\Sdk")
) | Where-Object { $_ } |
    ForEach-Object { Join-Path $_ "platform-tools\adb.exe" } |
    Where-Object { Test-Path $_ } |
    Select-Object -First 1
if (-not $adbPath) {
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { $adbPath = $onPath.Source }
}
if (-not $adbPath) {
    throw "adb not found. Set ANDROID_SDK_ROOT, or put adb on PATH."
}

# AGP 9.x needs JDK 17+, and a JAVA_HOME pointing at an older JDK (11 is a common
# system default) fails the build late with a confusing message. So the JDK is
# validated by major version, not merely by existing.
$MinJdkMajor = 17

# A missing lib/jvm.cfg means a broken/partial install - Android Studio's bundled
# jbr sometimes is one - so that file is the completeness check.
function Get-JdkMajor([string]$path) {
    if (-not $path -or -not (Test-Path (Join-Path $path "lib\jvm.cfg"))) { return 0 }
    $releaseFile = Join-Path $path "release"
    if (Test-Path $releaseFile) {
        $line = Select-String -Path $releaseFile -Pattern '^JAVA_VERSION="?([0-9]+)' -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($line) { return [int]$line.Matches[0].Groups[1].Value }
    }
    return 0
}

$jdkPath = $null
if ((Get-JdkMajor $env:JAVA_HOME) -ge $MinJdkMajor) {
    $jdkPath = $env:JAVA_HOME
} else {
    $jdkPath = @(
        (Join-Path $env:USERPROFILE ".jdks"),
        "C:\Program Files\Java",
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Android"
    ) | Where-Object { Test-Path $_ } |
        ForEach-Object { Get-ChildItem $_ -Directory -ErrorAction SilentlyContinue } |
        ForEach-Object { @($_.FullName, (Join-Path $_.FullName "jbr")) } |
        ForEach-Object { [pscustomobject]@{ Path = $_; Major = (Get-JdkMajor $_) } } |
        Where-Object { $_.Major -ge $MinJdkMajor } |
        Sort-Object Major -Descending |
        Select-Object -First 1 -ExpandProperty Path
}
if (-not $jdkPath) {
    throw "No JDK $MinJdkMajor+ found. Set JAVA_HOME to a JDK $MinJdkMajor or newer."
}

$gradlew = Join-Path $PSScriptRoot "..\gradlew.bat"
$apkPath = Join-Path $PSScriptRoot "..\app\build\outputs\apk\debug\app-debug.apk"

Write-Host "--- Building Project ---" -ForegroundColor Cyan
Write-Host "adb: $adbPath" -ForegroundColor DarkGray
Write-Host "JDK: $jdkPath" -ForegroundColor DarkGray
$env:JAVA_HOME = $jdkPath
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
if ($SkipBuild) {
    Write-Host "Skipping build (-SkipBuild); installing the APK already in build/outputs." -ForegroundColor Yellow
} else {
    & $gradlew assembleDebug
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Gradle build failed (exit $LASTEXITCODE) - aborting deploy so a stale APK is not installed."
        exit $LASTEXITCODE
    }
}

if (-not (Test-Path $apkPath)) {
    Write-Error "APK not found at $apkPath"
}

Write-Host ""
Write-Host "--- Detecting Car MMI ---" -ForegroundColor Cyan

# ADB port answers over Tailscale too, but through a DERP relay (~300 ms), so the
# probe needs a far longer timeout than the local-network one.
function Test-CarPort([string]$ip, [int]$port, [int]$timeoutMs) {
    $t = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $t.BeginConnect($ip, $port, $null, $null)
        if ($async.AsyncWaitHandle.WaitOne($timeoutMs)) {
            $t.EndConnect($async)
            return $true
        }
    } catch {
    } finally {
        $t.Close()
    }
    return $false
}

$mmiIp = ""

if ($Target) {
    $mmiIp = $Target
    Write-Host "[+] Using requested target $mmiIp" -ForegroundColor Green
}

# 2a. Tailscale (opt-in): reaches the unit from any network, so it is tried before
# the local subnet. Only runs when a machine name is supplied, since the name is
# specific to whoever set up the tailnet.
if (-not $mmiIp -and $TailscaleHost) {
    $tailscale = @(
        "C:\Program Files\Tailscale\tailscale.exe",
        "C:\Program Files (x86)\Tailscale IPN\tailscale.exe"
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1
    if ($tailscale) {
        $tsIp = (& $tailscale ip -4 $TailscaleHost 2>$null | Select-Object -First 1)
        if ($tsIp) {
            $tsIp = $tsIp.Trim()
            Write-Host "Testing Tailscale $TailscaleHost ($tsIp)..." -ForegroundColor DarkGray
            # A relayed path that has gone idle drops the first packets and takes
            # seconds to come up, so this needs several slow attempts, unlike the
            # LAN probe below.
            $reachable = $false
            foreach ($attempt in 1..3) {
                if (Test-CarPort $tsIp 5555 8000) { $reachable = $true; break }
                Write-Host "  attempt $attempt timed out; retrying..." -ForegroundColor DarkGray
            }
            if ($reachable) {
                $mmiIp = $tsIp
                Write-Host "[+] Found Car MMI over Tailscale at $mmiIp" -ForegroundColor Green
            } else {
                Write-Host "[!] Tailscale node did not answer on 5555" -ForegroundColor Yellow
            }
        }
    }
}

# 2b. Local Wi-Fi: scan the 192.168.33.x ARP table.
if (-not $mmiIp) {
    $candidates = @()
    foreach ($line in (arp -a)) {
        if ($line -match '192\.168\.33\.(\d{1,3})') {
            $lastOctet = $Matches[1]
            if ($lastOctet -ne "255" -and $lastOctet -ne "1") {
                $candidates += "192.168.33.$lastOctet"
            }
        }
    }
    foreach ($ip in ($candidates | Select-Object -Unique)) {
        Write-Host "Testing $ip..." -ForegroundColor DarkGray
        if ((Test-CarPort $ip 5555 200) -or (Test-CarPort $ip 23 200)) {
            $mmiIp = $ip
            Write-Host "[+] Found Car MMI at $mmiIp" -ForegroundColor Green
            break
        }
    }
}

if (-not $mmiIp) {
    throw @"
No head unit found. Pass one explicitly or set an environment variable:
  -Target <ip|host[:port]>        or  HAVAL_ADB_TARGET
  -TailscaleHost <machine-name>   or  HAVAL_TAILSCALE_HOST
Otherwise join the unit's Wi-Fi so it appears in the 192.168.33.x ARP table.
"@
}

Write-Host ""
Write-Host "--- Deploying to Car ---" -ForegroundColor Cyan

# adb writes transfer progress to stderr, which PowerShell turns into a
# NativeCommandError; under $ErrorActionPreference = "Stop" that aborted the run
# mid-sync and left half the theme files stale. Invoke-Adb keeps stderr as plain
# output and decides success from the exit code instead.
$serial = if ($mmiIp -match ':\d+$') { $mmiIp } else { "${mmiIp}:5555" }

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$AdbArgs)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & $adbPath @AdbArgs 2>&1 | ForEach-Object { "$_" }
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prev
    }
    if ($output) { $output | Write-Host }
    if ($code -ne 0) {
        throw "adb $($AdbArgs -join ' ') failed (exit $code)"
    }
    # adb install reports failures in its output while still exiting 0.
    if ($output -match 'Failure \[|adb: error:') {
        throw "adb $($AdbArgs -join ' ') reported an error"
    }
}

$pkg = "br.com.redesurftank.havalshisuku"
$themesRoot = Join-Path $PSScriptRoot "..\cluster-widgets\Themes\v1.0"
$minimalistBg = Join-Path $PSScriptRoot "..\cluster-widgets\source\v1.0\minimalist\src\assets\car-bg.png"

Write-Host "Connecting to $serial..."
Invoke-Adb connect $serial

Write-Host "Installing APK..."
Invoke-Adb -s $serial install -r $apkPath

# Theme packages live in the app's private storage, so each file is pushed to a
# world-readable temp path first and copied in as the app uid via run-as.
function Sync-ThemeFile {
    param([string]$LocalPath, [string]$ThemeFolder, [string]$FileName)
    if (-not (Test-Path $LocalPath)) {
        Write-Host "[!] Skipping missing $LocalPath" -ForegroundColor Yellow
        return
    }
    $tmp = "/data/local/tmp/theme_sync_$FileName"
    Invoke-Adb -s $serial push $LocalPath $tmp
    Invoke-Adb -s $serial shell "run-as $pkg sh -c 'mkdir -p files/themes/$ThemeFolder'"
    Invoke-Adb -s $serial shell "run-as $pkg sh -c 'cp $tmp files/themes/$ThemeFolder/$FileName'"
    Invoke-Adb -s $serial shell rm -f $tmp
}

Write-Host "Syncing Minimalist theme files to car internal storage..."
Sync-ThemeFile $minimalistBg "minimalist" "car-bg.png"
Sync-ThemeFile (Join-Path $themesRoot "minimalist\theme.xml") "minimalist" "theme.xml"
Sync-ThemeFile (Join-Path $themesRoot "minimalist\app.html") "minimalist" "app.html"

Write-Host "Syncing Default theme files to car internal storage..."
Sync-ThemeFile (Join-Path $themesRoot "Default\theme.xml") "Default" "theme.xml"
Sync-ThemeFile (Join-Path $themesRoot "Default\index.html") "Default" "index.html"

Write-Host "Cleaning up debug HTML override on car..."
Invoke-Adb -s $serial shell rm -f /data/local/tmp/app.html

Write-Host ""
Write-Host "Deployment Complete!" -ForegroundColor Green
Write-Host "Restart the app to pick up new theme files: adb -s $serial shell am force-stop $pkg" -ForegroundColor DarkGray
