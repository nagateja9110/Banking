package com.hdfc.banking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfc.banking.TestHelper;
import com.hdfc.banking.dto.request.CreateAccountRequest;
import com.hdfc.banking.enums.AccountType;
import com.hdfc.banking.repository.OtpTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AccountControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OtpTokenRepository otpTokenRepository;

    private TestHelper testHelper;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        testHelper = new TestHelper(mockMvc, objectMapper, otpTokenRepository);
        token = testHelper.getJwtToken("naga@test.com", "pass1234");
    }

    @Test
    void createAccount_shouldReturn201_withValidToken() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountType(AccountType.SAVINGS);

        mockMvc.perform(post("/api/accounts/create")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").isNotEmpty())
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.balance").value(0.0));
    }

    @Test
    void createAccount_shouldReturn401_withoutToken() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountType(AccountType.SAVINGS);

        mockMvc.perform(post("/api/accounts/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyAccounts_shouldReturnEmptyList_whenNoAccounts() throws Exception {
        mockMvc.perform(get("/api/accounts/my")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
