package com.examsolver.util;

import com.examsolver.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyCleanupTask {

    private final ApiKeyRepository apiKeyRepository;

    /**
     * Mỗi ngày lúc 2h sáng, tự động vô hiệu hóa các key đã hết hạn.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void deactivateExpiredKeys() {
        List<com.examsolver.entity.ApiKey> expired = apiKeyRepository.findAll().stream()
                .filter(k -> k.isActive() && k.getExpiresAt().isBefore(LocalDateTime.now()))
                .toList();

        expired.forEach(k -> k.setActive(false));
        if (!expired.isEmpty()) {
            apiKeyRepository.saveAll(expired);
            log.info("Deactivated {} expired API keys", expired.size());
        }
    }
}
