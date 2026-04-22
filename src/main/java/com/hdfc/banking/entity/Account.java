package com.hdfc.banking.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hdfc.banking.enums.AccountStatus;
import com.hdfc.banking.enums.AccountType;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name="accounts")
@EntityListeners(AuditingEntityListener.class)
@Builder
@Audited
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true,length = 20)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AccountStatus accountStatus=AccountStatus.ACTIVE;

    @Column(nullable = false,precision = 19,scale = 4)
    @Builder.Default
    private BigDecimal balance=BigDecimal.ZERO;

    @Column(nullable = false,precision = 5,scale = 2)
    @Builder.Default
    private BigDecimal interestRate=new BigDecimal("3.5");

    @ManyToOne
    @JoinColumn(name="user_id", nullable = false)
    @Audited(targetAuditMode = org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED)
    private User user;

    @OneToMany(mappedBy = "fromAccount")
    @JsonIgnore
    @NotAudited
    private List<Transaction> sentTransactions;

    @OneToMany(mappedBy = "toAccount")
    @JsonIgnore
    @NotAudited
    private List<Transaction> receivedTransactions;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;


    
}
