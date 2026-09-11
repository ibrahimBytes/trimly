package com.example.URLShortener.controllers;
import com.example.URLShortener.models.*;
import com.example.URLShortener.services.NotificationService;
import lombok.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
@RestController @RequestMapping("/api/notifications") @RequiredArgsConstructor
public class NotificationController {
 private final NotificationService service;
 @GetMapping public NotificationList list(Authentication a){User u=user(a);return new NotificationList(service.list(u).stream().map(NotificationView::from).toList(),service.unread(u));}
 @PatchMapping("/{id}/read") public ResponseEntity<Void> read(Authentication a,@PathVariable Long id){return service.read(user(a),id)?ResponseEntity.noContent().build():ResponseEntity.notFound().build();}
 @PostMapping("/read-all") public ResponseEntity<Void> all(Authentication a){service.readAll(user(a));return ResponseEntity.noContent().build();}
 @DeleteMapping("/{id}") public ResponseEntity<Void> dismiss(Authentication a,@PathVariable Long id){return service.dismiss(user(a),id)?ResponseEntity.noContent().build():ResponseEntity.notFound().build();}
 private User user(Authentication a){if(a==null||!(a.getPrincipal() instanceof User u))throw new IllegalStateException("Authenticated user is required");return u;}
 public record NotificationList(List<NotificationView> notifications,long unreadCount){}
 public record NotificationView(Long id,String type,String title,String message,String linkPath,LocalDateTime createdAt,LocalDateTime readAt){static NotificationView from(Notification n){return new NotificationView(n.getId(),n.getType(),n.getTitle(),n.getMessage(),n.getLinkPath(),n.getCreatedAt(),n.getReadAt());}}
}
