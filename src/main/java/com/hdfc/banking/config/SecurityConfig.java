package com.hdfc.banking.config;

import com.hdfc.banking.security.CustomUserDetailsService;
import com.hdfc.banking.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity       // enables @PreAuthorize("hasRole('ADMIN')") on methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;

    /**
     * WHY BCrypt?
     * Never store plain-text passwords. BCrypt:
     * - Adds random salt → same password hashes differently each time
     * - Intentionally slow (configurable rounds) → makes brute-force attacks expensive
     * - Industry standard for password hashing
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * DaoAuthenticationProvider wires together:
     * - WHERE to find users (userDetailsService → our DB)
     * - HOW to verify passwords (BCrypt)
     * Used internally by AuthenticationManager during login.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * AuthenticationManager is the entry point for authentication.
     * We expose it as a bean so AuthService can call:
     *   authManager.authenticate(new UsernamePasswordAuthenticationToken(email, password))
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())

            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                // STATELESS = Spring never creates HTTP sessions
                // Each request must carry JWT — no "remember me" cookies
            )

            .headers(headers -> headers
                .frameOptions(frame -> frame.disable())  // H2 console iframe
            )

            .authorizeHttpRequests(auth -> auth
                // PUBLIC — no token needed:
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/test/**").permitAll()     // test endpoints (Day 2)

                // ADMIN only:
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // Everything else needs a valid JWT:
                .anyRequest().authenticated()
            )

            // Add our JWT filter BEFORE Spring's default auth filter
            .addFilterBefore(jwtAuthenticationFilter,
                             UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}