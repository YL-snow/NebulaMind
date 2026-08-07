package com.nebulamind.cloud;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloudDriveFile {

    private String id;
    private String name;
    private String path;
    private String parentId;
    private Long size;
    private String mimeType;
    private String fileType;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @JsonProperty("is_folder")
    private Boolean isFolder;

    private String hash;
    private String category;
    private String tags;
}
