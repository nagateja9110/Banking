error id: file://<WORKSPACE>/src/main/java/com/hdfc/banking/config/SecurityConfig.java:_empty_/HttpSecurity#csrf#headers#
file://<WORKSPACE>/src/main/java/com/hdfc/banking/config/SecurityConfig.java
empty definition using pc, found symbol in pc: _empty_/HttpSecurity#csrf#headers#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 1364
uri: file://<WORKSPACE>/src/main/java/com/hdfc/banking/config/SecurityConfig.java
text:
```scala
package com.hdfc.banking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * TEMPORARY Security Config for Week 1 (Development Only)
 *
 * WHY this file?
 * Spring Security blocks ALL endpoints by default when it's on the classpath.
 * Right now we have no JWT, no login system — so we open everything up.
 * In Week 1 Day 3-4, this gets replaced with a REAL security config (JWT, roles, etc.)
 *
 * WHY disable CSRF?
 * CSRF protection blocks requests from non-browser clients (like H2 console, Postman).
 * In dev mode with stateless JWT, CSRF is not needed.
 *
 * WHY headers().frameOptions().disable()?
 * H2 console runs inside an HTML <iframe>.
 * Spring Security adds "X-Frame-Options: DENY" which blocks iframes.
 * We disable this so H2 console can load in the browser.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())   // disable CSRF for dev + Postman testing
            .he@@aders(headers -> headers
                .frameOptions(frame -> frame.disable())  // allow H2 console <iframe>
            )
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()   // allow ALL requests for now (Week 1)
                // Week 1 Day 3: we will replace this with JWT + role-based rules
            );

        return http.build();
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/HttpSecurity#csrf#headers#