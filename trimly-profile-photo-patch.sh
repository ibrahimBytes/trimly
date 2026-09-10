#!/usr/bin/env bash
set -Eeuo pipefail

# ============================================================================
# Trimly — persistent profile photo patch
# ============================================================================
# Adds:
#   - users.profile_image_url
#   - authenticated upload/delete endpoints
#   - public generated image resource
#   - frontend upload API
#   - AuthContext profile-image state
#   - Settings persistence
#   - conservative AppHeader wiring
#
# Safety:
#   - timestamped backups are created first
#   - JWT_SECRET is NOT changed or generated here
#   - existing JPA ddl-auto=update creates the new nullable column
# ============================================================================

ROOT_BACKEND="$HOME/url-shortener"
ROOT_FRONTEND="$HOME/trimly"

BACKEND_JAVA="$ROOT_BACKEND/src/main/java/com/example/URLShortener"
FRONTEND_SRC="$ROOT_FRONTEND/src"

STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="$ROOT_BACKEND/.trimly-backup/profile-photo-$STAMP"

mkdir -p "$BACKUP_DIR/backend" "$BACKUP_DIR/frontend"

die() {
  echo
  echo "ERROR: $*" >&2
  echo
  exit 1
}

require_file() {
  [[ -f "$1" ]] || die "Required file not found: $1"
}

backup() {
  cp -a "$1" "$BACKUP_DIR/$2"
}

echo "============================================================"
echo "Trimly — Persistent Profile Photo Patch"
echo "============================================================"
echo

require_file "$BACKEND_JAVA/models/User.java"
require_file "$BACKEND_JAVA/controllers/AuthController.java"
require_file "$BACKEND_JAVA/dto/UserResponse.java"
require_file "$ROOT_BACKEND/src/main/resources/application.yaml"
require_file "$FRONTEND_SRC/auth/authApi.ts"
require_file "$FRONTEND_SRC/auth/AuthContext.tsx"
require_file "$FRONTEND_SRC/pages/SettingsPage.tsx"
require_file "$FRONTEND_SRC/components/layout/AppHeader.tsx"

echo "[1/7] Backing up files..."

backup "$BACKEND_JAVA/models/User.java" "backend/User.java"
backup "$BACKEND_JAVA/controllers/AuthController.java" "backend/AuthController.java"
backup "$BACKEND_JAVA/dto/UserResponse.java" "backend/UserResponse.java"
backup "$ROOT_BACKEND/src/main/resources/application.yaml" "backend/application.yaml"
backup "$FRONTEND_SRC/auth/authApi.ts" "frontend/authApi.ts"
backup "$FRONTEND_SRC/auth/AuthContext.tsx" "frontend/AuthContext.tsx"
backup "$FRONTEND_SRC/pages/SettingsPage.tsx" "frontend/SettingsPage.tsx"
backup "$FRONTEND_SRC/components/layout/AppHeader.tsx" "frontend/AppHeader.tsx"

echo "Backup: $BACKUP_DIR"
echo

echo "[2/7] Patching backend model + DTO + AuthController..."

python3 - "$BACKEND_JAVA/models/User.java" "$BACKEND_JAVA/dto/UserResponse.java" "$BACKEND_JAVA/controllers/AuthController.java" <<'PY'
from pathlib import Path
import sys

user_path, response_path, controller_path = map(Path, sys.argv[1:])

def replace_once(path, old, new, label):
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(
            f"{label}: expected 1 occurrence, found {count} in {path}"
        )
    path.write_text(text.replace(old, new, 1))

replace_once(
    user_path,
"""    @Column(name = "full_name", length = 120)
    private String fullName;

""",
"""    @Column(name = "full_name", length = 120)
    private String fullName;

    /**
     * Public resource path for the user's profile image.
     * The actual image bytes are stored on the application filesystem.
     */
    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

""",
    "User.profileImageUrl",
)

response = response_path.read_text()

if "profileImageUrl" not in response:
    replace_once(
        response_path,
"""    private String fullName;

""",
"""    private String fullName;

    private String profileImageUrl;

""",
        "UserResponse.profileImageUrl",
    )

controller = controller_path.read_text()

old = ".fullName(user.getFullName())"
if controller.count(old) != 1:
    raise SystemExit(
        f"AuthController initial builder: expected 1 occurrence, "
        f"found {controller.count(old)}"
    )

