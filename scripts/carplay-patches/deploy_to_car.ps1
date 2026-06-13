# Windows PowerShell Script to Rebuild, Sign, Deploy and Bind-Mount Patched CarPlay APK
#
# Usage:
#   .\deploy_to_car.ps1
#   .\deploy_to_car.ps1 -MmiIp "192.168.33.202"
#

[CmdletBinding()]
param (
    [Parameter(Mandatory = $false)]
    [string]$MmiIp = "192.168.33.202"
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================================" -ForegroundColor DarkGray
Write-Host "     * CarPlay Reassembly & Root Bind-Mount Deployment Utility *" -ForegroundColor Green
Write-Host "==========================================================================" -ForegroundColor DarkGray

# 1. Resolve Path Variables
$sdkDir = "C:\Users\vanes\AppData\Local\Android\Sdk"
$adbPath = Join-Path $sdkDir "platform-tools\adb.exe"
$keystorePath = "C:\Users\vanes\.android\debug.keystore"

# Locate build-tools dynamically
$buildToolsDir = Join-Path $sdkDir "build-tools"
if (Test-Path $buildToolsDir) {
    $latestBuildTools = Get-ChildItem $buildToolsDir | Sort-Object Name -Descending | Select-Object -First 1
    if ($latestBuildTools) {
        $zipalignPath = Join-Path $latestBuildTools.FullName "zipalign.exe"
        $apksignerPath = Join-Path $latestBuildTools.FullName "apksigner.bat"
        Write-Host "[+] Found build-tools at: $($latestBuildTools.FullName)" -ForegroundColor DarkGray
    } else {
        $zipalignPath = "zipalign"
        $apksignerPath = "apksigner"
    }
} else {
    $zipalignPath = "zipalign"
    $apksignerPath = "apksigner"
}

# 2. Check Prerequisites
if (-not (Test-Path $keystorePath)) {
    Write-Error "Debug keystore not found at $keystorePath. Please ensure the keystore is available."
}

# 3. Rebuild Smali to Unsigned APK
Write-Host ""
Write-Host "[+] Rebuilding smali directory using apktool..." -ForegroundColor Cyan
$apktoolJar = "tools/apktool_3.0.2.jar"
$unsignedApk = "build_carplay/TsCarPlayApp_unsigned.apk"
$alignedApk = "build_carplay/TsCarPlayApp_aligned.apk"
$signedApk = "build_carplay/TsCarPlayApp_signed.apk"

# Ensure output directory exists
New-Item -ItemType Directory -Force -Path "build_carplay" | Out-Null

# Run apktool
& java -jar $apktoolJar b -o $unsignedApk build_carplay/app
if (-not (Test-Path $unsignedApk)) {
    Write-Error "Reassembly failed! Unsigned APK not generated."
}
Write-Host "[+] Reassembly complete." -ForegroundColor Green

# 4. Zipalign the APK
Write-Host ""
Write-Host "[+] Aligning the APK..." -ForegroundColor Cyan
if (Test-Path $alignedApk) { Remove-Item $alignedApk -Force }
& $zipalignPath -f 4 $unsignedApk $alignedApk
if (-not (Test-Path $alignedApk)) {
    Write-Error "Zipalign failed!"
}
Write-Host "[+] Zipalign complete." -ForegroundColor Green

# 5. Sign the APK
Write-Host ""
Write-Host "[+] Signing the APK with debug keystore..." -ForegroundColor Cyan
if (Test-Path $signedApk) { Remove-Item $signedApk -Force }
& $apksignerPath sign --ks $keystorePath --ks-pass pass:android --out $signedApk $alignedApk
if (-not (Test-Path $signedApk)) {
    Write-Error "Apksigner failed!"
}
Write-Host "[+] Signature complete." -ForegroundColor Green

# Verify signature
Write-Host "[*] Verifying APK signature..." -ForegroundColor DarkGray
& $apksignerPath verify --verbose $signedApk

# 6. ADB Connection
Write-Host ""
Write-Host "[+] Connecting to MMI via ADB (${MmiIp}:5555)..." -ForegroundColor Cyan
$null = & $adbPath connect "${MmiIp}:5555"
Start-Sleep -Milliseconds 500

$devices = & $adbPath devices
if ($devices -match "${MmiIp}:5555\s+device") {
    Write-Host "[+] Connected successfully via ADB." -ForegroundColor Green
} else {
    Write-Warning "Could not connect to ${MmiIp}:5555 via ADB. Trying to proceed anyway..."
}

# 7. Push Patched APK to Car
Write-Host ""
Write-Host "[+] Pushing patched APK to /data/local/tmp on the MMI..." -ForegroundColor Cyan
& $adbPath -s "${MmiIp}:5555" push $signedApk "/data/local/tmp/TsCarPlayApp_patched.apk"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to push APK to vehicle via ADB."
}
Write-Host "[+] Push complete." -ForegroundColor Green

