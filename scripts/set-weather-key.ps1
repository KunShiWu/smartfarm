param(
    [ValidateSet('User', 'Process')]
    [string]$Scope = 'User',

    [Security.SecureString]$SecureKey
)

$ErrorActionPreference = 'Stop'

if (-not $SecureKey) {
    $SecureKey = Read-Host '请输入天气 API 密钥（输入内容不会显示）' -AsSecureString
}

$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureKey)
try {
    $plainKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    if ([string]::IsNullOrWhiteSpace($plainKey)) {
        throw '天气 API 密钥不能为空'
    }

    $target = if ($Scope -eq 'User') {
        [EnvironmentVariableTarget]::User
    }
    else {
        [EnvironmentVariableTarget]::Process
    }

    [Environment]::SetEnvironmentVariable('SMARTFARM_WEATHER_API_KEY', $plainKey, $target)
    if ($Scope -eq 'User') {
        $env:SMARTFARM_WEATHER_API_KEY = $plainKey
    }
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
}

if ($Scope -eq 'User') {
    Write-Output '天气密钥已保存到当前 Windows 用户环境变量。请重启后端服务使配置生效。'
}
else {
    Write-Output '天气密钥已写入当前 PowerShell 进程。'
}
