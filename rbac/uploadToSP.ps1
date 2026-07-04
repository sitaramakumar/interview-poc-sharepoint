# Upload document to SharePoint
param(
    [string]$ClientId = $env:PnP_CLIENT_ID,
    [string]$ClientSecret = $env:PnP_CLIENT_SECRET,
    [string]$SiteUrl = "https://YOUR-TENANT.sharepoint.com",
    [string]$LibraryName = "Documents",
    [string]$FilePath
)

if (-not $ClientId) { $ClientId = Read-Host "Enter Entra App Client ID" }
if (-not $ClientSecret) { $ClientSecret = Read-Host "Enter Client Secret" }

try {
    Connect-PnPOnline -Url $SiteUrl -ClientId $clientId -ClientSecret $ClientSecret -WarningAction Ignore -ErrorAction Stop
    Write-Host "Connected to SharePoint: $SiteUrl" -ForegroundColor Green
} catch {
    Write-Error "Connection failed: $_"
    exit 1
}

# Check if library exists
try {
    $library = Get-PnPList -Identity $LibraryName -ErrorAction Stop
    Write-Host "Library '$LibraryName' found" -ForegroundColor Green
} catch {
    Write-Warning "Library '$LibraryName' not found. Creating..."
    try {
        New-PnPList -Title $LibraryName -Template DocumentLibrary -ErrorAction Stop
        Write-Host "Created library: $LibraryName" -ForegroundColor Green
    } catch {
        Write-Error "Cannot create library (need permission): $_"
        exit 1
    }
}

if (-not $FilePath) {
    $FilePath = "data\test-doc.json"
}

if (Test-Path $FilePath) {
    $fileName = Split-Path $FilePath -Leaf
    Write-Host "Uploading: $FilePath" -ForegroundColor Cyan
    Add-PnPFile -Path $FilePath -Folder "/$LibraryName" -FileName $fileName
    Write-Host "Upload complete" -ForegroundColor Green
    
    Write-Host "`nDocument library contents:" -ForegroundColor Cyan
    Get-PnPListItem -List $LibraryName | Select-Object Id, @{n="Name";e={$_.FieldValues.FileLeafRef}}
} else {
    Write-Warning "File not found: $FilePath"
}