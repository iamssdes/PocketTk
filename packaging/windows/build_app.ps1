# Builds the Windows app: dist\PocketTk-<version>-windows-<arch>.zip (portable) and ...-setup.exe (installer, if Inno Setup is there).
#     powershell -ExecutionPolicy Bypass -File packaging\windows\build_app.ps1 [-Arch x64|arm64]
# Needs: Windows, PowerShell 5+, Python 3 on PATH (only to read the version and draw the icon), internet.
#
# Inside: a relocatable CPython (python-build-standalone) with pywebview + certifi, and PocketTk itself. pywebview shows the
# window with Microsoft Edge WebView2 (part of Windows 11 and of current Windows 10; the installer does not add it).
# NOT inside: a JDK and the Android SDK. The app downloads those on its first run (you accept Google's license there).
param([string]$Arch = "x64", [string]$PySeries = "3.12")
$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$Here = $PSScriptRoot
switch ($Arch) {
  "x64"   { $Triple = "x86_64-pc-windows-msvc" }
  "arm64" { $Triple = "aarch64-pc-windows-msvc" }
  default { throw "unknown architecture: $Arch (x64 or arm64)" }
}
$line = Select-String -Path (Join-Path $Root "pockettk.py") -Pattern '^VERSION = "PocketTk v(.*)"' | Select-Object -First 1
if (-not $line) { throw "cannot read the version from pockettk.py" }
$Version = $line.Matches[0].Groups[1].Value
$Dist = Join-Path $Root "dist"; $Cache = Join-Path $Dist "cache"
$Out = Join-Path $Dist "windows-$Arch"
$Stage = Join-Path $Out "PocketTk"
if (Test-Path $Out) { Remove-Item -Recurse -Force $Out }
New-Item -ItemType Directory -Force -Path $Cache, $Stage | Out-Null
Write-Host "== PocketTk $Version for Windows $Arch"

# ---- 1. the Python
$tgz = Join-Path $Cache "python-$PySeries-$Triple.tar.gz"
if (-not (Test-Path $tgz)) {
  Write-Host "== finding python-build-standalone $PySeries ($Triple)"
  $h = @{ "User-Agent" = "PocketTk" }
  if ($env:GITHUB_TOKEN) { $h["Authorization"] = "Bearer $($env:GITHUB_TOKEN)" }
  $rel = Invoke-RestMethod -Headers $h -Uri "https://api.github.com/repos/astral-sh/python-build-standalone/releases/latest"
  $pat = "^cpython-$([regex]::Escape($PySeries))\.\d+\+\d+-$([regex]::Escape($Triple))-install_only\.tar\.gz$"
  $hit = $rel.assets | Where-Object { $_.name -match $pat } | Sort-Object name | Select-Object -Last 1
  if (-not $hit) { throw "no python-build-standalone asset for $PySeries/$Triple in the latest release" }
  Write-Host "== downloading $($hit.browser_download_url)"
  Invoke-WebRequest -Headers $h -Uri $hit.browser_download_url -OutFile "$tgz.part"
  Move-Item "$tgz.part" $tgz
}
$tmp = Join-Path $Out "_py"; New-Item -ItemType Directory -Force -Path $tmp | Out-Null
tar -xzf $tgz -C $tmp                                   # tar.exe ships with Windows 10 1803+; unpacks to python\
if ($LASTEXITCODE -ne 0) { throw "could not unpack $tgz" }
Move-Item (Join-Path $tmp "python") (Join-Path $Stage "python")
Remove-Item -Recurse -Force $tmp
$py = Join-Path $Stage "python\python.exe"
if (-not (Test-Path (Join-Path $Stage "python\pythonw.exe"))) { throw "unexpected layout: no python\pythonw.exe" }
& $py -I -c "import sys; print('prefix:', sys.prefix)"

