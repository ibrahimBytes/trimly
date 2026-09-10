package com.example.URLShortener.twofactor;

import com.example.URLShortener.models.RecoveryCode;
import com.example.URLShortener.models.User;
import com.example.URLShortener.repository.RecoveryCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecoveryCodeService {

    private static final int CODE_COUNT = 10;
    private static final int CODE_LENGTH = 10;
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final RecoveryCodeRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public List<String> replaceCodes(User user) {
        repository.deleteByUser(user);
        repository.flush();

        List<String> plaintextCodes = new ArrayList<>(CODE_COUNT);
        List<RecoveryCode> entities = new ArrayList<>(CODE_COUNT);

        for (int i = 0; i < CODE_COUNT; i++) {
            String code = generateCode();
            plaintextCodes.add(code);
            entities.add(RecoveryCode.builder()
                    .user(user)
                    .codeHash(passwordEncoder.encode(code))
                    .used(false)
                    .build());
        }

        repository.saveAll(entities);
        return plaintextCodes;
    }

    @Transactional
    public void invalidate(User user) {
        repository.deleteByUser(user);
        repository.flush();
    }

    @Transactional(readOnly = true)
    public int remainingCodes(User user) {
        return repository.findByUserAndUsedFalse(user).size();
    }

    @Transactional
    public boolean consume(User user, String suppliedCode) {
        if (suppliedCode == null || suppliedCode.isBlank()) {
            return false;
        }

        String normalized = normalize(suppliedCode);
        List<RecoveryCode> candidates = repository.findByUserAndUsedFalse(user);

        for (RecoveryCode candidate : candidates) {
            if (!passwordEncoder.matches(normalized, candidate.getCodeHash())) {
                continue;
            }

            if (candidate.isUsed()) {
                continue;
            }

            candidate.setUsed(true);
            candidate.setUsedAt(LocalDateTime.now());
            try {
                repository.saveAndFlush(candidate);
                return true;
            } catch (OptimisticLockingFailureException e) {
                return false;
            }
        }
        return false;
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH + 1);
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (i == CODE_LENGTH / 2) {
                code.append('-');
            }
            code.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }

    private String normalize(String value) {
        return value.trim().toUpperCase();
    }
}
