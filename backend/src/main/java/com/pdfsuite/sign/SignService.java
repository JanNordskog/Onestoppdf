package com.pdfsuite.sign;

import com.pdfsuite.auth.AppUserRepo;
import com.pdfsuite.common.ApiException;
import com.pdfsuite.files.DocumentDto;
import com.pdfsuite.files.DocumentService;
import com.pdfsuite.files.StoredDocument;
import com.pdfsuite.tools.EditService;
import com.pdfsuite.tools.PdfToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SignService {

    private static final Logger log = LoggerFactory.getLogger(SignService.class);
    private static final Set<String> FIELD_TYPES = Set.of("SIGNATURE", "NAME", "DATE", "TEXT");

    private final SignatureRequestRepo requests;
    private final SignerRepo signers;
    private final SignatureFieldRepo fields;
    private final AuditEventRepo audits;
    private final DocumentService documents;
    private final EditService edit;
    private final AppUserRepo users;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final boolean mailEnabled;
    private final String mailFrom;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public SignService(SignatureRequestRepo requests, SignerRepo signers, SignatureFieldRepo fields,
                       AuditEventRepo audits, DocumentService documents, EditService edit, AppUserRepo users,
                       ObjectProvider<JavaMailSender> mailSender,
                       @Value("${app.mail.enabled}") boolean mailEnabled,
                       @Value("${app.mail.from}") String mailFrom,
                       @Value("${app.frontend-url}") String frontendUrl) {
        this.requests = requests;
        this.signers = signers;
        this.fields = fields;
        this.audits = audits;
        this.documents = documents;
        this.edit = edit;
        this.users = users;
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.mailFrom = mailFrom;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @Transactional
    public SignDtos.Detail create(UUID ownerId, SignDtos.CreateSignRequest req) {
        if (req.title() == null || req.title().isBlank()) throw ApiException.badRequest("Title is required");
        if (req.signers() == null || req.signers().isEmpty() || req.signers().size() > 10)
            throw ApiException.badRequest("Between 1 and 10 signers are required");
        if (req.fields() == null || req.fields().isEmpty())
            throw ApiException.badRequest("Place at least one field on the document");
        StoredDocument doc = documents.get(req.documentId());
        if (!DocumentService.PDF.equals(doc.getContentType()))
            throw ApiException.badRequest("Only PDFs can be sent for signing");

        SignatureRequest request = new SignatureRequest();
        request.setOwnerId(ownerId);
        request.setDocumentId(doc.getId());
        request.setTitle(req.title().trim());
        request.setMessage(req.message());
        request.setStatus("SENT");
        request = requests.save(request);

        List<Signer> savedSigners = new ArrayList<>();
        for (int i = 0; i < req.signers().size(); i++) {
            SignDtos.CreateSigner s = req.signers().get(i);
            if (s.name() == null || s.name().isBlank() || s.email() == null || s.email().isBlank())
                throw ApiException.badRequest("Every signer needs a name and email");
            Signer signer = new Signer();
            signer.setRequestId(request.getId());
            signer.setName(s.name().trim());
            signer.setEmail(s.email().trim().toLowerCase());
            signer.setOrderIndex(i);
            byte[] tokenBytes = new byte[32];
            random.nextBytes(tokenBytes);
            signer.setToken(Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes));
            savedSigners.add(signers.save(signer));
        }
        for (SignDtos.CreateField f : req.fields()) {
            if (f.signerIndex() < 0 || f.signerIndex() >= savedSigners.size())
                throw ApiException.badRequest("Field references signer #" + f.signerIndex() + " which doesn't exist");
            if (!FIELD_TYPES.contains(f.type()))
                throw ApiException.badRequest("Unknown field type: " + f.type());
            if (doc.getPageCount() != null && (f.page() < 1 || f.page() > doc.getPageCount()))
                throw ApiException.badRequest("Field targets page " + f.page() + " which doesn't exist");
            SignatureField field = new SignatureField();
            field.setRequestId(request.getId());
            field.setSignerId(savedSigners.get(f.signerIndex()).getId());
            field.setPage(f.page());
            field.setType(f.type());
            field.setX(f.x()); field.setY(f.y()); field.setW(f.w()); field.setH(f.h());
            fields.save(field);
        }
        audits.save(new AuditEvent(request.getId(), ownerName(ownerId), "created",
                "Request created with " + savedSigners.size() + " signer(s)", null));
        for (Signer signer : savedSigners) {
            sendInvite(signer, request);
        }
        audits.save(new AuditEvent(request.getId(), "system", "sent",
                mailEnabled ? "Invitations emailed" : "Invitations created (mail disabled — links shared manually)", null));
        return detail(ownerId, request.getId());
    }

    public List<SignDtos.Summary> list(UUID ownerId) {
        return requests.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(r -> {
            List<Signer> ss = signers.findByRequestIdOrderByOrderIndex(r.getId());
            long signed = ss.stream().filter(s -> "SIGNED".equals(s.getStatus())).count();
            return new SignDtos.Summary(r.getId(), r.getTitle(), r.getStatus(), r.getCreatedAt(),
                    signed + "/" + ss.size() + " signed");
        }).toList();
    }

    public SignDtos.Detail detail(UUID ownerId, UUID requestId) {
        SignatureRequest r = owned(ownerId, requestId);
        List<SignDtos.SignerDto> signerDtos = signers.findByRequestIdOrderByOrderIndex(r.getId()).stream()
                .map(s -> new SignDtos.SignerDto(s.getId(), s.getName(), s.getEmail(), s.getStatus(),
                        s.getSignedAt(), frontendUrl + "/sign/t/" + s.getToken()))
                .toList();
        List<SignDtos.AuditDto> auditDtos = audits.findByRequestIdOrderByAt(r.getId()).stream()
                .map(a -> new SignDtos.AuditDto(a.getAt(), a.getActor(), a.getAction(), a.getDetail()))
                .toList();
        DocumentDto finalDoc = r.getFinalDocumentId() == null ? null
                : DocumentDto.from(documents.get(r.getFinalDocumentId()));
        return new SignDtos.Detail(r.getId(), r.getTitle(), r.getMessage(), r.getStatus(),
                DocumentDto.from(documents.get(r.getDocumentId())), finalDoc, signerDtos, auditDtos);
    }

    @Transactional
    public void cancel(UUID ownerId, UUID requestId) {
        SignatureRequest r = owned(ownerId, requestId);
        if ("COMPLETED".equals(r.getStatus())) throw ApiException.badRequest("Request is already completed");
        r.setStatus("CANCELLED");
        requests.save(r);
        audits.save(new AuditEvent(r.getId(), ownerName(ownerId), "cancelled", null, null));
    }

    public SignDtos.PublicInfo publicInfo(String token) {
        Signer signer = byToken(token);
        SignatureRequest r = requests.findById(signer.getRequestId()).orElseThrow();
        StoredDocument doc = documents.get(currentDocId(r));
        List<SignDtos.PublicField> myFields = fields.findBySignerId(signer.getId()).stream()
                .map(f -> new SignDtos.PublicField(f.getId(), f.getPage(), f.getType(),
                        f.getX(), f.getY(), f.getW(), f.getH()))
                .toList();
        return new SignDtos.PublicInfo(r.getTitle(), r.getMessage(), ownerName(r.getOwnerId()),
                r.getStatus(), signer.getName(), signer.getStatus(), doc.getPageCount(), myFields);
    }

    public byte[] renderPage(String token, int page) {
        Signer signer = byToken(token);
        SignatureRequest r = requests.findById(signer.getRequestId()).orElseThrow();
        return documents.renderPagePng(currentDocId(r), page, 120);
    }

    public record Download(String name, String contentType, byte[] bytes) {}

    public Download download(String token) {
        Signer signer = byToken(token);
        SignatureRequest r = requests.findById(signer.getRequestId()).orElseThrow();
        StoredDocument doc = documents.get(r.getFinalDocumentId() != null ? r.getFinalDocumentId() : currentDocId(r));
        return new Download(doc.getOriginalName(), doc.getContentType(), documents.bytes(doc.getId()));
    }

    @Transactional
    public SignDtos.PublicInfo complete(String token, SignDtos.Complete req, String ip, String userAgent) {
        Signer signer = byToken(token);
        SignatureRequest r = requests.findById(signer.getRequestId()).orElseThrow();
        if (!"SENT".equals(r.getStatus())) throw ApiException.badRequest("This request is " + r.getStatus().toLowerCase());
        if (!"PENDING".equals(signer.getStatus())) throw ApiException.badRequest("You have already signed this document");

        List<SignatureField> myFields = fields.findBySignerId(signer.getId());
        List<EditService.Element> elements = new ArrayList<>();
        for (SignatureField f : myFields) {
            switch (f.getType()) {
                case "SIGNATURE" -> {
                    if (req.signatureDataUrl() == null || req.signatureDataUrl().isBlank())
                        throw ApiException.badRequest("Draw your signature first");
                    elements.add(new EditService.Element(f.getPage(), "image", f.getX(), f.getY(), f.getW(), f.getH(),
                            null, null, null, req.signatureDataUrl(), null, null, null, null));
                    f.setValue("signature-image");
                }
                case "NAME" -> {
                    elements.add(textElement(f, signer.getName()));
                    f.setValue(signer.getName());
                }
                case "DATE" -> {
                    String today = LocalDate.now(ZoneOffset.UTC).toString();
                    elements.add(textElement(f, today));
                    f.setValue(today);
                }
                case "TEXT" -> {
                    String value = req.textValues() == null ? null : req.textValues().get(f.getId().toString());
                    elements.add(textElement(f, value == null ? "" : value));
                    f.setValue(value);
                }
                default -> throw ApiException.badRequest("Unknown field type " + f.getType());
            }
            fields.save(f);
        }

        byte[] current = documents.bytes(currentDocId(r));
        byte[] stamped = elements.isEmpty() ? current : edit.applyElements(current, elements);
        StoredDocument working = documents.create(r.getOwnerId(), r.getTitle() + " (in progress).pdf",
                DocumentService.PDF, stamped);
        r.setWorkingDocumentId(working.getId());

        signer.setStatus("SIGNED");
        signer.setSignedAt(Instant.now());
        signer.setSignerIp(ip);
        signer.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 490)));
        signers.save(signer);
        audits.save(new AuditEvent(r.getId(), signer.getName() + " <" + signer.getEmail() + ">", "signed", null, ip));

        List<Signer> allSigners = signers.findByRequestIdOrderByOrderIndex(r.getId());
        boolean allSigned = allSigners.stream().allMatch(s -> "SIGNED".equals(s.getStatus()));
        if (allSigned) {
            byte[] original = documents.bytes(r.getDocumentId());
            byte[] withCertificate = CertificatePage.append(stamped, r, allSigners, sha256(original));
            StoredDocument finalDoc = documents.create(r.getOwnerId(), r.getTitle() + " (signed).pdf",
                    DocumentService.PDF, withCertificate);
            r.setFinalDocumentId(finalDoc.getId());
            r.setStatus("COMPLETED");
            r.setCompletedAt(Instant.now());
            audits.save(new AuditEvent(r.getId(), "system", "completed",
                    "All signers signed; certificate appended", null));
        }
        requests.save(r);
        return publicInfo(token);
    }

    private EditService.Element textElement(SignatureField f, String text) {
        return new EditService.Element(f.getPage(), "text", f.getX(), f.getY(), f.getW(), f.getH(),
                text, 12f, "#111111", null, null, null, null, null);
    }

    private UUID currentDocId(SignatureRequest r) {
        return r.getWorkingDocumentId() != null ? r.getWorkingDocumentId() : r.getDocumentId();
    }

    private SignatureRequest owned(UUID ownerId, UUID requestId) {
        SignatureRequest r = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Signature request not found"));
        if (!r.getOwnerId().equals(ownerId)) throw ApiException.forbidden("Not your signature request");
        return r;
    }

    private Signer byToken(String token) {
        return signers.findByToken(token)
                .orElseThrow(() -> ApiException.notFound("This signing link is invalid or has been revoked"));
    }

    private String ownerName(UUID ownerId) {
        return users.findById(ownerId).map(u -> u.getDisplayName()).orElse("Unknown");
    }

    private void sendInvite(Signer signer, SignatureRequest request) {
        String url = frontendUrl + "/sign/t/" + signer.getToken();
        JavaMailSender sender = mailSender.getIfAvailable();
        if (mailEnabled && sender != null) {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(mailFrom);
            msg.setTo(signer.getEmail());
            msg.setSubject("Signature requested: " + request.getTitle());
            msg.setText((request.getMessage() == null ? "" : request.getMessage() + "\n\n")
                    + "Please review and sign the document here:\n" + url);
            try {
                sender.send(msg);
                return;
            } catch (Exception e) {
                log.warn("Mail send failed for {}: {}", signer.getEmail(), e.getMessage());
            }
        }
        log.info("Sign link for {} <{}>: {}", signer.getName(), signer.getEmail(), url);
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            return "unavailable";
        }
    }
}
