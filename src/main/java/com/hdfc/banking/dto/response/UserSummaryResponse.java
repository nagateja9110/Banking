package com.hdfc.banking.dto.response;

import com.hdfc.banking.enums.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserSummaryResponse {
    private Long id;
    private String name;
    private String email;
    private Role role;
    private boolean locked;
}