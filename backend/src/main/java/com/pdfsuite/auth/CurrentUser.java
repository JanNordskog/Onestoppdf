package com.pdfsuite.auth;

import com.pdfsuite.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class CurrentUser {

    private CurrentUser() {}

    public static UUID idOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UUID id)) {
            return null;
        }
        return id;
    }

    public static UUID idOrThrow() {
        UUID id = idOrNull();
        if (id == null) {
            throw ApiException.unauthorized("Sign in required");
        }
        return id;
    }
}
