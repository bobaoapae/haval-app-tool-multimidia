# Windows PowerShell Script to Dynamically Resize Android Auto on Haval MMI (192.168.33.225)
# Supports direct parameter resize, preset layouts, interactive menu, and live real-time keyboard arrow controls!
#
# Usage:
#   .\Resize-AndroidAuto.ps1                           <-- Starts interactive GUI/menu with live keyboard controls
#   .\Resize-AndroidAuto.ps1 -X 160 -Width 1600       <-- Resizes to specific X and Width (keeps Y/H defaults)
#   .\Resize-AndroidAuto.ps1 -Preset "CenterWide"      <-- Instantly applies a preset layout

[CmdletBinding()]
param (
    [Parameter(Mandatory = $false)]
    [int]$X = -1,

    [Parameter(Mandatory = $false)]
    [int]$Y = -1,

    [Parameter(Mandatory = $false)]
    [int]$Width = -1,

    [Parameter(Mandatory = $false)]
    [int]$Height = -1,

    [Parameter(Mandatory = $false)]
    [ValidateSet("Full", "LeftHalf", "RightHalf", "LeftWide", "RightWide", "CenterWide", "CenterExtra", "DefaultYOffset")]
    [string]$Preset,

    [Parameter(Mandatory = $false)]
    [switch]$Poke,

    [Parameter(Mandatory = $false)]
    [switch]$Status,

    [Parameter(Mandatory = $false)]
    [string]$AdbDevice = "192.168.33.225:5555"
)

$ErrorActionPreference = "Stop"

# Clear Host Screen for clean UI
Clear-Host

Write-Host "==========================================================================" -ForegroundColor DarkGray
Write-Host "     ⚡ MMI Android Auto Sizing & Position Testing Utility ⚡" -ForegroundColor Green
Write-Host "==========================================================================" -ForegroundColor DarkGray

# 1. Resolve ADB Executable Path
$sdkDir = "C:\Users\vanes\AppData\Local\Android\Sdk"
$adbPath = Join-Path $sdkDir "platform-tools\adb.exe"

