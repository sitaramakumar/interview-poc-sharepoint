try {
    Import-Module PnP.PowerShell -RequiredVersion 1.12.0 -ErrorAction Stop
} catch {
    Write-Error "PnP.PowerShell 1.12.0 is not installed. Run: Install-Module PnP.PowerShell -RequiredVersion 1.12.0 -Scope CurrentUser -Force"
    exit 1
}

$tenant = "YOUR-TENANT.onmicrosoft.com"
$siteUrl = "https://YOUR-TENANT.sharepoint.com"
$clientId = $env:PnP_CLIENT_ID
$clientSecret = $env:PnP_CLIENT_SECRET
$certPath = $env:PnP_CERT_PATH
$certPassword = $env:PnP_CERT_PASSWORD

if ([string]::IsNullOrWhiteSpace($clientId)) {
    $clientId = Read-Host "Enter Entra app Application (client) ID"
}

if ([string]::IsNullOrWhiteSpace($clientId)) {
    Write-Error "Application (client) ID is required."
    exit 1
}

# Try certificate auth first, then fall back to client secret
if ($certPath -and (Test-Path $certPath)) {
    $securePassword = if ($certPassword) { ConvertTo-SecureString -String $certPassword -AsPlainText -Force } else { $null }
    try {
        Connect-PnPOnline -Url $siteUrl -ClientId $clientId -Tenant $tenant -CertificatePath $certPath -CertificatePassword $securePassword -ErrorAction Stop
        Write-Host "Connected using certificate to: $siteUrl" -ForegroundColor Green
    } catch {
        Write-Error ("Certificate auth failed. Original error:`n" + ($_ | Out-String))
        exit 1
    }
} elseif ($clientSecret) {
    try {
        Connect-PnPOnline -Url $siteUrl -ClientId $clientId -ClientSecret $clientSecret -WarningAction Ignore -ErrorAction Stop
        Write-Host "Connected using client secret to: $siteUrl" -ForegroundColor Yellow
    } catch {
        Write-Error ("PnP connection failed. Original error:`n" + ($_ | Out-String))
        exit 1
    }
} else {
    Write-Error "Either PnP_CERT_PATH (with PnP_CERT_PASSWORD) or PnP_CLIENT_SECRET must be provided."
    exit 1
}

$allGroups = @()
try {
    $allGroups = Get-PnPGroup -ErrorAction SilentlyContinue
} catch {
    Write-Host "No SharePoint groups found at this site." -ForegroundColor Yellow
}

if ($allGroups) {
    Write-Host "`nAvailable SharePoint groups:" -ForegroundColor Yellow
    $allGroups | Select-Object Title
} else {
    Write-Host "`nNo SharePoint groups found. Trying alternate site URLs..." -ForegroundColor Yellow
    $altUrls = @(
        "https://YOUR-TENANT.sharepoint.com/sites/team",
        "https://YOUR-TENANT.sharepoint.com/sites/YOUR-TENANT",
        "https://YOUR-TENANT.sharepoint.com/sites/demosites"
    )
    $foundSite = $false
    foreach ($altUrl in $altUrls) {
        try {
            Connect-PnPOnline -Url $altUrl -ClientId $clientId -ClientSecret $clientSecret -WarningAction Ignore -ErrorAction Stop
            $allGroups = Get-PnPGroup -ErrorAction SilentlyContinue
            if ($allGroups) {
                $siteUrl = $altUrl
                Write-Host "Found site with groups: $altUrl" -ForegroundColor Green
                $allGroups | Select-Object Title
                $foundSite = $true
                break
            }
        } catch {
            # Continue to next site
        }
    }
    if (-not $foundSite) {
        Write-Host "(No sites with groups found - requires admin permissions to enumerate all sites)" -ForegroundColor Gray
    }
}

$groups = @(
    "YOUR-TENANT Readers",
    "YOUR-TENANT Writers",
    "YOUR-TENANT Admins"
)

foreach ($groupName in $groups) {
    $group = $null
    if ($allGroups) {
        $group = $allGroups | Where-Object { $_.Title -eq $groupName }
        if (-not $group) {
            $group = $allGroups | Where-Object { $_.Title -like "*$groupName*" }
        }
    }
    
    if ($group) {
        try {
            Get-PnPProperty -ClientObject $group -Property Users
            Write-Host "`nGroup: $($group.Title)" -ForegroundColor Cyan
            $group.Users | Select-Object Title, LoginName, Email
        } catch {
            Write-Warning "Could not retrieve users for group '$($group.Title)': $_"
        }
    } else {
        Write-Warning "Group '$groupName' not found."
    }
}