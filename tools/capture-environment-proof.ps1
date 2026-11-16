param([switch]$Replay)
$ErrorActionPreference = 'Stop'
$qaProject = Join-Path $PSScriptRoot '../run/qube/tectonic-surveys-v2/20261107'
# These are only the named fixture worker and loose fixture items in the isolated QA world.
foreach ($cleanup in @('kill @e[tag=environment_worker]','kill @e[type=minecraft:item,x=110,y=149,z=110,dx=8,dy=8,dz=8]')) {
    $cleanupOutput = & qube rcon --project $qaProject $cleanup 2>&1
    if ($LASTEXITCODE -ne 0 -and ($cleanupOutput -join "`n") -notmatch 'No entity was found') { throw ($cleanupOutput -join "`n") }
}
if ($Replay) { & qube shot --project $qaProject --dedicated --replay }
else {
    & qube shot --project $qaProject --dedicated --new-session --segment ('@' + (Join-Path $PSScriptRoot '../validation/tectonic-terrain-weather.json'))
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & qube shot --project $qaProject --dedicated --segment ('@' + (Join-Path $PSScriptRoot '../validation/tectonic-irrigation.json'))
}
exit $LASTEXITCODE
