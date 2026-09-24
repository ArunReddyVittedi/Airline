package com.telusko.airline.controller;

import com.telusko.airline.dto.AuthDtos.LoginRequest;
import com.telusko.airline.dto.AuthDtos.RegisterRequest;
import com.telusko.airline.dto.AuthDtos.TokenResponse;
import com.telusko.airline.enums.Role;
import com.telusko.airline.model.AppUser;
import com.telusko.airline.repository.AppUserRepository;
import com.telusko.airline.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AppUserRepository users, PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        if (users.existsByEmail(request.email())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new com.telusko.airline.dto.ApiDtos.ApiError(
                            "email_taken", "That email is already registered."));
        }

        AppUser user = new AppUser(request.email(),
                passwordEncoder.encode(request.password()),
                request.fullName(),
                Role.USER);

        users.save(user);

        // Signed in straight after registering. Asking someone to log in again immediately
        // adds a step and no security.
        return ResponseEntity.status(HttpStatus.CREATED).body(tokenFor(user));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException ex) {
            // One message for a wrong password and for an unknown email, on purpose. Telling
            // the caller which one it was turns the login form into a way of discovering
            // whether an address has an account here.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new com.telusko.airline.dto.ApiDtos.ApiError(
                            "bad_credentials", "Email or password is incorrect."));
        }

        AppUser user = users.findByEmail(request.email()).orElseThrow();
        return ResponseEntity.ok(tokenFor(user));
    }

    private TokenResponse tokenFor(AppUser user) {
        return new TokenResponse(
                jwtService.issue(user.getEmail(), user.getRole().name()),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getTier());
    }
}
