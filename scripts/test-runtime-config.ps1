$ErrorActionPreference = 'Stop'

$runtimeConfigScript = Join-Path $PSScriptRoot 'runtime-config.ps1'
. $runtimeConfigScript

$tempFile = Join-Path ([System.IO.Path]::GetTempPath()) ("smartfarm-secrets-{0}.env" -f ([guid]::NewGuid()))
$managedNames = @(
    'SMARTFARM_WEATHER_API_KEY',
    'MIJIA_BRIDGE_TOKEN',
    'SMARTFARM_EZVIZ_APP_SECRET'
)

try {
    @'
# comments and blank lines are ignored

SMARTFARM_WEATHER_API_KEY=fake-weather-key
MIJIA_BRIDGE_TOKEN=bridge-token-with-equals=kept
SMARTFARM_EZVIZ_APP_SECRET="quoted-secret"
'@ | Set-Content -LiteralPath $tempFile -Encoding utf8

    Import-RuntimeSecrets -Path $tempFile

    if ($env:SMARTFARM_WEATHER_API_KEY -ne 'fake-weather-key') {
        throw '天气密钥没有被载入当前进程'
    }
    if ($env:MIJIA_BRIDGE_TOKEN -ne 'bridge-token-with-equals=kept') {
        throw '包含等号的 Token 被错误截断'
    }
    if ($env:SMARTFARM_EZVIZ_APP_SECRET -ne 'quoted-secret') {
        throw '带引号的密钥没有被正确解析'
    }

    Assert-RuntimeSecrets -Names $managedNames

    Remove-Item Env:MIJIA_BRIDGE_TOKEN
    $missingDetected = $false
    try {
        Assert-RuntimeSecrets -Names $managedNames
    } catch {
        $missingDetected = $_.Exception.Message -like '*MIJIA_BRIDGE_TOKEN*'
    }
    if (-not $missingDetected) {
        throw '缺少关键密钥时没有返回明确错误'
    }

    Write-Output 'runtime-config tests passed'
} finally {
    Remove-Item -LiteralPath $tempFile -Force -ErrorAction SilentlyContinue
    foreach ($name in $managedNames) {
        Remove-Item -Path "Env:$name" -ErrorAction SilentlyContinue
    }
}
