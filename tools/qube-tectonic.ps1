param([Parameter(ValueFromRemainingArguments=$true)][string[]]$QubeArguments)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'prepare-tectonic-runtime.ps1')
$previousProfile = $env:ORG_GRADLE_PROJECT_tectonicProfile
try {
    $env:ORG_GRADLE_PROJECT_tectonicProfile = 'true'
    & qube @QubeArguments
    exit $LASTEXITCODE
} finally { $env:ORG_GRADLE_PROJECT_tectonicProfile = $previousProfile }
