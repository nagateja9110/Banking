package com.hdfc.banking.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;

import com.hdfc.banking.dto.request.LoginRequest;
import com.hdfc.banking.dto.request.OtpVerifyRequest;
import com.hdfc.banking.dto.request.RegisterRequest;
import com.hdfc.banking.dto.response.LoginResponse;
import com.hdfc.banking.entity.OtpToken;
import com.hdfc.banking.entity.User;
import com.hdfc.banking.enums.Role;
import com.hdfc.banking.exception.DuplicateAccountException;
import com.hdfc.banking.exception.OtpExpiredException;
import com.hdfc.banking.exception.UnauthorizedException;
import com.hdfc.banking.repository.OtpTokenRepository;
import com.hdfc.banking.repository.UserRepository;
import com.hdfc.banking.config.JwtTokenProvider;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OtpTokenRepository otpTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public String register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw  new DuplicateAccountException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.CUSTOMER)
                .build();

        userRepository.save(user);
        log.info("Registered: {}", request.getEmail());
        return "Registration successful";
    }

    @Transactional
    public String login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (user.isLocked()) {
            throw new UnauthorizedException("Account lockedfter 3 failed attempts");
        }
        try {
            authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (BadCredentialsException e) {
            user.setFailedLogins(user.getFailedLogins() + 1);
            if (user.getFailedLogins() >= 3) {
                user.setLocked(true);
            }
            userRepository.save(user);
            throw new UnauthorizedException("Invalid Credentials");
        }
        user.setFailedLogins(0);
        userRepository.save(user);

        String otp = String.valueOf(100000 + new SecureRandom().nextInt(900000));

        OtpToken otpToken=OtpToken.builder()
                           .user(user)
                           .otpCode(otp)
                           .expiresAt(LocalDateTime.now().plusMinutes(5))
                           .isUsed(false)
                           .build();

        otpTokenRepository.save(otpToken);

        log.info("otp for {}:{}",request.getEmail(),otp);
        return "OTP sent to "+request.getEmail();


    }

    @Transactional
public LoginResponse verifyOtp(OtpVerifyRequest request) {
    User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new UnauthorizedException("User not found"));

    OtpToken otp = otpTokenRepository
            .findTopByUserAndIsUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(user, LocalDateTime.now())
            .orElseThrow(() -> new OtpExpiredException());

    if (!otp.getOtpCode().equals(request.getOtpCode())) {
        throw new UnauthorizedException("Invalid OTP");
    }

    otp.setIsUsed(true);
    otpTokenRepository.save(otp);

    String token = jwtTokenProvider.generateToken(user.getEmail(), user.getRole().name());
    return LoginResponse.builder()
            .token(token)
            .name(user.getName())
            .email(user.getEmail())
            .role(user.getRole())
            .build();
}

}
