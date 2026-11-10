[CmdletBinding()]
param(
    [string]$SecretsPath,
    [switch]$CheckOnly,
    [switch]$SkipTunnelCheck
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $SecretsPath) {
    $SecretsPath = Join-Path $projectRoot 'config\runtime-secrets.env'
}

. (Join-Path $PSScriptRoot 'runtime-config.ps1')

$requiredSecrets = @(
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

function Test-TcpPort {
    param(
        [Parameter(Mandatory = $true)][string]$ComputerName,
        [Parameter(Mandatory = $true)][int]$Port,
        [int]$TimeoutMilliseconds = 700
    )

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $pending = $client.BeginConnect($ComputerName, $Port, $null, $null)
        if (-not $pending.AsyncWaitHandle.WaitOne($TimeoutMilliseconds)) {
            return $false
        }
        $client.EndConnect($pending)
        return $true
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Wait-TcpPort {
    param(
        [Parameter(Mandatory = $true)][string]$ComputerName,
        [Parameter(Mandatory = $true)][int]$Port,
        [int]$TimeoutSeconds = 60
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (Test-TcpPort -ComputerName $ComputerName -Port $Port) {
            return $true
        }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Resolve-CommandPath {
    param([Parameter(Mandatory = $true)][string[]]$Names)

    foreach ($name in $Names) {
        $command = Get-Command $name -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($command) {
            return $command.Source
        }
    }
    throw "未找到运行命令: $($Names -join ' / ')"
}

Import-RuntimeSecrets -Path $SecretsPath
Assert-RuntimeSecrets -Names $requiredSecrets

if ($CheckOnly) {
    Write-Output '配置检查通过：运行密钥完整，且未输出任何密钥值。'
    return
}

$backendPath = Join-Path $projectRoot 'smartfarm_backend'
$frontendPath = Join-Path $projectRoot 'smartfarm_front'
if (-not (Test-Path -LiteralPath $backendPath -PathType Container)) {
    throw "后端目录不存在: $backendPath"
}
if (-not (Test-Path -LiteralPath $frontendPath -PathType Container)) {
    throw "前端目录不存在: $frontendPath"
}

if (-not $SkipTunnelCheck) {
    try {
        $bridgeUri = [uri]$env:SMARTFARM_MIJIA_BASE_URL
    } catch {
        throw 'SMARTFARM_MIJIA_BASE_URL 不是有效地址'
    }

    if (-not (Test-TcpPort -ComputerName $bridgeUri.Host -Port $bridgeUri.Port)) {
        $sshHost = if ($env:SMARTFARM_SSH_HOST) { $env:SMARTFARM_SSH_HOST } else { '<云服务器IP>' }
        $sshUser = if ($env:SMARTFARM_SSH_USER) { $env:SMARTFARM_SSH_USER } else { 'root' }
        $remotePort = if ($env:SMARTFARM_SSH_REMOTE_PORT) { $env:SMARTFARM_SSH_REMOTE_PORT } else { '18081' }
        throw "米家 SSH 隧道未连接。请先在单独的 PowerShell 中运行: ssh -N -L $($bridgeUri.Port):127.0.0.1:$remotePort $sshUser@$sshHost"
    }
}

$logsPath = Join-Path $projectRoot 'logs'
New-Item -ItemType Directory -Path $logsPath -Force | Out-Null

if (Test-TcpPort -ComputerName '127.0.0.1' -Port 8080) {
    Write-Output '后端端口 8080 已在运行，跳过重复启动。'
} else {
    $maven = Resolve-CommandPath -Names @('mvn.cmd', 'mvn')
    Start-Process `
        -FilePath $maven `
        -ArgumentList @('spring-boot:run') `
        -WorkingDirectory $backendPath `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logsPath 'backend.out.log') `
        -RedirectStandardError (Join-Path $logsPath 'backend.err.log') | Out-Null
    if (-not (Wait-TcpPort -ComputerName '127.0.0.1' -Port 8080)) {
        throw "后端启动超时，请检查日志: $logsPath"
    }
    Write-Output '后端已启动：http://127.0.0.1:8080'
}

if (Test-TcpPort -ComputerName '127.0.0.1' -Port 5173) {
    Write-Output '前端端口 5173 已在运行，跳过重复启动。'
} else {
    $npm = Resolve-CommandPath -Names @('npm.cmd', 'npm')
    Start-Process `
        -FilePath $npm `
        -ArgumentList @('run', 'dev', '--', '--host', '127.0.0.1') `
        -WorkingDirectory $frontendPath `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logsPath 'frontend.out.log') `
        -RedirectStandardError (Join-Path $logsPath 'frontend.err.log') | Out-Null
    if (-not (Wait-TcpPort -ComputerName '127.0.0.1' -Port 5173)) {
        throw "前端启动超时，请检查日志: $logsPath"
    }
    Write-Output '前端已启动：http://127.0.0.1:5173'
}

Write-Output '项目启动完成。'
