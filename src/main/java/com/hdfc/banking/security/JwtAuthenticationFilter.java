package com.hdfc.banking.security;

import com.hdfc.banking.config.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // STEP 1: Extract token from header
        String token = extractToken(request);

        // STEP 2: If token exists and is valid
        if (token != null && jwtTokenProvider.validateToken(token)) {

            // STEP 3: Get email from token (no DB call yet)
            String email = jwtTokenProvider.getEmailFromToken(token);

            // STEP 4: Load full UserDetails from DB (to get roles/authorities)
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            // STEP 5: Create authentication object
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,                           // credentials = null (already validated)
                    userDetails.getAuthorities()    // roles: [ROLE_CUSTOMER] or [ROLE_ADMIN]
                );
            authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
            );

            // STEP 6: Set in SecurityContext — THIS is what marks the user as "logged in"
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // WHY clear after request? Thread pooling — same thread handles next request
            // Spring actually clears this automatically after each request, but being explicit
        }

        // STEP 7: Always continue the chain (let Spring Security decide if request is allowed)
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts JWT from "Authorization: Bearer <token>" header.
     * Returns null if header is missing or doesn't start with "Bearer ".
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7); // Remove "Bearer " prefix (7 characters)
        }
        return null;
    }
}