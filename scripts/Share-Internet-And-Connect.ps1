# Windows PowerShell Script to Automate MMI Routing & Internet Sharing Setup
# Supports connection via ADB (Port 5555) or Telnet fallback (Port 23)
#
# Features:
#   - Auto-detects Car MMI IP from ARP table (ignoring PC interface IP)
#   - Auto-checks if internet sharing is already active before applying
#   - Keeps connection alive with heartbeat checks
#   - Auto-reconnects and re-applies routing if MMI reboots/disconnects
#
# Usage:
#   .\Share-Internet-And-Connect.ps1
#

[CmdletBinding()]
param (
    [Parameter(Mandatory = $false)]
    [string]$MmiAddress = "",

    [Parameter(Mandatory = $false)]
    [int]$HeartbeatIntervalSec = 5
)

$ErrorActionPreference = "Stop"

# Clear Host Screen for clean UI
Clear-Host

Write-Host "==========================================================================" -ForegroundColor DarkGray
Write-Host "     ⚡ GWM/Haval MMI Internet Sharing & Continuous Connection ⚡" -ForegroundColor Green
Write-Host "==========================================================================" -ForegroundColor DarkGray

# Embedded Android-side Shell Script to configure routing, forwarding, and NAT
$androidScript = @(
    '#!/system/bin/sh',
    '# Auto-detect uplink and enable internet sharing from Car MMI to connected clients (like PC)',
    '',
    'echo "=== Car Internet Sharing Script ==="',
    '',
    '# 1. Check if wlan0 (Client Wi-Fi) is connected and has an IP',
    'WLAN0_IP=$(ip addr show wlan0 2>/dev/null | grep -o ''inet [0-9.]*'' | cut -d'' '' -f2)',
    '',
    'if [ ! -z "$WLAN0_IP" ]; then',
    '    echo "Detected active uplink: wlan0 ($WLAN0_IP)"',
    '    ',
    '    # Find gateway',
    '    GW=$(ip route show | grep "dev wlan0" | grep default | awk ''{print $3}'')',
    '    if [ -z "$GW" ]; then',
    '        # Try to extract the gateway from resolved ARP entries on wlan0',
    '        GW=$(cat /proc/net/arp | grep wlan0 | grep -E ''0x2|0x6'' | awk ''{print $1}'' | head -n 1)',
    '    fi',
    '    if [ -z "$GW" ]; then',
    '        # Fallback to .254 as a sensible default',
    '        GW="192.168.1.254"',
    '    fi',
    '    ',
    '    echo "Setting default route via wlan0 gateway: $GW"',
    '    ip route del default 2>/dev/null',
    '    ip route add default via "$GW" dev wlan0 2>/dev/null',
    '    ',
    '    # Enable routing/NAT',
    '    echo 1 > /proc/sys/net/ipv4/ip_forward',
    '    ',
    '    # Clean old rule to avoid duplicates, then insert at top',
    '    iptables -D FORWARD -j ACCEPT 2>/dev/null',
    '    iptables -I FORWARD 1 -j ACCEPT',
    '    ',
    '    iptables -t nat -D POSTROUTING -o wlan0 -j MASQUERADE 2>/dev/null',
    '    iptables -t nat -I POSTROUTING 1 -o wlan0 -j MASQUERADE',
    '    ',
    '    echo "Internet sharing enabled successfully via wlan0!"',
    '    exit 0',
    'fi',
    '',
    '# 2. Check if vt3 (T-Box cellular modem) is connected and has an IP',
    'VT3_IP=$(ip addr show vt3 2>/dev/null | grep -o ''inet [0-9.]*'' | cut -d'' '' -f2)',
    'if [ ! -z "$VT3_IP" ]; then',
    '    GW="192.168.118.2"',
    '    echo "Detected active uplink: vt3 ($VT3_IP)"',
    '    echo "Setting default route via vt3 gateway: $GW"',
    '    ',
    '    ip route del default 2>/dev/null',
    '    ip route add default via "$GW" dev vt3 2>/dev/null',
    '    ',
    '    # Enable routing/NAT',
    '    echo 1 > /proc/sys/net/ipv4/ip_forward',
    '    ',
    '    iptables -D FORWARD -j ACCEPT 2>/dev/null',
    '    iptables -I FORWARD 1 -j ACCEPT',
    '    ',
    '    iptables -t nat -D POSTROUTING -o vt3 -j MASQUERADE 2>/dev/null',
    '    iptables -t nat -I POSTROUTING 1 -o vt3 -j MASQUERADE',
    '    ',
    '    echo "Internet sharing enabled successfully via vt3!"',
    '    exit 0',
    'fi',
    '',
    'echo "Error: No active internet connection found on wlan0 or vt3!"',
    'exit 1'
) -join "`n"

