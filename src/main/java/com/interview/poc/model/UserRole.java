package com.interview.poc.model;

public enum UserRole {
    READ("read", "GET"),
    WRITE("write", "GET,PUT"),
    ADMIN("admin", "GET,PUT,DELETE");

    private final String name;
    private final String allowedMethods;

    UserRole(String name, String allowedMethods) {
        this.name = name;
        this.allowedMethods = allowedMethods;
    }

    public boolean allows(String method) {
        if (method == null) {
            return false;
        }
        return allowedMethods.contains(method.toUpperCase());
    }

    public String getName() { return name; }
    public String getAllowedMethods() { return allowedMethods; }

    public static UserRole fromClientId(String clientId) {
        if (clientId == null) return READ;
        String lower = clientId.toLowerCase();
        if (lower.contains("admin")) return ADMIN;
        if (lower.contains("write")) return WRITE;
        return READ;
    }
}
