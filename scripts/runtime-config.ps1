function Import-RuntimeSecrets {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "运行密钥文件不存在: $Path"
    }

    foreach ($line in Get-Content -LiteralPath $Path -Encoding utf8) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) {
            continue
        }

        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1) {
            throw "运行密钥文件包含无效配置行（应为 NAME=VALUE）"
        }

        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
            throw "运行密钥文件包含无效变量名: $name"
        }

        if ($value.Length -ge 2) {
            $first = $value[0]
            $last = $value[$value.Length - 1]
            if (($first -eq '"' -and $last -eq '"') -or ($first -eq "'" -and $last -eq "'")) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }

        Set-Item -Path "Env:$name" -Value $value
    }
}

function Assert-RuntimeSecrets {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Names
    )

    $missing = @(
        foreach ($name in $Names) {
            $value = [Environment]::GetEnvironmentVariable($name, 'Process')
            if ([string]::IsNullOrWhiteSpace($value)) {
                $name
            }
        }
    )

    if ($missing.Count -gt 0) {
        throw "运行密钥文件缺少以下配置: $($missing -join ', ')"
    }
}
