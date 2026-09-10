package com.example.URLShortener.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_two_factor")
public class UserTwoFactor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_user_two_factor_user"))
    private User user;

    @Column(name = "secret", length = 128)
    private String secret;

    @Column(name = "pending_secret", length = 128)
    private String pendingSecret;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;

    @Column(name = "enabled_at")
    private LocalDateTime enabledAt;
}
