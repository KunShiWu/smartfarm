[CmdletBinding()]
param(
    [string]$BackendConfigPath,
    [string]$FrontendEnvPath,
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $BackendConfigPath) {
    $BackendConfigPath = Join-Path $projectRoot 'smartfarm_backend\src\main\resources\application.yml'
}
if (-not $FrontendEnvPath) {
    $FrontendEnvPath = Join-Path $projectRoot 'smartfarm_front\.env'
}
if (-not $OutputPath) {
    $OutputPath = Join-Path $projectRoot 'config\runtime-secrets.env'
}

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

function Read-EnvValues {
    param([string]$Path)

    $result = @{}
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $result
    }

    foreach ($line in Get-Content -LiteralPath $Path -Encoding utf8) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) {
            continue
        }
        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1) {
            continue
        }
        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        if ($value.Length -ge 2 -and $value[0] -eq '"' -and $value[$value.Length - 1] -eq '"') {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $result[$name] = $value
    }
    return $result
}

$values = @{}

if (Test-Path -LiteralPath $BackendConfigPath -PathType Leaf) {
    $yaml = Get-Content -LiteralPath $BackendConfigPath -Raw -Encoding utf8
    $matches = [regex]::Matches($yaml, '\$\{(?<name>[A-Z][A-Z0-9_]*):(?<value>[^}]*)\}')
    foreach ($match in $matches) {
        if (-not [string]::IsNullOrWhiteSpace($match.Groups['value'].Value)) {
            $values[$match.Groups['name'].Value] = $match.Groups['value'].Value
        }
    }
}

foreach ($entry in (Read-EnvValues -Path $FrontendEnvPath).GetEnumerator()) {
    $values[$entry.Key] = $entry.Value
}
foreach ($entry in (Read-EnvValues -Path $OutputPath).GetEnumerator()) {
    $values[$entry.Key] = $entry.Value
}

foreach ($name in $requiredNames) {
    foreach ($scope in @('Process', 'User', 'Machine')) {
        $candidate = [Environment]::GetEnvironmentVariable($name, $scope)
        if (-not [string]::IsNullOrWhiteSpace($candidate)) {
            $values[$name] = $candidate
            break
        }
    }
}

$missing = @($requiredNames | Where-Object {
    -not $values.ContainsKey($_) -or [string]::IsNullOrWhiteSpace([string]$values[$_])
})
if ($missing.Count -gt 0) {
    throw "无法生成完整运行密钥文件，缺少: $($missing -join ', ')"
}

$outputDirectory = Split-Path $OutputPath -Parent
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add('# Smart Farm private runtime configuration')
$lines.Add('# Only include this file in archives shared with fully trusted recipients.')
foreach ($name in $requiredNames) {
    $value = [string]$values[$name]
    if ($value.Contains("`r") -or $value.Contains("`n")) {
        throw "配置 $name 包含不支持的换行符"
    }
    $lines.Add("$name=$value")
}
$lines.Add('SMARTFARM_SSH_HOST=116.204.111.100')
$lines.Add('SMARTFARM_SSH_USER=root')
$lines.Add('SMARTFARM_SSH_REMOTE_PORT=18081')

[System.IO.File]::WriteAllLines(
    $OutputPath,
    $lines,
    [System.Text.UTF8Encoding]::new($false)
)

Write-Output "运行密钥文件已生成: $OutputPath"
Write-Output '未在控制台输出任何密钥值。'
