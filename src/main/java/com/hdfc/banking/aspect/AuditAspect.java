package com.hdfc.banking.aspect;

import com.hdfc.banking.entity.AuditLog;
import com.hdfc.banking.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;

    // Intercept all public methods in TransactionService
    @Pointcut("execution(* com.hdfc.banking.service.TransactionService.*(..))")
    public void transactionMethods() {
    }

    // Intercept all public methods in AdminService
    @Pointcut("execution(* com.hdfc.banking.service.AdminService.*(..))")
    public void adminMethods() {
    }

    // Combine both
    @Pointcut("transactionMethods() || adminMethods()")
    public void sensitiveMethods() {
    }

    @Before("sensitiveMethods()")
    public void logBefore(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        String args = Arrays.toString(joinPoint.getArgs());
        String user = getCurrentUser();

        log.info("[AUDIT] {} called {} with args: {}", user, methodName, args);
    }

    @AfterReturning(pointcut = "sensitiveMethods()", returning = "result")
    public void logAfterSuccess(JoinPoint joinPoint, Object result) {
        String methodName = joinPoint.getSignature().getName();
        String user = getCurrentUser();

        // Determine entity type and action from method name
        String entityType = methodName.contains("Account") ? "ACCOUNT" : "TRANSACTION";
        String action = methodName.toUpperCase();

        // Save to DB
        AuditLog auditLog = AuditLog.builder()
                .timestamp(LocalDateTime.now())
                .entityType(entityType)
                .action(action)
                .changedBy(user)
                .newValue(result != null ? result.toString() : "void")
                .build();

        auditLogRepository.save(auditLog);
        log.info("[AUDIT] {} completed {} successfully", user, methodName);
    }

    @AfterThrowing(pointcut = "sensitiveMethods()", throwing = "ex")
    public void logAfterException(JoinPoint joinPoint, Throwable ex) {
        String methodName = joinPoint.getSignature().getName();
        String user = getCurrentUser();

        log.warn("[AUDIT] {} failed {} — reason: {}", user, methodName, ex.getMessage());

        AuditLog auditLog = AuditLog.builder()
                .timestamp(LocalDateTime.now())
                .entityType("ERROR")
                .action(methodName.toUpperCase() + "_FAILED")
                .changedBy(user)
                .newValue("ERROR: " + ex.getMessage())
                .build();

        auditLogRepository.save(auditLog);
    }

    private String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            return auth.getName(); // returns email (JWT subject)
        }
        return "SYSTEM"; // for scheduled jobs like interest calculation
    }
} // end of class