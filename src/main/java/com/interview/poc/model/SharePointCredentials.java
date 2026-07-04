package com.interview.poc.model;

public class SharePointCredentials {
    private String siteUrl;
    private String clientId;
    private String tenantId;
    private String clientSecret;
    private String libraryPath;
    private String certificatePath;
    private String keyPath;
    private String username;
    private String password;

    public SharePointCredentials() {}

    public SharePointCredentials(String siteUrl, String clientId, String tenantId,
                                 String certificatePath, String keyPath, String username, String password) {
        this(siteUrl, clientId, tenantId, null, "/Shared Documents", certificatePath, keyPath, username, password);
    }

    public SharePointCredentials(String siteUrl, String clientId, String tenantId, String clientSecret,
                                 String libraryPath, String certificatePath, String keyPath,
                                 String username, String password) {
        this.siteUrl = siteUrl;
        this.clientId = clientId;
        this.tenantId = tenantId;
        this.clientSecret = clientSecret;
        this.libraryPath = libraryPath;
        this.certificatePath = certificatePath;
        this.keyPath = keyPath;
        this.username = username;
        this.password = password;
    }

    public String getSiteUrl() { return siteUrl; }
    public void setSiteUrl(String siteUrl) { this.siteUrl = siteUrl; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getLibraryPath() { return libraryPath; }
    public void setLibraryPath(String libraryPath) { this.libraryPath = libraryPath; }
    public String getCertificatePath() { return certificatePath; }
    public void setCertificatePath(String certificatePath) { this.certificatePath = certificatePath; }
    public String getKeyPath() { return keyPath; }
    public void setKeyPath(String keyPath) { this.keyPath = keyPath; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
