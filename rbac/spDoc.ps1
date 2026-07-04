# SharePoint Upload/Download Script
# Works in two modes:
# 1. Local mock: .\spDoc.ps1 -UseMock
# 2. Real SharePoint: .\spDoc.ps1 -ClientId <id> -ClientSecret <sec> -SiteUrl <url>

param(
    [switch]$UseMock,
    [string]$ClientId,
    [string]$ClientSecret,
    [string]$SiteUrl = "https://YOUR-TENANT.sharepoint.com/sites/team",
    [string]$LibraryName = "Documents",
    [string]$DocTitle,
    [string]$DocContent,
    [string]$DownloadId
)

if ($UseMock) {
    $baseUrl = "http://localhost:8080/api"
    try {
        $tokens = Invoke-RestMethod -Uri "$baseUrl/tokens" -Method Get
    } catch {
        Write-Warning "Mock server not running. Start with: docker compose up --build"
        exit 1
    }
} else {
    if (-not $ClientId) { $ClientId = Read-Host "Entra App Client ID" }
    if (-not $ClientSecret) { $ClientSecret = Read-Host "Client Secret" }
    Connect-PnPOnline -Url $SiteUrl -ClientId $ClientId -ClientSecret $ClientSecret -WarningAction Ignore
}

if ($DownloadId) {
    if ($UseMock) {
        Invoke-RestMethod -Uri "$baseUrl/document/$DownloadId" -Headers @{ "Authorization" = "Bearer $($tokens.read)" }
    } else {
        Get-PnPFile -Url "/$LibraryName/$DownloadId" -Path "downloads" -FileName $DownloadId -AsFile -Force
    }
} elseif ($DocTitle) {
    if ($UseMock) {
        $json = @{ title = $DocTitle; content = $DocContent } | ConvertTo-Json
        Invoke-RestMethod -Uri "$baseUrl/document/upload" -Method Put -Headers @{ "Authorization" = "Bearer $($tokens.write)" } -Body $json -ContentType "application/json"
    } else {
        $tempFile = "$env:TEMP\$($DocTitle -replace '[^a-zA-Z0-9]', '_').json"
        @{ title = $DocTitle; content = $DocContent; modifiedBy = "PowerShell" } | ConvertTo-Json | Set-Content $tempFile
        Add-PnPFile -Path $tempFile -Folder "/$LibraryName" -FileName "$($DocTitle -replace '[^a-zA-Z0-9]', '_').json"
    }
} else {
    if ($UseMock) {
        Invoke-RestMethod -Uri "$baseUrl/document" -Headers @{ "Authorization" = "Bearer $($tokens.read)" }
    } else {
        Get-PnPListItem -List $LibraryName | Select-Object Id, @{n="Title";e={$_.FieldValues.Title}} | Format-Table
    }
}