package com.telusko.airline.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JsonAuthEntryPoint jsonAuthEntryPoint;
    private final String allowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          JsonAuthEntryPoint jsonAuthEntryPoint,
                          @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.jsonAuthEntryPoint = jsonAuthEntryPoint;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // No cookies and no sessions, so there is no session for an attacker to
                // ride. Leaving CSRF on would only block our own JSON calls.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Only the original request is authorised. The container's other
                        // dispatch types are not separate requests and must not be checked
                        // again.
                        //
                        // ASYNC is the one that actually bit. The assistant streams its answer,
                        // and a streamed response finishes on a different thread from the one
                        // that was authenticated. Spring Security re-checked the async dispatch,
                        // found no SecurityContext on that thread, and denied it after the
                        // response had already been sent, so every streamed message logged
                        // "Access Denied" followed by "the response is already committed".
                        //
                        // ERROR covers the forward Spring makes when a controller throws, which
                        // is why an invalid date used to come back as a bare 403.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.FORWARD,
                                DispatcherType.ERROR).permitAll()

                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()

                        // /error has to be open, and this is not optional.
                        //
                        // When a controller throws, Spring forwards the request to /error to
                        // render the response. If that path is secured, the forward is refused
                        // and the caller gets a bare 403 with an empty body instead of the
                        // real problem. A bad date on the public flight search came back as
                        // 403, which is a maddening thing to debug from the browser.
                        .requestMatchers("/error").permitAll()

                        // Browsing flights needs no account. Everything a passenger can do
                        // to a booking does.
                        .requestMatchers(HttpMethod.GET, "/api/flights/**").permitAll()

                        // The sentence search too, and it has to be listed separately because
                        // it is a POST and the rule above only covers GET.
                        //
                        // It was not, and the effect was worse than it sounds: the home page
                        // offers both search tabs to a signed out visitor, so the very first
                        // thing somebody tried on the landing page came back 401 and the
                        // frontend told them their session had expired. They never had one.
                        //
                        // It costs one small model call, which is the same trade as the plain
                        // search sitting next to it. The trip planner stays behind sign in
                        // because that one is three agents.
                        .requestMatchers(HttpMethod.POST, "/api/flights/search/natural").permitAll()

                        // Actuator is open here because it runs on the same port in local
                        // development. In a real deployment move it to a management port and
                        // keep it off the internet: /actuator/prometheus lists every metric,
                        // including how much the AI features cost.
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                // 401 for no or bad credentials, 403 for signed in but not allowed, both as
                // JSON. The defaults are a bare 403 with an empty body for either case.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jsonAuthEntryPoint)
                        .accessDeniedHandler(jsonAuthEntryPoint))

                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(CustomUserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider::authenticate;
    }
}
