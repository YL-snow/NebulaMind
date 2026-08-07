package com.nebulamind.controller;

import com.nebulamind.ai.AiGenerateResponse;
import com.nebulamind.ai.AiServiceClient;
import com.nebulamind.api.client.maas.MaasApiClient;
import com.nebulamind.dto.GenerateRequest;
import com.nebulamind.entity.File;
import com.nebulamind.entity.User;
import com.nebulamind.repository.UserRepository;
import com.nebulamind.service.MinIOService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GenerateController.class)
class GenerateControllerTest {

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
    private MaasApiClient maasApiClient;

    @MockBean
    private com.nebulamind.service.FileService fileService;

    @MockBean
    private MinIOService minIOService;

    @MockBean
    private UserRepository userRepository;

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
    void testGenerateSummary() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        when(fileService.getFileById(any(UUID.class), any(UUID.class))).thenReturn(testFile);
        
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());
        when(minIOService.downloadFile(anyString())).thenReturn(inputStream);

        AiGenerateResponse generateResponse = AiGenerateResponse.builder()
                .fileId(testFile.getId().toString())
                .content("test summary content")
                .keyPoints(Arrays.asList("key point 1", "key point 2"))
                .format("text")
                .build();

        when(aiServiceClient.generateSummary(anyString(), anyString())).thenReturn(generateResponse);

        GenerateRequest request = GenerateRequest.builder()
                .fileId(testFile.getId().toString())
                .build();

        mockMvc.perform(post("/api/v1/generate/summary")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("test summary content"))
                .andExpect(jsonPath("$.keyPoints[0]").value("key point 1"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testGenerateReport() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        when(fileService.getUserFiles(any(UUID.class), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(Arrays.asList(testFile)));

        AiGenerateResponse generateResponse = AiGenerateResponse.builder()
                .content("test report content")
                .keyPoints(Arrays.asList("report point 1"))
                .format("markdown")
                .build();

        when(aiServiceClient.generateReport(anyList(), anyString())).thenReturn(generateResponse);

        GenerateRequest request = GenerateRequest.builder()
                .topic("Test Report")
                .build();

        mockMvc.perform(post("/api/v1/generate/report")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("test report content"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testGeneratePPT() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        when(fileService.getUserFiles(any(UUID.class), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(Arrays.asList(testFile)));

        AiGenerateResponse generateResponse = AiGenerateResponse.builder()
                .content("PPT content")
                .keyPoints(Arrays.asList("slide 1", "slide 2"))
                .format("pptx")
                .build();

        when(aiServiceClient.generatePPT(anyList(), anyString())).thenReturn(generateResponse);

        GenerateRequest request = GenerateRequest.builder()
                .topic("Test Presentation")
                .build();

        mockMvc.perform(post("/api/v1/generate/ppt")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("pptx"));
    }
}
