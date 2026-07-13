package com.pdfsuite.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class CleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(CleanupScheduler.class);

    private final StoredDocumentRepo repo;
    private final StorageService storage;

    public CleanupScheduler(StoredDocumentRepo repo, StorageService storage) {
        this.repo = repo;
        this.storage = storage;
    }

    @Scheduled(fixedDelay = 600_000)
    public void deleteExpired() {
        List<StoredDocument> expired = repo.findByExpiresAtBefore(Instant.now());
        for (StoredDocument doc : expired) {
            storage.delete(doc.getId());
            repo.delete(doc);
        }
        if (!expired.isEmpty()) {
            log.info("Deleted {} expired anonymous files", expired.size());
        }
    }
}
