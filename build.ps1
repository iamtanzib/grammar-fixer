# Builds GrammarFixer.exe with the C# compiler that ships with Windows (.NET Framework 4.x).
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$csc = "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
$opts = '/nologo', '/target:winexe', '/optimize', '/out:GrammarFixer.exe',
  '/r:System.dll', '/r:System.Core.dll', '/r:System.Drawing.dll', '/r:System.Windows.Forms.dll',
  '/r:System.Net.Http.dll', '/r:System.Security.dll', '/r:System.Web.Extensions.dll'

Get-Process GrammarFixer -ErrorAction SilentlyContinue | Stop-Process -Force
& $csc @opts GrammarFixer.cs; if ($LASTEXITCODE) { throw 'Build failed' }
# The app draws its own logo; export it, then rebuild with it as the exe icon.
Start-Process .\GrammarFixer.exe -ArgumentList '--export-icon', 'app.ico' -Wait
& $csc @opts /win32icon:app.ico GrammarFixer.cs; if ($LASTEXITCODE) { throw 'Build failed' }
Write-Host 'Built GrammarFixer.exe'
