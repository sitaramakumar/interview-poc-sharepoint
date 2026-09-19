package com.interview.poc.acl;

import com.interview.poc.model.UserRole;

/**
 * Pure resource-level access decision, kept separate from SharePointClient so
 * it's testable without a live Graph API dependency. DocumentApi's coarse
 * role/method check (role.allows(method)) answers "can this role call this
 * HTTP verb at all"; this answers "can this specific caller touch this
 * specific document" — the check a blanket @PreAuthorize("hasRole(...)")
 * can't express, because the decision depends on data attached to the
 * resource (its owner, its allowed roles), not just the endpoint.
 */
public final class DocumentAccessGuard {

    private DocumentAccessGuard() {}

    /**
     * No ACL record means this document was never tagged by this app (e.g.
     * pre-existing SharePoint content, or anything uploaded before this
     * feature existed) — fail open, so untagged content stays gated by the
     * coarse role/method check alone, exactly as it was before ACLs existed.
     */
    public static boolean allows(DocumentAcl acl, String callerId, UserRole callerRole) {
        if (acl == null) {
            return true;
        }
        if (callerId != null && callerId.equals(acl.getOwnerId())) {
            return true;
        }
        if (acl.getAllowedRoles() == null) {
            return false;
        }
        return acl.getAllowedRoles().stream()
                .anyMatch(role -> role.equalsIgnoreCase(callerRole.name()));
    }

    public static void check(DocumentAcl acl, String callerId, UserRole callerRole, String documentId) {
        if (!allows(acl, callerId, callerRole)) {
            throw new AccessDeniedException(
                    "Caller '" + callerId + "' with role '" + callerRole.getName()
                            + "' is not the owner of document '" + documentId
                            + "' and that role is not in its allowed roles");
        }
    }
}
