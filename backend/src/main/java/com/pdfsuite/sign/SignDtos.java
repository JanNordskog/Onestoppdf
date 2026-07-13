package com.pdfsuite.sign;

import com.pdfsuite.files.DocumentDto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SignDtos {

    private SignDtos() {}

    public record CreateSigner(String name, String email) {}

    public record CreateField(int signerIndex, int page, String type, float x, float y, float w, float h) {}

    public record CreateSignRequest(UUID documentId, String title, String message,
                                    List<CreateSigner> signers, List<CreateField> fields) {}

    public record SignerDto(UUID id, String name, String email, String status,
                            Instant signedAt, String signUrl) {}

    public record AuditDto(Instant at, String actor, String action, String detail) {}

    public record Summary(UUID id, String title, String status, Instant createdAt, String signerSummary) {}

    public record Detail(UUID id, String title, String message, String status,
                         DocumentDto document, DocumentDto finalDocument,
                         List<SignerDto> signers, List<AuditDto> audit) {}

    public record PublicField(UUID id, int page, String type, float x, float y, float w, float h) {}

    public record PublicInfo(String requestTitle, String message, String ownerName, String status,
                             String signerName, String signerStatus, Integer pageCount,
                             List<PublicField> myFields) {}

    public record Complete(String signatureDataUrl, Map<String, String> textValues) {}
}
