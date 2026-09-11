package com.example.URLShortener.services;
import com.example.URLShortener.models.*;
import com.example.URLShortener.repository.UserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@Service @RequiredArgsConstructor
public class SessionService {
 private final UserSessionRepository repository;
 private final NotificationService notifications;
 @Transactional public boolean validateAndTouch(User user, UUID id, Date expiry, HttpServletRequest request) {
  LocalDateTime now=LocalDateTime.now();
  UserSession session=repository.findById(id).orElseGet(() -> {
   UserSession created=repository.save(UserSession.builder().id(id).user(user).deviceName(device(request)).ipAddress(ip(request)).createdAt(now).lastActiveAt(now).expiresAt(expiry.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()).build());
   notifications.create(user,"security","New sign-in","A new device signed in to your Trimly account.","/settings");
   return created;
  });
  if (!session.getUser().getId().equals(user.getId()) || session.getRevokedAt()!=null || !session.getExpiresAt().isAfter(now)) return false;
  session.setLastActiveAt(now); return true;
 }
 @Transactional(readOnly=true) public List<UserSession> list(User user){ return repository.findByUserOrderByLastActiveAtDesc(user); }
 @Transactional public boolean revoke(User user, UUID id){ return repository.findByIdAndUser(id,user).map(s->{ if(s.getRevokedAt()==null){s.setRevokedAt(LocalDateTime.now());notifications.create(user,"security","Device signed out","A device was signed out of your account.","/settings");} return true;}).orElse(false); }
 @Transactional public void revokeOthers(User user, UUID current){ repository.findByUserOrderByLastActiveAtDesc(user).forEach(s->{if(!s.getId().equals(current)&&s.getRevokedAt()==null)s.setRevokedAt(LocalDateTime.now());}); }
 @Transactional public void deleteFor(User user){ repository.deleteByUser(user); }
 private String device(HttpServletRequest r){String ua=Optional.ofNullable(r.getHeader("User-Agent")).orElse("Unknown device"); return ua.length()>255?ua.substring(0,255):ua;}
 private String ip(HttpServletRequest r){String v=r.getHeader("X-Forwarded-For"); return v==null?r.getRemoteAddr():v.split(",")[0].trim();}
}