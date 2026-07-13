package com.pdfsuite.files;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StoredDocumentRepo extends JpaRepository<StoredDocument, UUID> {
    List<StoredDocument> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    List<StoredDocument> findByExpiresAtBefore(Instant cutoff);
}
