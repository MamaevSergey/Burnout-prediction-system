package com.lamart.burnout.burnoutpredictionsystem.service;

import com.lamart.burnout.burnoutpredictionsystem.entity.RefreshToken;
import com.lamart.burnout.burnoutpredictionsystem.entity.SystemUser;
import com.lamart.burnout.burnoutpredictionsystem.repository.RefreshTokenRepository;
import com.lamart.burnout.burnoutpredictionsystem.repository.SystemUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final Long refreshTokenDurationMs = 604800000L;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SystemUserRepository userRepository;

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    @Transactional
    public RefreshToken createRefreshToken(String username) {
        SystemUser user = userRepository.findByUsername(username).orElseThrow();

        refreshTokenRepository.deleteByUser(user);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));
        refreshToken.setToken(UUID.randomUUID().toString());

        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Срок действия токена обновления истек. Пожалуйста, отправьте новый запрос на вход.");
        }
        return token;
    }

    @Transactional
    public void deleteByUsername(String username) {
        userRepository.findByUsername(username).ifPresent(refreshTokenRepository::deleteByUser);
    }
}