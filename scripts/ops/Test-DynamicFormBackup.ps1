[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$BackupPath
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$backupDir = [System.IO.Path]::GetFullPath($BackupPath)
$manifestPath = Join-Path $backupDir "manifest.json"
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw "Backup manifest not found: $manifestPath"
}

$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($manifest.formatVersion -ne 1) {
    throw "Unsupported backup format version: $($manifest.formatVersion)"
}

foreach ($file in $manifest.files) {
    if ($file.name -notmatch '^[a-zA-Z0-9._-]+$') {
        throw "Unsafe file name in manifest: $($file.name)"
    }
    $path = Join-Path $backupDir $file.name
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Backup file is missing: $($file.name)"
    }
    $item = Get-Item -LiteralPath $path
    if ($item.Length -ne [long]$file.sizeBytes) {
        throw "Size mismatch for $($file.name)."
    }
    $actualHash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $file.sha256.ToLowerInvariant()) {
        throw "SHA-256 mismatch for $($file.name)."
    }
}

Write-Output "Backup '$($manifest.backupId)' is complete and checksums are valid."
