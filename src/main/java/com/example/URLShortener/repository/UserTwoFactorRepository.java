package com.example.URLShortener.repository;

import com.example.URLShortener.models.User;
import com.example.URLShortener.models.UserTwoFactor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserTwoFactorRepository extends JpaRepository<UserTwoFactor, Long> {
    Optional<UserTwoFactor> findByUser(User user);
}
