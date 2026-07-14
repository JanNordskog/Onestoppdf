package com.pdfsuite.files;

import com.pdfsuite.common.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class DocumentService {

    public static final String PDF = "application/pdf";

    private final StoredDocumentRepo repo;
    private final StorageService storage;
    private final long anonymousExpiryMinutes;

    public DocumentService(StoredDocumentRepo repo, StorageService storage,
                           @Value("${app.anonymous-expiry-minutes}") long anonymousExpiryMinutes) {
        this.repo = repo;
        this.storage = storage;
        this.anonymousExpiryMinutes = anonymousExpiryMinutes;
    }

    public StoredDocument create(UUID ownerId, String originalName, String contentType, byte[] bytes) {
        StoredDocument doc = new StoredDocument();
        doc.setOwnerId(ownerId);
        doc.setOriginalName(originalName == null || originalName.isBlank() ? "document" : originalName);
        doc.setContentType(contentType == null ? "application/octet-stream" : contentType);
        doc.setSizeBytes(bytes.length);
        if (PDF.equals(doc.getContentType())) {
            try (PDDocument pdf = Loader.loadPDF(bytes)) {
                doc.setPageCount(pdf.getNumberOfPages());
            } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
                // Password-protected output (e.g. the Protect tool) is a valid PDF whose
                // page count simply can't be read without the password.
                doc.setPageCount(null);
            } catch (IOException e) {
                throw ApiException.badRequest("\"" + doc.getOriginalName() + "\" is not a valid PDF");
            }
        }
        doc.setExpiresAt(ownerId == null ? Instant.now().plus(Duration.ofMinutes(anonymousExpiryMinutes)) : null);
        doc = repo.save(doc);
        storage.writeBytes(doc.getId(), bytes);
        return doc;
    }

    public StoredDocument get(UUID id) {
        StoredDocument doc = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("File not found (it may have expired)"));
        if (doc.getExpiresAt() != null && doc.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.notFound("File not found (it may have expired)");
        }
        return doc;
    }

    public byte[] bytes(UUID id) {
        get(id);
        return storage.readBytes(id);
    }

    public void delete(StoredDocument doc) {
        storage.delete(doc.getId());
        repo.delete(doc);
    }

    public byte[] renderPagePng(UUID id, int page, float dpi) {
        StoredDocument doc = get(id);
        if (!PDF.equals(doc.getContentType())) {
            throw ApiException.badRequest("Only PDF pages can be previewed");
        }
        try (PDDocument pdf = Loader.loadPDF(storage.readBytes(id))) {
            if (page < 1 || page > pdf.getNumberOfPages()) {
                throw ApiException.badRequest("Page " + page + " is out of range");
            }
            BufferedImage image = new PDFRenderer(pdf).renderImageWithDPI(page - 1, dpi);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed rendering page", e);
        }
    }
}
