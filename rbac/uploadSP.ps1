# SharePoint Upload/Download - Real Tenant Support
# Uses: Shared Documents library

param(
    [string]$ClientId = $env:PnP_CLIENT_ID,
    [string]$ClientSecret = $env:PnP_CLIENT_SECRET,
    [string]$SiteUrl = "https://YOUR-TENANT.sharepoint.com",
    [Parameter(ValueFromRemainingArguments=$true)][string[]]$UploadFiles
)

# Connect to SharePoint
if (-not $ClientId) { $ClientId = "YOUR-CLIENT-ID" }
if (-not $ClientSecret) { $ClientSecret = Read-Host "Client Secret" }

# Try connection
try {
    Connect-PnPOnline -Url $SiteUrl -ClientId $ClientId -ClientSecret $ClientSecret -WarningAction Ignore -ErrorAction Stop
} catch {
    # Try mock server
    try {
        $mock = Invoke-RestMethod -Uri "http://localhost:8080/api/tokens" -ErrorAction Stop
        Write-Host "Using mock server (real SharePoint connection failed)" -ForegroundColor Yellow
        $tokens = $mock
        $useMock = $true
    } catch {
        Write-Error "Cannot connect to SharePoint or mock server"
        exit 1
    }
}

if ($useMock) {
    foreach ($f in $UploadFiles) {
        if (Test-Path $f) {
            $json = Get-Content $f | ConvertFrom-Json
            Invoke-RestMethod -Uri "http://localhost:8080/api/document/upload" -Method Put -Headers @{ "Authorization" = "Bearer $($tokens.write)" } -Body (ConvertTo-Json $json) -ContentType "application/json"
            Write-Host "Uploaded: $f" -ForegroundColor Green
        }
    }
    return
}

# Real SharePoint - Shared Documents library
$library = "Documents"  # Default
try {
    $items = Get-PnPListItem -List $library -ErrorAction Stop
    Write-Host "Library '$library' has $($items.Count) items" -ForegroundColor Green
} catch {
    # Try "Shared Documents" 
    try {
        $items = Get-PnPListItem -List "Shared Documents" -ErrorAction Stop
        $library = "Shared Documents"
    } catch {
        Write-Warning "No accessible document library. Check permissions (need Sites.ReadWrite.All)"
    }
}

foreach ($f in $UploadFiles) {
    if (Test-Path $f) {
        Add-PnPFile -Path $f -Folder "/$library" -FileName (Split-Path $f -Leaf)
        Write-Host "Uploaded: $f to $library" -ForegroundColor Green
    }
}