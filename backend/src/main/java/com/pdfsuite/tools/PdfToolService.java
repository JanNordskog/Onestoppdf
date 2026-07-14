package com.pdfsuite.tools;

import com.pdfsuite.common.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class PdfToolService {

    public static final PDFont HELVETICA = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    public byte[] merge(List<byte[]> inputs) {
        try (PDDocument dest = new PDDocument()) {
            PDFMergerUtility merger = new PDFMergerUtility();
            List<PDDocument> sources = new ArrayList<>();
            try {
                for (byte[] input : inputs) {
                    PDDocument src = load(input);
                    sources.add(src);
                    merger.appendDocument(dest, src);
                }
                return toBytes(dest);
            } finally {
                for (PDDocument src : sources) src.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Merge failed", e);
        }
    }

    /**
     * Merge an explicit page sequence drawn from several source PDFs — the caller decides
     * exactly which pages appear and in what order (interleaving across files is fine).
     * Each seq entry is {sourceIndex, 1-based page}.
     */
    public byte[] mergePages(List<byte[]> inputs, List<int[]> seq) {
        if (seq == null || seq.isEmpty()) throw ApiException.badRequest("Page sequence is empty");
        List<PDDocument> srcs = new ArrayList<>();
        try (PDDocument dst = new PDDocument()) {
            try {
                for (byte[] input : inputs) srcs.add(load(input));
                for (int[] ref : seq) {
                    if (ref[0] < 0 || ref[0] >= srcs.size())
                        throw ApiException.badRequest("Bad source document index");
                    PDDocument src = srcs.get(ref[0]);
                    if (ref[1] < 1 || ref[1] > src.getNumberOfPages())
                        throw ApiException.badRequest("Page " + ref[1] + " is out of range");
                    dst.importPage(src.getPage(ref[1] - 1));
                }
                return toBytes(dst);
            } finally {
                for (PDDocument src : srcs) src.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Merge failed", e);
        }
    }

    /** ranges e.g. "1-3,5"; extracts those pages into one PDF. */
    public byte[] extractPages(byte[] input, String ranges) {
        try (PDDocument src = load(input); PDDocument dst = new PDDocument()) {
            for (int pageNo : parseRanges(ranges, src.getNumberOfPages())) {
                dst.importPage(src.getPage(pageNo - 1));
            }
            return toBytes(dst);
        } catch (IOException e) {
            throw new UncheckedIOException("Split failed", e);
        }
    }

    /** One single-page PDF per page, zipped. */
    public byte[] splitToZip(byte[] input) {
        try (PDDocument src = load(input)) {
            ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(zipBytes)) {
                for (int i = 0; i < src.getNumberOfPages(); i++) {
                    try (PDDocument single = new PDDocument()) {
                        single.importPage(src.getPage(i));
                        zip.putNextEntry(new ZipEntry("page-" + (i + 1) + ".pdf"));
                        zip.write(toBytes(single));
                        zip.closeEntry();
                    }
                }
            }
            return zipBytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Split failed", e);
        }
    }

    /** Rebuild with the given 1-based page order; omitted pages are dropped. */
    public byte[] organize(byte[] input, List<Integer> pages) {
        try (PDDocument src = load(input); PDDocument dst = new PDDocument()) {
            if (pages == null || pages.isEmpty()) throw ApiException.badRequest("Page order is empty");
            for (int pageNo : pages) {
                if (pageNo < 1 || pageNo > src.getNumberOfPages())
                    throw ApiException.badRequest("Page " + pageNo + " is out of range");
                dst.importPage(src.getPage(pageNo - 1));
            }
            return toBytes(dst);
        } catch (IOException e) {
            throw new UncheckedIOException("Organize failed", e);
        }
    }

    public byte[] rotate(byte[] input, int degrees, List<Integer> pages) {
        if (degrees % 90 != 0) throw ApiException.badRequest("Rotation must be a multiple of 90");
        try (PDDocument doc = load(input)) {
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                if (pages == null || pages.isEmpty() || pages.contains(i + 1)) {
                    PDPage page = doc.getPage(i);
                    page.setRotation(((page.getRotation() + degrees) % 360 + 360) % 360);
                }
            }
            return toBytes(doc);
        } catch (IOException e) {
            throw new UncheckedIOException("Rotate failed", e);
        }
    }

    public byte[] compress(byte[] input, String level) {
        float quality = switch (level == null ? "medium" : level) {
            case "low" -> 0.75f; case "high" -> 0.3f; default -> 0.5f;
        };
        try (PDDocument doc = load(input)) {
            for (PDPage page : doc.getPages()) {
                var res = page.getResources();
                if (res == null) continue;
                for (var name : res.getXObjectNames()) {
                    try {
                        if (res.getXObject(name) instanceof PDImageXObject img) {
                            BufferedImage bi = img.getImage();
                            if (bi == null) continue;
                            BufferedImage rgb = toRgb(downscale(bi, 1600));
                            res.put(name, JPEGFactory.createFromImage(doc, rgb, quality));
                        }
                    } catch (Exception e) {
                        // leave this image as-is; compression is best-effort per image
                    }
                }
            }
            return toBytes(doc);
        } catch (IOException e) {
            throw new UncheckedIOException("Compress failed", e);
        }
    }

    public byte[] watermark(byte[] input, String text, float fontSize, float opacity) {
        try (PDDocument doc = load(input)) {
            for (PDPage page : doc.getPages()) {
                PDRectangle box = page.getMediaBox();
                try (PDPageContentStream cs = new PDPageContentStream(doc, page, AppendMode.APPEND, true, true)) {
                    PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                    gs.setNonStrokingAlphaConstant(opacity);
                    cs.setGraphicsStateParameters(gs);
                    cs.setNonStrokingColor(0.5f, 0.5f, 0.5f);
                    float textWidth = HELVETICA.getStringWidth(text) / 1000f * fontSize;
                    cs.beginText();
                    cs.setFont(HELVETICA, fontSize);
                    cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(45),
                            box.getWidth() / 2 - textWidth / 2.8f, box.getHeight() / 2 - textWidth / 2.8f));
                    cs.showText(text);
                    cs.endText();
                }
            }
            return toBytes(doc);
        } catch (IOException e) {
            throw new UncheckedIOException("Watermark failed", e);
        }
    }

    public byte[] pageNumbers(byte[] input, int startAt) {
        try (PDDocument doc = load(input)) {
            int total = doc.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                PDPage page = doc.getPage(i);
                String label = (startAt + i) + " / " + (startAt + total - 1);
                float textWidth = HELVETICA.getStringWidth(label) / 1000f * 10;
                try (PDPageContentStream cs = new PDPageContentStream(doc, page, AppendMode.APPEND, true, true)) {
                    cs.beginText();
                    cs.setFont(HELVETICA, 10);
                    cs.setNonStrokingColor(0.3f, 0.3f, 0.3f);
                    cs.newLineAtOffset(page.getMediaBox().getWidth() / 2 - textWidth / 2, 20);
                    cs.showText(label);
                    cs.endText();
                }
            }
            return toBytes(doc);
        } catch (IOException e) {
            throw new UncheckedIOException("Page numbering failed", e);
        }
    }

    public byte[] protect(byte[] input, String password) {
        try (PDDocument doc = load(input)) {
            StandardProtectionPolicy policy = new StandardProtectionPolicy(password, password, new AccessPermission());
            policy.setEncryptionKeyLength(256);
            policy.setPreferAES(true);
            doc.protect(policy);
            return toBytes(doc);
        } catch (IOException e) {
            throw new UncheckedIOException("Protect failed", e);
        }
    }

    public byte[] unlock(byte[] input, String password) {
        try (PDDocument doc = Loader.loadPDF(input, password == null ? "" : password)) {
            doc.setAllSecurityToBeRemoved(true);
            return toBytes(doc);
        } catch (InvalidPasswordException e) {
            throw ApiException.badRequest("Wrong password for this PDF");
        } catch (IOException e) {
            throw new UncheckedIOException("Unlock failed", e);
        }
    }

    public byte[] pdfToImagesZip(byte[] input, float dpi) {
        try (PDDocument doc = load(input)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(zipBytes)) {
                for (int i = 0; i < doc.getNumberOfPages(); i++) {
                    BufferedImage image = renderer.renderImageWithDPI(i, Math.min(dpi, 300));
                    ByteArrayOutputStream png = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", png);
                    zip.putNextEntry(new ZipEntry("page-" + (i + 1) + ".png"));
                    zip.write(png.toByteArray());
                    zip.closeEntry();
                }
            }
            return zipBytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("PDF to images failed", e);
        }
    }

    public byte[] imagesToPdf(List<byte[]> images) {
        try (PDDocument doc = new PDDocument()) {
            for (byte[] data : images) {
                PDImageXObject img;
                try {
                    img = PDImageXObject.createFromByteArray(doc, data, "img");
                } catch (IOException e) {
                    throw ApiException.badRequest("One of the files is not a supported image (use JPG or PNG)");
                }
                PDPage page = new PDPage(new PDRectangle(img.getWidth(), img.getHeight()));
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.drawImage(img, 0, 0, img.getWidth(), img.getHeight());
                }
            }
            return toBytes(doc);
        } catch (IOException e) {
            throw new UncheckedIOException("Images to PDF failed", e);
        }
    }

    public static PDDocument load(byte[] input) throws IOException {
        try {
            return Loader.loadPDF(input);
        } catch (InvalidPasswordException e) {
            throw ApiException.badRequest("This PDF is password-protected — unlock it first");
        }
    }

    public static byte[] toBytes(PDDocument doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return out.toByteArray();
    }

    static List<Integer> parseRanges(String ranges, int pageCount) {
        if (ranges == null || ranges.isBlank()) throw ApiException.badRequest("Page ranges are required (e.g. 1-3,5)");
        List<Integer> result = new ArrayList<>();
        for (String part : ranges.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            try {
                int from, to;
                if (p.contains("-")) {
                    String[] ends = p.split("-", 2);
                    from = Integer.parseInt(ends[0].trim());
                    to = Integer.parseInt(ends[1].trim());
                } else {
                    from = to = Integer.parseInt(p);
                }
                if (from < 1 || to > pageCount || from > to)
                    throw ApiException.badRequest("Range \"" + p + "\" is outside 1-" + pageCount);
                for (int i = from; i <= to; i++) result.add(i);
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("Can't read page range \"" + p + "\" — use e.g. 1-3,5");
            }
        }
        if (result.isEmpty()) throw ApiException.badRequest("Page ranges are required (e.g. 1-3,5)");
        return result;
    }

    private static BufferedImage downscale(BufferedImage src, int maxSide) {
        int max = Math.max(src.getWidth(), src.getHeight());
        if (max <= maxSide) return src;
        float scale = maxSide / (float) max;
        int w = Math.max(1, Math.round(src.getWidth() * scale));
        int h = Math.max(1, Math.round(src.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, src.getWidth(), src.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }
}
