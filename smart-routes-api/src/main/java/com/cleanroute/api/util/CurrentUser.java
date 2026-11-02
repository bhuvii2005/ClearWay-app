package com.cleanroute.api.util;

import com.cleanroute.api.exception.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * The id of the user making the current request, taken only from the verified JWT.
 * Controllers never trust a user id sent in a request body.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UUID userId) {
            return userId;
        }
        throw ApiException.unauthorized("Authentication required");
    }
}
