$siteUrl = "https://YOUR-TENANT.sharepoint.com"
$adminUser = "admin@YOUR-TENANT.com"
$csvPath = ".\rbac.csv"

Connect-PnPOnline -Url $siteUrl -Interactive

$roleMap = @{
    Read   = "Read"
    Write  = "Contribute"
    Admin  = "Full Control"
}

$csv = Import-Csv $csvPath

foreach ($roleName in @("Read", "Write", "Admin")) {
    $groupName = "YOUR-TENANT $roleName"
    $permissionLevel = $roleMap[$roleName]

    if (-not (Get-PnPGroup -Identity $groupName -ErrorAction SilentlyContinue)) {
        New-PnPGroup -Title $groupName -Owner $adminUser -Description "$roleName access to $siteUrl"
    }

    Set-PnPGroupPermissions -Identity $groupName -AddRoleDefinition $permissionLevel

    $users = $csv | Where-Object { $_.Role -eq $roleName } | Select-Object -ExpandProperty User

    foreach ($user in $users) {
        Ensure-PnPUser -LoginName $user -ErrorAction SilentlyContinue
        Add-PnPUserToGroup -LoginName $user -Identity $groupName
    }
}