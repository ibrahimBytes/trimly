package com.example.URLShortener.controllers;
import com.example.URLShortener.models.*;
import com.example.URLShortener.services.SessionService;
import com.example.URLShortener.services.JwtService;
import lombok.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/auth/me/sessions") @RequiredArgsConstructor
public class SessionController {
 private final SessionService sessions;
 private final JwtService jwtService;
 @GetMapping public List<SessionView> list(Authentication a,@RequestHeader(value="Authorization",required=false) String auth){User u=user(a); UUID current=current(auth); return sessions.list(u).stream().map(s->SessionView.from(s,s.getId().equals(current))).toList();}
 @DeleteMapping("/{id}") public ResponseEntity<Void> revoke(Authentication a,@PathVariable UUID id){return sessions.revoke(user(a),id)?ResponseEntity.noContent().build():ResponseEntity.notFound().build();}
 @PostMapping("/revoke-others") public ResponseEntity<Void> others(Authentication a,@RequestHeader("Authorization") String auth){sessions.revokeOthers(user(a),current(auth));return ResponseEntity.noContent().build();}
 private User user(Authentication a){if(a==null||!(a.getPrincipal() instanceof User u))throw new IllegalStateException("Authenticated user is required");return u;}
 private UUID current(String auth){
  if(auth==null||!auth.startsWith("Bearer ")) throw new IllegalArgumentException("Current session is required");
  return jwtService.extractSessionId(auth.substring(7).trim());
 }
 @Value @Builder static class SessionView {UUID id;String deviceName;String ipAddress;java.time.LocalDateTime createdAt,lastActiveAt,expiresAt;boolean current;static SessionView from(UserSession s,boolean c){return builder().id(s.getId()).deviceName(s.getDeviceName()).ipAddress(s.getIpAddress()).createdAt(s.getCreatedAt()).lastActiveAt(s.getLastActiveAt()).expiresAt(s.getExpiresAt()).current(c).build();}}
}