# Helper check script run on Android shell
$checkShellCommand = 'UP=""; if [ ! -z "$(ip addr show wlan0 2>/dev/null | grep -o ''inet '')" ]; then UP="wlan0"; elif [ ! -z "$(ip addr show vt3 2>/dev/null | grep -o ''inet '')" ]; then UP="vt3"; fi; if [ -z "$UP" ]; then echo "NOT_APPLIED"; else GW=$(ip route show | grep "dev $UP" | grep default | awk ''{print $3}''); if [ -z "$GW" ] && [ "$UP" = "wlan0" ]; then GW=$(cat /proc/net/arp | grep wlan0 | grep -E ''0x2|0x6'' | awk ''{print $1}'' | head -n 1); fi; if [ -z "$GW" ] && [ "$UP" = "vt3" ]; then GW="192.168.118.2"; fi; if [ "$(cat /proc/sys/net/ipv4/ip_forward 2>/dev/null)" = "1" ] && [ ! -z "$(ip route show | grep \"default via $GW dev $UP\" 2>/dev/null)" ] && [ ! -z "$(iptables -t nat -S POSTROUTING 2>/dev/null | grep -E \"MASQUERADE.*$UP|-A POSTROUTING -o $UP -j MASQUERADE\")" ]; then echo "YES_OK"; else echo "NOT_APPLIED"; fi; fi'

# 1. Resolve ADB Executable Path
$sdkDir = "C:\Users\vanes\AppData\Local\Android\Sdk"
$adbPath = Join-Path $sdkDir "platform-tools\adb.exe"

$localProps = Join-Path $PSScriptRoot "local.properties"
if (-not (Test-Path $localProps)) {
    $localProps = Join-Path (Split-Path $PSScriptRoot) "local.properties"
}

