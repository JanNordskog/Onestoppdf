package com.pdfsuite.files;

import com.pdfsuite.auth.CurrentUser;
import com.pdfsuite.common.ApiException;
import com.pdfsuite.jobs.ToolJob;
import com.pdfsuite.jobs.ToolJobRepo;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class FileController {

    public record JobDto(UUID id, String tool, String status, String inputNames,
                         UUID outputDocId, Instant createdAt) {}

    private final DocumentService documents;
    private final StoredDocumentRepo documentRepo;
    private final ToolJobRepo jobRepo;
    private final WordExtractor wordExtractor;

    public FileController(DocumentService documents, StoredDocumentRepo documentRepo, ToolJobRepo jobRepo,
                          WordExtractor wordExtractor) {
        this.documents = documents;
        this.documentRepo = documentRepo;
        this.jobRepo = jobRepo;
        this.wordExtractor = wordExtractor;
    }

    @PostMapping("/files")
    public List<DocumentDto> upload(@RequestParam("file") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw ApiException.badRequest("No files uploaded");
        }
        UUID ownerId = CurrentUser.idOrNull();
        return files.stream().map(f -> {
            try {
                return DocumentDto.from(documents.create(ownerId, f.getOriginalFilename(),
                        f.getContentType(), f.getBytes()));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed reading upload", e);
            }
        }).toList();
    }

    @GetMapping("/files/{id}")
    public DocumentDto get(@PathVariable UUID id) {
        return DocumentDto.from(documents.get(id));
    }

    @GetMapping("/files/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        StoredDocument doc = documents.get(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(doc.getContentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(doc.getOriginalName(), java.nio.charset.StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(documents.bytes(id));
    }

    @GetMapping("/files/{id}/pages/{n}")
    public ResponseEntity<byte[]> page(@PathVariable UUID id, @PathVariable int n,
                                       @RequestParam(defaultValue = "96") float dpi) {
        byte[] png = documents.renderPagePng(id, n, Math.min(dpi, 300));
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    @GetMapping("/files/{id}/pages/{n}/words")
    public List<WordExtractor.Word> words(@PathVariable UUID id, @PathVariable int n) {
        return wordExtractor.words(documents.bytes(id), n);
    }

    @DeleteMapping("/files/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        StoredDocument doc = documents.get(id);
        if (doc.getOwnerId() != null && !doc.getOwnerId().equals(CurrentUser.idOrNull())) {
            throw ApiException.forbidden("Not your file");
        }
        documents.delete(doc);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/files")
    public List<DocumentDto> myFiles() {
        return documentRepo.findByOwnerIdOrderByCreatedAtDesc(CurrentUser.idOrThrow())
                .stream().map(DocumentDto::from).toList();
    }

    @GetMapping("/me/jobs")
    public List<JobDto> myJobs() {
        return jobRepo.findTop50ByOwnerIdOrderByCreatedAtDesc(CurrentUser.idOrThrow()).stream()
                .map(j -> new JobDto(j.getId(), j.getTool(), j.getStatus(), j.getInputNames(),
                        j.getOutputDocId(), j.getCreatedAt()))
                .toList();
    }
}
