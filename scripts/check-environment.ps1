$ErrorActionPreference = "Stop"

function Test-Command {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string[]]$VersionArguments
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $command) {
        Write-Host "[MISSING] $Name" -ForegroundColor Yellow
        return $false
    }

    # Some tools (notably Java) print version information to stderr.
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        $version = & $command.Source @VersionArguments 2>&1 | Select-Object -First 1
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }
    if ([string]::IsNullOrWhiteSpace([string]$version)) {
        $version = $command.Source
    }
    Write-Host "[OK] $Name - $version" -ForegroundColor Green
    return $true
}

Write-Host "Checking Dynamic Form Platform prerequisites..."

$dockerOk = Test-Command -Name "docker" -VersionArguments @("--version")
$javaOk = Test-Command -Name "java" -VersionArguments @("-version")
$nodeOk = Test-Command -Name "node" -VersionArguments @("--version")
$npmOk = Test-Command -Name "npm" -VersionArguments @("--version")

if ($dockerOk) {
    try {
        $composeVersion = docker compose version 2>&1 | Select-Object -First 1
        Write-Host "[OK] Docker Compose - $composeVersion" -ForegroundColor Green
    }
    catch {
        Write-Host "[MISSING] Docker Compose v2" -ForegroundColor Yellow
    }
}

if (-not ($dockerOk -or ($javaOk -and $nodeOk -and $npmOk))) {
    Write-Host "Install Docker, or install both the Java and Node.js development toolchains." -ForegroundColor Red
    exit 1
}

Write-Host "Environment check completed. Missing local tools are acceptable when their services run in Docker."
