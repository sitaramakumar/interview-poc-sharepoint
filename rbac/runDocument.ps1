# SharePoint Document Operations - Complete Working Script

# Check if mock server is running
$mockUrl = "http://localhost:8080/api"
try {
    $null = Invoke-RestMethod -Uri "$mockUrl/tokens" -Method Get -ErrorAction Stop
    $useMock = $true
    Write-Host "Using LOCAL mock server" -ForegroundColor Green
} catch {
    $useMock = $false
    Write-Host "Using REAL SharePoint tenant" -ForegroundColor Yellow
}

if ($useMock) {
    # MOCK SERVER (always works)
    $tokens = Invoke-RestMethod -Uri "$mockUrl/tokens" -Method Get
    
    # Upload
    $doc = @{ title = "PowerShell Test"; content = "Created at $(Get-Date)" } | ConvertTo-Json
    $result = Invoke-RestMethod -Uri "$mockUrl/document/upload" -Method Put -Headers @{ "Authorization" = "Bearer $($tokens.write)" } -Body $doc -ContentType "application/json"
    Write-Host "Uploaded: $($result.message)" -ForegroundColor Cyan
    
    # Download/List
    $docs = Invoke-RestMethod -Uri "$mockUrl/document" -Headers @{ "Authorization" = "Bearer $($tokens.read)" }
    $docs.documents | Format-Table id, title, modifiedBy, modifiedAt
} else {
    # REAL SHAREPOINT
    $clientId = $env:PnP_CLIENT_ID
    $clientSecret = $env:PnP_CLIENT_SECRET
    
    if (-not $clientId -or -not $clientSecret) {
        Write-Warning "Set env vars: PnP_CLIENT_ID and PnP_CLIENT_SECRET"
        exit 1
    }
    
    $siteUrl = $env:PnP_SITE_URL
    if (-not $siteUrl) { $siteUrl = Read-Host "Site URL (e.g., https://YOUR-TENANT.sharepoint.com/sites/team)" }
    
    Connect-PnPOnline -Url $siteUrl -ClientId $clientId -ClientSecret $clientSecret -WarningAction Ignore
    
    # Try Documents library
    try {
        $items = Get-PnPListItem -List "Documents" -ErrorAction Stop
        Write-Host "Documents library found with $($items.Count) items" -ForegroundColor Green
    } catch {
        Write-Warning "No 'Documents' library. Available:"
        Get-PnPList | Where-Object {$_.BaseTemplate -eq 101} | Select-Object Title, ItemCount
    }
}