controller = controller.replace(
    old,
    old + "\n                        .profileImageUrl(user.getProfileImageUrl())",
    1,
)

old = ".fullName(updatedUser.getFullName())"
if controller.count(old) != 1:
    raise SystemExit(
        f"AuthController updated builder: expected 1 occurrence, "
        f"found {controller.count(old)}"
    )

controller = controller.replace(
    old,
    old + "\n                        .profileImageUrl(updatedUser.getProfileImageUrl())",
    1,
)

controller_path.write_text(controller)
PY

echo "Backend model/DTO/controller patched."
echo

echo "[3/7] Creating profile-image backend service + controller..."

cat > "$BACKEND_JAVA/services/ProfileImageService.java" <<'JAVA'
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
JAVA

cat > "$BACKEND_JAVA/controllers/ProfileImageController.java" <<'JAVA'
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
JAVA

echo "Profile-image backend classes created."
echo

echo "[4/7] Updating SecurityConfig + multipart configuration..."

python3 - "$ROOT_BACKEND/src/main/java/com/example/URLShortener/config/SecurityConfig.java" "$ROOT_BACKEND/src/main/resources/application.yaml" <<'PY'
from pathlib import Path
import sys

security_path = Path(sys.argv[1])
yaml_path = Path(sys.argv[2])

security = security_path.read_text()

if "/api/auth/me/photo" not in security:
    anchor = """                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/analytics"
                        )
                        .authenticated()

"""
    if anchor not in security:
        raise SystemExit(
            "SecurityConfig authenticated-route anchor not found"
        )

    addition = """                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/me/photo"
                        )
                        .authenticated()

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/auth/me/photo"
                        )
                        .authenticated()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/profile-images/*"
                        )
                        .permitAll()

"""

    security = security.replace(
        anchor,
        anchor + addition,
        1,
    )

security_path.write_text(security)

yaml = yaml_path.read_text()

if "  servlet:\n    multipart:" not in yaml:
    anchor = """spring:
  application:
"""
    if anchor not in yaml:
        raise SystemExit(
            "application.yaml spring anchor not found"
        )

    yaml = yaml.replace(
        anchor,
"""spring:
  servlet:
    multipart:
      max-file-size: 2MB
      max-request-size: 2MB

  application:
""",
        1,
    )

if "  profile-image-dir:" not in yaml:
    anchor = "app:\n  base-url:"

    if anchor in yaml:
        yaml = yaml.replace(
            anchor,
"""app:
  profile-image-dir: ${TRIMLY_PROFILE_IMAGE_DIR:${user.home}/.trimly/profile-images}
  base-url:""",
            1,
        )
    else:
        yaml += """
app:
  profile-image-dir: ${TRIMLY_PROFILE_IMAGE_DIR:${user.home}/.trimly/profile-images}
"""

yaml_path.write_text(yaml)
PY

echo "Security and multipart configuration patched."
echo

echo "[5/7] Updating frontend auth API + AuthContext..."

python3 - "$FRONTEND_SRC/auth/authApi.ts" "$FRONTEND_SRC/auth/AuthContext.tsx" <<'PY'
from pathlib import Path
import sys

api_path = Path(sys.argv[1])
context_path = Path(sys.argv[2])

def replace_once(path, old, new, label):
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(
            f"{label}: expected 1 occurrence, found {count} in {path}"
        )
    path.write_text(text.replace(old, new, 1))

api = api_path.read_text()

if "profileImageUrl: string | null;" not in api:
    replace_once(
        api_path,
"""  fullName: string | null;
  createdAt?: string;
""",
"""  fullName: string | null;
  profileImageUrl: string | null;
  createdAt?: string;
""",
        "AuthUser.profileImageUrl",
    )

api = api_path.read_text()

