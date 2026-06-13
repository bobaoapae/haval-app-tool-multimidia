<#
.SYNOPSIS
    Detects and returns the IP address of the Haval Car MMI (192.168.33.x).
.DESCRIPTION
    Scans the ARP table for potential candidates in the 192.168.33.x range.
    Optionally pings the subnet to refresh the ARP table, and validates
    activity by checking ports 5555 (ADB) and 23 (Telnet).
.PARAMETER Raw
    If specified, only outputs the IP address (or nothing if not found),
    making it ideal for script consumption: $ip = .\scripts\Get-Car-IP.ps1 -Raw
.PARAMETER Quick
    If specified, skips refreshing the ARP table and only scans the current cache.
#>
[CmdletBinding()]
param(
    [switch]$Raw,
    [switch]$Quick
)

$ErrorActionPreference = "SilentlyContinue"

# 1. Optionally refresh ARP cache (unless -Quick is specified)
if (-not $Quick) {
    if (-not $Raw) {
        Write-Host "Refreshing ARP cache..." -ForegroundColor DarkGray
    }
    # Fast parallel ping to refresh ARP cache
    $ips = 1..254 | ForEach-Object { "192.168.33.$_" }
    $tasks = @()
    foreach ($ip in $ips) {
        try {
            $ping = New-Object System.Net.NetworkInformation.Ping
            $tasks += $ping.SendPingAsync($ip, 150)
        } catch {}
    }
    if ($tasks) {
        [System.Threading.Tasks.Task]::WaitAll($tasks) | Out-Null
    }
}

# 2. Parse ARP table
$arp = arp -a
$candidates = @()
foreach ($line in $arp) {
    # Skip interface headers to avoid picking up the computer's own IP
    if ($line -match 'Interface') { continue }
    if ($line -match '192\.168\.33\.(\d{1,3})') {
        $lastOctet = $Matches[1]
        # Exclude gateway (.1), broadcast (.255), and loopbacks/broadcasts
        if ($lastOctet -ne "255" -and $lastOctet -ne "1" -and $lastOctet -ne "0") {
            $candidates += "192.168.33.$lastOctet"
        }
    }
}
$candidates = $candidates | Select-Object -Unique

# 3. Test candidates on Ports 5555 (ADB) and 23 (Telnet)
$carIp = ""
$activePort = 0

foreach ($ip in $candidates) {
    $isActive = $false
    foreach ($port in @(5555, 23)) {
        try {
            $t = New-Object System.Net.Sockets.TcpClient
            $async = $t.BeginConnect($ip, $port, $null, $null)
            if ($async.AsyncWaitHandle.WaitOne(150)) {
                $t.EndConnect($async)
                $isActive = $true
                $activePort = $port
            }
            $t.Close()
        } catch {}
        if ($isActive) { break }
    }
    if ($isActive) {
        $carIp = $ip
        break
    }
}

# 4. Output results
if ($carIp) {
    if ($Raw) {
        Write-Output $carIp
    } else {
        $service = if ($activePort -eq 5555) { "ADB" } else { "Telnet" }
        Write-Host "[+] Found Car MMI at " -NoNewline -ForegroundColor Green
        Write-Host $carIp -NoNewline -ForegroundColor Yellow
        Write-Host " ($service active)" -ForegroundColor Green
    }
    exit 0
} else {
    if (-not $Raw) {
        Write-Host "[-] Car MMI not detected on the 192.168.33.x subnet." -ForegroundColor Red
        Write-Host "    Make sure you are connected to the car's Wi-Fi network." -ForegroundColor Yellow
    }
    exit 1
}
