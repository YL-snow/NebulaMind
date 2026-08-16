package com.nebulamind.util;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.Set;

public final class TextExtractionUtil {

    private static final Set<String> PLAIN_TEXT_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "log", "csv", "json", "xml", "yml", "yaml",
            "ini", "conf", "properties", "sql", "html", "htm", "css", "js", "ts",
            "jsx", "tsx", "java", "kt", "py", "c", "cpp", "h", "hpp", "sh",
            "bat", "ps1", "env", "gitignore"
    );

    private TextExtractionUtil() {
    }

    public static boolean isPlainTextFile(String pathOrName) {
        if (pathOrName == null) {
            return false;
        }
        String lower = pathOrName.toLowerCase(Locale.ROOT);
        int slash = Math.max(lower.lastIndexOf('/'), lower.lastIndexOf('\\'));
        String name = slash >= 0 ? lower.substring(slash + 1) : lower;
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1) : name;
        return PLAIN_TEXT_EXTENSIONS.contains(ext);
    }

    public static String extractText(byte[] bytes, String pathOrName) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            Metadata metadata = new Metadata();
            if (pathOrName != null) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, pathOrName);
            }
            String text = new Tika().parseToString(in, metadata);
            return text == null ? "" : text.trim();
        } catch (Exception e) {
            return "";
        }
    }
}
