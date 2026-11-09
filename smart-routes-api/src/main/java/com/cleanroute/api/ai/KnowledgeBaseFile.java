package com.cleanroute.api.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One Markdown file from {@code classpath:kb/}. It must start with front matter giving
 * {@code title} and {@code source_url}:
 * <pre>
 * ---
 * title: WHO 2021 air quality guidelines
 * source_url: https://www.who.int/...
 * ---
 * </pre>
 */
record KnowledgeBaseFile(String name, String title, String sourceUrl, String body, String rawContent) {

    static KnowledgeBaseFile parse(String name, String content) {
        String text = content.replace("\r\n", "\n");
        if (!text.startsWith("---\n")) {
            throw new IllegalArgumentException(name + ": missing front matter (title, source_url)");
        }
        int end = text.indexOf("\n---", 4);
        if (end < 0) {
            throw new IllegalArgumentException(name + ": front matter is not closed with ---");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : text.substring(4, end).split("\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                fields.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
        }
        String title = fields.get("title");
        String url = fields.get("source_url");
        if (title == null || title.isBlank() || url == null || !url.startsWith("https://")) {
            throw new IllegalArgumentException(name + ": front matter needs a title and an https source_url");
        }
        String body = text.substring(text.indexOf('\n', end + 1) + 1).strip();
        if (body.isEmpty()) {
            throw new IllegalArgumentException(name + ": no text after the front matter");
        }
        return new KnowledgeBaseFile(name, title, url, body, text);
    }

    /** Changes when the file, the chunk size or the vector size changes, so the file is re-embedded. */
    String contentHash(int chunkSizeTokens, int dimensions) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest((rawContent + "|chunk=" + chunkSizeTokens + "|dims=" + dimensions)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
