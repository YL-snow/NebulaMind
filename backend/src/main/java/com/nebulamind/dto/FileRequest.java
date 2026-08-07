package com.nebulamind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileRequest {

    @NotBlank(message = "File name is required")
    @Size(max = 500, message = "File name must be less than 500 characters")
    private String name;

    @Size(max = 1000, message = "File path must be less than 1000 characters")
    private String path;

    private Long size;

    @NotBlank(message = "MIME type is required")
    @Size(max = 100, message = "MIME type must be less than 100 characters")
    private String mimeType;

    private byte[] content;
}
