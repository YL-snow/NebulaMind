package com.nebulamind.service;

import com.nebulamind.dto.FileEventDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Dev profile 消息队列占位服务，替代 RabbitMQ。
 * 所有消息发送操作仅记录日志，不执行实际发送。
 */
@Slf4j
@Service
@Profile("dev")
public class NoOpMessageService {

    public void sendFileUploadEvent(FileEventDTO event) {
        log.debug("Dev mode: skipping RabbitMQ upload event for fileId={}", event.getFileId());
    }

    public void sendFileDeleteEvent(FileEventDTO event) {
        log.debug("Dev mode: skipping RabbitMQ delete event for fileId={}", event.getFileId());
    }

    public void sendFileProcessedEvent(FileEventDTO event) {
        log.debug("Dev mode: skipping RabbitMQ processed event for fileId={}", event.getFileId());
    }
}
