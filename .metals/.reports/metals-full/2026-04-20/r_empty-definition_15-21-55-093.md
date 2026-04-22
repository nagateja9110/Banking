error id: file://<WORKSPACE>/src/main/java/com/hdfc/banking/service/AuthService.java:
file://<WORKSPACE>/src/main/java/com/hdfc/banking/service/AuthService.java
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 547
uri: file://<WORKSPACE>/src/main/java/com/hdfc/banking/service/AuthService.java
text:
```scala
package com.hdfc.banking.service;



import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.hdfc.banking.repository.OtpTokenRepository;
import com.hdfc.banking.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserRepository userRepository;
    private final OtpTokenRepository otpTokenRepository;
    private final PasswordEncoder@@ passwordEncoder;
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: 