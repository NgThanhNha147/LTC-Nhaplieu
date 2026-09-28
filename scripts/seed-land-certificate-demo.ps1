param(
    [string]$BaseUrl = "http://localhost:8080/api/v1"
)

$ErrorActionPreference = "Stop"
$api = $BaseUrl.TrimEnd('/')
$configPath = Join-Path $PSScriptRoot "land-certificate-demo.json"
$config = Get-Content -LiteralPath $configPath -Encoding UTF8 -Raw | ConvertFrom-Json

function Invoke-Json {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Uri,
        [object]$Body
    )

    $parameters = @{
        Method = $Method
        Uri = $Uri
        ContentType = "application/json; charset=utf-8"
    }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 15
        $parameters.Body = [Text.Encoding]::UTF8.GetBytes($json)
    }
    return Invoke-RestMethod @parameters
}

function Has-Property {
    param([object]$Object, [string]$Name)
    return $Object.PSObject.Properties.Name -contains $Name
}

$existing = @()
foreach ($candidate in (Invoke-Json -Method Get -Uri "$api/templates")) {
    if ($candidate.code -eq $config.template.code) { $existing += $candidate }
}
if ($existing.Count -gt 0) {
    Write-Host "Template $($config.template.code) already exists: $($existing[0].id)"
    exit 0
}

Write-Host "1/5 Create template"
$template = Invoke-Json -Method Post -Uri "$api/templates" -Body @{
    code = $config.template.code
    name = $config.template.name
    description = $config.template.description
}
$versionId = $template.currentVersionId

Write-Host "2/5 Create groups"
$groups = @{}
foreach ($definition in $config.groups) {
    $group = Invoke-Json -Method Post -Uri "$api/template-versions/$versionId/groups" -Body @{
        code = $definition.code
        label = $definition.label
        displayOrder = $definition.displayOrder
        columnCount = $definition.columnCount
        collapsible = $definition.collapsible
        defaultCollapsed = $false
        repeatable = $false
    }
    $groups[$definition.key] = $group.id
}

Write-Host "3/5 Create fields"
$orders = @{}
foreach ($definition in $config.fields) {
    if (-not $orders.ContainsKey($definition.group)) { $orders[$definition.group] = 0 }
    $orders[$definition.group]++
    $body = @{
        groupId = $groups[$definition.group]
        fieldCode = $definition.code
        label = $definition.label
        dataType = $definition.dataType
        componentType = $definition.componentType
        required = (Has-Property $definition "required") -and $definition.required
        uniqueValue = (Has-Property $definition "uniqueValue") -and $definition.uniqueValue
        indexed = (Has-Property $definition "indexed") -and $definition.indexed
        searchable = (Has-Property $definition "searchable") -and $definition.searchable
        sortable = (Has-Property $definition "searchable") -and $definition.searchable
        exportable = $true
        displayOrder = $orders[$definition.group]
        gridSpan = $definition.gridSpan
        readOnly = $false
        hidden = $false
        validations = @()
    }
    if (Has-Property $definition "maxLength") { $body.maxLength = $definition.maxLength }
    if (Has-Property $definition "precision") {
        $body.precision = $definition.precision
        $body.scale = $definition.scale
    }
    if (Has-Property $definition "lookupCode") {
        $body.lookup = @{
            lookupCode = $definition.lookupCode
            selectionMode = "SINGLE"
            allowClear = $true
            autocomplete = $false
            minSearchLength = 0
        }
    }
    if (Has-Property $definition "minValue") {
        $body.validations = @(@{
            ruleType = "MIN_VALUE"
            ruleConfig = @{ value = $definition.minValue }
            errorMessage = $config.messages.minimumValue
            displayOrder = 0
        })
    }
    Invoke-Json -Method Post -Uri "$api/template-versions/$versionId/fields" -Body $body | Out-Null
}

Write-Host "4/5 Validate and generate PostgreSQL table"
$validation = Invoke-Json -Method Post -Uri "$api/template-versions/$versionId/validate"
if (-not $validation.valid) {
    throw "Metadata validation failed: $($validation.errors | ConvertTo-Json -Depth 10)"
}
Invoke-Json -Method Post -Uri "$api/template-versions/$versionId/generate" | Out-Null

Write-Host "5/5 Publish template"
Invoke-Json -Method Post -Uri "$api/template-versions/$versionId/publish" | Out-Null

Write-Host "Demo template ready: $($config.template.code)"
Write-Host "Open: http://localhost:5173/workspaces/$($config.template.code)/new"
