package com.nebulamind.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileEventDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID fileId;
    private String filePath;
    private UUID userId;
    private String eventType;
    private LocalDateTime timestamp;

    public static FileEventDTO ofUpload(UUID fileId, String filePath, UUID userId) {
        return FileEventDTO.builder()
                .fileId(fileId)
                .filePath(filePath)
                .userId(userId)
                .eventType("UPLOAD")
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static FileEventDTO ofDelete(UUID fileId, UUID userId) {
        return FileEventDTO.builder()
                .fileId(fileId)
                .userId(userId)
                .eventType("DELETE")
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static FileEventDTO ofProcessed(UUID fileId, UUID userId) {
        return FileEventDTO.builder()
                .fileId(fileId)
                .userId(userId)
                .eventType("PROCESSED")
                .timestamp(LocalDateTime.now())
                .build();
    }
}
