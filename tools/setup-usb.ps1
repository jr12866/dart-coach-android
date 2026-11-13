#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Prepare the Windows machine to talk to the phone over USB.

.DESCRIPTION
    Installs the Google USB driver and checks that adb is working. The
    driver install needs administrator rights, so it is done in a separate,
    elevated process.

    Run this once. After that, plugging the phone in is enough.

    What is checked:
      * the Google USB driver is downloaded and installed
      * the platform-tools are present and on PATH
      * adb sees the phone

.PARAMETER CheckOnly
    Do not install anything. Just report what is missing.
#>
[CmdletBinding()]
param(
    [switch] $CheckOnly
)

$ErrorActionPreference = 'Continue'

$SdkRoot = if ($env:ANDROID_HOME) {
    $env:ANDROID_HOME
} else {
    Join-Path $env:LOCALAPPDATA 'Android/Sdk'
}

$adb = Join-Path $SdkRoot 'platform-tools/adb.exe'
$driverRoot = Join-Path $SdkRoot 'extras/google/usb_driver/usb_driver'
$inf = Join-Path $driverRoot 'android_winusb.inf'
$driverZip = Join-Path $env:TEMP 'usb_driver_r13-windows.zip'
$driverUrl = 'https://dl.google.com/android/repository/usb_driver_r13-windows.zip'

Write-Host "=== adb ==="
if (Test-Path $adb) {
    Write-Host "  found: $adb"
} else {
    Write-Host "  MISSING: $adb"
    Write-Host "  Install it with: sdkmanager 'platform-tools'"
}

Write-Host ""
Write-Host "=== Google USB driver ==="
if (Test-Path $inf) {
    Write-Host "  downloaded: $inf"
} else {
    if ($CheckOnly) {
        Write-Host "  MISSING: $inf"
    } else {
        Write-Host "  downloading..."
        try {
            Invoke-WebRequest -Uri $driverUrl -OutFile $driverZip -UseBasicParsing
            Add-Type -AssemblyName System.IO.Compression.FileSystem
            $dest = Join-Path $SdkRoot 'extras/google/usb_driver'
            New-Item -ItemType Directory -Path $dest -Force | Out-Null
            [System.IO.Compression.ZipFile]::ExtractToDirectory($driverZip, $dest)
            Write-Host "  downloaded"
        } catch {
            Write-Host "  FAILED: $($_.Exception.Message)"
        }
    }
}

if ((Test-Path $inf) -and -not $CheckOnly) {
    Write-Host ""
    Write-Host "=== installing the driver (needs administrator) ==="
    # pnputil needs elevation, so it is run in a separate elevated process.
    $args = @('/add-driver', "`"$inf`"", '/install')
    try {
        $proc = Start-Process -FilePath 'pnputil.exe' -ArgumentList $args -Verb RunAs -Wait -PassThru -NoNewWindow
        if ($proc.ExitCode -eq 0) {
            Write-Host "  installed"
        } else {
            Write-Host "  pnputil exited with $($proc.ExitCode)"
        }
    } catch {
        Write-Host "  Could not elevate. Run this by hand as Administrator:"
        Write-Host "    pnputil /add-driver `"$inf`" /install"
    }
}

Write-Host ""
Write-Host "=== devices ==="
if (Test-Path $adb) {
    & $adb start-server 2>&1 | Out-Null
    $devices = & $adb devices -l 2>&1
    Write-Host $devices
    if ($devices -notmatch 'device$|device product') {
        Write-Host ""
        Write-Host "No phone yet. When you plug it in:"
        Write-Host ""
        Write-Host "  1. Unlock the phone and accept the 'Allow USB debugging?' prompt."
        Write-Host "     Tick 'Always allow from this computer'."
        Write-Host "  2. If it is not offered, change the USB mode to 'File transfer'."
        Write-Host "     On a Pixel 7, swipe down twice for Quick Settings, then"
        Write-Host "     tap the USB notification and pick File transfer."
        Write-Host "  3. Developer options > USB debugging must be on."
        Write-Host ""
        Write-Host "  If Windows cannot find a driver, it will offer to install one."
        Write-Host "  Point it at $driverRoot"
    }
}