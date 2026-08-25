<#
.SYNOPSIS
    Read-only snapshot of the Car MMI's network state. Changes nothing on the head unit.
.DESCRIPTION
    Share-Internet-And-Connect.ps1 has to decide which interface is the UPLINK (the head
    unit's link to the phone hotspot) and which is the AP serving this PC. Guessing that
    wrong is what takes the car offline: the default route gets pointed at the AP side, and
    since this PC's gateway IS the car, the PC loses internet at the same moment.

    Run this BEFORE running the sharing script, while everything still works. The output
    shows which interface actually owns the default route, so the guess can be replaced
    with a fact.

    Every command here is read-only: ip/iptables list operations and getprop. There is no
    route change, no iptables insert, no file written to the head unit.
.PARAMETER MmiAddress
    Car MMI IP. Auto-detected when omitted.
.PARAMETER OutFile
    Where to save the transcript. Defaults to a timestamped file next to this script.
.EXAMPLE
    .\Diagnose-Car-Network.ps1
#>
[CmdletBinding()]
param (
    [Parameter(Mandatory = $false)]
    [string]$MmiAddress = "",

    [Parameter(Mandatory = $false)]
    [string]$OutFile = ""
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================================" -ForegroundColor DarkGray
Write-Host "     Car MMI Network Diagnostics (read-only - changes nothing)" -ForegroundColor Green
Write-Host "==========================================================================" -ForegroundColor DarkGray

if (-not $OutFile) {
    $OutFile = Join-Path $PSScriptRoot ("car-network-{0}.txt" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
}

# --- Resolve ADB -----------------------------------------------------------
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
if (-not (Test-Path $adbPath)) { $adbPath = "adb" }

# --- Report this PC's side first ------------------------------------------
# If the car is our gateway, its AP address is already sitting in the route table.
$pcReport = @()
$pcReport += "===== THIS PC ====="
$pcReport += (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike '127.*' } |
    Select-Object InterfaceAlias, IPAddress, PrefixLength | Format-Table -AutoSize | Out-String -Width 120)
$pcReport += "----- default routes -----"
$pcReport += (Get-NetRoute -AddressFamily IPv4 -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue |
    Select-Object InterfaceAlias, NextHop, RouteMetric | Format-Table -AutoSize | Out-String -Width 120)
$pcReport += "----- arp cache -----"
$pcReport += ((arp -a) -join "`n")

$pcReport -join "`n" | Write-Host

# --- Find the car ----------------------------------------------------------
$mmiIp = $MmiAddress

if (-not $mmiIp) {
    Write-Host "`n[i] Looking for the Car MMI..." -ForegroundColor Cyan
    $candidates = @()

    # The car is this PC's default gateway when we are joined to its AP - try that first.
    foreach ($r in (Get-NetRoute -AddressFamily IPv4 -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue)) {
        if ($r.NextHop -and $r.NextHop -ne '0.0.0.0') { $candidates += $r.NextHop }
    }
    # Then anything already in the ARP cache (no sweeping - costs nothing, disturbs nothing).
    foreach ($line in (arp -a)) {
        if ($line -match 'Interface') { continue }
        if ($line -match '(\d{1,3}\.\d{1,3}\.\d{1,3}\.(\d{1,3}))') {
            $ip = $Matches[1]
            $last = $Matches[2]
            if ($last -ne "255" -and $last -ne "0" -and $ip -notlike '224.*' -and $ip -notlike '239.*') {
                $candidates += $ip
            }
        }
    }
    $candidates = @($candidates | Select-Object -Unique)

    foreach ($testIp in $candidates) {
        foreach ($port in @(5555, 23)) {
            $t = New-Object System.Net.Sockets.TcpClient
            try {
                $a = $t.BeginConnect($testIp, $port, $null, $null)
                if ($a.AsyncWaitHandle.WaitOne(400)) {
                    $t.EndConnect($a)
                    $mmiIp = $testIp
                    Write-Host "[+] Found a candidate at ${testIp} (port $port open)" -ForegroundColor Green
                }
            } catch { }
            $t.Close()
            if ($mmiIp) { break }
        }
        if ($mmiIp) { break }
    }
}

if (-not $mmiIp) {
    Write-Host "`n[-] Could not find the Car MMI." -ForegroundColor Red
    Write-Host "    Join the head unit's Wi-Fi and retry, or pass -MmiAddress <ip>." -ForegroundColor Yellow
    $pcReport -join "`n" | Set-Content -Path $OutFile -Encoding utf8
    Write-Host "    PC-side snapshot saved to $OutFile" -ForegroundColor DarkGray
    exit 1
}

# --- Connect ---------------------------------------------------------------
$ErrorActionPreference = "Continue"
$useAdb = $false

Write-Host "`n[*] Connecting to ${mmiIp}..." -ForegroundColor Cyan
$null = & $adbPath connect "${mmiIp}:5555" 2>&1
Start-Sleep -Milliseconds 500
$devices = & $adbPath devices 2>&1
if ($devices -match "${mmiIp}:5555\s+device") {
    $useAdb = $true
    Write-Host "  [+] ADB online." -ForegroundColor Green
} else {
    Write-Host "  [!] ADB offline - falling back to Telnet." -ForegroundColor Yellow
}

# Read-only probes only. Nothing below modifies the head unit.
$probes = @(
    @{ Label = "interfaces + addresses";        Cmd = "ip addr show" },
    @{ Label = "link state";                    Cmd = "ip -o link show" },
    @{ Label = "routes: ALL tables";            Cmd = "ip route show table all" },
    @{ Label = "routes: main table";            Cmd = "ip route show table main" },
    @{ Label = "policy rules";                  Cmd = "ip rule show" },
    @{ Label = "neighbours (arp)";              Cmd = "ip neigh show" },
    @{ Label = "ip_forward";                    Cmd = "cat /proc/sys/net/ipv4/ip_forward" },
    @{ Label = "iptables filter";               Cmd = "iptables -S" },
    @{ Label = "iptables nat";                  Cmd = "iptables -t nat -S" },
    @{ Label = "wifi/dhcp properties";          Cmd = 'getprop | grep -i -E ''wlan|dhcp|softap|tether''' },
    @{ Label = "uplink the script would pick";  Cmd = 'ip route show table all 2>/dev/null | awk ''$1=="default"{dev="";gw="";for(i=1;i<=NF;i++){if($i=="dev")dev=$(i+1); if($i=="via")gw=$(i+1)} if(dev!="" && gw!="") print dev" via "gw}''' }
)

$report = @()
$report += $pcReport
$report += ""
$report += "===== CAR MMI ($mmiIp) ====="
$report += "collected: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"

if ($useAdb) {
    $idOut = (& $adbPath -s "${mmiIp}:5555" shell id 2>&1) -join " "
    $report += "shell identity: $idOut"
    if ($idOut -notmatch "uid=0\(root\)") {
        Write-Host "  [!] Shell is NOT root - iptables output will be empty or partial." -ForegroundColor Yellow
    }

    foreach ($p in $probes) {
        Write-Host "  -> $($p.Label)" -ForegroundColor DarkGray
        $out = (& $adbPath -s "${mmiIp}:5555" shell $p.Cmd 2>&1) -join "`n"
        $report += ""
        $report += "----- $($p.Label) -----"
        $report += "`$ $($p.Cmd)"
        $report += $out
    }
} else {
    try {
        $client = New-Object System.Net.Sockets.TcpClient($mmiIp, 23)
    } catch {
        Write-Host "[-] Telnet also unreachable. Nothing collected from the car." -ForegroundColor Red
        $report -join "`n" | Set-Content -Path $OutFile -Encoding utf8
        exit 1
    }
    $stream = $client.GetStream()
    $writer = New-Object System.IO.StreamWriter($stream)
    $writer.AutoFlush = $true

    function Read-All {
        $buffer = New-Object byte[] 16384
        $out = ""
        Start-Sleep -Milliseconds 400
        while ($stream.DataAvailable) {
            $read = $stream.Read($buffer, 0, $buffer.Length)
            $out += [System.Text.Encoding]::ASCII.GetString($buffer, 0, $read)
            Start-Sleep -Milliseconds 100
        }
        return $out
    }

    $null = Read-All
    foreach ($p in $probes) {
        Write-Host "  -> $($p.Label)" -ForegroundColor DarkGray
        $writer.WriteLine($p.Cmd)
        $out = Read-All
        $report += ""
        $report += "----- $($p.Label) -----"
        $report += "`$ $($p.Cmd)"
        $report += ($out -replace "`r", "")
    }
    $client.Close()
}

$report -join "`n" | Set-Content -Path $OutFile -Encoding utf8

Write-Host "`n==========================================================================" -ForegroundColor DarkGray
Write-Host "[+] Snapshot saved to:" -ForegroundColor Green
Write-Host "    $OutFile" -ForegroundColor Yellow
Write-Host "`n    Nothing on the head unit was modified." -ForegroundColor DarkGray
Write-Host "    The 'uplink the script would pick' section is the key one - it must name" -ForegroundColor DarkGray
Write-Host "    the interface facing the PHONE, not the one serving this PC." -ForegroundColor DarkGray
Write-Host "==========================================================================" -ForegroundColor DarkGray
