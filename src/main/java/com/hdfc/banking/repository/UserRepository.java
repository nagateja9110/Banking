package com.hdfc.banking.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.hdfc.banking.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
