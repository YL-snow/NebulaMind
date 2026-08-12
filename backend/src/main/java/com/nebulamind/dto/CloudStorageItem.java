package com.nebulamind.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloudStorageItem {

    private String path;
    private String name;
    private boolean folder;
    private Long size;
    private String mimeType;
    private LocalDateTime updatedAt;
}
