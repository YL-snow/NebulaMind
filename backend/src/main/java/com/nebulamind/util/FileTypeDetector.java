package com.nebulamind.util;

import java.util.Locale;
import java.util.Map;

public final class FileTypeDetector {

    private static final Map<String, String> EXTENSION_TYPES = Map.ofEntries(
            Map.entry("pdf", "pdf"),
            Map.entry("doc", "doc"),
            Map.entry("docx", "docx"),
            Map.entry("xls", "xls"),
            Map.entry("xlsx", "xlsx"),
            Map.entry("csv", "csv"),
            Map.entry("ppt", "ppt"),
            Map.entry("pptx", "pptx"),
            Map.entry("jpg", "jpg"),
            Map.entry("jpeg", "jpeg"),
            Map.entry("png", "png"),
            Map.entry("gif", "gif"),
            Map.entry("bmp", "bmp"),
            Map.entry("webp", "webp"),
            Map.entry("tiff", "tiff"),
            Map.entry("tif", "tif"),
            Map.entry("zip", "zip"),
            Map.entry("rar", "rar"),
            Map.entry("7z", "7z"),
            Map.entry("gz", "gz"),
            Map.entry("tar", "tar"),
            Map.entry("txt", "txt"),
            Map.entry("md", "md")
    );

    private FileTypeDetector() {
    }

    public static String detect(String mimeType, String fileName) {
        String fromMime = detectFromMime(mimeType);
        if (fromMime != null) {
            return fromMime;
        }
        String fromName = detectFromFileName(fileName);
        return fromName != null ? fromName : "unknown";
    }

    public static String detectStored(String storedType, String mimeType, String fileName) {
        String detected = detect(mimeType, fileName);
        if (detected != null && !"unknown".equals(detected)) {
            return detected;
        }
        return normalize(storedType);
    }

    public static String normalize(String fileType) {
        if (fileType == null || fileType.isBlank()) {
            return "unknown";
        }
        return switch (fileType.toLowerCase(Locale.ROOT)) {
            case "word" -> "docx";
            case "excel", "spreadsheet" -> "xlsx";
            case "ppt", "presentation" -> "pptx";
            case "archive" -> "zip";
            default -> fileType.toLowerCase(Locale.ROOT);
        };
    }

    private static String detectFromMime(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return null;
        }
        String mime = mimeType.toLowerCase(Locale.ROOT);
        if (mime.contains("pdf")) {
            return "pdf";
        }
        if (mime.contains("wordprocessingml")) {
            return "docx";
        }
        if (mime.contains("msword")) {
            return "doc";
        }
        if (mime.contains("spreadsheetml")) {
            return "xlsx";
        }
        if (mime.contains("ms-excel") || mime.contains("excel")) {
            return "xls";
        }
        if (mime.contains("presentationml")) {
            return "pptx";
        }
        if (mime.contains("ms-powerpoint") || mime.contains("powerpoint")) {
            return "ppt";
        }
        if (mime.contains("x-7z-compressed")) {
            return "7z";
        }
        if (mime.contains("x-rar-compressed")) {
            return "rar";
        }
        if (mime.contains("zip") || mime.contains("x-compressed") || mime.contains("gzip")) {
            return "zip";
        }
        if (mime.contains("csv")) {
            return "csv";
        }
        if (mime.contains("markdown")) {
            return "md";
        }
        if (mime.startsWith("image/")) {
            return switch (mime.substring("image/".length())) {
                case "jpeg", "pjpeg" -> "jpg";
                case "jpg" -> "jpg";
                case "png" -> "png";
                case "gif" -> "gif";
                case "bmp" -> "bmp";
                case "webp" -> "webp";
                case "tiff", "tif" -> "tiff";
                default -> "image";
            };
        }
        if (mime.startsWith("text/")) {
            return "txt";
        }
        if (mime.startsWith("video/")) {
            return "video";
        }
        if (mime.startsWith("audio/")) {
            return "audio";
        }
        return null;
    }

    private static String detectFromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0 || dot == lower.length() - 1) {
            return null;
        }
        return EXTENSION_TYPES.get(lower.substring(dot + 1));
    }
}
