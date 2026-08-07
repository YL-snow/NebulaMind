package com.nebulamind.service;

import com.nebulamind.config.RabbitMQConfig;
import com.nebulamind.dto.FileEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("!dev")
@RequiredArgsConstructor
public class RabbitMQMessageService {

    private final RabbitTemplate rabbitTemplate;

    public void sendFileUploadEvent(FileEventDTO event) {
        log.info("Sending file upload event: {}", event.getFileId());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, "file.upload", event);
    }

    public void sendFileDeleteEvent(FileEventDTO event) {
        log.info("Sending file delete event: {}", event.getFileId());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, "file.delete", event);
    }

    public void sendFileProcessedEvent(FileEventDTO event) {
        log.info("Sending file processed event: {}", event.getFileId());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, "file.processed", event);
    }
}
