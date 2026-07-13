package com.pdfsuite.files;

import java.time.Instant;
import java.util.UUID;

public record DocumentDto(UUID id, String name, String contentType, long sizeBytes,
                          Integer pageCount, Instant createdAt, Instant expiresAt) {

    public static DocumentDto from(StoredDocument doc) {
        return new DocumentDto(doc.getId(), doc.getOriginalName(), doc.getContentType(),
                doc.getSizeBytes(), doc.getPageCount(), doc.getCreatedAt(), doc.getExpiresAt());
    }
}
