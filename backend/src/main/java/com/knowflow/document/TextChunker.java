package com.knowflow.document;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {
    private static final int CHUNK_SIZE = 800;
    private static final int OVERLAP = 120;

    public List<Chunk> chunk(ParsedDocument document) {
        List<Chunk> result = new ArrayList<>();
        int index = 0;
        for (ParsedDocument.Section section : document.sections()) {
            String cleaned = clean(section.text());
            if (cleaned.isBlank()) continue;
            int start = 0;
            while (start < cleaned.length()) {
                int end = Math.min(cleaned.length(), start + CHUNK_SIZE);
                if (end < cleaned.length()) {
                    int natural = Math.max(cleaned.lastIndexOf('\n', end), cleaned.lastIndexOf('。', end));
                    if (natural > start + CHUNK_SIZE / 2) end = natural + 1;
                }
                String content = cleaned.substring(start, end).trim();
                if (!content.isBlank()) result.add(new Chunk(index++, section.pageNumber(), section.heading(), content));
                if (end >= cleaned.length()) break;
                start = Math.max(start + 1, end - OVERLAP);
            }
        }
        return result;
    }

    private String clean(String text) {
        if (text == null) return "";
        return text.replace("\u0000", "").replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\\n\\n").trim();
    }

    public record Chunk(int index, Integer pageNumber, String heading, String content) {}
}