# Double check local.properties
$localProps = Join-Path $PSScriptRoot "local.properties"
if (Test-Path $localProps) {
    $sdkLine = Get-Content $localProps | Where-Object { $_ -match "^sdk\.dir=" }
    if ($sdkLine -and $sdkLine -match "sdk\.dir=(.+)") {
        # Clean escape chars from properties format
        $parsedPath = $Matches[1].Replace("\\", "\").Replace("\:", ":")
        $adbPath = Join-Path $parsedPath "platform-tools\adb.exe"
    }
}

if (-not (Test-Path $adbPath)) {
    # Fallback to system command path
    $adbPath = "adb"
}

Write-Host "🔍 Using ADB path: $adbPath" -ForegroundColor DarkGray

# 2. Check Connection
Write-Host "🔌 Checking connection to MMI ($AdbDevice)..." -ForegroundColor Cyan
& $adbPath connect $AdbDevice | Out-Null
Start-Sleep -Milliseconds 300

$devices = & $adbPath devices
if ($devices -match "$AdbDevice\s+device") {
    Write-Host "✅ MMI Connected successfully!" -ForegroundColor Green
} else {
    Write-Host "❌ Device $AdbDevice is not online or authorized." -ForegroundColor Red
    Write-Host "Please ensure you are on the MMI Wi-Fi and the screen is turned on." -ForegroundColor Yellow
    exit
}

# 3. Dynamic Stack Finder Helper
function Get-AndroidAutoStackInfo {
    $stacks = & $adbPath -s $AdbDevice shell am stack list
    $stackId = $null
    $currStack = $null
    $currDisplay = $null
    $currBounds = $null
    
    foreach ($line in ($stacks -split '\r?\n')) {
        # Match stack declaration
        if ($line -match "Stack id=(\d+).*displayId=(\d+)") {
            $currStack = [int]$Matches[1]
            $currDisplay = [int]$Matches[2]
            
            # Extract bounds if available: bounds=[0,62][1920,658]
            if ($line -match "bounds=\[(\d+),(\d+)\]\[(\d+),(\d+)\]") {
                $currBounds = @([int]$Matches[1], [int]$Matches[2], [int]$Matches[3], [int]$Matches[4])
            } else {
                $currBounds = $null
            }
        }
        
        # Match the Android Auto package name
        if ($line -match "taskId=(\d+):\s*com\.ts\.androidauto\.app/") {
            $taskId = [int]$Matches[1]
            
            # Check if task line has bounds instead of stack line
            if ($line -match "bounds=\[(\d+),(\d+)\]\[(\d+),(\d+)\]") {
                $currBounds = @([int]$Matches[1], [int]$Matches[2], [int]$Matches[3], [int]$Matches[4])
            }
            
            return [PSCustomObject]@{
                StackId   = $currStack
                TaskId    = $taskId
                DisplayId = $currDisplay
                Bounds    = $currBounds
            }
        }
    }
    return $null
}

# 4. Apply Resize Helper
function Apply-Resize {
    param(
        [int]$stackId,
        [int]$x,
        [int]$y,
        [int]$width,
        [int]$height
    )
    $right = $x + $width
    $bottom = $y + $height
    
    Write-Host "📐 Sending Resize Command: Stack $stackId -> X=$x, Y=$y, Width=$width, Height=$height (Bounds: [$x,$y][$right,$bottom])" -ForegroundColor Cyan
    $cmd = "am stack resize $stackId $x $y $right $bottom"
    $result = & $adbPath -s $AdbDevice shell $cmd
    if ($result -match "Exception" -or $result -match "Error") {
        Write-Host "⚠️ Warning/Error from am stack resize: $result" -ForegroundColor Yellow
    } else {
        Write-Host "🚀 Bounds updated successfully!" -ForegroundColor Green
    }
}

# 5. Video Focus Restoration Helper
function Send-FocusPoke {
    Write-Host "⚡ Restoring Android Auto focus broadcasts..." -ForegroundColor Yellow
    # Set video focus to android auto and kick start the service
    $focusCmd = "am broadcast -a com.ts.carplay.action.VIDEO_FOCUS_CHANGE --es `"focus`" `"com.ts.androidauto.app`" --ei `"displayId`" 3; am broadcast -a com.ts.androidauto.action.AndroidAutoService"
    $null = & $adbPath -s $AdbDevice shell $focusCmd
    Start-Sleep -Milliseconds 150
    # Re-launch AapActivity in secondary windowing mode to push to front
    $startCmd = "am start -n com.ts.androidauto.app/com.ts.androidauto.app.display.AapActivity --display 3 --windowingMode 5"
    $null = & $adbPath -s $AdbDevice shell $startCmd
}

# --- Query initial Android Auto state ---
$aaInfo = Get-AndroidAutoStackInfo

if (-not $aaInfo) {
    Write-Host "⚠️ com.ts.androidauto.app is not currently running in any stack!" -ForegroundColor Yellow
    Write-Host "🚀 Launching Android Auto on Cluster (Display 3)..." -ForegroundColor Cyan
    $launchCmd = "am start -n com.ts.androidauto.app/com.ts.androidauto.app.display.AapActivity --display 3 --windowingMode 5"
    & $adbPath -s $AdbDevice shell $launchCmd | Out-Null
    Start-Sleep -Seconds 2
    $aaInfo = Get-AndroidAutoStackInfo
    
    if (-not $aaInfo) {
        Write-Host "❌ Could not find or launch Android Auto stack." -ForegroundColor Red
        Write-Host "Please open Android Auto on your MMI screen first, then run this script again." -ForegroundColor Yellow
        exit
    }
}

Write-Host "ℹ️ Current Android Auto State:" -ForegroundColor Green
Write-Host "  - Stack ID:   $($aaInfo.StackId)" -ForegroundColor White
Write-Host "  - Task ID:    $($aaInfo.TaskId)" -ForegroundColor White
Write-Host "  - Display ID: $($aaInfo.DisplayId)" -ForegroundColor White
if ($aaInfo.Bounds) {
    $curW = $aaInfo.Bounds[2] - $aaInfo.Bounds[0]
    $curH = $aaInfo.Bounds[3] - $aaInfo.Bounds[1]
    Write-Host "  - Bounds:     [$($aaInfo.Bounds[0]), $($aaInfo.Bounds[1])][$($aaInfo.Bounds[2]), $($aaInfo.Bounds[3])] (Width: $curW, Height: $curH)" -ForegroundColor White
} else {
    Write-Host "  - Bounds:     Fullscreen/Unknown" -ForegroundColor White
}
Write-Host "==========================================================================" -ForegroundColor DarkGray

# If only -Status was requested
if ($Status) {
    exit
}

# If only -Poke focus was requested
if ($Poke) {
    Send-FocusPoke
    exit
}

# Initialize defaults
$defX = 0
$defY = 62
$defW = 1920
$defH = 596 # 658 - 62

if ($aaInfo.Bounds) {
    $defX = $aaInfo.Bounds[0]
    $defY = $aaInfo.Bounds[1]
    $defW = $aaInfo.Bounds[2] - $aaInfo.Bounds[0]
    $defH = $aaInfo.Bounds[3] - $aaInfo.Bounds[1]
}

# Apply direct arguments if provided
if ($X -ge 0 -or $Y -ge 0 -or $Width -gt 0 -or $Height -gt 0) {
    $finalX = if ($X -ge 0) { $X } else { $defX }
    $finalY = if ($Y -ge 0) { $Y } else { $defY }
    $finalW = if ($Width -gt 0) { $Width } else { $defW }
    $finalH = if ($Height -gt 0) { $Height } else { $defH }
    
    Apply-Resize -stackId $aaInfo.StackId -x $finalX -y $finalY -width $finalW -height $finalH
    exit
}

# Apply presets directly if provided
if ($Preset) {
    switch ($Preset) {
        "Full" {
            Apply-Resize -stackId $aaInfo.StackId -x 0 -y 62 -width 1920 -height 596
        }
        "LeftHalf" {
            Apply-Resize -stackId $aaInfo.StackId -x 0 -y 62 -width 960 -height 596
        }
        "RightHalf" {
            Apply-Resize -stackId $aaInfo.StackId -x 960 -y 62 -width 960 -height 596
        }
        "LeftWide" {
            Apply-Resize -stackId $aaInfo.StackId -x 0 -y 62 -width 1280 -height 596
        }
        "RightWide" {
            Apply-Resize -stackId $aaInfo.StackId -x 640 -y 62 -width 1280 -height 596
        }
        "CenterWide" {
            Apply-Resize -stackId $aaInfo.StackId -x 160 -y 62 -width 1600 -height 596
        }
        "CenterExtra" {
            Apply-Resize -stackId $aaInfo.StackId -x 260 -y 62 -width 1400 -height 596
        }
    }
    exit
}

# Helper: Interactive Live Keyboard arrow controller
function Start-LiveKeyboardController {
    param(
        [int]$stackId,
        [int]$initialX,
        [int]$initialY,
        [int]$initialW,
        [int]$initialH
    )
    
    $liveX = $initialX
    $liveY = $initialY
    $liveW = $initialW
    $liveH = $initialH
    $step = 10 # Pixels to change per key press
    
    Clear-Host
    Write-Host "==========================================================================" -ForegroundColor DarkGray
    Write-Host "          🎮 Real-time Keyboard Arrow-Key Controller Mode 🎮" -ForegroundColor Green
    Write-Host "==========================================================================" -ForegroundColor DarkGray
    Write-Host " Keep this window focused and press keys to resize MMI screen live:" -ForegroundColor DarkGray
    Write-Host ""
    Write-Host "  [Left / Right Arrow]  -> Adjust X Position (Moves AA screen left/right)" -ForegroundColor Cyan
    Write-Host "  [Up / Down Arrow]     -> Adjust WIDTH (Expands or contracts horizontally)" -ForegroundColor Cyan
    Write-Host "  [PageUp / PageDown]   -> Adjust Y Position (Moves AA screen up/down)" -ForegroundColor Yellow
    Write-Host "  [ + / - (Plus/Minus)] -> Adjust HEIGHT (Expands or contracts vertically)" -ForegroundColor Yellow
    Write-Host "  [F Key]               -> Force Video Focus Poke (fixes blank screens)" -ForegroundColor Green
    Write-Host "  [R Key]               -> Reset to Default (0, 62, 1920, 596)" -ForegroundColor DarkGray
    Write-Host "  [ESC or ENTER]        -> Finish & return to Main Menu" -ForegroundColor White
    Write-Host "==========================================================================" -ForegroundColor DarkGray
    Write-Host ""
    
    Write-Host "Current Coordinates: X=$liveX, Y=$liveY, Width=$liveW, Height=$liveH" -ForegroundColor Magenta
    Write-Host "Adjusting... (press keys now)" -ForegroundColor DarkGray
    
    while ($true) {
        if ($Host.UI.RawUI.KeyAvailable) {
            $keyInfo = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
            $key = $keyInfo.VirtualKeyCode
            $changed = $false
            $pokeFocus = $false
            
            switch ($key) {
                37 { # Left Arrow -> Decrease X
                    $liveX = [Math]::Max(0, $liveX - $step)
                    $changed = $true
                }
                39 { # Right Arrow -> Increase X
                    $liveX = [Math]::Min(1920 - 100, $liveX + $step)
                    $changed = $true
                }
                38 { # Up Arrow -> Increase Width
                    $liveW = [Math]::Min(1920 - $liveX, $liveW + $step)
                    $changed = $true
                }
                40 { # Down Arrow -> Decrease Width
                    $liveW = [Math]::Max(100, $liveW - $step)
                    $changed = $true
                }
                33 { # Page Up -> Decrease Y
                    $liveY = [Math]::Max(0, $liveY - $step)
                    $changed = $true
                }
                34 { # Page Down -> Increase Y
                    $liveY = [Math]::Min(720 - 100, $liveY + $step)
                    $changed = $true
                }
                107 { # Numpad + -> Increase Height
                    $liveH = [Math]::Min(720 - $liveY, $liveH + $step)
                    $changed = $true
                }
                187 { # Plus/Equals key
                    $liveH = [Math]::Min(720 - $liveY, $liveH + $step)
                    $changed = $true
                }
                109 { # Numpad - -> Decrease Height
                    $liveH = [Math]::Max(50, $liveH - $step)
                    $changed = $true
                }
                189 { # Minus/Underscore key
                    $liveH = [Math]::Max(50, $liveH - $step)
                    $changed = $true
                }
                70 { # F Key
                    $pokeFocus = $true
                }
                82 { # R Key -> Reset
                    $liveX = 0
                    $liveY = 62
                    $liveW = 1920
                    $liveH = 596
                    $changed = $true
                }
                13 { # Enter
                    break
                }
                27 { # Escape
                    break
                }
            }
            
            if ($changed) {
                $right = $liveX + $liveW
                $bottom = $liveY + $liveH
                Write-Host "`r[LIVE UPDATE] X=$liveX, Y=$liveY, W=$liveW, H=$liveH (Bounds: [$liveX,$liveY][$right,$bottom])      " -NoNewline -ForegroundColor Cyan
                $cmd = "am stack resize $stackId $liveX $liveY $right $bottom"
                $null = & $adbPath -s $AdbDevice shell $cmd
            }
            
            if ($pokeFocus) {
                Write-Host "`r[FOCUS POKE] Sending broadcasts & restarting activity...             " -NoNewline -ForegroundColor Yellow
                Send-FocusPoke
                Write-Host "`r[FOCUS POKE] Done! Resume keyboard controls.                     " -NoNewline -ForegroundColor Yellow
            }
        }
        Start-Sleep -Milliseconds 30
    }
    Write-Host "`nLive mode exited. Saved final coordinates: X=$liveX, Y=$liveY, W=$liveW, H=$liveH" -ForegroundColor Green
    return @($liveX, $liveY, $liveW, $liveH)
}

# --- Main Interactive Menu Loop ---
$currentX = $defX
$currentY = $defY
$currentW = $defW
$currentH = $defH

while ($true) {
    Write-Host ""
    Write-Host "Select Sizing Test Option:" -ForegroundColor Yellow
    Write-Host "  [1] Live Keyboard Arrow Controls (Interactive Sizing)" -ForegroundColor Green
    Write-Host "  [2] Apply Predefined Layout Presets..." -ForegroundColor Cyan
    Write-Host "  [3] Set Manual Coordinates (Prompt X, Y, Width, Height)" -ForegroundColor Yellow
    Write-Host "  [4] Trigger Force Video Focus Poke" -ForegroundColor Green
    Write-Host "  [5] Reset to MMI Default Sizing (0, 62, 1920, 596)" -ForegroundColor Magenta
    Write-Host "  [Q] Exit Sizing Utility" -ForegroundColor Red
    Write-Host ""
    $opt = Read-Host "Enter option number"
    
    switch ($opt) {
        "1" {
            $coords = Start-LiveKeyboardController -stackId $aaInfo.StackId -initialX $currentX -initialY $currentY -initialW $currentW -initialH $currentH
            $currentX = $coords[0]
            $currentY = $coords[1]
            $currentW = $coords[2]
            $currentH = $coords[3]
        }
        "2" {
            Clear-Host
            Write-Host "Select Preset Layout:" -ForegroundColor Cyan
            Write-Host "  [1] Full Screen (1920 x 596)"
            Write-Host "  [2] Left Half (960 x 596)"
            Write-Host "  [3] Right Half (960 x 596, starting at X=960)"
            Write-Host "  [4] Left Wide (1280 x 596)"
            Write-Host "  [5] Right Wide (1280 x 596, starting at X=640)"
            Write-Host "  [6] Center Wide (1600 x 596, X=160)"
            Write-Host "  [7] Center Custom (1400 x 596, X=260)"
            Write-Host "  [B] Back to Main Menu"
            Write-Host ""
            $presetOpt = Read-Host "Choose preset"
            
            switch ($presetOpt) {
                "1" { $currentX=0;   $currentY=62; $currentW=1920; $currentH=596 }
                "2" { $currentX=0;   $currentY=62; $currentW=960;  $currentH=596 }
                "3" { $currentX=960; $currentY=62; $currentW=960;  $currentH=596 }
                "4" { $currentX=0;   $currentY=62; $currentW=1280; $currentH=596 }
                "5" { $currentX=640; $currentY=62; $currentW=1280; $currentH=596 }
                "6" { $currentX=160; $currentY=62; $currentW=1600; $currentH=596 }
                "7" { $currentX=260; $currentY=62; $currentW=1400; $currentH=596 }
                Default { continue }
            }
            Apply-Resize -stackId $aaInfo.StackId -x $currentX -y $currentY -width $currentW -height $currentH
        }
        "3" {
            $inputX = Read-Host "Enter X Position (0-1920, default $currentX)"
            $inputY = Read-Host "Enter Y Position (0-720, default $currentY)"
            $inputW = Read-Host "Enter Width (100-1920, default $currentW)"
            $inputH = Read-Host "Enter Height (50-720, default $currentH)"
            
            if (-not [string]::IsNullOrWhiteSpace($inputX)) { $currentX = [int]$inputX }
            if (-not [string]::IsNullOrWhiteSpace($inputY)) { $currentY = [int]$inputY }
            if (-not [string]::IsNullOrWhiteSpace($inputW)) { $currentW = [int]$inputW }
            if (-not [string]::IsNullOrWhiteSpace($inputH)) { $currentH = [int]$inputH }
            
            Apply-Resize -stackId $aaInfo.StackId -x $currentX -y $currentY -width $currentW -height $currentH
        }
        "4" {
            Send-FocusPoke
        }
        "5" {
            $currentX = 0
            $currentY = 62
            $currentW = 1920
            $currentH = 596
            Apply-Resize -stackId $aaInfo.StackId -x $currentX -y $currentY -width $currentW -height $currentH
        }
        "q" {
            break
        }
        "Q" {
            break
        }
    }
}

Write-Host "✨ Exited Sizing Utility. Happy testing!" -ForegroundColor Green
