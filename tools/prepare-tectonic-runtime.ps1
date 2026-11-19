param([string]$Project = (Join-Path $PSScriptRoot '..'))
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath $Project).Path
$manifest = Get-Content -LiteralPath (Join-Path $projectRoot 'config/tectonic-runtime.json') -Raw | ConvertFrom-Json
if ($manifest.schema -ne 1) { throw 'Unsupported Tectonic runtime manifest.' }
$runtimeRoot = Join-Path $projectRoot 'build/tectonic-runtime'
$gameConfigRoot = Join-Path $projectRoot 'run/config'
New-Item -ItemType Directory -Force -Path $gameConfigRoot | Out-Null
if (-not (Test-Path -LiteralPath (Join-Path $gameConfigRoot 'tectonic.json'))) { Copy-Item -LiteralPath (Join-Path $projectRoot 'config/tectonic-realism.json') -Destination (Join-Path $gameConfigRoot 'tectonic.json') }
New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null
foreach ($artifact in $manifest.artifacts) {
    if ($artifact.filename -notmatch '^[a-zA-Z0-9.+_-]+\.jar$' -or $artifact.url -notlike 'https://cdn.modrinth.com/*') { throw 'Invalid runtime artifact path or source.' }
    $destination = Join-Path $runtimeRoot $artifact.filename
    if ((Test-Path -LiteralPath $destination) -and (Get-FileHash -LiteralPath $destination -Algorithm SHA512).Hash -eq $artifact.sha512) { continue }
    $reference = Join-Path $projectRoot ('reference/tectonic/' + $artifact.filename)
    $temporary = $destination + '.download'
    if ((Test-Path -LiteralPath $reference) -and (Get-FileHash -LiteralPath $reference -Algorithm SHA512).Hash -eq $artifact.sha512) { Copy-Item -LiteralPath $reference -Destination $temporary }
    else { Invoke-WebRequest -Uri $artifact.url -OutFile $temporary -UseBasicParsing }
    if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA512).Hash -ne $artifact.sha512) { throw "Hash mismatch for $($artifact.name). Runtime was not installed." }
    Move-Item -LiteralPath $temporary -Destination $destination -Force
}
Write-Output "Tectonic 3.0.32 and Lithostitched 2.0.6 verified in $runtimeRoot"
