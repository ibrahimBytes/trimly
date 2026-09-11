package com.example.URLShortener.models;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(name="user_sessions")
public class UserSession {
 @Id private UUID id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id", nullable=false) private User user;
 @Column(name="device_name", nullable=false) private String deviceName;
 @Column(name="ip_address") private String ipAddress;
 @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
 @Column(name="last_active_at", nullable=false) private LocalDateTime lastActiveAt;
 @Column(name="expires_at", nullable=false) private LocalDateTime expiresAt;
 @Column(name="revoked_at") private LocalDateTime revokedAt;
}
