param(
    [Parameter(Mandatory=$true, Position=0)]
    [ValidateSet('library','app')]
    [string]$Project,
    [Parameter(ValueFromRemainingArguments=$true)]
    [string[]]$GradleArgs
)
$ErrorActionPreference = 'Stop'
$libraryRoot = Split-Path -Parent $PSScriptRoot
$repo = if ($Project -eq 'library') { $libraryRoot } else {
    Join-Path (Split-Path -Parent $libraryRoot) 'Vitals'
}
if (-not (Test-Path -LiteralPath (Join-Path $repo 'gradlew.bat'))) {
    throw "Missing project checkout: $repo"
}
$workRoot = if ($env:LIQUIDGLASS_WORK_HOME) { $env:LIQUIDGLASS_WORK_HOME } else {
    Join-Path $libraryRoot '.local'
}
$workRoot = [System.IO.Path]::GetFullPath($workRoot)
$env:GRADLE_USER_HOME = Join-Path $workRoot 'gradle-home'
$maven = Join-Path $workRoot 'maven-local'
$temp = Join-Path $workRoot 'temp'
foreach ($directory in @($env:GRADLE_USER_HOME, $maven, $temp)) {
    [System.IO.Directory]::CreateDirectory($directory) | Out-Null
}
$env:TMP = $temp
$env:TEMP = $temp
Push-Location -LiteralPath $repo
try {
    & (Join-Path $repo 'gradlew.bat') "-Dmaven.repo.local=$maven" @GradleArgs
    $result = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $result
