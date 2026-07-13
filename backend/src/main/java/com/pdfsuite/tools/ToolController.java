package com.pdfsuite.tools;

import com.pdfsuite.auth.CurrentUser;
import com.pdfsuite.common.ApiException;
import com.pdfsuite.files.DocumentDto;
import com.pdfsuite.files.DocumentService;
import com.pdfsuite.files.StoredDocument;
import com.pdfsuite.jobs.ToolJob;
import com.pdfsuite.jobs.ToolJobRepo;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tools")
public class ToolController {

    public record IdsRequest(List<UUID> documentIds) {}
    public record IdRequest(UUID documentId) {}
    public record SplitRequest(UUID documentId, String ranges) {}
    public record OrganizeRequest(UUID documentId, List<Integer> pages) {}
    public record RotateRequest(UUID documentId, int degrees, List<Integer> pages) {}
    public record CompressRequest(UUID documentId, String level) {}
    public record WatermarkRequest(UUID documentId, String text, Float fontSize, Float opacity) {}
    public record PageNumbersRequest(UUID documentId, Integer startAt) {}
    public record ProtectRequest(UUID documentId, String password) {}
    public record ImagesRequest(UUID documentId, Float dpi) {}
    public record EditRequest(UUID documentId, List<EditService.Element> elements) {}
    public record FormDataRequest(UUID documentId, String format) {}

    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final DocumentService documents;
    private final ToolJobRepo jobs;
    private final PdfToolService pdf;
    private final EditService edit;
    private final ConvertService convert;

    public ToolController(DocumentService documents, ToolJobRepo jobs, PdfToolService pdf,
                          EditService edit, ConvertService convert) {
        this.documents = documents;
        this.jobs = jobs;
        this.pdf = pdf;
        this.edit = edit;
        this.convert = convert;
    }

    @PostMapping("/merge")
    public DocumentDto merge(@RequestBody IdsRequest req) {
        List<StoredDocument> inputs = requireDocs(req.documentIds(), 2);
        return run("merge", inputs, () -> {
            byte[] out = pdf.merge(inputs.stream().map(d -> documents.bytes(d.getId())).toList());
            return save("merged.pdf", DocumentService.PDF, out);
        });
    }

    @PostMapping("/split")
    public DocumentDto split(@RequestBody SplitRequest req) {
        StoredDocument in = requireDoc(req.documentId());
        return run("split", List.of(in), () -> {
            if (req.ranges() == null || req.ranges().isBlank()) {
                return save(base(in) + "-pages.zip", "application/zip", pdf.splitToZip(bytes(in)));
            }
            return save(base(in) + "-extract.pdf", DocumentService.PDF, pdf.extractPages(bytes(in), req.ranges()));
        });
    }

    @PostMapping("/organize")
    public DocumentDto organize(@RequestBody OrganizeRequest req) {
        StoredDocument in = requireDoc(req.documentId());
        return run("organize", List.of(in),
                () -> save(base(in) + "-organized.pdf", DocumentService.PDF, pdf.organize(bytes(in), req.pages())));
    }

    @PostMapping("/rotate")
    public DocumentDto rotate(@RequestBody RotateRequest req) {
        StoredDocument in = requireDoc(req.documentId());
        return run("rotate", List.of(in),
                () -> save(base(in) + "-rotated.pdf", DocumentService.PDF, pdf.rotate(bytes(in), req.degrees(), req.pages())));
    }

    @PostMapping("/compress")
    public DocumentDto compress(@RequestBody CompressRequest req) {
        StoredDocument in = requireDoc(req.documentId());
        return run("compress", List.of(in),
                () -> save(base(in) + "-compressed.pdf", DocumentService.PDF, pdf.compress(bytes(in), req.level())));
    }

    @PostMapping("/watermark")
    public DocumentDto watermark(@RequestBody WatermarkRequest req) {
        StoredDocument in = requireDoc(req.documentId());
        if (req.text() == null || req.text().isBlank()) throw ApiException.badRequest("Watermark text is required");
        float fontSize = req.fontSize() == null ? 48f : req.fontSize();
        float opacity = req.opacity() == null ? 0.25f : Math.clamp(req.opacity(), 0.05f, 1f);
        return run("watermark", List.of(in),
                () -> save(base(in) + "-watermarked.pdf", DocumentService.PDF, pdf.watermark(bytes(in), req.text(), fontSize, opacity)));
    }

    @PostMapping("/page-numbers")
    public DocumentDto pageNumbers(@RequestBody PageNumbersRequest req) {
        StoredDocument in = requireDoc(req.documentId());
        int startAt = req.startAt() == null ? 1 : req.startAt();
        return run("page-numbers", List.of(in),
                () -> save(base(in) + "-numbered.pdf", DocumentService.PDF, pdf.pageNumbers(bytes(in), startAt)));
    }

