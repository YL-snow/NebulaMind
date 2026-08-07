package com.nebulamind.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("!dev")
public class FileSpeedOptimizationService {

    private final CdnService cdnService;
    private final MinIOService minIOService;

    public byte[] optimizeDownload(String filePath) throws Exception {
        try (InputStream inputStream = minIOService.downloadFile(filePath)) {
            return gzipCompress(inputStream);
        }
    }

    public String getOptimizedDownloadUrl(String filePath) {
        String cdnUrl = cdnService.getDownloadUrl(filePath);
        if (cdnUrl != null) {
            return cdnUrl;
        }
        return null;
    }

    private byte[] gzipCompress(InputStream inputStream) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(baos)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) > 0) {
                gzipOutputStream.write(buffer, 0, len);
            }
        }
        log.debug("GZIP compression completed");
        return baos.toByteArray();
    }

    public boolean isGzipSupported(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        return mimeType.startsWith("text/") ||
               mimeType.contains("json") ||
               mimeType.contains("xml") ||
               mimeType.contains("javascript") ||
               mimeType.contains("css");
    }

    public long calculateOptimalChunkSize(long fileSize) {
        if (fileSize < 1024 * 1024) {
            return 256 * 1024;
        } else if (fileSize < 10 * 1024 * 1024) {
            return 512 * 1024;
        } else if (fileSize < 100 * 1024 * 1024) {
            return 1 * 1024 * 1024;
        } else {
            return 5 * 1024 * 1024;
        }
    }

    public int calculateTotalChunks(long fileSize, long chunkSize) {
        return (int) Math.ceil((double) fileSize / chunkSize);
    }
}
