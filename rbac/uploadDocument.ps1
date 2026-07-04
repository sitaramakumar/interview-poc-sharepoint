# SharePoint Document Operations - Real Tenant
# Prerequisites:
# 1. Entra App Registration with SharePoint permissions
# 2. Site URL where you have access

param(
    [string]$ClientId,
    [string]$ClientSecret,
    [string]$SiteUrl = "https://YOUR-TENANT.sharepoint.com",
    [string]$LibraryName = "Documents"
)

# Get credentials
if (-not $ClientId) { $ClientId = Read-Host "Entra App Client ID (press Enter to use: YOUR-CLIENT-ID)" }
if (-not $ClientId) { $ClientId = "YOUR-CLIENT-ID" }

if (-not $ClientSecret) { $ClientSecret = Read-Host "Client Secret (press Enter to use stored value)" }
if (-not $ClientSecret) { $ClientSecret = "Jaihanuman@1" }

# Connect
Connect-PnPOnline -Url $SiteUrl -ClientId $ClientId -ClientSecret $ClientSecret -WarningAction Ignore

# Try to get existing library or documents
try {
    $items = Get-PnPListItem -List $LibraryName -ErrorAction Stop
    Write-Host "Found $LibraryName library with $($items.Count) items" -ForegroundColor Green
    $items | Select-Object Id, @{n="Name";e={$_.FieldValues.FileLeafRef}} | Format-Table
} catch {
    Write-Host "No '$LibraryName' library. Checking available libraries..." -ForegroundColor Yellow
    Get-PnPList | Where-Object {$_.BaseTemplate -eq 101} | Select-Object Title, ItemCount, Url
}

# Quick upload function
function Upload-Doc($title, $content, $path = ".") {
    $fileName = "$($title -replace '[^a-zA-Z0-9]', '_').txt"
    $tempFile = Join-Path $path $fileName
    "Uploaded by PowerShell at $(Get-Date)" | Set-Content $tempFile
    Add-PnPFile -Path $tempFile -Folder "/$LibraryName" -FileName $fileName
    Remove-Item $tempFile
    Write-Host "Uploaded: $fileName" -ForegroundColor Green
}

# Upload menu
Write-Host "`nCommands: Upload-Doc '<title>' '<content>'" -ForegroundColor Cyan
Write-Host "Example: Upload-Doc 'MyDoc' 'Hello World'"