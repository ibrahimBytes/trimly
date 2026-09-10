package com.example.URLShortener.controllers;

import com.example.URLShortener.models.User;
import com.example.URLShortener.services.ProfileImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ProfileImageController {

    private final ProfileImageService profileImageService;

    @PostMapping(
            value = "/api/auth/me/photo",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> upload(
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) {
        User user =
                getAuthenticatedUser(authentication);

        try {
            User updated =
                    profileImageService.upload(user, file);

            return ResponseEntity.ok(
                    Map.of(
                            "profileImageUrl",
                            updated.getProfileImageUrl()
                    )
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "message",
                                    e.getMessage()
                            )
                    );
        } catch (IllegalStateException e) {
            return ResponseEntity.internalServerError()
                    .body(
                            Map.of(
                                    "message",
                                    "Unable to save profile image"
                            )
                    );
        }
    }

    @DeleteMapping("/api/auth/me/photo")
    public ResponseEntity<Void> delete(
            Authentication authentication
    ) {
        User user =
                getAuthenticatedUser(authentication);

        profileImageService.delete(user);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/profile-images/{filename:.+}")
    public ResponseEntity<Resource> image(
            @PathVariable String filename
    ) {
        try {
            Path path =
                    profileImageService.resolve(filename);

            if (!Files.isRegularFile(path)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource =
                    new UrlResource(path.toUri());

            String contentType =
                    Files.probeContentType(path);

            MediaType mediaType =
                    switch (
                            contentType == null
                                    ? ""
                                    : contentType
                    ) {
                        case "image/png" ->
                                MediaType.IMAGE_PNG;
                        case "image/gif" ->
                                MediaType.IMAGE_GIF;
                        default ->
                                MediaType.IMAGE_JPEG;
                    };

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .cacheControl(CacheControl.noCache())
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private User getAuthenticatedUser(
            Authentication authentication
    ) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                instanceof User user)) {
            throw new IllegalStateException(
                    "Authenticated user is required"
            );
        }

        return user;
    }
}
