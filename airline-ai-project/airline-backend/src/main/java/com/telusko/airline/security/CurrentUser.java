package com.telusko.airline.security;

import com.telusko.airline.model.AppUser;
import com.telusko.airline.repository.AppUserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated identity from Spring Security. Tools use this identity instead
 * of model-supplied account identifiers, enforcing passenger-level data isolation.
 */
@Component
public class CurrentUser {

    private final AppUserRepository users;

    public CurrentUser(AppUserRepository users) {
        this.users = users;
    }

    /** The signed in passenger's email, or null when the request is anonymous. */
    public String emailOrNull() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserDetails details)) {
            return null;
        }
        return details.getUsername();
    }

    public String requireEmail() {
        String email = emailOrNull();
        if (email == null) {
            throw new IllegalStateException("No signed in user on this request");
        }
        return email;
    }

    public AppUser require() {
        return users.findByEmail(requireEmail())
                .orElseThrow(() -> new IllegalStateException("Signed in user no longer exists"));
    }
}
