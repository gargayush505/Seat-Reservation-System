package com.paytm.seatreservation.security;

import com.paytm.seatreservation.exception.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final String adminToken;

    public AuthService(@Value("${app.auth.admin-token}") String adminToken) {
        this.adminToken = adminToken;
    }

    public TokenIdentity user(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "missing_token", "Bearer token is required");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "invalid_token", "Bearer token is empty");
        }
        // Take-home auth contract: the opaque bearer token is the user's identity.
        // Production deployment should replace this with JWT/OIDC signature validation.
        return new TokenIdentity(token);
    }

    public void requireAdmin(String authorization) {
        if (authorization == null || !authorization.equals("Bearer " + adminToken)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "admin_required", "Admin token is required");
        }
    }
}
