package com.interview.poc.acl;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown by DocumentAccessGuard when a caller clears the coarse role/method
 * check in DocumentApi but fails the fine-grained, per-document ACL check —
 * distinct from DocumentApi.ForbiddenException so the two enforcement layers
 * (method-level vs resource-level) stay visibly separate rather than sharing
 * one exception type.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
