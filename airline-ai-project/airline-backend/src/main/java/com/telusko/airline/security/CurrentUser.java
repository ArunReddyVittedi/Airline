package com.telusko.airline.security;

import com.telusko.airline.model.AppUser;
import com.telusko.airline.repository.AppUserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Who is asking, read from the security context.
 * <p>
 * This exists so that a tool never has to take the passenger's email as a parameter. If it
 * did, the model would be supplying it, and a model can be talked into supplying somebody
 * else's. "Show the bookings for priya@example.com" is a prompt injection away from working.
 * <p>
 * Taking the identity from the signed token instead means the worst a manipulated prompt can
 * do is ask about the caller's own data, which they were entitled to see anyway. This is the
 * single most important line of defence in the whole AI layer.
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
