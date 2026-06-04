package com.hdfc.banking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfc.banking.dto.request.LoginRequest;
import com.hdfc.banking.dto.request.RegisterRequest;
import com.hdfc.banking.entity.OtpToken;
import com.hdfc.banking.repository.OtpTokenRepository;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// NOT a @Component — instantiated manually in each test class @BeforeEach
// Avoids MockMvc injection failure in Spring contexts without @AutoConfigureMockMvc
public class TestHelper {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final OtpTokenRepository otpTokenRepository;

    public TestHelper(MockMvc mockMvc, ObjectMapper objectMapper, OtpTokenRepository otpTokenRepository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.otpTokenRepository = otpTokenRepository;
    }

    // Register + Login + OTP verify — returns JWT token
    public String getJwtToken(String email, String password) throws Exception {

        // Step 1: Register
        RegisterRequest reg = new RegisterRequest();
        reg.setName("Test User");
        reg.setEmail(email);
        reg.setPassword(password);
        reg.setPhone("9876543210");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)));

        // Step 2: Login
        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword(password);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)));

        // Step 3: Get OTP from DB (skip email, read directly)
        OtpToken otp = otpTokenRepository.findAll()
                .stream()
                .filter(o -> o.getUser().getEmail().equals(email))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("OTP not found in DB"));

        // Step 4: Verify OTP → get JWT
        String otpBody = "{\"email\":\"" + email + "\",\"otpCode\":\"" + otp.getOtpCode() + "\"}";

        MvcResult result = mockMvc.perform(post("/api/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(otpBody))
                .andReturn();

        // Extract token from response JSON
        String responseBody = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).get("token").asText();
    }
}
