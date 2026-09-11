package com.example.URLShortener.repository;
import com.example.URLShortener.models.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.*;
public interface NotificationRepository extends JpaRepository<Notification, Long> {
 List<Notification> findTop50ByUserOrderByCreatedAtDesc(User user);
 Optional<Notification> findByIdAndUser(Long id, User user);
 long countByUserAndReadAtIsNull(User user);
 void deleteByUser(User user);
}
