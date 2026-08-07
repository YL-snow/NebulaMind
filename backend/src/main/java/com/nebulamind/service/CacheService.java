package com.nebulamind.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@Profile("!dev")
@RequiredArgsConstructor
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PREFIX_FILE_LIST = "file:list:";
    private static final String PREFIX_FILE_DETAIL = "file:detail:";
    private static final String PREFIX_SEARCH = "search:";
    private static final String PREFIX_QA = "qa:";
    private static final String PREFIX_USER_STATS = "user:stats:";

    public void cacheFileList(String userId, Object fileList) {
        String key = PREFIX_FILE_LIST + userId;
        redisTemplate.opsForValue().set(key, fileList, 10, TimeUnit.MINUTES);
        log.debug("Cached file list for user: {}", userId);
    }

    public Object getCachedFileList(String userId) {
        String key = PREFIX_FILE_LIST + userId;
        return redisTemplate.opsForValue().get(key);
    }

    public void evictFileList(String userId) {
        String key = PREFIX_FILE_LIST + userId;
        redisTemplate.delete(key);
        log.debug("Evicted file list cache for user: {}", userId);
    }

    public void cacheFileDetail(String fileId, Object fileDetail) {
        String key = PREFIX_FILE_DETAIL + fileId;
        redisTemplate.opsForValue().set(key, fileDetail, 15, TimeUnit.MINUTES);
        log.debug("Cached file detail: {}", fileId);
    }

    public Object getCachedFileDetail(String fileId) {
        String key = PREFIX_FILE_DETAIL + fileId;
        return redisTemplate.opsForValue().get(key);
    }

    public void evictFileDetail(String fileId) {
        String key = PREFIX_FILE_DETAIL + fileId;
        redisTemplate.delete(key);
        log.debug("Evicted file detail cache: {}", fileId);
    }

    public void cacheSearchResult(String userId, String query, Object result) {
        String key = PREFIX_SEARCH + userId + ":" + hashQuery(query);
        redisTemplate.opsForValue().set(key, result, 3, TimeUnit.MINUTES);
        log.debug("Cached search result for user: {}, query: {}", userId, query);
    }

    public Object getCachedSearchResult(String userId, String query) {
        String key = PREFIX_SEARCH + userId + ":" + hashQuery(query);
        return redisTemplate.opsForValue().get(key);
    }

    public void cacheQAResult(String userId, String question, Object result) {
        String key = PREFIX_QA + userId + ":" + hashQuery(question);
        redisTemplate.opsForValue().set(key, result, 5, TimeUnit.MINUTES);
        log.debug("Cached QA result for user: {}, question: {}", userId, question);
    }

    public Object getCachedQAResult(String userId, String question) {
        String key = PREFIX_QA + userId + ":" + hashQuery(question);
        return redisTemplate.opsForValue().get(key);
    }

    public void cacheUserStats(String userId, Object stats) {
        String key = PREFIX_USER_STATS + userId;
        redisTemplate.opsForValue().set(key, stats, 5, TimeUnit.MINUTES);
        log.debug("Cached user stats: {}", userId);
    }

    public Object getCachedUserStats(String userId) {
        String key = PREFIX_USER_STATS + userId;
        return redisTemplate.opsForValue().get(key);
    }

    public void incrementApiCall(String endpoint) {
        String key = "api:call:" + endpoint;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofHours(24));
    }

    public Long getApiCallCount(String endpoint) {
        String key = "api:call:" + endpoint;
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value.toString()) : 0L;
    }

    private String hashQuery(String query) {
        return String.valueOf(query.hashCode());
    }
}
