package com.pdfsuite.sign;

import com.pdfsuite.tools.PdfToolService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Appends the tamper-evidence certificate page to a completed signature request. */
final class CertificatePage {

    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private CertificatePage() {}

    static byte[] append(byte[] pdf, SignatureRequest request, List<Signer> signers, String originalSha256) {
        try (PDDocument doc = PdfToolService.load(pdf)) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 780;
                y = line(cs, BOLD, 20, 50, y, "Signature Certificate");
                y -= 8;
                y = line(cs, PdfToolService.HELVETICA, 11, 50, y, "Document: " + request.getTitle());
                y = line(cs, PdfToolService.HELVETICA, 11, 50, y, "Request ID: " + request.getId());
                y = line(cs, PdfToolService.HELVETICA, 11, 50, y, "Completed: " + TS.format(java.time.Instant.now()));
                y = line(cs, PdfToolService.HELVETICA, 11, 50, y, "Original document SHA-256:");
                y = line(cs, PdfToolService.HELVETICA, 9, 50, y, originalSha256);
                y -= 14;
                y = line(cs, BOLD, 14, 50, y, "Signers");
                for (Signer s : signers) {
                    y -= 4;
                    y = line(cs, BOLD, 11, 50, y, s.getName() + " <" + s.getEmail() + ">");
                    y = line(cs, PdfToolService.HELVETICA, 10, 62, y,
                            "Signed " + (s.getSignedAt() == null ? "-" : TS.format(s.getSignedAt()))
                                    + (s.getSignerIp() == null ? "" : "  ·  IP " + s.getSignerIp()));
                }
                y -= 20;
                line(cs, PdfToolService.HELVETICA, 8, 50, y,
                        "This page was generated automatically when the final signer completed the request. "
                                + "The SHA-256 fingerprint above identifies the original, unsigned document.");
            }
            return PdfToolService.toBytes(doc);
        } catch (IOException e) {
            throw new UncheckedIOException("Certificate page failed", e);
        }
    }

    private static float line(PDPageContentStream cs, PDFont font, float size,
                              float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text == null ? "" : text.replaceAll("[\\r\\n]", " "));
        cs.endText();
        return y - size * 1.5f;
    }
}