    @PostMapping("/protect")
    public DocumentDto protect(@RequestBody ProtectRequest req) {
        StoredDocument in = requireDoc(req.documentId());
        if (req.password() == null || req.password().length() < 4)
            throw ApiException.badRequest("Password must be at least 4 characters");
        return run("protect", List.of(in),
                () -> save(base(in) + "-protected.pdf", DocumentService.PDF, pdf.protect(bytes(in), req.password())));
    }

    @PostMapping("/unlock")
    public DocumentDto unlock(@RequestBody ProtectRequest req) {
        StoredDocument in = requireDoc(req.documentId());
        return run("unlock", List.of(in),
                () -> save(base(in) + "-unlocked.pdf", DocumentService.PDF, pdf.unlock(bytes(in), req.password())));
    }

    @PostMapping("/pdf-to-word")
    public DocumentDto pdfToWord(@RequestBody IdRequest req) {
        StoredDocument in = requireDoc(req.documentId());
        return run("pdf-to-word", List.of(in), () -> save(base(in) + ".docx", DOCX, convert.pdfToWord(bytes(in))));
    }

    @PostMapping("/pdf-to-text")
    public DocumentDto pdfToText(@RequestBody IdRequest req) {
        StoredDocument in = requireDoc(req.documentId());
        return run("pdf-to-text", List.of(in), () -> save(base(in) + ".txt", "text/plain", convert.pdfToText(bytes(in))));
    }

    @PostMapping("/pdf-to-images")
    public DocumentDto pdfToImages(@RequestBody ImagesRequest req) {
        StoredDocument in = requireDoc(req.documentId());
        float dpi = req.dpi() == null ? 150f : req.dpi();
        return run("pdf-to-images", List.of(in),
                () -> save(base(in) + "-images.zip", "application/zip", pdf.pdfToImagesZip(bytes(in), dpi)));
    }

    @PostMapping("/images-to-pdf")
    public DocumentDto imagesToPdf(@RequestBody IdsRequest req) {
        List<StoredDocument> inputs = requireDocs(req.documentIds(), 1);
        return run("images-to-pdf", inputs, () -> {
            byte[] out = pdf.imagesToPdf(inputs.stream().map(d -> documents.bytes(d.getId())).toList());
            return save("images.pdf", DocumentService.PDF, out);
        });
    }

    @PostMapping("/edit")
    public DocumentDto editPdf(@RequestBody EditRequest req) {
        StoredDocument in = requireDoc(req.documentId());
        return run("edit", List.of(in),
                () -> save(base(in) + "-edited.pdf", DocumentService.PDF, edit.applyElements(bytes(in), req.elements())));
    }

    @PostMapping("/extract-form-data")
    public DocumentDto extractFormData(@RequestBody FormDataRequest req) {
        StoredDocument in = requireDoc(req.documentId());
        boolean csv = "csv".equalsIgnoreCase(req.format());
        return run("extract-form-data", List.of(in),
                () -> save(base(in) + "-form-data." + (csv ? "csv" : "json"),
                        csv ? "text/csv" : "application/json",
                        edit.extractFormData(bytes(in), csv ? "csv" : "json")));
    }

    @PostMapping("/strip-metadata")
    public DocumentDto stripMetadata(@RequestBody IdRequest req) {
        StoredDocument in = requireDoc(req.documentId());
        return run("strip-metadata", List.of(in),
                () -> save(base(in) + "-clean.pdf", DocumentService.PDF, edit.stripMetadata(bytes(in))));
    }

    private DocumentDto run(String tool, List<StoredDocument> inputs, Supplier<StoredDocument> op) {
        ToolJob job = new ToolJob();
        job.setOwnerId(CurrentUser.idOrNull());
        job.setTool(tool);
        String names = inputs.stream().map(StoredDocument::getOriginalName).collect(Collectors.joining(", "));
        job.setInputNames(names.length() > 990 ? names.substring(0, 990) : names);
        try {
            StoredDocument out = op.get();
            job.setStatus("DONE");
            job.setOutputDocId(out.getId());
            return DocumentDto.from(out);
        } catch (RuntimeException e) {
            job.setStatus("ERROR");
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            job.setErrorMessage(msg.length() > 990 ? msg.substring(0, 990) : msg);
            throw e;
        } finally {
            jobs.save(job);
        }
    }

    private StoredDocument save(String name, String contentType, byte[] bytes) {
        return documents.create(CurrentUser.idOrNull(), name, contentType, bytes);
    }

    private StoredDocument requireDoc(UUID id) {
        if (id == null) throw ApiException.badRequest("documentId is required");
        return documents.get(id);
    }

    private List<StoredDocument> requireDocs(List<UUID> ids, int min) {
        if (ids == null || ids.size() < min)
            throw ApiException.badRequest("At least " + min + " file" + (min > 1 ? "s are" : " is") + " required");
        return ids.stream().map(documents::get).toList();
    }

    private byte[] bytes(StoredDocument doc) {
        return documents.bytes(doc.getId());
    }

    private static String base(StoredDocument doc) {
        String name = doc.getOriginalName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
