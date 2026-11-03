# Local secret scan for the WM OS repository.
#
# Run from the repository root:  pwsh -File scripts\check-secrets.ps1
# Exits non-zero when a likely secret is found, so it can gate a commit hook or CI step.

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$patterns = @(
    'sk-[A-Za-z0-9]{20,}',                       # OpenAI-style keys
    'gsk_[A-Za-z0-9]{20,}',                      # Groq
    'AIza[0-9A-Za-z\-_]{30,}',                   # Google API keys
    'ghp_[A-Za-z0-9]{30,}',                      # GitHub personal token
    'xox[baprs]-[A-Za-z0-9\-]{10,}',             # Slack
    '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----',
    'Bearer\s+[A-Za-z0-9\-_\.]{25,}'
)

$excludeDirs = @('\.git\', '\build\', '\.gradle\', '\.kotlin\', '\tools\')
$extensions = @('.kt', '.kts', '.xml', '.json', '.yml', '.yaml', '.md', '.properties', '.gradle', '.ps1', '.sh')

$files = Get-ChildItem -Path $root -Recurse -File |
    Where-Object {
        $path = $_.FullName
        ($extensions -contains $_.Extension) -and
        -not ($excludeDirs | Where-Object { $path -like "*$_*" })
    }

$findings = @()
foreach ($file in $files) {
    $line = 0
    foreach ($content in Get-Content -LiteralPath $file.FullName -ErrorAction SilentlyContinue) {
        $line++
        foreach ($pattern in $patterns) {
            if ($content -match $pattern) {
                $findings += [pscustomobject]@{
                    File = $file.FullName.Substring($root.Length + 1)
                    Line = $line
                    Pattern = $pattern
                }
            }
        }
    }
}

if ($findings.Count -gt 0) {
    Write-Output "Найдены возможные секреты:"
    $findings | ForEach-Object { "  {0}:{1}  {2}" -f $_.File, $_.Line, $_.Pattern }
    exit 1
}

Write-Output ("Проверено файлов: {0}. Секретов не найдено." -f $files.Count)
exit 0
