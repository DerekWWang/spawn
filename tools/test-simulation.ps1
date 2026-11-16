$ErrorActionPreference = 'Stop'
$javaCompiler = Get-ChildItem -LiteralPath "$env:LOCALAPPDATA/Qube/toolchains/java" -Filter javac.exe -Recurse -ErrorAction SilentlyContinue | Where-Object { $_.FullName -match '[\\/]25\.' } | Select-Object -First 1
if ($null -eq $javaCompiler) { throw 'Run qube build first to prepare the Java 25 toolchain.' }
$toolchainRoot = $javaCompiler.Directory.FullName
$testOutput = Join-Path $PSScriptRoot '../build/simulation-tests'
New-Item -ItemType Directory -Force -Path $testOutput | Out-Null
$testSources = Get-ChildItem -LiteralPath "$PSScriptRoot/../src/test/java/dev/qube/spawn_9b5fbb9/survival" -Filter *.java | Select-Object -ExpandProperty FullName
$fieldSources = @(Get-ChildItem -LiteralPath "$PSScriptRoot/../src/main/java/dev/qube/spawn_9b5fbb9/world" -Filter ResourceFields.java | Select-Object -ExpandProperty FullName)
& "$toolchainRoot/javac.exe" -encoding UTF-8 -d $testOutput "$PSScriptRoot/../src/main/java/dev/qube/spawn_9b5fbb9/survival/SimulationRules.java" "$PSScriptRoot/../src/main/java/dev/qube/spawn_9b5fbb9/survival/SkillSet.java" "$PSScriptRoot/../src/main/java/dev/qube/spawn_9b5fbb9/survival/SurvivalState.java" "$PSScriptRoot/../src/main/java/dev/qube/spawn_9b5fbb9/environment/EnvironmentModel.java" $fieldSources $testSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& "$toolchainRoot/java.exe" -cp "$testOutput;$PSScriptRoot/../src/test/resources" dev.qube.spawn_9b5fbb9.survival.SimulationTests
exit $LASTEXITCODE