if "export async function uploadProfilePhoto" not in api:
    anchor = """// ================================================================
// PASSWORD
// ================================================================
"""
    if anchor not in api:
        raise SystemExit(
            "authApi password anchor not found"
        )

    insertion = """// ================================================================
// PROFILE PHOTO
// ================================================================

export interface ProfilePhotoResponse {
  profileImageUrl: string | null;
}

export async function uploadProfilePhoto(
  file: File,
): Promise<ProfilePhotoResponse> {
  const formData = new FormData();
  formData.append('file', file);

  return apiFetch<ProfilePhotoResponse>(
    '/api/auth/me/photo',
    {
      method: 'POST',
      body: formData,
    },
  );
}

export async function deleteProfilePhoto(): Promise<void> {
  await apiFetch<void>(
    '/api/auth/me/photo',
    {
      method: 'DELETE',
    },
  );
}

"""

    api = api.replace(
        anchor,
        insertion + anchor,
        1,
    )

    api_path.write_text(api)

context = context_path.read_text()

if "uploadProfilePhoto as uploadProfilePhotoApi" not in context:
    old = """  updateProfile as updateProfileApi,
  verifyTwoFactorLogin,
"""
    new = """  updateProfile as updateProfileApi,
  uploadProfilePhoto as uploadProfilePhotoApi,
  deleteProfilePhoto as deleteProfilePhotoApi,
  verifyTwoFactorLogin,
"""
    if old not in context:
        raise SystemExit(
            "AuthContext authApi import anchor not found"
        )
    context = context.replace(old, new, 1)

if "  uploadProfilePhoto: (" not in context:
    anchor = """  updateProfile: (
    fullName: string,
  ) => Promise<AuthUser>;
"""
    if anchor not in context:
        raise SystemExit(
            "AuthContext interface anchor not found"
        )

    context = context.replace(
        anchor,
"""  updateProfile: (
    fullName: string,
  ) => Promise<AuthUser>;

  uploadProfilePhoto: (
    file: File,
  ) => Promise<AuthUser>;

  deleteProfilePhoto: () => Promise<AuthUser>;
""",
        1,
    )

if "const uploadProfilePhoto =" not in context:
    anchor = """  /*
   * ============================================================
   * SIGN OUT
   * ============================================================
   */
"""
    if anchor not in context:
        raise SystemExit(
            "AuthContext SIGN OUT anchor not found"
        )

    implementation = """  /*
   * ============================================================
   * PROFILE PHOTO
   * ============================================================
   */

  const uploadProfilePhoto = useCallback(
    async (file: File): Promise<AuthUser> => {
      const response =
        await uploadProfilePhotoApi(file);

      const updatedUser =
        user !== null
          ? {
              ...user,
              profileImageUrl:
                response.profileImageUrl,
            }
          : await getCurrentUser();

      setUser(updatedUser);

      return updatedUser;
    },
    [user],
  );

  const deleteProfilePhoto = useCallback(
    async (): Promise<AuthUser> => {
      await deleteProfilePhotoApi();

      const updatedUser =
        user !== null
          ? {
              ...user,
              profileImageUrl: null,
            }
          : await getCurrentUser();

      setUser(updatedUser);

      return updatedUser;
    },
    [user],
  );

"""

    context = context.replace(
        anchor,
        implementation + anchor,
        1,
    )

if "        uploadProfilePhoto,\n        deleteProfilePhoto," not in context:
    anchor = """        signUp,
        signOut,
        updateProfile,
"""
    if anchor not in context:
        raise SystemExit(
            "AuthContext value anchor not found"
        )

    context = context.replace(
        anchor,
"""        signUp,
        signOut,
        updateProfile,
        uploadProfilePhoto,
        deleteProfilePhoto,
""",
        1,
    )

    deps = """        signOut,
        updateProfile,
      ],
"""
    if deps not in context:
        raise SystemExit(
            "AuthContext dependency anchor not found"
        )

    context = context.replace(
        deps,
"""        signOut,
        updateProfile,
        uploadProfilePhoto,
        deleteProfilePhoto,
      ],
""",
        1,
    )

context_path.write_text(context)
PY

echo "Frontend auth API and context patched."
echo

echo "[6/7] Updating SettingsPage + AppHeader..."

python3 - "$FRONTEND_SRC/pages/SettingsPage.tsx" "$FRONTEND_SRC/components/layout/AppHeader.tsx" <<'PY'
from pathlib import Path
import re
import sys

settings_path = Path(sys.argv[1])
header_path = Path(sys.argv[2])

settings = settings_path.read_text()

def fail(message):
    raise SystemExit(message)

