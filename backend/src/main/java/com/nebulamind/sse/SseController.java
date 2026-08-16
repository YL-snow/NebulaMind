package com.nebulamind.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/sse")
public class SseController {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    @GetMapping(value = "/subscribe/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String userId) {
        log.info("SSE subscription request from user: {}", userId);

        SseEmitter emitter = new SseEmitter(0L);
        emitters.put(userId, emitter);

        ScheduledFuture<?> heartbeat = scheduler.scheduleAtFixedRate(() -> {
            SseEmitter current = emitters.get(userId);
            if (current == null) {
                return;
            }
            try {
                current.send(SseEmitter.event()
                        .name("heartbeat")
                        .data(Map.of("time", System.currentTimeMillis())));
            } catch (IOException e) {
                log.warn("SSE heartbeat failed for user: {}", userId, e);
                emitters.remove(userId);
            }
        }, 25, 25, TimeUnit.SECONDS);

        Runnable stopHeartbeat = () -> {
            heartbeat.cancel(false);
            emitters.remove(userId);
        };

        emitter.onCompletion(() -> {
            log.info("SSE connection completed for user: {}", userId);
            stopHeartbeat.run();
        });

        emitter.onTimeout(() -> {
            log.info("SSE connection timeout for user: {}", userId);
            stopHeartbeat.run();
        });

        emitter.onError(e -> {
            log.error("SSE connection error for user: {}", userId, e);
            stopHeartbeat.run();
        });

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("message", "SSE connection established", "userId", userId)));
        } catch (IOException e) {
            log.error("Failed to send initial SSE message", e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    public void sendProgress(String userId, String fileId, int progress, String message) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("progress")
                        .data(Map.of(
                                "fileId", fileId,
                                "progress", progress,
                                "message", message,
                                "timestamp", System.currentTimeMillis()
                        )));
            } catch (IOException e) {
                log.error("Failed to send SSE progress update", e);
                emitters.remove(userId);
            }
        }
    }

    public void sendTaskStatus(String userId, String taskId, String status, String message) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("task")
                        .data(Map.of(
                                "taskId", taskId,
                                "status", status,
                                "message", message,
                                "timestamp", System.currentTimeMillis()
                        )));
            } catch (IOException e) {
                log.error("Failed to send SSE task status", e);
                emitters.remove(userId);
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        emitters.values().forEach(emitter -> {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("Error closing SSE emitter", e);
            }
        });
        emitters.clear();
        scheduler.shutdownNow();
        log.info("SSE controller cleaned up");
    }
}
