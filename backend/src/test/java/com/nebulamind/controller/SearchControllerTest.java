package com.nebulamind.controller;

import com.nebulamind.ai.AiSearchResponse;
import com.nebulamind.ai.AiServiceClient;
import com.nebulamind.dto.SearchRequest;
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

@WebMvcTest(SearchController.class)
class SearchControllerTest {

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
    void testSemanticSearch() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        when(fileService.getUserFiles(any(UUID.class), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(Arrays.asList(testFile)));

        AiSearchResponse.SearchResult result = AiSearchResponse.SearchResult.builder()
                .fileId(testFile.getId().toString())
                .fileName("test.txt")
                .snippet("test snippet")
                .score(0.9)
                .category("文档")
                .build();

        AiSearchResponse searchResponse = AiSearchResponse.builder()
                .query("test query")
                .results(Arrays.asList(result))
                .build();

        when(aiServiceClient.semanticSearch(anyString(), anyList(), any(Integer.class))).thenReturn(searchResponse);

        SearchRequest request = SearchRequest.builder()
                .query("test query")
                .topK(10)
                .build();

        mockMvc.perform(post("/api/v1/search")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("test query"))
                .andExpect(jsonPath("$.items[0].fileId").value(testFile.getId().toString()))
                .andExpect(jsonPath("$.items[0].relevance").value(0.9))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(10));
    }
}
