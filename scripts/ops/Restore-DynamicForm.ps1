[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$BackupPath,
    [Parameter(Mandatory)]
    [string]$Confirmation,
    [string]$ComposeFile = "docker-compose.yml",
    [string]$SafetyBackupRoot = "./backups/pre-restore",
    [string]$ArchiveImage = $(if ($env:BACKUP_ARCHIVE_IMAGE) { $env:BACKUP_ARCHIVE_IMAGE } else { "alpine:3.20" })
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$requiredConfirmation = "RESTORE DYNAMIC FORM DATA"

if ($Confirmation -cne $requiredConfirmation) {
    throw "Restore cancelled. Pass -Confirmation '$requiredConfirmation' exactly."
}

function Invoke-Native {
    param([Parameter(Mandatory)][scriptblock]$Command, [string]$FailureMessage = "Native command failed")
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$FailureMessage (exit code $LASTEXITCODE)."
    }
}

function Get-ComposeContainerId {
    param([Parameter(Mandatory)][string]$Service)
    $containerId = (& docker compose -f $ComposeFile ps -q $Service 2>$null).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
        throw "Service '$Service' is not running."
    }
    return $containerId
}

$backupDir = [System.IO.Path]::GetFullPath($BackupPath)
& (Join-Path $PSScriptRoot "Test-DynamicFormBackup.ps1") -BackupPath $backupDir | Out-Host

$databaseDump = Join-Path $backupDir "database.dump"
$documentsArchive = Join-Path $backupDir "documents.tar.gz"
$restoreId = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ").ToLowerInvariant()
$databaseTempPath = "/tmp/dynamic-form-restore-$restoreId.dump"
$archiveContainer = "dynamic-form-restore-$restoreId"
$applicationStopped = $false
$restoreDocumentsCommand = @'
tar -tzf /tmp/documents.tar.gz | awk '/^\// || /(^|\/)\.\.($|\/)/ { exit 1 }'
mkdir -p /restore-stage
tar -xzf /tmp/documents.tar.gz -C /restore-stage
find /target -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
cp -a /restore-stage/. /target/
rm -rf /restore-stage
'@

Write-Host "Creating a safety backup of the current state before restore..."
& (Join-Path $PSScriptRoot "Backup-DynamicForm.ps1") -OutputRoot $SafetyBackupRoot -ComposeFile $ComposeFile -ArchiveImage $ArchiveImage -SkipRetention

$postgresContainer = Get-ComposeContainerId "postgres"
$backendContainer = Get-ComposeContainerId "backend"
$backendInspection = (& docker inspect $backendContainer | ConvertFrom-Json)[0]
$documentMount = $backendInspection.Mounts | Where-Object { $_.Destination -eq "/app/data/documents" } | Select-Object -First 1
if ($null -eq $documentMount -or [string]::IsNullOrWhiteSpace($documentMount.Name)) {
    throw "Could not resolve the document Docker volume."
}
$documentVolume = $documentMount.Name

try {
    Write-Host "Stopping application services for a consistent restore..."
    Invoke-Native { docker compose -f $ComposeFile stop frontend backend ocr } "Could not stop application services"
    $applicationStopped = $true

    Invoke-Native { docker cp $databaseDump "${postgresContainer}:${databaseTempPath}" } "Could not stage database backup"
    Write-Host "Restoring PostgreSQL..."
    Invoke-Native {
        docker exec $postgresContainer sh -ceu 'PGPASSWORD="$POSTGRES_PASSWORD" pg_restore --clean --if-exists --no-owner --no-privileges --exit-on-error --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" "$1"' sh $databaseTempPath
    } "PostgreSQL restore failed"

    Write-Host "Restoring document volume..."
    Invoke-Native {
        docker create --name $archiveContainer --mount "type=volume,source=$documentVolume,target=/target" $ArchiveImage sh -ceu $restoreDocumentsCommand
    } "Could not create the document restore helper"
    Invoke-Native { docker cp $documentsArchive "${archiveContainer}:/tmp/documents.tar.gz" } "Could not stage document archive"
    Invoke-Native { docker start -a $archiveContainer } "Document restore failed"

    Write-Host "Starting application services..."
    Invoke-Native { docker compose -f $ComposeFile up -d backend frontend ocr } "Could not restart application services"
    $applicationStopped = $false
    Write-Host "Restore completed. Run smoke tests before allowing users back into the system."
}
catch {
    Write-Error $_
    if ($applicationStopped) {
        Write-Warning "Application services remain stopped to avoid serving a partially restored state. Inspect the error, then restore the safety backup."
    }
    throw
}
finally {
    & docker exec $postgresContainer rm -f -- $databaseTempPath 2>$null | Out-Null
    & docker inspect $archiveContainer 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        & docker rm -f $archiveContainer 2>$null | Out-Null
    }
}
