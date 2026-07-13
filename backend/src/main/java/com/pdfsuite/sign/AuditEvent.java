package com.pdfsuite.sign;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sign_audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID requestId;

    @Column(nullable = false)
    private Instant at = Instant.now();

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String action;

    @Column(length = 1000)
    private String detail;

    @Column
    private String ip;

    public AuditEvent() {}

    public AuditEvent(UUID requestId, String actor, String action, String detail, String ip) {
        this.requestId = requestId;
        this.actor = actor;
        this.action = action;
        this.detail = detail;
        this.ip = ip;
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public Instant getAt() { return at; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
    public String getIp() { return ip; }
}
