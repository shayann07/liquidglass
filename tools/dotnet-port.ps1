param([Parameter(ValueFromRemainingArguments=$true)][string[]]$DotnetArgs)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$env:DOTNET_CLI_TELEMETRY_OPTOUT = '1'
$env:DOTNET_GENERATE_ASPNET_CERTIFICATE = 'false'
$env:DOTNET_CLI_HOME = Join-Path $repoRoot '.local/dotnet-home'
$env:NUGET_PACKAGES = Join-Path $repoRoot '.local/nuget-packages'
$privateSdk = Join-Path $repoRoot '.local/dotnet-sdk/dotnet.exe'
$sdk = if (Test-Path -LiteralPath $privateSdk) { $privateSdk } else { (Get-Command dotnet).Source }
Push-Location -LiteralPath $repoRoot
try {
    & $sdk @DotnetArgs
    $result = $LASTEXITCODE
} finally { Pop-Location }
exit $result
