package com.nebulamind.config;

import com.nebulamind.service.LocalStorageService;
import com.nebulamind.service.MinIOService;
import com.nebulamind.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
public class StorageConfig {

    @Value("${nebulamind.storage.enabled:local}")
    private String storageType;

    @Bean
    @Primary
    public StorageService storageService(
            MinIOService minIOService,
            LocalStorageService localStorageService) {

        StorageService service;
        if ("minio".equalsIgnoreCase(storageType)) {
            service = minIOService;
            log.info("Using MinIO storage service");
        } else {
            service = localStorageService;
            log.info("Using local storage service");
        }

        try {
            service.ensureBucketExists();
            log.info("Storage service initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize storage service", e);
        }

        return service;
    }
}