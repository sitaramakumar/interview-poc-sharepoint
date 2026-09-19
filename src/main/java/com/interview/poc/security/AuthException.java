package com.interview.poc.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Missing/invalid/expired token, or wrong login credentials. */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class AuthException extends RuntimeException {
    public AuthException(String msg) { super(msg); }
}
