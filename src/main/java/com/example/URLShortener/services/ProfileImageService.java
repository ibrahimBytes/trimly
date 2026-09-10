package com.example.URLShortener.services;

import com.example.URLShortener.models.User;
import com.example.URLShortener.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileImageService {

    private static final long MAX_FILE_SIZE = 2L * 1024L * 1024L;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif"
    );

    private final UserRepository userRepository;

    @Value("${app.profile-image-dir:${user.home}/.trimly/profile-images}")
    private String configuredDirectory;

    @Transactional
    public User upload(User user, MultipartFile file) {
        validate(file);

        String contentType =
                file.getContentType().toLowerCase(Locale.ROOT);

        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            default -> throw new IllegalArgumentException(
                    "Unsupported image type"
            );
        };

        Path directory =
                Path.of(configuredDirectory)
                        .toAbsolutePath()
                        .normalize();

        String filename =
                UUID.randomUUID() + extension;

        Path target =
                directory.resolve(filename)
                        .normalize();

        if (!target.startsWith(directory)) {
            throw new IllegalStateException(
                    "Invalid profile image path"
            );
        }

        try {
            Files.createDirectories(directory);

            try (InputStream input = file.getInputStream()) {
                Files.copy(
                        input,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to store profile image",
                    e
            );
        }

        String oldUrl =
                user.getProfileImageUrl();

        user.setProfileImageUrl(
                "/api/profile-images/" + filename
        );

        try {
            User saved =
                    userRepository.save(user);

            deleteStoredFile(oldUrl);

            return saved;
        } catch (RuntimeException e) {
            deletePath(target);
            throw e;
        }
    }

    @Transactional
    public User delete(User user) {
        String oldUrl =
                user.getProfileImageUrl();

        user.setProfileImageUrl(null);

        User saved =
                userRepository.save(user);

        deleteStoredFile(oldUrl);

        return saved;
    }

    public Path resolve(String filename) {
        if (filename == null
                || filename.isBlank()
                || filename.contains("/")
                || filename.contains("\\")
                || !filename.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException(
                    "Invalid profile image name"
            );
        }

        return Path.of(configuredDirectory)
                .toAbsolutePath()
                .normalize()
                .resolve(filename)
                .normalize();
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Choose a profile image"
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "Image must be smaller than 2MB"
            );
        }

        String contentType =
                file.getContentType();

        if (contentType == null
                || !ALLOWED_CONTENT_TYPES.contains(
                        contentType.toLowerCase(Locale.ROOT)
                )) {
            throw new IllegalArgumentException(
                    "Please choose a JPG, PNG, or GIF image"
            );
        }
    }

    private void deleteStoredFile(String url) {
        if (url == null
                || !url.startsWith(
                        "/api/profile-images/"
        )) {
            return;
        }

        String filename =
                url.substring(
                        "/api/profile-images/".length()
                );

        try {
            deletePath(resolve(filename));
        } catch (RuntimeException ignored) {
            // Best-effort cleanup.
        }
    }

    private void deletePath(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup.
        }
    }
}