if "    uploadProfilePhoto," not in settings:
    old = """    user,
    updateProfile,
    signOut,
"""
    new = """    user,
    updateProfile,
    uploadProfilePhoto,
    deleteProfilePhoto,
    signOut,
"""
    if old not in settings:
        fail("Settings AuthContext destructuring anchor not found")
    settings = settings.replace(old, new, 1)

if "const [selectedPhoto, setSelectedPhoto]" not in settings:
    old = """  const [photo, setPhoto] =
    useState<string | null>(null);

"""
    new = """  const [photo, setPhoto] =
    useState<string | null>(null);

  const [selectedPhoto, setSelectedPhoto] =
    useState<File | null>(null);

  const [isPhotoSaving, setIsPhotoSaving] =
    useState(false);

"""
    if old not in settings:
        fail("Settings photo state anchor not found")
    settings = settings.replace(old, new, 1)

if "setPhoto(user.profileImageUrl ?? null);" not in settings:
    old = """    setName(user.fullName ?? '');
    setEmail(user.email);
"""
    new = """    setName(user.fullName ?? '');
    setEmail(user.email);
    setPhoto(user.profileImageUrl ?? null);
    setSelectedPhoto(null);
"""
    if old not in settings:
        fail("Settings user hydration anchor not found")
    settings = settings.replace(old, new, 1)

if "setSelectedPhoto(file);" not in settings:
    old = """    setPhoto(URL.createObjectURL(file));

    notify('Profile photo selected.');

    event.target.value = '';
"""
    new = """    const previewUrl =
      URL.createObjectURL(file);

    setPhoto((previous) => {
      if (previous?.startsWith('blob:')) {
        URL.revokeObjectURL(previous);
      }

      return previewUrl;
    });

    setSelectedPhoto(file);

    notify(
      'Profile photo selected. Save changes to keep it.',
    );

    event.target.value = '';
"""
    if old not in settings:
        fail("Settings photo selection anchor not found")
    settings = settings.replace(old, new, 1)

if "const removeProfilePhoto = async" not in settings:
    old = """  const saveProfile = async () => {
    const trimmedName = name.trim();

    if (!trimmedName) {
      notify('Please enter your name.');
      return;
    }

    try {
      const updatedUser =
        await updateProfile(trimmedName);

      setName(updatedUser.fullName ?? '');
      setEmail(updatedUser.email);

      setSaved(true);
      notify('Profile updated successfully.');

      window.setTimeout(() => {
        setSaved(false);
      }, 1800);
    } catch {
      notify('Unable to update your profile.');
    }
  };
"""

    new = """  const removeProfilePhoto = async () => {
    if (isPhotoSaving) {
      return;
    }

    try {
      setIsPhotoSaving(true);

      const updatedUser =
        await deleteProfilePhoto();

      if (photo?.startsWith('blob:')) {
        URL.revokeObjectURL(photo);
      }

      setPhoto(
        updatedUser.profileImageUrl ?? null,
      );
      setSelectedPhoto(null);

      notify('Profile photo removed.');
    } catch (error) {
      notify(
        error instanceof Error
          ? error.message
          : 'Unable to remove your profile photo.',
      );
    } finally {
      setIsPhotoSaving(false);
    }
  };

  const saveProfile = async () => {
    const trimmedName = name.trim();

    if (!trimmedName) {
      notify('Please enter your name.');
      return;
    }

    if (isPhotoSaving) {
      return;
    }

    try {
      setIsPhotoSaving(true);

      let updatedUser =
        await updateProfile(trimmedName);

      if (selectedPhoto) {
        updatedUser =
          await uploadProfilePhoto(
            selectedPhoto,
          );

        setPhoto(
          updatedUser.profileImageUrl ?? null,
        );
        setSelectedPhoto(null);
      }

      setName(updatedUser.fullName ?? '');
      setEmail(updatedUser.email);

      setSaved(true);
      notify('Profile updated successfully.');

      window.setTimeout(() => {
        setSaved(false);
      }, 1800);
    } catch (error) {
      notify(
        error instanceof Error
          ? error.message
          : 'Unable to update your profile.',
      );
    } finally {
      setIsPhotoSaving(false);
    }
  };
"""

    if old not in settings:
        fail("Settings saveProfile block not found")

    settings = settings.replace(old, new, 1)

