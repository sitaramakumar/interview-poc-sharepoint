# SharePoint Document Operations (Mock Local + Real SharePoint)
param(
    [switch]$UseMock,
    [string]$ClientId,
    [string]$ClientSecret,
    [string]$SiteUrl = "https://localhost:4567/sharepoint/sites/demo"
)

if ($UseMock) {
    Write-Host "Using MOCK SharePoint server at: $SiteUrl" -ForegroundColor Cyan
    $baseUrl = $SiteUrl
} else {
    Write-Host "Using REAL SharePoint tenant" -ForegroundColor Yellow
    if (-not $ClientId) { $ClientId = Read-Host "Enter Entra app Client ID" }
    if (-not $ClientSecret) { $ClientSecret = Read-Host "Enter Entra app Client Secret" }
    if (-not $ClientId) { Write-Error "Client ID required"; exit 1 }
    if (-not $ClientSecret) { Write-Error "Client Secret required"; exit 1 }
    
    # Connect to real SharePoint
    try {
        Connect-PnPOnline -Url $SiteUrl -ClientId $ClientId -ClientSecret $ClientSecret -WarningAction Ignore -ErrorAction Stop
        $baseUrl = $SiteUrl
    } catch {
        Write-Error "PnP connection failed: $_"
        exit 1
    }
}

# Get JWT tokens (for mock) or use direct PnP cmdlets
if ($UseMock) {
    Write-Host "`n1. Getting JWT tokens..." -ForegroundColor Cyan
    try {
        $tokens = Invoke-RestMethod -Uri "$baseUrl/api/tokens" -Method Get
        Write-Host "Tokens acquired for roles: read, write, admin" -ForegroundColor Green
    } catch {
        Write-Warning "Could not connect to mock server. Is it running on $baseUrl?"
        Write-Host "Start with: docker compose up --build" -ForegroundColor Gray
        exit 1
    }
    
    Write-Host "`n2. Uploading document (WRITE role)..." -ForegroundColor Cyan
    try {
        $uploadBody = @{
            title = "POC Test Document"
            content = "Created via PowerShell at $(Get-Date)"
        } | ConvertTo-Json
        $result = Invoke-RestMethod -Uri "$baseUrl/api/document/upload" -Method Put -Headers @{ "Authorization" = "Bearer $($tokens.write)" } -ContentType "application/json" -Body $uploadBody
        Write-Host "Uploaded: $($result.message)" -ForegroundColor Green
    } catch {
        Write-Warning "Upload failed: $_"
    }
    
    Write-Host "`n3. Listing documents (READ role)..." -ForegroundColor Cyan
    try {
        $docs = Invoke-RestMethod -Uri "$baseUrl/api/document" -Headers @{ "Authorization" = "Bearer $($tokens.read)" }
        $docs.documents | Format-Table id, title, modifiedBy
    } catch {
        Write-Warning "List failed: $_"
    }
} else {
    # Real SharePoint - use PnP cmdlets
    Write-Host "`n1. Listing available document libraries..." -ForegroundColor Cyan
    Get-PnPList | Where-Object { $_.BaseTemplate -eq 101 } | Select-Object Title
    
    Write-Host "`n2. Listing documents from default library..." -ForegroundColor Cyan
    Get-PnPListItem -List "Documents" | Select-Object Id, @{n="Title";e={$_.FieldValues.Title}}
}