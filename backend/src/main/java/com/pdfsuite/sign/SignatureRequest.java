package com.pdfsuite.sign;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "signature_request")
public class SignatureRequest {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private UUID documentId;

    /** Latest partially-signed copy; chains across signers. Null until the first signature. */
    @Column
    private UUID workingDocumentId;

    @Column
    private UUID finalDocumentId;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String message;

    /** SENT | COMPLETED | CANCELLED */
    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column
    private Instant completedAt;

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public UUID getWorkingDocumentId() { return workingDocumentId; }
    public void setWorkingDocumentId(UUID workingDocumentId) { this.workingDocumentId = workingDocumentId; }
    public UUID getFinalDocumentId() { return finalDocumentId; }
    public void setFinalDocumentId(UUID finalDocumentId) { this.finalDocumentId = finalDocumentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
