package com.telusko.airline.model;

import com.telusko.airline.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Named AppUser rather than User because "user" is a reserved word in Postgres, and a table
 * called user needs quoting in every hand written query. Renaming the class once is cheaper
 * than remembering the quotes forever.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String fullName;

    /** Shown to the assistant so it can address the passenger by their frequent flyer tier. */
    @Column(nullable = false)
    private String tier = "BLUE";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    private Instant createdAt = Instant.now();

    public AppUser(String email, String password, String fullName, Role role) {
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
    }
}
