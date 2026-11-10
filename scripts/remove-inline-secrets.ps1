[CmdletBinding()]
param(
    [string]$Path
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $Path) {
    $Path = Join-Path $projectRoot 'smartfarm_backend\src\main\resources\application.yml'
}
if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    throw "后端配置文件不存在: $Path"
}

$sensitiveNames = @(
    'SMARTFARM_UPSTREAM_ACCOUNT',
    'SMARTFARM_UPSTREAM_PASSWORD',
    'SMARTFARM_WEATHER_API_KEY',
    'SMARTFARM_EZVIZ_APP_KEY',
    'SMARTFARM_EZVIZ_APP_SECRET',
    'SMARTFARM_EZVIZ_DEVICE_SERIAL',
    'SMARTFARM_DEEPSEEK_API_KEY',
    'MIJIA_BRIDGE_TOKEN'
)

$content = Get-Content -LiteralPath $Path -Raw -Encoding utf8
foreach ($name in $sensitiveNames) {
    $pattern = '\$\{' + [regex]::Escape($name) + ':[^}]*\}'
    $replacement = '${' + $name + ':}'
    $content = [regex]::Replace(
        $content,
        $pattern,
        [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $replacement }
    )
}

[System.IO.File]::WriteAllText(
    $Path,
    $content,
    [System.Text.UTF8Encoding]::new($false)
)

Write-Output '后端配置中的敏感默认值已移除。'
