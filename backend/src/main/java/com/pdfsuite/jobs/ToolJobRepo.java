package com.pdfsuite.jobs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ToolJobRepo extends JpaRepository<ToolJob, UUID> {
    List<ToolJob> findTop50ByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
