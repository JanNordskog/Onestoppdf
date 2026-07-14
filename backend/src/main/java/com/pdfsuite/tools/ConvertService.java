package com.pdfsuite.tools;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * PDF -> Word conversion.
 *
 * <p>"layout" mode calls the local pdf2docx sidecar for high-fidelity, editable output
 * (real paragraphs, tables and images). "basic" mode uses the built-in Apache POI text
 * extraction. The sidecar runs on localhost only, so files never leave the server; if it is
 * unreachable, layout mode automatically falls back to the POI path.
 */
@Service
public class ConvertService {

    private static final Logger log = LoggerFactory.getLogger(ConvertService.class);
    private static final String DOCX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final String converterUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)  // uvicorn speaks HTTP/1.1; h2c upgrade drops the body
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public ConvertService(@Value("${app.converter.url:}") String converterUrl) {
        this.converterUrl = converterUrl == null ? "" : converterUrl.replaceAll("/+$", "");
    }

    /** True when the layout sidecar produced the docx; false when we fell back to POI. */
    public record WordResult(byte[] bytes, boolean layoutAccurate) {}

    /**
     * High-fidelity conversion via the pdf2docx sidecar, falling back to POI extraction when the
     * sidecar is not configured, unreachable, or errors on this document.
     */
    public WordResult pdfToWordLayout(byte[] input) {
        if (!converterUrl.isBlank()) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(converterUrl + "/convert"))
                        .timeout(Duration.ofSeconds(120))
                        .header("Content-Type", "application/pdf")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(input))
                        .build();
                HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200 && response.body().length > 0) {
                    return new WordResult(response.body(), true);
                }
                log.warn("Converter returned HTTP {} — falling back to POI extraction", response.statusCode());
            } catch (Exception e) {
                log.warn("Converter unreachable ({}) — falling back to POI extraction", e.getMessage());
            }
        }
        return new WordResult(pdfToWord(input), false);
    }

    public byte[] pdfToWord(byte[] input) {
        try (PDDocument pdf = PdfToolService.load(input); XWPFDocument docx = new XWPFDocument()) {
            PDFTextStripper stripper = new PDFTextStripper();
            int pages = pdf.getNumberOfPages();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(pdf);
                for (String line : text.split("\r?\n")) {
                    XWPFParagraph p = docx.createParagraph();
                    p.createRun().setText(line);
                }
                if (page < pages) {
                    XWPFParagraph breaker = docx.createParagraph();
                    XWPFRun run = breaker.createRun();
                    run.addBreak(BreakType.PAGE);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            docx.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("PDF to Word failed", e);
        }
    }

    public byte[] pdfToText(byte[] input) {
        try (PDDocument pdf = PdfToolService.load(input)) {
            return new PDFTextStripper().getText(pdf).getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("PDF to text failed", e);
        }
    }
}
