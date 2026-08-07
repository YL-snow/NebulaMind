package com.nebulamind.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendProgress(String userId, String fileId, int progress, String message) {
        log.info("Sending progress update for user {} file {}: {}% - {}", userId, fileId, progress, message);
        messagingTemplate.convertAndSendToUser(
                userId,
                "/topic/progress",
                Map.of(
                        "fileId", fileId,
                        "progress", progress,
                        "message", message,
                        "timestamp", System.currentTimeMillis()
                )
        );
    }

    public void sendTaskStatus(String userId, String taskId, String status, String message) {
        log.info("Sending task status for user {} task {}: {} - {}", userId, taskId, status, message);
        messagingTemplate.convertAndSendToUser(
                userId,
                "/topic/task",
                Map.of(
                        "taskId", taskId,
                        "status", status,
                        "message", message,
                        "timestamp", System.currentTimeMillis()
                )
        );
    }

    public void sendNotification(String userId, String type, String message) {
        log.info("Sending notification for user {}: {} - {}", userId, type, message);
        messagingTemplate.convertAndSendToUser(
                userId,
                "/topic/notification",
                Map.of(
                        "type", type,
                        "message", message,
                        "timestamp", System.currentTimeMillis()
                )
        );
    }
}
