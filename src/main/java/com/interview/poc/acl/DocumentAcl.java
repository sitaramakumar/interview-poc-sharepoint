package com.interview.poc.acl;

import java.util.List;

/** Resource-level ACL for one document: who owns it, and which roles besides the owner may access it. */
public class DocumentAcl {
    private String ownerId;
    private List<String> allowedRoles;

    public DocumentAcl() {}

    public DocumentAcl(String ownerId, List<String> allowedRoles) {
        this.ownerId = ownerId;
        this.allowedRoles = allowedRoles;
    }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public List<String> getAllowedRoles() { return allowedRoles; }
    public void setAllowedRoles(List<String> allowedRoles) { this.allowedRoles = allowedRoles; }
}