if (Test-Path $localProps) {
    $sdkLine = Get-Content $localProps | Where-Object { $_ -match "^sdk\.dir=" }
    if ($sdkLine -and $sdkLine -match "sdk\.dir=(.+)") {
        $parsedPath = $Matches[1].Replace("\\", "\").Replace("\:", ":")
        $adbPath = Join-Path $parsedPath "platform-tools\adb.exe"
    }
}

if (-not (Test-Path $adbPath)) {
    $adbPath = "adb"
}

# 2. Main Monitoring and Reconnection Loop
$mmiIp = $MmiAddress

while ($true) {
    # Auto-detect IP if not provided and not set from a previous loop
    if (-not $mmiIp) {
        Write-Host "`n[i] Searching for Car MMI IP in ARP table..." -ForegroundColor Cyan
        Write-Host "    (Scanning continuously until MMI connects. Press SPACE to skip and use default: 192.168.33.225)" -ForegroundColor DarkGray
        Write-Host -NoNewline "[i] Scanning" -ForegroundColor Cyan

        $attempt = 1
        while ($true) {
            # Force-refresh ARP cache by pinging the 192.168.33.x subnet in parallel (ultra-fast 150ms timeout)
            $ips = 1..254 | ForEach-Object { "192.168.33.$_" }
            $tasks = @()
            foreach ($ip in $ips) {
                try {
                    $ping = New-Object System.Net.NetworkInformation.Ping
                    $tasks += $ping.SendPingAsync($ip, 150)
                } catch {
                    # Ignore ping creation errors during adapter transitions
                }
            }
            if ($tasks) {
                try {
                    [System.Threading.Tasks.Task]::WaitAll($tasks) | Out-Null
                } catch {
                    # Ignore asynchronous ping wait failures during adapter transitions
                }
            }

            $arp = arp -a
            $mmiIpList = @()
            foreach ($line in $arp) {
                # Skip interface headers to avoid picking up the computer's own IP
                if ($line -match 'Interface') { continue }

                if ($line -match '192\.168\.33\.(\d{1,3})') {
                    $lastOctet = $Matches[1]
                    if ($lastOctet -ne "255" -and $lastOctet -ne "1") {
                        $mmiIpList += "192.168.33.$lastOctet"
                    }
                }
            }
            $mmiIpList = @($mmiIpList | Select-Object -Unique)
            
            if ($mmiIpList) {
                # Test connectivity for each candidate IP to bypass stale cached entries
                foreach ($testIp in $mmiIpList) {
                    $isActive = $false
                    foreach ($port in @(23, 5555)) {
                        try {
                            $t = New-Object System.Net.Sockets.TcpClient
                            $async = $t.BeginConnect($testIp, $port, $null, $null)
                            if ($async.AsyncWaitHandle.WaitOne(200)) {
                                $t.EndConnect($async)
                                $isActive = $true
                            }
                            $t.Close()
                        } catch {}
                        if ($isActive) { break }
                    }
                    
                    if ($isActive) {
                        $mmiIp = $testIp
                        Write-Host "`n[+] Auto-detected active Car MMI IP: $mmiIp" -ForegroundColor Green
                        break
                    }
                }
                
                if ($mmiIp) {
                    break
                }
            }
            
            # Check for user input to skip
            $bypass = $false
            try {
                if ($Host.UI.RawUI.KeyAvailable) {
                    $key = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
                    if ($key.VirtualKeyCode -eq 32) { # Spacebar
                        $bypass = $true
                    }
                }
            } catch {
                # Gracefully ignore if run in non-interactive shell
            }
            
            if ($bypass) {
                $mmiIp = "192.168.33.225"
                Write-Host "`n[!] Bypassed. Defaulting to: $mmiIp" -ForegroundColor Yellow
                break
            }
            
            Write-Host -NoNewline "." -ForegroundColor Cyan
            Start-Sleep -Seconds 2
            $attempt++
            if ($attempt % 20 -eq 0) {
                Write-Host "`n[i] Still scanning... (Attempt $attempt)" -ForegroundColor DarkGray
                Write-Host -NoNewline "[i] Scanning" -ForegroundColor Cyan
            }
        }
    }

    # Temporarily set ErrorActionPreference to Continue for native standard error capturing
    $oldEAP = $ErrorActionPreference
    $ErrorActionPreference = "Continue"

    $useAdb = $false
    $isConnected = $false

    Write-Host "`n[*] Connecting to Car MMI at ${mmiIp}..." -ForegroundColor Cyan

    # Try ADB connection first
    Write-Host "  -> Trying ADB (Port 5555)..." -ForegroundColor DarkGray
    $null = & $adbPath connect "${mmiIp}:5555" 2>&1
    Start-Sleep -Milliseconds 500

    $devices = & $adbPath devices 2>&1
    if ($devices -match "${mmiIp}:5555\s+device") {
        # Check if ADB shell has root permissions
        $adbId = & $adbPath -s "${mmiIp}:5555" shell id 2>&1
        if ($adbId -match "uid=0\(root\)") {
            $useAdb = $true
            $isConnected = $true
            Write-Host "  [+] ADB Connection online as root!" -ForegroundColor Green
        } else {
            Write-Host "  [!] ADB is online but running as non-root (cannot configure routing). Falling back to Telnet..." -ForegroundColor Yellow
        }
    } else {
        Write-Host "  [!] ADB connection offline. Trying Telnet..." -ForegroundColor Yellow
    }

    # Fallback to Telnet if ADB is offline or doesn't have root
    if (-not $useAdb) {
        Write-Host "  -> Trying Telnet (Port 23)..." -ForegroundColor DarkGray
        try {
            $client = New-Object System.Net.Sockets.TcpClient($mmiIp, 23)
            $isConnected = $true
            Write-Host "  [+] Telnet Connection online!" -ForegroundColor Green
        } catch {
            $isConnected = $false
            Write-Host "  [-] Connection to ${mmiIp} failed (both ADB and Telnet are unresponsive or lack root)." -ForegroundColor Red
        }
    }

    if (-not $isConnected) {
        Write-Host "[!] Could not reach MMI. Retrying in 5 seconds... (Press Ctrl+C to exit)" -ForegroundColor Yellow
        $ErrorActionPreference = $oldEAP
        # Clear MMI IP so we rescan the ARP table in the next attempt if it was auto-detected
        if (-not $MmiAddress) {
            $mmiIp = ""
        }
        Start-Sleep -Seconds 5
        continue
    }

    # Connection established! Check and Apply Routing
    if ($useAdb) {
        # Check if routing is already applied
        Write-Host "[*] Checking if internet routing is already configured on MMI..." -ForegroundColor Cyan
        $check = & $adbPath -s "${mmiIp}:5555" shell $checkShellCommand 2>&1
        
        $hasApplied = $false
        foreach ($l in ($check -split "\r?\n")) {
            if ($l.Trim() -eq "YES_OK") {
                $hasApplied = $true
            }
        }
        
        if ($hasApplied) {
            Write-Host "[+] Internet routing is already configured on MMI! Skipping application." -ForegroundColor Green
        } else {
            Write-Host "[!] Routing not configured. Applying internet sharing now..." -ForegroundColor Yellow
            
            # ADB execution flow
            Write-Host "[+] Transferring routing script via ADB..." -ForegroundColor Cyan
            $tempFile = [System.IO.Path]::GetTempFileName()
            [System.IO.File]::WriteAllText($tempFile, $androidScript, [System.Text.Encoding]::ASCII)
            
            & $adbPath -s "${mmiIp}:5555" push $tempFile "/data/local/tmp/share_internet.sh" 2>&1 | Out-Null
            Remove-Item $tempFile -Force
            
            & $adbPath -s "${mmiIp}:5555" shell "chmod 755 /data/local/tmp/share_internet.sh" 2>&1 | Out-Null
            
            Write-Host "[*] Executing internet sharing script..." -ForegroundColor Cyan
            $output = & $adbPath -s "${mmiIp}:5555" shell "/system/bin/sh /data/local/tmp/share_internet.sh" 2>&1
            
            # Keeping script on MMI for manual runs as requested
            # & $adbPath -s "${mmiIp}:5555" shell "rm /data/local/tmp/share_internet.sh" 2>&1 | Out-Null
            
            Write-Host "`n--- MMI Script Execution Log ---" -ForegroundColor Yellow
            Write-Host $output
            Write-Host "--------------------------------" -ForegroundColor Yellow
        }
    } else {
        # Telnet Fallback Execution
        $stream = $client.GetStream()
        $writer = New-Object System.IO.StreamWriter($stream)
        $writer.AutoFlush = $true
        
        function Send-Cmd {
            param([string]$cmd)
            $writer.WriteLine($cmd)
            Start-Sleep -Milliseconds 100
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
        
        # Flush Telnet welcome header
        $null = Read-All
        
        # Check if already applied
        Send-Cmd $checkShellCommand
        $checkOutput = Read-All
        
        $hasApplied = $false
        foreach ($l in ($checkOutput -split "\r?\n")) {
            if ($l.Trim() -eq "YES_OK") {
                $hasApplied = $true
            }
        }
        
        if ($hasApplied) {
            Write-Host "[+] Internet routing is already configured on MMI! Skipping application." -ForegroundColor Green
            $client.Close()
        } else {
            Write-Host "[!] Routing not configured. Applying internet sharing now..." -ForegroundColor Yellow
            Write-Host "Connected via Telnet! Transferring routing script line-by-line..." -ForegroundColor DarkGray
            
            # Create empty script
            Send-Cmd "echo '#!/system/bin/sh' > /data/local/tmp/share_internet.sh"
            
            $lines = $androidScript -split "\r?\n"
            foreach ($line in $lines) {
                if ($line -match "^#!" -or $line -match "^\s*$") { continue }
                $escaped = $line.Replace('\', '\\').Replace("'", "'\''")
                Send-Cmd "echo '$escaped' >> /data/local/tmp/share_internet.sh"
            }
            
            Send-Cmd "chmod 755 /data/local/tmp/share_internet.sh"
            $null = Read-All
            
            Write-Host "[*] Executing internet sharing script..." -ForegroundColor Cyan
            Send-Cmd "/system/bin/sh /data/local/tmp/share_internet.sh"
            Start-Sleep -Seconds 2
            $output = Read-All
            
            # Keeping script on MMI for manual runs as requested
            # Send-Cmd "rm /data/local/tmp/share_internet.sh"
            # $null = Read-All
            $client.Close()
            
            $cleanOutput = $output -replace "\r", ""
            Write-Host "`n--- MMI Script Execution Log ---" -ForegroundColor Yellow
            Write-Host $cleanOutput
            Write-Host "--------------------------------" -ForegroundColor Yellow
        }
    }

    $ErrorActionPreference = $oldEAP
    Write-Host "`n[+] Internet sharing configuration complete!" -ForegroundColor Green
    Write-Host "[*] Entering persistent connection monitoring loop... (Press Ctrl+C to stop)" -ForegroundColor Cyan
    Write-Host "--------------------------------------------------------------------------" -ForegroundColor DarkGray

    # 3. Persistent Monitoring Loop
    while ($true) {
        Start-Sleep -Seconds $HeartbeatIntervalSec
        
        # Heartbeat connection check
        $heartbeatOk = $false
        
        if ($useAdb) {
            # Check ADB devices
            $devices = & $adbPath devices 2>$null
            if ($devices -match "${mmiIp}:5555\s+device") {
                $heartbeatOk = $true
            }
        } else {
            # Try to connect to Telnet port to check if still open
            try {
                $tTest = New-Object System.Net.Sockets.TcpClient
                $tTest.Connect($mmiIp, 23)
                $tTest.Close()
                $heartbeatOk = $true
            } catch {
                $heartbeatOk = $false
            }
        }
        
        if (-not $heartbeatOk) {
            Write-Host "`n[!] Connection lost to MMI at ${mmiIp}!" -ForegroundColor Red
            # If the IP was auto-detected, clear it to scan the ARP table again (in case the IP changed on reconnect)
            if (-not $MmiAddress) {
                $mmiIp = ""
            }
            break # Exit inner monitoring loop to trigger reconnect/rescanning
        } else {
            # Print a periodic heartbeat dot to show active health
            Write-Host -NoNewline "." -ForegroundColor Green
        }
    }
}
