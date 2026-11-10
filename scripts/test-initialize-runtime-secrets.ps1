$ErrorActionPreference = 'Stop'

$initializer = Join-Path $PSScriptRoot 'initialize-runtime-secrets.ps1'
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("smartfarm-init-{0}" -f ([guid]::NewGuid()))
$backendConfig = Join-Path $tempRoot 'application.yml'
$frontendEnv = Join-Path $tempRoot '.env'
$outputPath = Join-Path $tempRoot 'runtime-secrets.env'
$processNames = @(
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

try {
    New-Item -ItemType Directory -Path $tempRoot | Out-Null
    @'
smartfarm:
  upstream:
    account: ${SMARTFARM_UPSTREAM_ACCOUNT:fake-account}
    password: ${SMARTFARM_UPSTREAM_PASSWORD:fake-password}
  weather:
    api-key: ${SMARTFARM_WEATHER_API_KEY:}
  ezviz:
    app-key: ${SMARTFARM_EZVIZ_APP_KEY:fake-ezviz-key}
    app-secret: ${SMARTFARM_EZVIZ_APP_SECRET:fake-ezviz-secret}
    device-serial: ${SMARTFARM_EZVIZ_DEVICE_SERIAL:fake-device}
  deepseek:
    api-key: ${SMARTFARM_DEEPSEEK_API_KEY:fake-deepseek}
  mijia:
    base-url: ${SMARTFARM_MIJIA_BASE_URL:http://127.0.0.1:18083}
    token: ${MIJIA_BRIDGE_TOKEN:fake-bridge}
'@ | Set-Content -LiteralPath $backendConfig -Encoding utf8
    @'
VITE_USE_MOCK=false
VITE_API_BASE_URL=/api
'@ | Set-Content -LiteralPath $frontendEnv -Encoding utf8
    foreach ($name in $processNames) {
        Remove-Item -Path "Env:$name" -ErrorAction SilentlyContinue
    }
    $env:SMARTFARM_WEATHER_API_KEY = 'fake-weather-from-environment'

    $output = (& $initializer `
        -BackendConfigPath $backendConfig `
        -FrontendEnvPath $frontendEnv `
        -OutputPath $outputPath | Out-String)

    if (-not (Test-Path -LiteralPath $outputPath -PathType Leaf)) {
        throw '初始化脚本没有生成运行密钥文件'
    }
    if ($output -like '*fake-weather*' -or $output -like '*fake-bridge*') {
        throw '初始化脚本不应输出密钥值'
    }

    . (Join-Path $PSScriptRoot 'runtime-config.ps1')
    Import-RuntimeSecrets -Path $outputPath
    if ($env:SMARTFARM_WEATHER_API_KEY -ne 'fake-weather-from-environment') {
        throw '没有优先使用当前环境中的天气密钥'
    }
    if ($env:MIJIA_BRIDGE_TOKEN -ne 'fake-bridge') {
        throw '没有迁移后端配置中的米家 Token'
    }
    if ($env:VITE_USE_MOCK -ne 'false') {
        throw '没有迁移前端运行配置'
    }

    Write-Output 'initialize-runtime-secrets tests passed'
} finally {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
    foreach ($name in $processNames) {
        Remove-Item -Path "Env:$name" -ErrorAction SilentlyContinue
    }
}
