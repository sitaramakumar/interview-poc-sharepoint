package com.interview.poc;

import com.interview.poc.auth.IdentityProvider;
import com.interview.poc.model.UserRole;

public class TokenGenerator {
    public static void main(String[] args) {
        System.out.println("=== Pre-generated JWT Tokens (valid 1 hour) ===");
        System.out.println("READ_TOKEN=" + gen("read-client-poc-001", UserRole.READ));
        System.out.println("WRITE_TOKEN=" + gen("write-client-poc-002", UserRole.WRITE));
        System.out.println("ADMIN_TOKEN=" + gen("admin-client-poc-003", UserRole.ADMIN));
    }

    private static String gen(String clientId, UserRole role) {
        return IdentityProvider.issueToken(clientId, role, 3600);
    }
}
