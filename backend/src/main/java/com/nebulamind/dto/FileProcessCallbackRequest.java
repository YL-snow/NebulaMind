package com.nebulamind.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileProcessCallbackRequest {

    private UUID fileId;
    private String status;
    private String category;
    private String tags;
    private String summary;
    private String sensitiveLevel;
    private String sensitiveItems;
    private String errorMessage;
}
