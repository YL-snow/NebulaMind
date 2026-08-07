package com.nebulamind.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

public interface StorageService {

    void ensureBucketExists() throws Exception;

    String uploadFile(String objectName, MultipartFile file) throws Exception;

    String uploadFile(String objectName, byte[] content, String contentType) throws Exception;

    InputStream downloadFile(String objectName) throws Exception;

    String copyFile(String sourceObjectName, String destObjectName) throws Exception;

    void deleteFile(String objectName) throws Exception;

    boolean fileExists(String objectName) throws Exception;
}