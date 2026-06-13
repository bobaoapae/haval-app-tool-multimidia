$MmiIp = "192.168.33.202"
try {
    $client = New-Object System.Net.Sockets.TcpClient($MmiIp, 23)
    $stream = $client.GetStream()
    $writer = New-Object System.IO.StreamWriter($stream)
    $writer.AutoFlush = $true
    
    Start-Sleep -Milliseconds 500
    # Clear initial buffer
    $buffer = New-Object byte[] 8192
    while ($stream.DataAvailable) {
        $read = $stream.Read($buffer, 0, $buffer.Length)
    }
    
    Write-Host "Starting front-end CarPlay activity..."
    $writer.WriteLine("am start -n com.ts.carplay.app/com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity")
    Start-Sleep -Seconds 2
    
    $out = ""
    while ($stream.DataAvailable) {
        $read = $stream.Read($buffer, 0, $buffer.Length)
        $out += [System.Text.Encoding]::ASCII.GetString($buffer, 0, $read)
    }
    
    $client.Close()
    Write-Host "Telnet Output:"
    Write-Host ($out -replace "`r", "")
} catch {
    Write-Error "Failed: $_"
}
