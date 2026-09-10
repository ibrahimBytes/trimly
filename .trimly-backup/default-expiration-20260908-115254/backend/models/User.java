package com.example.URLShortener.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_users_email",
                        columnNames = "email"
                )
        }
)
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(
            nullable = false,
            length = 320
    )
    private String email;

    @Column(name = "full_name", length = 120)
    private String fullName;

    /**
     * Public resource path for the user's profile image.
     * The actual image bytes are stored on the application filesystem.
     */
    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(
            name = "password_hash",
            nullable = false,
            length = 255
    )
    private String passwordHash;

    /**
     * Server-side authentication generation.
     *
     * Every JWT contains the token version that was current
     * when the token was issued.
     *
     * Incrementing this value invalidates all existing JWTs
     * for this user.
     */
    @Column(
            name = "token_version",
            nullable = false
    )
    @Builder.Default
    private long tokenVersion = 0L;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
