# Test the local Java POC server with JWT authentication
$baseUrl = "http://localhost:8080"

Write-Host "Fetching JWT tokens from local POC server..." -ForegroundColor Cyan
try {
    $tokens = Invoke-RestMethod -Uri "$baseUrl/api/tokens" -Method Get
    Write-Host "Got tokens for roles: read, write, admin" -ForegroundColor Green
} catch {
    Write-Error "Could not connect to POC server at $baseUrl. Is it running?"
    Write-Error "Start with: mvn compile exec:java \"-Dexec.mainClass=com.interview.poc.Server\""
    exit 1
}

# Get vault credentials
Write-Host "`nFetching vault config..." -ForegroundColor Cyan
try {
    $vault = Invoke-RestMethod -Uri "$baseUrl/api/vault/credentials" -Method Get
    $vault | Format-List
} catch {
    Write-Warning "Could not fetch vault credentials: $_"
}

# List documents with READ role
Write-Host "`nListing documents (READ role)..." -ForegroundColor Cyan
try {
    $headers = @{ "Authorization" = "Bearer $($tokens.read)" }
    $docs = Invoke-RestMethod -Uri "$baseUrl/api/document" -Headers $headers -Method Get
    $docs.documents | Format-Table id, title, modifiedBy
} catch {
    Write-Warning "Could not list documents: $_"
}