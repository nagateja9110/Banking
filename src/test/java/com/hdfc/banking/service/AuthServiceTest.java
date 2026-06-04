package com.hdfc.banking.service;

import com.hdfc.banking.dto.request.RegisterRequest;
import com.hdfc.banking.exception.DuplicateAccountException;
import com.hdfc.banking.repository.OtpTokenRepository;
import com.hdfc.banking.repository.UserRepository;
import com.hdfc.banking.config.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OtpTokenRepository otpTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_shouldSucceed_whenEmailIsNew() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Naga");
        request.setEmail("naga@test.com");
        request.setPassword("pass1234");
        request.setPhone("9876543210");

        when(userRepository.existsByEmail("naga@test.com")).thenReturn(false);
        when(passwordEncoder.encode("pass1234")).thenReturn("hashed_password");

        String result = authService.register(request);

        assertEquals("Registration successful", result);
        verify(userRepository).save(any());
    }

    @Test
    void register_shouldThrow_whenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@test.com");
        request.setPassword("pass1234");
        request.setName("Test");
        request.setPhone("1234567890");

        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThrows(DuplicateAccountException.class,
                () -> authService.register(request));

        // Verify user was NOT saved
        verify(userRepository, never()).save(any());
    }
}