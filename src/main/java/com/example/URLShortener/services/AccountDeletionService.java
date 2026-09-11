package com.example.URLShortener.services;

import com.example.URLShortener.dto.AccountDeletionRequest;
import com.example.URLShortener.models.URL;
import com.example.URLShortener.models.User;
import com.example.URLShortener.models.UserTwoFactor;
import com.example.URLShortener.repository.ClickEventRepository;
import com.example.URLShortener.repository.RecoveryCodeRepository;
import com.example.URLShortener.repository.UrlRepository;
import com.example.URLShortener.repository.UserRepository;
import com.example.URLShortener.repository.UserTwoFactorRepository;
import com.example.URLShortener.twofactor.RecoveryCodeService;
import com.example.URLShortener.twofactor.TotpService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final UrlRepository urlRepository;
    private final ClickEventRepository clickEventRepository;
    private final UserTwoFactorRepository twoFactorRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final RecoveryCodeService recoveryCodeService;
    private final TotpService totpService;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final ProfileImageService profileImageService;

    @Transactional
    public void deleteAccount(User user, AccountDeletionRequest request) {
        if (request == null || !"DELETE".equals(request.getConfirmation())) {
            throw new InvalidAccountDeletionRequestException("Type DELETE to confirm account deletion");
        }

        if (user.getPasswordHash() != null && !passwordEncoder.matches(
                request.getCurrentPassword() == null ? "" : request.getCurrentPassword(),
                user.getPasswordHash())) {
            throw new InvalidAccountDeletionCredentialsException("Unable to confirm account deletion");
        }

        UserTwoFactor twoFactor = twoFactorRepository.findByUser(user).orElse(null);
        if (twoFactor != null && twoFactor.isEnabled()) {
            String code = request.getTwoFactorCode() == null ? "" : request.getTwoFactorCode().trim();
            boolean validTotp = twoFactor.getSecret() != null && totpService.isValidCode(twoFactor.getSecret(), code);
            boolean validRecovery = !validTotp && recoveryCodeService.consume(user, code);
            if (!validTotp && !validRecovery) {
                throw new InvalidAccountDeletionCredentialsException("Unable to confirm account deletion");
            }
        }

        List<URL> urls = urlRepository.findByOwner(user);
        for (URL url : urls) {
            clickEventRepository.deleteByShortUrl(url.getShortUrl());
            redisTemplate.delete("short:" + url.getShortUrl());
            redisTemplate.delete("long:user:" + user.getId() + ":" + url.getLongUrl());
        }

        urlRepository.deleteAll(urls);
        recoveryCodeRepository.deleteByUser(user);
        if (twoFactor != null) {
            twoFactorRepository.delete(twoFactor);
        }
        profileImageService.deleteStoredImageFor(user);
        userRepository.delete(user);
    }

    public static class InvalidAccountDeletionRequestException extends RuntimeException {
        public InvalidAccountDeletionRequestException(String message) { super(message); }
    }

    public static class InvalidAccountDeletionCredentialsException extends RuntimeException {
        public InvalidAccountDeletionCredentialsException(String message) { super(message); }
    }
}
