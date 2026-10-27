package com.example.govagent.model;

public record Citation(
        String documentId,
        String chunkId,
        String title,
        String source
) {
}
