package com.nebulamind.controller;

import com.nebulamind.ai.AiQAResponse;
import com.nebulamind.ai.AiServiceClient;
import com.nebulamind.dto.QARequest;
import com.nebulamind.entity.File;
import com.nebulamind.entity.User;
import com.nebulamind.repository.UserRepository;
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

@WebMvcTest(QAController.class)
class QAControllerTest {

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
    void testDocumentQA() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));

        AiQAResponse qaResponse = AiQAResponse.builder()
                .question("test question")
                .answer("test answer")
                .sourceFileId(testFile.getId().toString())
                .sourceSnippets(Arrays.asList("snippet1"))
                .confidence(0.85)
                .build();

        when(aiServiceClient.documentQA(anyString(), anyString())).thenReturn(qaResponse);

        QARequest request = QARequest.builder()
                .question("test question")
                .fileId(testFile.getId().toString())
                .build();

        mockMvc.perform(post("/api/v1/qa")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("test question"))
                .andExpect(jsonPath("$.answer").value("test answer"))
                .andExpect(jsonPath("$.confidence").value(0.85));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testCrossDocumentQA() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        when(fileService.getUserFiles(any(UUID.class), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(Arrays.asList(testFile)));

        AiQAResponse qaResponse = AiQAResponse.builder()
                .question("cross document question")
                .answer("cross document answer")
                .sourceFileId(testFile.getId().toString())
                .sourceSnippets(Arrays.asList("snippet1", "snippet2"))
                .confidence(0.9)
                .build();

        when(aiServiceClient.crossDocumentQA(anyList(), anyString())).thenReturn(qaResponse);

        QARequest request = QARequest.builder()
                .question("cross document question")
                .build();

        mockMvc.perform(post("/api/v1/qa/cross")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("cross document question"))
                .andExpect(jsonPath("$.answer").value("cross document answer"));
    }
}
