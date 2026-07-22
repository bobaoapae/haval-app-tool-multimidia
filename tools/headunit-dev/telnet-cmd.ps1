param(
    [Parameter(Mandatory=$true)][string]$Command,
    [string]$HostIp = "10.104.228.72",
    [int]$Port = 23,
    [int]$WaitSeconds = 5
)

$client = New-Object System.Net.Sockets.TcpClient($HostIp, $Port)
$stream = $client.GetStream()
$writer = New-Object System.IO.StreamWriter($stream)
$writer.NewLine = "`n"
$writer.AutoFlush = $true

Start-Sleep -Milliseconds 500

# Drain any banner/prompt
$buffer = New-Object byte[] 4096
while ($stream.DataAvailable) {
    $read = $stream.Read($buffer, 0, $buffer.Length)
    if ($read -le 0) { break }
}

$writer.WriteLine($Command)

$deadline = (Get-Date).AddSeconds($WaitSeconds)
$output = New-Object System.Text.StringBuilder
while ((Get-Date) -lt $deadline) {
    if ($stream.DataAvailable) {
        $read = $stream.Read($buffer, 0, $buffer.Length)
        if ($read -gt 0) {
            $text = [System.Text.Encoding]::ASCII.GetString($buffer, 0, $read)
            [void]$output.Append($text)
        }
    } else {
        Start-Sleep -Milliseconds 200
    }
}

Write-Output $output.ToString()

$writer.Close()
$stream.Close()
$client.Close()
