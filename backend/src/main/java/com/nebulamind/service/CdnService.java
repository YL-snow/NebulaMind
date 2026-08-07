package com.nebulamind.service;

import com.nebulamind.config.CdnConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class CdnService {

    private final CdnConfig cdnConfig;

    public String getDownloadUrl(String filePath) {
        if (cdnConfig.isEnabled() && cdnConfig.getBaseUrl() != null && !cdnConfig.getBaseUrl().isEmpty()) {
            String url = cdnConfig.getBaseUrl() + "/" + filePath;
            log.debug("CDN download URL: {}", url);
            return url;
        }
        return null;
    }

    public String getUploadUrl(String filePath) {
        if (cdnConfig.isEnabled() && cdnConfig.getBaseUrl() != null && !cdnConfig.getBaseUrl().isEmpty()) {
            String url = cdnConfig.getBaseUrl() + "/" + filePath + "?token=" + generateUploadToken(filePath);
            log.debug("CDN upload URL: {}", url);
            return url;
        }
        return null;
    }

    private String generateUploadToken(String filePath) {
        long timestamp = System.currentTimeMillis();
        String random = String.format("%08d", ThreadLocalRandom.current().nextInt(100000000));
        return String.format("%d_%s_%s", timestamp, filePath.hashCode(), random);
    }

    public boolean isEnabled() {
        return cdnConfig.isEnabled();
    }

    public void purgeCache(String filePath) {
        if (cdnConfig.isEnabled()) {
            log.info("Purging CDN cache for: {}", filePath);
        }
    }
}
