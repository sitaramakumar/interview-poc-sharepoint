# SharePoint Document Upload via Microsoft Graph
param(
    [string]$ClientId = $env:GRAPH_CLIENT_ID,
    [string]$ClientSecret = $env:GRAPH_CLIENT_SECRET,
    [string]$DocTitle,
    [string]$DocContent
)

if (-not $ClientId) { throw "ClientId is required. Pass -ClientId or set GRAPH_CLIENT_ID." }
if (-not $ClientSecret) { throw "ClientSecret is required. Pass -ClientSecret or set GRAPH_CLIENT_SECRET." }

$tenant = "YOUR-TENANT.onmicrosoft.com"
$siteHostName = "YOUR-TENANT.sharepoint.com"
$body = @{
    grant_type    = "client_credentials"
    client_id     = $ClientId
    client_secret = $ClientSecret
    scope         = "https://graph.microsoft.com/.default"
}
$token = Invoke-RestMethod -Uri "https://login.microsoftonline.com/$tenant/oauth2/v2.0/token" -Method Post -Body $body
$headers = @{ Authorization = "Bearer $($token.access_token)" }

# Get the root site that contains /Shared Documents
$site = Invoke-RestMethod -Uri "https://graph.microsoft.com/v1.0/sites/${siteHostName}:/" -Headers $headers

if ($site) {
# Use the full site ID in hostname,siteId,webId format for Graph API
     $siteId = $site.id
     Write-Host "Using site: $($site.displayName) - $siteId" -ForegroundColor Green
     
     # Get document library
     try {
         $drives = Invoke-RestMethod -Uri "https://graph.microsoft.com/v1.0/sites/$($siteId)/drive/root/children" -Headers $headers
        Write-Host "`nDocument library contents:" -ForegroundColor Cyan
        $drives.value | Select-Object name, size, lastModifiedDateTime | Format-Table
    } catch {
        Write-Warning "Cannot access document library: $_"
    }
    
    # Upload document
    if ($DocTitle -and $DocContent) {
        $fileName = "$($DocTitle -replace '[^a-zA-Z0-9]', '_').txt"
        $tempFile = "$env:TEMP\$fileName"
        $DocContent | Set-Content $tempFile
        
try {
             $uploadUrl = "https://graph.microsoft.com/v1.0/sites/$($siteId)/drive/root:/$([uri]::EscapeDataString($fileName)):/content"
            $content = [System.IO.File]::ReadAllBytes($tempFile)
            $uploadResult = Invoke-RestMethod -Uri $uploadUrl -Method Put -Headers $headers -Body $content -ContentType "text/plain"
            Write-Host "Uploaded: $fileName" -ForegroundColor Green
            Remove-Item $tempFile
        } catch {
            Write-Warning "Upload failed: $_"
        }
    }
} else {
    Write-Warning "No sites found. Check permissions (need Sites.ReadWrite.All)"
}