package com.example.URLShortener.twofactor;

import com.example.URLShortener.dto.AuthResponse;
import com.example.URLShortener.dto.TwoFactorSetupResponse;
import com.example.URLShortener.dto.TwoFactorSetupVerifyResponse;
import com.example.URLShortener.dto.TwoFactorStatusResponse;
import com.example.URLShortener.models.User;
import com.example.URLShortener.models.UserTwoFactor;
import com.example.URLShortener.repository.UserRepository;
import com.example.URLShortener.repository.UserTwoFactorRepository;
import com.example.URLShortener.services.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TwoFactorService {

    private static final String ISSUER = "Trimly";

    private final UserTwoFactorRepository twoFactorRepository;
    private final UserRepository userRepository;
    private final RecoveryCodeService recoveryCodeService;
    private final TotpService totpService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public TwoFactorSetupResponse beginSetup(User user) {
        UserTwoFactor settings = twoFactorRepository.findByUser(user)
                .orElseGet(() -> UserTwoFactor.builder().user(user).enabled(false).build());

        if (settings.isEnabled()) {
            throw new TwoFactorAlreadyEnabledException("Two-factor authentication is already enabled");
        }

        String pendingSecret = totpService.generateSecret();
        settings.setPendingSecret(pendingSecret);
        twoFactorRepository.save(settings);

        return TwoFactorSetupResponse.builder()
                .secret(pendingSecret)
                .otpauthUri(totpService.buildOtpAuthUri(ISSUER, user.getEmail(), pendingSecret))
                .build();
    }

    @Transactional
    public TwoFactorSetupVerifyResponse verifySetup(User user, String code) {
        UserTwoFactor settings = getSettings(user);

        if (settings.isEnabled()) {
            throw new TwoFactorAlreadyEnabledException("Two-factor authentication is already enabled");
        }

        if (settings.getPendingSecret() == null
                || !totpService.isValidCode(settings.getPendingSecret(), code)) {
            throw new InvalidTwoFactorCodeException("Invalid verification code");
        }

        settings.setSecret(settings.getPendingSecret());
        settings.setPendingSecret(null);
        settings.setEnabled(true);
        settings.setEnabledAt(LocalDateTime.now());
        twoFactorRepository.save(settings);

        List<String> recoveryCodes = recoveryCodeService.replaceCodes(user);

        invalidateSessions(user);

        return TwoFactorSetupVerifyResponse.builder()
                .enabled(true)
                .recoveryCodes(recoveryCodes)
                .build();
    }

    @Transactional(readOnly = true)
    public TwoFactorStatusResponse getStatus(User user) {
        UserTwoFactor settings = twoFactorRepository.findByUser(user).orElse(null);
        boolean enabled = settings != null && settings.isEnabled();
        return TwoFactorStatusResponse.builder()
                .enabled(enabled)
                .remainingRecoveryCodes(enabled ? recoveryCodeService.remainingCodes(user) : 0)
                .build();
    }

    @Transactional
    public void disable(User user, String password, String code) {
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidTwoFactorDisableCredentialsException("Invalid password or verification code");
        }

        UserTwoFactor settings = getSettings(user);
        if (!settings.isEnabled() || settings.getSecret() == null
                || !totpService.isValidCode(settings.getSecret(), code)) {
            throw new InvalidTwoFactorDisableCredentialsException("Invalid password or verification code");
        }

        settings.setEnabled(false);
        settings.setSecret(null);
        settings.setPendingSecret(null);
        settings.setEnabledAt(null);
        twoFactorRepository.save(settings);
        recoveryCodeService.invalidate(user);
        invalidateSessions(user);
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(User user) {
        return twoFactorRepository.findByUser(user)
                .map(UserTwoFactor::isEnabled)
                .orElse(false);
    }

    public String createLoginChallenge(User user) {
        String token = jwtService.generateTwoFactorChallengeToken(
                user.getEmail(),
                user.getTokenVersion()
        );
        JwtService.TwoFactorChallenge challenge = jwtService.parseTwoFactorChallenge(token);
        redisTemplate.opsForValue().set(
                "2fa:challenge:" + challenge.id(),
                user.getId().toString(),
                Duration.ofMinutes(5)
        );
        return token;
    }

    @Transactional
    public AuthResponse verifyLoginChallenge(String challengeToken, String code) {
        JwtService.TwoFactorChallenge challenge = jwtService.parseTwoFactorChallenge(challengeToken);
        User user = userRepository.findByEmail(challenge.email())
                .orElseThrow(() -> new InvalidTwoFactorCodeException("Invalid two-factor challenge"));

        if (user.getTokenVersion() != challenge.tokenVersion()) {
            throw new InvalidTwoFactorCodeException("Invalid or expired two-factor challenge");
        }

        UserTwoFactor settings = getSettings(user);
        if (!settings.isEnabled() || settings.getSecret() == null) {
            throw new InvalidTwoFactorCodeException("Two-factor authentication is not enabled");
        }

        boolean validTotp = totpService.isValidCode(settings.getSecret(), code);
        boolean validRecovery = !validTotp && recoveryCodeService.consume(user, code);

        if (!validTotp && !validRecovery) {
            throw new InvalidTwoFactorCodeException("Invalid verification code");
        }

        String challengeKey = "2fa:challenge:" + challenge.id();
        Boolean consumed = redisTemplate.delete(challengeKey) ? Boolean.TRUE : Boolean.FALSE;
        if (!Boolean.TRUE.equals(consumed)) {
            throw new InvalidTwoFactorCodeException("Two-factor challenge has already been used or expired");
        }

        String token = jwtService.generateToken(user.getEmail(), user.getTokenVersion());
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .requiresTwoFactor(false)
                .build();
    }

    private UserTwoFactor getSettings(User user) {
        return twoFactorRepository.findByUser(user)
                .orElseThrow(() -> new TwoFactorNotConfiguredException("Two-factor authentication is not configured"));
    }

    private void invalidateSessions(User user) {
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
    }

    public static class TwoFactorAlreadyEnabledException extends RuntimeException {
        public TwoFactorAlreadyEnabledException(String message) { super(message); }
    }

    public static class InvalidTwoFactorCodeException extends RuntimeException {
        public InvalidTwoFactorCodeException(String message) { super(message); }
    }

    public static class TwoFactorNotConfiguredException extends RuntimeException {
        public TwoFactorNotConfiguredException(String message) { super(message); }
    }

    public static class InvalidTwoFactorDisableCredentialsException extends RuntimeException {
        public InvalidTwoFactorDisableCredentialsException(String message) { super(message); }
    }
}
