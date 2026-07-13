package com.pdfsuite.sign;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SignatureRequestRepo extends JpaRepository<SignatureRequest, UUID> {
    List<SignatureRequest> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}

interface SignerRepo extends JpaRepository<Signer, UUID> {
    Optional<Signer> findByToken(String token);
    List<Signer> findByRequestIdOrderByOrderIndex(UUID requestId);
}

interface SignatureFieldRepo extends JpaRepository<SignatureField, UUID> {
    List<SignatureField> findByRequestId(UUID requestId);
    List<SignatureField> findBySignerId(UUID signerId);
}

interface AuditEventRepo extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findByRequestIdOrderByAt(UUID requestId);
}
