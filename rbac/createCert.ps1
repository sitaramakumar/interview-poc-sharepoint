# Create mock certificates for the POC
$certDir = "C:\Users\Ravi Shankar\AppData\Local/Programs/Microsoft VS Code/InterviewProject/interview-poc\data\certificates"

if (-not (Test-Path $certDir)) {
    New-Item -ItemType Directory -Path $certDir -Force
}

# Generate self-signed certificate for local auth simulation
$cert = New-SelfSignedCertificate -Type Custom -Subject "CN=POC Client Certificate" -KeyUsage DigitalSignature -FriendlyName "POC Client Auth" -CertStoreLocation "Cert:\CurrentUser\My" -NotAfter (Get-Date).AddYears(1)

# Export to PEM format
$password = ConvertTo-SecureString -String "poc-password" -AsPlainText -Force
Export-PfxCertificate -Cert "Cert:\CurrentUser\My\$($cert.Thumbprint)" -FilePath "$certDir\client-cert.pfx" -Password $password

# Convert PFX to PEM (OpenSSL required)
& openssl pkcs12 -in "$certDir\client-cert.pfx" -out "$certDir\client-cert.pem" -nokeys -passin pass:poc-password -passout pass:poc-password 2>$null
& openssl pkcs12 -in "$certDir\client-cert.pfx" -out "$certDir\client-key.pem" -nocerts -passin pass:poc-password -passout pass:poc-password 2>$null
& openssl rsa -in "$certDir\client-key.pem" -out "$certDir\client-key.pem" -passin pass:poc-password -passout pass:poc-password 2>$null

Write-Host "Certificates created in $certDir" -ForegroundColor Green
Write-Host "Set these env vars for auth:" -ForegroundColor Yellow
Write-Host "  `$env:PnP_CERT_PATH='$certDir\client-cert.pem'" -ForegroundColor Gray
Write-Host "  `$env:PnP_KEY_PATH='$certDir\client-key.pem'" -ForegroundColor Gray
Write-Host "  `$env:PnP_CERT_PASSWORD='poc-password'" -ForegroundColor Gray