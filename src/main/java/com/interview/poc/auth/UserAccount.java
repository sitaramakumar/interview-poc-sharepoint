package com.interview.poc.auth;

public class UserAccount {
    private String clientId;
    private String passwordSalt;
    private String passwordHash;
    private String role;

    public UserAccount() {}

    public UserAccount(String clientId, String passwordSalt, String passwordHash, String role) {
        this.clientId = clientId;
        this.passwordSalt = passwordSalt;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getPasswordSalt() { return passwordSalt; }
    public void setPasswordSalt(String passwordSalt) { this.passwordSalt = passwordSalt; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