# 8. Connect to root Telnet and apply Bind Mount & Dalvik Cache Eviction
Write-Host ""
Write-Host "[+] Establishing root Telnet connection (Port 23) to apply bind mount..." -ForegroundColor Cyan
try {
    $client = New-Object System.Net.Sockets.TcpClient($MmiIp, 23)
    $stream = $client.GetStream()
    $writer = New-Object System.IO.StreamWriter($stream)
    $writer.AutoFlush = $true
    
    function Send-Cmd {
        param([string]$cmd)
        $writer.WriteLine($cmd)
        Start-Sleep -Milliseconds 150
    }
    
    function Read-All {
        $buffer = New-Object byte[] 8192
        $out = ""
        Start-Sleep -Milliseconds 250
        while ($stream.DataAvailable) {
            $read = $stream.Read($buffer, 0, $buffer.Length)
            $out += [System.Text.Encoding]::ASCII.GetString($buffer, 0, $read)
            Start-Sleep -Milliseconds 50
        }
        return $out
    }

    # Flush Telnet greetings
    $null = Read-All

    Write-Host "[*] Configuring permissions and SELinux context..." -ForegroundColor DarkGray
    Send-Cmd "chmod 644 /data/local/tmp/TsCarPlayApp_patched.apk"
    Send-Cmd "chcon u:object_r:system_file:s0 /data/local/tmp/TsCarPlayApp_patched.apk"
    
    Write-Host "[*] Clearing existing bind mounts..." -ForegroundColor DarkGray
    Send-Cmd "umount -l /system/app/TsCarPlayApp/TsCarPlayApp.apk"
    
    Write-Host "[*] Executing bind mount..." -ForegroundColor Cyan
    Send-Cmd "mount --bind /data/local/tmp/TsCarPlayApp_patched.apk /system/app/TsCarPlayApp/TsCarPlayApp.apk"
    
    Write-Host "[*] Evicting Dalvik/DEX cache..." -ForegroundColor DarkGray
    Send-Cmd "rm -rf /data/dalvik-cache/arm/system@app@TsCarPlayApp@TsCarPlayApp.apk*"
    Send-Cmd "rm -rf /data/dalvik-cache/arm64/system@app@TsCarPlayApp@TsCarPlayApp.apk*"
    
    Write-Host "[*] Force-stopping and restarting CarPlay app..." -ForegroundColor Cyan
    Send-Cmd "am force-stop com.ts.carplay.app"
    # Restart the main CarPlay display activity
    Send-Cmd "am start -n com.ts.carplay.app/com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity"
    
    $telnetOutput = Read-All
    $client.Close()
    
    Write-Host ""
    Write-Host "--- MMI Shell Execution Log ---" -ForegroundColor Yellow
    Write-Host ($telnetOutput -replace "\r", "")
    Write-Host "--------------------------------" -ForegroundColor Yellow
    Write-Host "[+] Root bind-mount and system restart applied successfully!" -ForegroundColor Green

} catch {
    Write-Error "Failed to execute Telnet root commands: $_"
}

Write-Host ""
Write-Host "[+] Deployment complete! Please test focus, resize and sidebar hide live on-vehicle." -ForegroundColor Green
