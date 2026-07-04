# Azure CLI deployment script for interview-poc
# Prereqs: Azure CLI, logged in with 'az login'

param(
    [string]$resourceGroup = "poc-rg",
    [string]$location = "eastus",
    [string]$appName = "interview-poc-app",
    [string]$imageName = "interview-poc:latest"
)

# Create resource group
az group create --name $resourceGroup --location $location

# Build and push image to Azure Container Registry
$acrName = "$($appName)acr"
az acr create --resource-group $resourceGroup --name $acrName --sku Basic --admin-enabled true

# Tag and push image (requires Docker)
docker tag $imageName "$acrName.azurecr.io/$imageName"
az acr login --name $acrName
docker push "$acrName.azurecr.io/$imageName"

# Deploy to Azure Container Apps
az containerapp env create --name "$($appName)-env" --resource-group $resourceGroup --location $location
az containerapp create `
    --name $appName `
    --resource-group $resourceGroup `
    --environment "$($appName)-env" `
    --image "$acrName.azurecr.io/$imageName" `
    --cpu 1 --memory 1Gi `
    -e server.port=8080 `
    -e jwt.secret="YOUR-JWT-SECRET" `
    --ingress 'external' --target-port 8080

# Get the URL
az containerapp show --name $appName --resource-group $resourceGroup --query properties.configuration.ingress.fqdn -o tsv