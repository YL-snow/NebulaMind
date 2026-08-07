package com.nebulamind.controller;

import com.nebulamind.ai.AiClassifyResponse;
import com.nebulamind.ai.AiServiceClient;
import com.nebulamind.entity.File;
import com.nebulamind.entity.User;
import com.nebulamind.repository.FileRepository;
import com.nebulamind.repository.UserRepository;
import com.nebulamind.service.MinIOService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AIController.class)
class AIControllerTest {

    @TestConfiguration
    static class SecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiServiceClient aiServiceClient;

    @MockBean
    private com.nebulamind.service.FileService fileService;

    @MockBean
    private MinIOService minIOService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private FileRepository fileRepository;

    private User testUser;
    private File testFile;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .password("password")
                .build();

        testFile = File.builder()
                .id(UUID.randomUUID())
                .name("test.txt")
                .path("test/test.txt")
                .hash("test-hash")
                .size(100L)
                .user(testUser)
                .build();
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testClassifyFile() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        when(fileService.getFileById(any(UUID.class), any(UUID.class))).thenReturn(testFile);
        
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());
        when(minIOService.downloadFile(anyString())).thenReturn(inputStream);

        AiClassifyResponse classifyResponse = AiClassifyResponse.builder()
                .fileId(testFile.getId().toString())
                .category("文档")
                .tags(Arrays.asList("测试", "重要"))
                .sensitiveLevel("LOW")
                .confidence(0.95)
                .build();
        when(aiServiceClient.classifyFile(anyString(), anyString())).thenReturn(classifyResponse);

        mockMvc.perform(post("/api/v1/files/{id}/classify", testFile.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(testFile.getId().toString()))
                .andExpect(jsonPath("$.category").value("文档"))
                .andExpect(jsonPath("$.tags[0]").value("测试"))
                .andExpect(jsonPath("$.confidence").value(0.95));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testDetectDuplicates() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));

        File duplicateFile1 = File.builder()
                .id(UUID.randomUUID())
                .name("file1.txt")
                .path("test/file1.txt")
                .hash("duplicate-hash")
                .size(100L)
                .user(testUser)
                .build();

        File duplicateFile2 = File.builder()
                .id(UUID.randomUUID())
                .name("file2.txt")
                .path("test/file2.txt")
                .hash("duplicate-hash")
                .size(100L)
                .user(testUser)
                .build();

        List<File> files = Arrays.asList(testFile, duplicateFile1, duplicateFile2);
        when(fileService.getUserFiles(any(UUID.class), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(files));

        mockMvc.perform(get("/api/v1/files/duplicates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].files.length()").value(2));
    }
}
