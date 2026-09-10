package com.example.URLShortener.repository;

import com.example.URLShortener.models.RecoveryCode;
import com.example.URLShortener.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {
    List<RecoveryCode> findByUserAndUsedFalse(User user);
    void deleteByUser(User user);
}
