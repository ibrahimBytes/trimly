package com.example.URLShortener.repository;
import com.example.URLShortener.models.User;
import com.example.URLShortener.models.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.*;
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
 List<UserSession> findByUserOrderByLastActiveAtDesc(User user);
 Optional<UserSession> findByIdAndUser(UUID id, User user);
 void deleteByUser(User user);
}
