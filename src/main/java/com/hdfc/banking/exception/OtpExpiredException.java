package com.hdfc.banking.exception;

public class OtpExpiredException extends RuntimeException {

    public OtpExpiredException() {
        super("OTP has expired. Please request a new OTP.");
    }
}
