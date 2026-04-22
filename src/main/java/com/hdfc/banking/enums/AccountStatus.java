package com.hdfc.banking.enums;

public enum AccountStatus {
    PENDING,
    ACTIVE,
    FROZEN,   // ✅ Fixed: was "FORZEN" (typo)
    CLOSED
}
