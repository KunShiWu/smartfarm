$ErrorActionPreference = 'Stop'

$sanitizer = Join-Path $PSScriptRoot 'remove-inline-secrets.ps1'
$tempFile = Join-Path ([System.IO.Path]::GetTempPath()) ("smartfarm-yaml-{0}.yml" -f ([guid]::NewGuid()))

try {
    @'
account: ${SMARTFARM_UPSTREAM_ACCOUNT:fake-account}
password: ${SMARTFARM_UPSTREAM_PASSWORD:fake-password}
weather: ${SMARTFARM_WEATHER_API_KEY:fake-weather}
base-url: ${SMARTFARM_MIJIA_BASE_URL:http://127.0.0.1:18083}
token: ${MIJIA_BRIDGE_TOKEN:fake-bridge}
'@ | Set-Content -LiteralPath $tempFile -Encoding utf8

    & $sanitizer -Path $tempFile
    $sanitized = Get-Content -LiteralPath $tempFile -Raw -Encoding utf8

    if ($sanitized -notlike '*${SMARTFARM_UPSTREAM_ACCOUNT:}*' -or
        $sanitized -notlike '*${SMARTFARM_UPSTREAM_PASSWORD:}*' -or
        $sanitized -notlike '*${SMARTFARM_WEATHER_API_KEY:}*' -or
        $sanitized -notlike '*${MIJIA_BRIDGE_TOKEN:}*') {
        throw '敏感默认值没有被完整移除'
    }
    if ($sanitized -notlike '*${SMARTFARM_MIJIA_BASE_URL:http://127.0.0.1:18083}*') {
        throw '非敏感的桥接地址不应被清除'
    }
    if ($sanitized -like '*fake-account*' -or $sanitized -like '*fake-bridge*') {
        throw '清理后仍然残留敏感值'
    }

    Write-Output 'remove-inline-secrets tests passed'
} finally {
    Remove-Item -LiteralPath $tempFile -Force -ErrorAction SilentlyContinue
}