if 'aria-label="Remove profile photo"' not in settings:
    anchor = """              <input
                ref={fileRef}
"""
    if anchor not in settings:
        fail("Settings file input anchor not found")

    button = """              {photo && (
                <button
                  type="button"
                  onClick={() => void removeProfilePhoto()}
                  disabled={isPhotoSaving}
                  className="absolute -bottom-2 -left-2 flex h-7 w-7 items-center justify-center rounded-md border border-white bg-white text-[#6E6E73] shadow-md hover:text-[#B42318] disabled:opacity-50"
                  aria-label="Remove profile photo"
                  title="Remove photo"
                >
                  <X size={13} />
                </button>
              )}

"""

    settings = settings.replace(
        anchor,
        button + anchor,
        1,
    )

settings = settings.replace(
"""            {saved ? 'Saved' : 'Save changes'}
""",
"""            {isPhotoSaving
              ? 'Saving…'
              : saved
                ? 'Saved'
                : 'Save changes'}
""",
1,
)

settings_path.write_text(settings)

# ---------------------------------------------------------------------------
# AppHeader: patch only if the existing source has the known avatar marker
# and a nearby initials expression. Otherwise fail safely rather than guessing.
# ---------------------------------------------------------------------------

header = header_path.read_text()

if "profileImageUrl" not in header:
    marker = "{/* Avatar */}"
    marker_pos = header.find(marker)

    if marker_pos == -1:
        fail(
            "AppHeader: {/* Avatar */} marker not found; "
            "header was not modified."
        )

    bounded = header[marker_pos:marker_pos + 4000]

    if "user.fullName" not in bounded:
        fail(
            "AppHeader: nearby user.fullName avatar fallback not found; "
            "header was not modified."
        )

    if "const profileImageUrl" not in header:
        return_anchor = "  return ("
        if return_anchor not in header:
            fail(
                "AppHeader: return anchor not found; "
                "header was not modified."
            )

        header = header.replace(
            return_anchor,
"""  const profileImageUrl =
    user?.profileImageUrl ?? null;

  return (""",
            1,
        )

    marker_pos = header.find(marker)
    bounded = header[marker_pos:marker_pos + 4000]

    match = re.search(
        r'\{\s*user\.fullName\?\.charAt\(0\)\.toUpperCase\(\)\s*\}',
        bounded,
    )

    if not match:
        fail(
            "AppHeader: initials expression could not be identified safely; "
            "header was not modified."
        )

    start = marker_pos + match.start()
    end = marker_pos + match.end()

    replacement = """{profileImageUrl ? (
                  <img
                    src={profileImageUrl}
                    alt=""
                    className="h-full w-full object-cover"
                  />
                ) : (
                  user.fullName?.charAt(0).toUpperCase()
                )}"""

    header = header[:start] + replacement + header[end:]

header_path.write_text(header)
PY

echo "SettingsPage and AppHeader patched."
echo

echo "[7/7] Validating and testing..."

cd "$ROOT_BACKEND"

grep -q "profile_image_url" \
  "$BACKEND_JAVA/models/User.java" \
  || die "profile_image_url missing from User.java"

grep -q "profileImageUrl" \
  "$BACKEND_JAVA/dto/UserResponse.java" \
  || die "profileImageUrl missing from UserResponse.java"

grep -q "/api/auth/me/photo" \
  "$BACKEND_JAVA/controllers/ProfileImageController.java" \
  || die "ProfileImageController missing"

echo "Running backend tests..."
./mvnw test

echo
echo "Running frontend production build..."
cd "$ROOT_FRONTEND"
npm run build

echo
echo "============================================================"
echo "PATCH COMPLETE"
echo "============================================================"
echo
echo "Profile-image storage:"
echo "  $HOME/.trimly/profile-images"
echo
echo "Endpoints:"
echo "  POST   /api/auth/me/photo"
echo "  DELETE /api/auth/me/photo"
echo "  GET    /api/profile-images/{generated-name}"
echo
echo "Backup:"
echo "  $BACKUP_DIR"
echo
echo "Next:"
echo "  1. Restart the backend."
echo "  2. Open Trimly → Settings → Account."
echo "  3. Choose a JPG/PNG/GIF under 2MB."
echo "  4. Click Save changes."
echo "  5. Refresh the page."
echo "  6. Confirm the photo persists."
echo "  7. Confirm the header avatar uses it."
echo