# ---- 2. pywebview and certifi
Write-Host "== installing pywebview and certifi"
& $py -m pip install --disable-pip-version-check --no-warn-script-location --no-compile pywebview certifi
if ($LASTEXITCODE -ne 0) { throw "pip install failed" }
foreach ($d in "test", "idlelib", "turtledemo") {
  $p = Join-Path $Stage "python\Lib\$d"; if (Test-Path $p) { Remove-Item -Recurse -Force $p }
}

# ---- 3. PocketTk itself
Write-Host "== copying PocketTk"
$app = Join-Path $Stage "app"; New-Item -ItemType Directory -Force -Path $app | Out-Null
foreach ($f in "pockettk.py","pockettk_gui.py","pockettk_deps.py","pockettk_preview.py","pockettk_ipa.py","transpile.py","LICENSE","README.md","CHANGELOG.md") {
  $s = Join-Path $Root $f; if (Test-Path $s) { Copy-Item $s $app }
}
foreach ($d in "rt","preview","ipa","webui","demo") {
  $s = Join-Path $Root $d; if (Test-Path $s) { Copy-Item -Recurse $s (Join-Path $app $d) }
}
Get-ChildItem -Recurse -Force -Directory -Filter "__pycache__" $Stage | Remove-Item -Recurse -Force

# ---- 4. icon, launcher, notes
python (Join-Path $Here "make_ico.py") (Join-Path $Stage "PocketTk.ico")
if ($LASTEXITCODE -ne 0) { throw "could not make the icon" }
@'
@echo off
rem Starts the PocketTk window (no console). Double-click this file, or use the shortcut the installer makes.
set PYTHONNOUSERSITE=1
set PYTHONDONTWRITEBYTECODE=1
start "" "%~dp0python\pythonw.exe" "%~dp0app\pockettk_gui.py" %*
'@ | Set-Content -Encoding ASCII (Join-Path $Stage "PocketTk.cmd")
@'
@echo off
rem The command line tool, with the same Python:   pockettk build app.py   /   pockettk doctor   /   pockettk gui
set PYTHONNOUSERSITE=1
set PYTHONDONTWRITEBYTECODE=1
"%~dp0python\python.exe" "%~dp0app\pockettk.py" %*
'@ | Set-Content -Encoding ASCII (Join-Path $Stage "pockettk.cmd")
Copy-Item (Join-Path $Here "READ ME FIRST.txt") $Stage

# ---- 5. does the finished app work? (no window opens)
Write-Host "== self-test"
$env:POCKETTK_GUI_SELFTEST = "1"
$env:PYTHONNOUSERSITE = "1"
& (Join-Path $Stage "python\python.exe") (Join-Path $app "pockettk_gui.py")
if ($LASTEXITCODE -ne 0) { throw "self-test failed" }
Remove-Item Env:\POCKETTK_GUI_SELFTEST
& (Join-Path $Stage "python\python.exe") (Join-Path $app "pockettk.py") version

# ---- 6. the zip and the installer
$zip = Join-Path $Dist "PocketTk-$Version-windows-$Arch.zip"
if (Test-Path $zip) { Remove-Item $zip }
Compress-Archive -Path $Stage -DestinationPath $zip
Write-Host "== $zip"
$iscc = @("$env:ProgramFiles(x86)\Inno Setup 6\ISCC.exe", "$env:ProgramFiles\Inno Setup 6\ISCC.exe") | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $iscc) { $c = Get-Command iscc -ErrorAction SilentlyContinue; if ($c) { $iscc = $c.Source } }
if ($iscc) {
  Write-Host "== building the installer with Inno Setup"
  & $iscc "/DAppVersion=$Version" "/DSourceDir=$Stage" "/DOutDir=$Dist" "/DArch=$Arch" (Join-Path $Here "PocketTk.iss")
  if ($LASTEXITCODE -ne 0) { throw "Inno Setup failed" }
} else {
  Write-Host "== Inno Setup not found: no installer, only the portable zip (get it from https://jrsoftware.org/isinfo.php)"
}
Write-Host "== done"
