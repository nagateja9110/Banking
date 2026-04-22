error id: file://<WORKSPACE>/src/main/java/com/hdfc/banking/security/CustomUserDetailsService.java:_empty_/User#getRole#
file://<WORKSPACE>/src/main/java/com/hdfc/banking/security/CustomUserDetailsService.java
empty definition using pc, found symbol in pc: _empty_/User#getRole#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 1143
uri: file://<WORKSPACE>/src/main/java/com/hdfc/banking/security/CustomUserDetailsService.java
text:
```scala
package com.hdfc.banking.security;

import com.hdfc.banking.entity.User;
import com.hdfc.banking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

         return org.springframework.security.core.userdetails.User
        .withUsername(email)
        .password(user.getPassword())
        .authorities(new SimpleGrantedAuthority("ROLE_" + user.getRole@@().name()))
        .accountExpired(false)
        .accountLocked(false)
        .credentialsExpired(false)
        .disabled(false)
        .build();
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/User#getRole#