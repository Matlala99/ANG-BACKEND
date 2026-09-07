package com.agc.cms.security;

import java.util.Set;
import java.util.regex.Pattern;

public final class SanitizerUtils {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "csv", "txt", "png", "jpg", "jpeg", "webp"
    );

    private SanitizerUtils() {}

    /**
     * Escape special HTML characters to prevent XSS attacks.
     */
    public static String escapeHtml(String input) {
        if (input == null) return null;
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#x27;");
    }

    /**
     * Strip HTML tags and trim whitespace.
     */
    public static String stripHtml(String input) {
        if (input == null) return "";
        return HTML_TAG_PATTERN.matcher(input).replaceAll("").trim();
    }

    /**
     * Sanitize a filename, stripping directory traversal characters and verifying safe extensions.
     */
    public static String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            return "unnamed-document.dat";
        }

        // Remove any path traversal tokens
        String clean = originalFilename.replaceAll("[\\\\/]+", "_")
                                       .replaceAll("\\.\\.+", ".")
                                       .trim();

        // Extract extension
        int lastDot = clean.lastIndexOf('.');
        String ext = (lastDot > 0 && lastDot < clean.length() - 1) ? clean.substring(lastDot + 1).toLowerCase() : "";

        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            // Force safe extension if illegal or unknown
            clean = clean + ".pdf";
        }

        // Replace any remaining unsafe characters
        clean = clean.replaceAll("[^a-zA-Z0-9._-]", "_");

        return clean;
    }

    /**
     * Sanitize and limit length of user text.
     */
    public static String sanitizeText(String text, int maxLength) {
        if (text == null) return null;
        String sanitized = escapeHtml(text.trim());
        if (sanitized.length() > maxLength) {
            return sanitized.substring(0, maxLength);
        }
        return sanitized;
    }
}
