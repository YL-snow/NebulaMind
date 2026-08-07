package com.nebulamind.controller;

import com.nebulamind.ai.AiSearchResponse;
import com.nebulamind.ai.AiServiceClient;
import com.nebulamind.dto.SearchRequest;
import com.nebulamind.dto.SearchResponse;
import com.nebulamind.dto.SearchResultItem;
import com.nebulamind.entity.File;
import com.nebulamind.repository.UserRepository;
import com.nebulamind.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final AiServiceClient aiServiceClient;
    private final FileService fileService;
    private final UserRepository userRepository;

    public SearchController(AiServiceClient aiServiceClient, FileService fileService,
                            UserRepository userRepository) {
        this.aiServiceClient = aiServiceClient;
        this.fileService = fileService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<SearchResponse> semanticSearch(
            Authentication authentication,
            @RequestBody SearchRequest request) {
        
        try {
            UUID userId = getUserIdFromAuthentication(authentication);
            
            List<String> fileIds;
            if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
                fileIds = request.getFileIds();
            } else {
                fileIds = fileService.getUserFiles(userId, org.springframework.data.domain.PageRequest.of(0, 500))
                        .getContent()
                        .stream()
                        .map(f -> f.getId().toString())
                        .collect(Collectors.toList());
            }

            int topK = request.getTopK() != null ? request.getTopK() : 10;
            List<SearchResultItem> items = new ArrayList<>();

            try {
                AiSearchResponse aiResponse = aiServiceClient.semanticSearch(request.getQuery(), fileIds, topK);
                
                if (aiResponse.getResults() != null) {
                    items = aiResponse.getResults().stream().map(r -> {
                        List<String> highlights = r.getSnippet() != null
                                ? List.of(r.getSnippet())
                                : Collections.emptyList();
                        return SearchResultItem.builder()
                                .fileId(r.getFileId())
                                .fileName(r.getFileName())
                                .fileType(r.getCategory())
                                .size(0L)
                                .relevance(r.getScore() != null ? r.getScore() : 0.0)
                                .summary(r.getSnippet() != null ? r.getSnippet() : "")
                                .highlights(highlights)
                                .matchedChunks(Collections.emptyList())
                                .build();
                    }).collect(Collectors.toList());
                }
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("调用次数已达上限") || msg.contains("rate limit") || msg.contains("429")) {
                    log.warn("AI semantic search rate limited, using fallback");
                } else {
                    log.warn("AI semantic search failed, using fallback: {}", msg);
                }
            }

            // 回退：AI搜索无结果时，按文件名/标签搜索
            if (items.isEmpty() && request.getQuery() != null && !request.getQuery().isBlank()) {
                String query = request.getQuery().toLowerCase();
                List<File> allFiles = fileService.getUserFiles(userId,
                        org.springframework.data.domain.PageRequest.of(0, 500)).getContent();
                
                for (File f : allFiles) {
                    boolean nameMatch = f.getName() != null && f.getName().toLowerCase().contains(query);
                    boolean tagMatch = f.getTags() != null && f.getTags().toLowerCase().contains(query);
                    boolean typeMatch = f.getFileType() != null && f.getFileType().toLowerCase().contains(query);
                    
                    if (nameMatch || tagMatch || typeMatch) {
                        double relevance = nameMatch ? 0.8 : (tagMatch ? 0.6 : 0.4);
                        items.add(SearchResultItem.builder()
                                .fileId(f.getId().toString())
                                .fileName(f.getName())
                                .fileType(f.getFileType())
                                .size(f.getSize())
                                .relevance(relevance)
                                .summary("文件名/标签匹配: " + f.getName())
                                .highlights(List.of(f.getName()))
                                .matchedChunks(Collections.emptyList())
                                .build());
                    }
                }
            }

            // 分页
            int page = request.getPage() != null ? request.getPage() : 0;
            int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;
            int totalCount = items.size();
            
            List<SearchResultItem> pagedItems;
            if (request.getPage() != null || request.getPageSize() != null) {
                int fromIndex = page * pageSize;
                int toIndex = Math.min(fromIndex + pageSize, totalCount);
                if (fromIndex >= totalCount) {
                    pagedItems = Collections.emptyList();
                } else {
                    pagedItems = items.subList(fromIndex, toIndex);
                }
            } else {
                pagedItems = items;
            }

            SearchResponse response = SearchResponse.builder()
                    .query(request.getQuery())
                    .items(pagedItems)
                    .totalCount(totalCount)
                    .page(page)
                    .pageSize(pageSize)
                    .build();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("语义搜索失败", e);
            return ResponseEntity.ok(SearchResponse.builder()
                    .query(request.getQuery())
                    .items(Collections.emptyList())
                    .totalCount(0)
                    .page(0)
                    .pageSize(10)
                    .build());
        }
    }

    private UUID getUserIdFromAuthentication(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .map(user -> user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
