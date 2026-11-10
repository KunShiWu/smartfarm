$ErrorActionPreference = 'Stop'

$scriptPath = Join-Path $PSScriptRoot 'set-weather-key.ps1'
$testValue = 'test-weather-key'
$secureValue = ConvertTo-SecureString $testValue -AsPlainText -Force

try {
    & $scriptPath -Scope Process -SecureKey $secureValue
    if ($env:SMARTFARM_WEATHER_API_KEY -ne $testValue) {
        throw '脚本没有设置 SMARTFARM_WEATHER_API_KEY'
    }
    Write-Output 'set-weather-key.ps1 test passed'
}
finally {
    Remove-Item Env:SMARTFARM_WEATHER_API_KEY -ErrorAction SilentlyContinue
}
