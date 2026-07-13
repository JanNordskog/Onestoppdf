package com.pdfsuite.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tool_job")
public class ToolJob {

    @Id
    @GeneratedValue
    private UUID id;

    @Column
    private UUID ownerId;

    @Column(nullable = false)
    private String tool;

    @Column(nullable = false)
    private String status;

    @Column(length = 1000)
    private String inputNames;

    @Column
    private UUID outputDocId;

    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public String getTool() { return tool; }
    public void setTool(String tool) { this.tool = tool; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getInputNames() { return inputNames; }
    public void setInputNames(String inputNames) { this.inputNames = inputNames; }
    public UUID getOutputDocId() { return outputDocId; }
    public void setOutputDocId(UUID outputDocId) { this.outputDocId = outputDocId; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
