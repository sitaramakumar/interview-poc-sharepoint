# Test the LOCAL mock SharePoint server
$baseUrl = "http://localhost:8080"

Write-Host "Testing mock SharePoint POC server at: $baseUrl" -ForegroundColor Cyan

# Check if server is running
try {
    $null = Invoke-RestMethod -Uri "$baseUrl/api/tokens" -Method Get -ErrorAction Stop
} catch {
    Write-Warning "Server not responding. Start with: docker compose up --build"
    exit 1
}

# Get tokens
Write-Host "`n1. Getting JWT tokens..." -ForegroundColor Cyan
$tokens = Invoke-RestMethod -Uri "$baseUrl/api/tokens" -Method Get
$env:READ_TOKEN = $tokens.read
$env:WRITE_TOKEN = $tokens.write
$env:ADMIN_TOKEN = $tokens.admin
Write-Host "Tokens acquired. Roles: read, write, admin" -ForegroundColor Green

# Upload document
Write-Host "`n2. Uploading document (WRITE role)..." -ForegroundColor Cyan
$uploadBody = @{
    title = "POC Test Document"
    content = "Created via PowerShell at $(Get-Date)"
} | ConvertTo-Json
$result = Invoke-RestMethod -Uri "$baseUrl/api/document/upload" -Method Put -Headers @{ "Authorization" = "Bearer $($env:WRITE_TOKEN)" } -ContentType "application/json" -Body $uploadBody
Write-Host "Uploaded: $($result.message)" -ForegroundColor Green

# List documents
Write-Host "`n3. Listing documents (READ role)..." -ForegroundColor Cyan
$docs = Invoke-RestMethod -Uri "$baseUrl/api/document" -Headers @{ "Authorization" = "Bearer $($env:READ_TOKEN)" }
$docs.documents | Format-Table id, title, modifiedBy -AutoSize

# Show vault info
Write-Host "`n4. Vault configuration (simulated):" -ForegroundColor Cyan
Invoke-RestMethod -Uri "$baseUrl/api/vault/credentials" | Format-List