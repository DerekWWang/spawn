param([Parameter(Mandatory=$true)][long]$Seed)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$qaRoot = Join-Path $projectRoot "run/qube/tectonic-surveys-v2/$Seed"
if (-not (Test-Path -LiteralPath (Join-Path $qaRoot '.qube/project.json'))) { throw 'Prepare and boot this isolated QA project first.' }
$surveyCommands = @('seed')
foreach ($pass in 0..1) { foreach ($x in @(-4096,-2048,0,2048,4096)) { foreach ($z in @(-4096,-2048,0,2048,4096)) { $surveyCommands += "survival environmentSurvey $x $z" } } }
$surveyOutput = & qube rcon --project $qaRoot @surveyCommands
if ($LASTEXITCODE -ne 0) { throw 'Terrain survey failed.' }
$surveyOutput | Set-Content -LiteralPath (Join-Path $qaRoot 'terrain-survey.txt') -Encoding UTF8
Write-Output "Saved two-pass terrain and cache survey for seed $Seed."
