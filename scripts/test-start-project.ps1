$ErrorActionPreference = 'Stop'

$startScript = Join-Path $PSScriptRoot 'start-project.ps1'
$tempFile = Join-Path ([System.IO.Path]::GetTempPath()) ("smartfarm-start-{0}.env" -f ([guid]::NewGuid()))
$requiredNames = @(
    'SMARTFARM_UPSTREAM_ACCOUNT',
    'SMARTFARM_UPSTREAM_PASSWORD',
    'SMARTFARM_WEATHER_API_KEY',
    'SMARTFARM_EZVIZ_APP_KEY',
    'SMARTFARM_EZVIZ_APP_SECRET',
    'SMARTFARM_EZVIZ_DEVICE_SERIAL',
    'SMARTFARM_DEEPSEEK_API_KEY',
    'SMARTFARM_MIJIA_BASE_URL',
    'MIJIA_BRIDGE_TOKEN',
    'VITE_USE_MOCK',
    'VITE_API_BASE_URL'
)

function Clear-TestEnvironment {
    foreach ($name in $requiredNames) {
        Remove-Item -Path "Env:$name" -ErrorAction SilentlyContinue
    }
}

try {
    @'
SMARTFARM_UPSTREAM_ACCOUNT=fake-account
SMARTFARM_UPSTREAM_PASSWORD=fake-password
SMARTFARM_WEATHER_API_KEY=fake-weather
SMARTFARM_EZVIZ_APP_KEY=fake-ezviz-key
SMARTFARM_EZVIZ_APP_SECRET=fake-ezviz-secret
SMARTFARM_EZVIZ_DEVICE_SERIAL=fake-device
SMARTFARM_DEEPSEEK_API_KEY=fake-deepseek
SMARTFARM_MIJIA_BASE_URL=http://127.0.0.1:18083
MIJIA_BRIDGE_TOKEN=fake-bridge
VITE_USE_MOCK=false
VITE_API_BASE_URL=/api
'@ | Set-Content -LiteralPath $tempFile -Encoding utf8

    Clear-TestEnvironment
    $output = (& $startScript -SecretsPath $tempFile -CheckOnly -SkipTunnelCheck | Out-String)
    if ($output -notlike '*配置检查通过*') {
        throw '检查模式没有返回成功结果'
    }
    if ($output -like '*fake-weather*' -or $output -like '*fake-bridge*') {
        throw '启动脚本不应输出密钥值'
    }

    @'
SMARTFARM_UPSTREAM_ACCOUNT=fake-account
SMARTFARM_UPSTREAM_PASSWORD=fake-password
SMARTFARM_WEATHER_API_KEY=fake-weather
SMARTFARM_EZVIZ_APP_KEY=fake-ezviz-key
SMARTFARM_EZVIZ_APP_SECRET=fake-ezviz-secret
SMARTFARM_EZVIZ_DEVICE_SERIAL=fake-device
SMARTFARM_DEEPSEEK_API_KEY=fake-deepseek
SMARTFARM_MIJIA_BASE_URL=http://127.0.0.1:18083
VITE_USE_MOCK=false
VITE_API_BASE_URL=/api
'@ | Set-Content -LiteralPath $tempFile -Encoding utf8

    Clear-TestEnvironment
    $missingDetected = $false
    try {
        & $startScript -SecretsPath $tempFile -CheckOnly -SkipTunnelCheck
    } catch {
        $missingDetected = $_.Exception.Message -like '*MIJIA_BRIDGE_TOKEN*'
    }
    if (-not $missingDetected) {
        throw '启动前没有拦截缺少米家 Token 的配置'
    }

    Write-Output 'start-project tests passed'
} finally {
    Remove-Item -LiteralPath $tempFile -Force -ErrorAction SilentlyContinue
    Clear-TestEnvironment
}
