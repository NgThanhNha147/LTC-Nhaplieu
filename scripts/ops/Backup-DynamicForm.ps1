[CmdletBinding()]
param(
    [string]$OutputRoot = $(if ($env:BACKUP_ROOT) { $env:BACKUP_ROOT } else { "./backups" }),
    [ValidateRange(1, 3650)]
    [int]$RetentionDays = $(if ($env:BACKUP_RETENTION_DAYS) { [int]$env:BACKUP_RETENTION_DAYS } else { 14 }),
    [string]$ComposeFile = "docker-compose.yml",
    [string]$ArchiveImage = $(if ($env:BACKUP_ARCHIVE_IMAGE) { $env:BACKUP_ARCHIVE_IMAGE } else { "alpine:3.20" }),
    [switch]$SkipRetention
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Invoke-Native {
    param([Parameter(Mandatory)][scriptblock]$Command, [string]$FailureMessage = "Native command failed")
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$FailureMessage (exit code $LASTEXITCODE)."
    }
}

function Assert-DockerAvailable {
    Invoke-Native { docker version --format '{{.Server.Version}}' | Out-Null } "Docker daemon is unavailable"
    Invoke-Native { docker compose -f $ComposeFile config --quiet } "Docker Compose configuration is invalid"
}

function Get-ComposeContainerId {
    param([Parameter(Mandatory)][string]$Service)
    $containerId = (& docker compose -f $ComposeFile ps -q $Service 2>$null).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
        throw "Service '$Service' is not running. Start it before creating a backup."
    }
    return $containerId
}

function Test-IsChildPath {
    param([Parameter(Mandatory)][string]$Parent, [Parameter(Mandatory)][string]$Child)
    $parentPath = [System.IO.Path]::GetFullPath($Parent).TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    $childPath = [System.IO.Path]::GetFullPath($Child)
    return $childPath.StartsWith($parentPath, [System.StringComparison]::OrdinalIgnoreCase)
}

Assert-DockerAvailable

$root = [System.IO.Path]::GetFullPath($OutputRoot)
[System.IO.Directory]::CreateDirectory($root) | Out-Null
$timestamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$backupDir = Join-Path $root $timestamp
[System.IO.Directory]::CreateDirectory($backupDir) | Out-Null

$postgresContainer = Get-ComposeContainerId "postgres"
$backendContainer = Get-ComposeContainerId "backend"
$databaseDump = Join-Path $backupDir "database.dump"
$documentsArchive = Join-Path $backupDir "documents.tar.gz"
$databaseTempPath = "/tmp/dynamic-form-$timestamp.dump"
$archiveContainer = "dynamic-form-backup-$($timestamp.ToLowerInvariant())"
$applicationStopped = $false

$backendInspection = (& docker inspect $backendContainer | ConvertFrom-Json)[0]
$documentMount = $backendInspection.Mounts | Where-Object { $_.Destination -eq "/app/data/documents" } | Select-Object -First 1
if ($null -eq $documentMount -or [string]::IsNullOrWhiteSpace($documentMount.Name)) {
    throw "Could not resolve the document Docker volume."
}
$documentVolume = $documentMount.Name

try {
    Write-Host "Stopping application services for a consistent backup..."
    Invoke-Native { docker compose -f $ComposeFile stop frontend backend ocr } "Could not stop application services"
    $applicationStopped = $true

    Write-Host "Creating PostgreSQL backup..."
    Invoke-Native {
        docker exec $postgresContainer sh -ceu 'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump --format=custom --compress=6 --no-owner --no-privileges --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --file="$1"' sh $databaseTempPath
    } "PostgreSQL backup failed"
    Invoke-Native { docker cp "${postgresContainer}:${databaseTempPath}" $databaseDump } "Could not copy PostgreSQL backup"

    Write-Host "Creating document archive..."
    Invoke-Native {
        docker create --name $archiveContainer --mount "type=volume,source=$documentVolume,target=/source,readonly" $ArchiveImage sh -ceu 'tar -czf /tmp/documents.tar.gz -C /source .'
    } "Could not create the document backup helper"
    Invoke-Native { docker start -a $archiveContainer } "Document backup failed"
    Invoke-Native { docker cp "${archiveContainer}:/tmp/documents.tar.gz" $documentsArchive } "Could not copy the document archive"

    $files = @($databaseDump, $documentsArchive) | ForEach-Object {
        $item = Get-Item -LiteralPath $_
        [ordered]@{
            name = $item.Name
            sizeBytes = $item.Length
            sha256 = (Get-FileHash -LiteralPath $item.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    }

    $projectName = (& docker compose -f $ComposeFile config --format json | ConvertFrom-Json).name
    $manifest = [ordered]@{
        formatVersion = 1
        backupId = $timestamp
        createdAtUtc = (Get-Date).ToUniversalTime().ToString("o")
        composeProject = $projectName
        database = [ordered]@{
            engine = "postgresql"
            format = "pg_dump-custom"
            containerImage = (& docker inspect --format '{{.Config.Image}}' $postgresContainer).Trim()
        }
        documents = [ordered]@{
            format = "tar-gzip"
            sourceVolume = $documentVolume
        }
        files = $files
    }
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $backupDir "manifest.json") -Encoding utf8

    & (Join-Path $PSScriptRoot "Test-DynamicFormBackup.ps1") -BackupPath $backupDir | Out-Host

    if (-not $SkipRetention) {
        $cutoff = (Get-Date).ToUniversalTime().AddDays(-$RetentionDays)
        Get-ChildItem -LiteralPath $root -Directory | Where-Object {
            $_.Name -match '^\d{8}T\d{6}Z$' -and $_.CreationTimeUtc -lt $cutoff
        } | ForEach-Object {
            if (-not (Test-IsChildPath -Parent $root -Child $_.FullName)) {
                throw "Refusing to remove path outside backup root: $($_.FullName)"
            }
            Write-Host "Removing expired backup $($_.Name)..."
            Remove-Item -LiteralPath $_.FullName -Recurse -Force
        }
    }

    Write-Host "Backup completed: $backupDir"
}
catch {
    Write-Error $_
    throw
}
finally {
    & docker exec $postgresContainer rm -f -- $databaseTempPath 2>$null | Out-Null
    & docker inspect $archiveContainer 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        & docker rm -f $archiveContainer 2>$null | Out-Null
    }
    if ($applicationStopped) {
        Write-Host "Starting application services..."
        & docker compose -f $ComposeFile up -d backend frontend ocr | Out-Host
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "Backup finished but application services could not be restarted automatically."
        }
    }
}
