$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
& (Join-Path $PSScriptRoot 'prepare-tectonic-runtime.ps1')
$modJar = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'build/libs') -Filter '*.jar' | Where-Object { $_.Name -notmatch 'sources|javadoc' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($null -eq $modJar) { throw 'Run qube build before packaging.' }
$packageRoot = Join-Path $projectRoot ('build/tectonic-environment-runtime-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path (Join-Path $packageRoot 'mods'),(Join-Path $packageRoot 'config') | Out-Null
Copy-Item -LiteralPath $modJar.FullName -Destination (Join-Path $packageRoot 'mods')
$manifest = Get-Content -LiteralPath (Join-Path $projectRoot 'config/tectonic-runtime.json') -Raw | ConvertFrom-Json
foreach ($artifact in $manifest.artifacts) { Copy-Item -LiteralPath (Join-Path $projectRoot ('build/tectonic-runtime/' + $artifact.filename)) -Destination (Join-Path $packageRoot 'mods') }
Copy-Item -LiteralPath (Join-Path $projectRoot 'config/tectonic-realism.json') -Destination (Join-Path $packageRoot 'config/tectonic.json')
Copy-Item -LiteralPath (Join-Path $projectRoot 'config/tectonic-runtime.json') -Destination $packageRoot
Copy-Item -LiteralPath (Join-Path $projectRoot 'config/THIRD_PARTY_NOTICES.txt') -Destination $packageRoot
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs/tectonic-environment.md') -Destination (Join-Path $packageRoot 'README.md')
# Preserve the MIT notices from the redistributed official artifacts.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$licenseRoot = Join-Path $packageRoot 'licenses'
New-Item -ItemType Directory -Force -Path $licenseRoot | Out-Null
foreach ($artifact in $manifest.artifacts) {
    $archive = [IO.Compression.ZipFile]::OpenRead((Join-Path $projectRoot ('build/tectonic-runtime/' + $artifact.filename)))
    try { foreach ($entry in $archive.Entries) { if ($entry.Name -match '^LICENSE') { [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $licenseRoot ($artifact.name + '-' + $entry.Name)),$true) } } } finally { $archive.Dispose() }
}
$zip = Join-Path $projectRoot 'build/tectonic-environment-runtime.zip'
Compress-Archive -Path (Join-Path $packageRoot '*') -DestinationPath $zip -Force
Get-FileHash -LiteralPath $zip -Algorithm SHA512
Write-Output $zip
