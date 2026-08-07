package com.nebulamind.config;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class LoggingConfig {

    public static final Logger APPLICATION_LOGGER = LoggerFactory.getLogger("com.nebulamind");
    public static final Logger SECURITY_LOGGER = LoggerFactory.getLogger("com.nebulamind.security");
    public static final Logger API_LOGGER = LoggerFactory.getLogger("com.nebulamind.api");
    public static final Logger SERVICE_LOGGER = LoggerFactory.getLogger("com.nebulamind.service");

    public LoggingConfig() {
        log.info("Logging configuration initialized");
    }
}
