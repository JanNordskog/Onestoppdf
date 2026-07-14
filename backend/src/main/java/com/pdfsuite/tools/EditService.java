package com.pdfsuite.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdfsuite.common.ApiException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bakes editor overlay elements into pages, plus form-data extraction and metadata stripping. */
@Service
public class EditService {

    /** Overlay element; x,y,w,h are 0..1 fractions of the page with origin at the TOP-LEFT.
     *  For "replace-text", originalText names the word being replaced (so its glyphs can be
     *  removed from the text layer, not just covered) and bold/italic/family describe the
     *  original word's styling so the replacement can be rendered to look identical. */
    public record Element(int page, String type, float x, float y, float w, float h,
                          String text, Float fontSize, String color, String imageDataUrl,
                          String originalText, Boolean bold, Boolean italic, String family,
                          String originalFontName) {}

    private final FontService fontService;

    public EditService(FontService fontService) {
        this.fontService = fontService;
    }

    public byte[] applyElements(byte[] input, List<Element> elements) {
        if (elements == null || elements.isEmpty()) throw ApiException.badRequest("Nothing to apply");
        try (PDDocument doc = PdfToolService.load(input)) {
            // First pass: remove the replaced glyphs from the text layer. Removal is targeted
            // by position (the element's box), so duplicate words and partial lines are safe.
            boolean[] trulyRemoved = new boolean[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                Element el = elements.get(i);
                if (!"replace-text".equals(el.type())
                        || el.page() < 1 || el.page() > doc.getNumberOfPages()) continue;
                trulyRemoved[i] = TextRegionRemover.removeRegion(
                        doc, doc.getPage(el.page() - 1), el.x(), el.y(), el.w(), el.h());
            }

            // One embedded instance per bundled face is reused across all replacements in this doc.
            Map<String, PDFont> fontCache = new HashMap<>();

            for (int i = 0; i < elements.size(); i++) {
                Element el = elements.get(i);
                if (el.page() < 1 || el.page() > doc.getNumberOfPages())
                    throw ApiException.badRequest("Element targets page " + el.page() + " which doesn't exist");
                PDPage page = doc.getPage(el.page() - 1);
                PDRectangle box = page.getMediaBox();
                float x = el.x() * box.getWidth();
                float y = box.getHeight() - (el.y() + el.h()) * box.getHeight();
                float w = el.w() * box.getWidth();
                float h = el.h() * box.getHeight();
                try (PDPageContentStream cs = new PDPageContentStream(doc, page, AppendMode.APPEND, true, true)) {
                    switch (el.type() == null ? "" : el.type()) {
                        case "text" -> {
                            float size = el.fontSize() == null ? 14f : el.fontSize();
                            float[] rgb = hexToRgb(el.color(), 0f, 0f, 0f);
                            cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
                            cs.beginText();
                            cs.setFont(PdfToolService.HELVETICA, size);
                            cs.setLeading(size * 1.2f);
                            cs.newLineAtOffset(x, y + h - size);
                            String[] lines = (el.text() == null ? "" : el.text()).split("\r?\n");
                            for (int ln = 0; ln < lines.length; ln++) {
                                if (ln > 0) cs.newLine();
                                cs.showText(sanitize(lines[ln]));
                            }
                            cs.endText();
                        }
                        case "highlight" -> {
                            PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                            gs.setNonStrokingAlphaConstant(0.35f);
                            cs.setGraphicsStateParameters(gs);
                            float[] rgb = hexToRgb(el.color(), 1f, 0.92f, 0.23f);
                            cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
                            cs.addRect(x, y, w, h);
                            cs.fill();
                        }
                        case "rect" -> {
                            float[] rgb = hexToRgb(el.color(), 0.86f, 0.15f, 0.15f);
                            cs.setStrokingColor(rgb[0], rgb[1], rgb[2]);
                            cs.setLineWidth(1.5f);
                            cs.addRect(x, y, w, h);
                            cs.stroke();
                        }
                        case "image" -> {
                            PDImageXObject img = imageFromDataUrl(doc, el.imageDataUrl());
                            cs.drawImage(img, x, y, w, h);
                        }
                        // Replace an existing word: if the original glyphs couldn't be removed
                        // from the content stream, cover the box with white (padded for
                        // descenders/antialiasing); either way draw the new text on the old baseline.
                        case "replace-text" -> {
                            float size = el.fontSize() == null ? 12f : el.fontSize();
                            String rawText = el.text() == null ? "" : el.text();
                            // Match the original word's typeface: reuse the document's own embedded
                            // font when it can render the new characters (pixel-identical), else embed
                            // a bundled face that matches the original by name/family/weight/slant.
                            PDFont font = pickFont(doc, el, rawText, fontCache);
                            String newText = sanitizeFor(font, rawText);
                            // A wider replacement would overlap the next word — shrink to fit the
                            // original word's box (with a little tolerance).
                            float naturalWidth = safeWidth(font, newText) / 1000f * size;
                            if (naturalWidth > w * 1.05f && naturalWidth > 0) {
                                size = size * (w * 1.05f / naturalWidth);
                            }
                            if (!trulyRemoved[i]) {
                                float padY = h * 0.35f;
                                float padX = Math.min(w * 0.06f, 1.5f);
                                cs.setNonStrokingColor(1f, 1f, 1f);
                                cs.addRect(x - padX, y - padY, w + 2 * padX, h + 1.6f * padY);
                                cs.fill();
                            }
                            float[] rgb = hexToRgb(el.color(), 0f, 0f, 0f);
                            cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
                            cs.beginText();
                            cs.setFont(font, size);
                            cs.newLineAtOffset(x, y);
                            cs.showText(newText);
                            cs.endText();
                        }
                        default -> throw ApiException.badRequest("Unknown element type: " + el.type());
                    }
                }
            }
            return PdfToolService.toBytes(doc);
        } catch (IOException e) {
            throw new UncheckedIOException("Edit failed", e);
        }
    }

    public byte[] extractFormData(byte[] input, String format) {
        try (PDDocument doc = PdfToolService.load(input)) {
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
            if (form == null || form.getFields().isEmpty())
                throw ApiException.badRequest("This PDF has no fillable form fields");
            Map<String, String> values = new LinkedHashMap<>();
            collectFields(form.getFields(), values);
            if ("csv".equals(format)) {
                StringBuilder csv = new StringBuilder("field,value\r\n");
                values.forEach((k, v) -> csv.append(csvCell(k)).append(',').append(csvCell(v)).append("\r\n"));
                return csv.toString().getBytes(StandardCharsets.UTF_8);
            }
            return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(values);
        } catch (IOException e) {
            throw new UncheckedIOException("Form extraction failed", e);
        }
    }

    public byte[] stripMetadata(byte[] input) {
        try (PDDocument doc = PdfToolService.load(input)) {
            doc.setDocumentInformation(new PDDocumentInformation());
            doc.getDocumentCatalog().setMetadata(null);
            return PdfToolService.toBytes(doc);
        } catch (IOException e) {
            throw new UncheckedIOException("Metadata strip failed", e);
        }
    }

    private static void collectFields(List<PDField> fields, Map<String, String> out) {
        for (PDField field : fields) {
            if (field instanceof PDNonTerminalField parent) {
                collectFields(parent.getChildren(), out);
            } else {
                out.put(field.getFullyQualifiedName(), field.getValueAsString());
            }
        }
    }

    static PDImageXObject imageFromDataUrl(PDDocument doc, String dataUrl) throws IOException {
        if (dataUrl == null || !dataUrl.contains("base64,"))
            throw ApiException.badRequest("Image element needs a base64 data URL");
        byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf("base64,") + 7));
        return PDImageXObject.createFromByteArray(doc, bytes, "overlay");
    }

    /** Helvetica (WinAnsi) can't encode every character; swap unknowns for '?'. */
    private static String sanitize(String text) {
        return sanitizeFor(PdfToolService.HELVETICA, text);
    }

    /** Drops characters the chosen font can't encode (replacing them with '?'). */
    private static String sanitizeFor(PDFont font, String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            try {
                font.encode(String.valueOf(c));
                sb.append(c);
            } catch (Exception e) {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    /**
     * Chooses the font for a text replacement: the document's own embedded font when it can
     * render the whole new string (identical look), otherwise the closest Standard-14 face
     * matching the original word's family, weight and slant.
     */
    /**
     * Chooses the font that makes the replacement look identical to the original, in order:
     *  1. the document's own embedded font, when it is genuinely embedded and can render the new
     *     text — pixel-identical (typo fixes, fully-embedded fonts);
     *  2. a bundled face matching the original typeface by name/family/weight/slant, embedded as a
     *     subset (Carlito≈Calibri, Caladea≈Cambria, Liberation≈Arial/Times/Courier) — visually
     *     identical and still searchable;
     *  3. a Standard-14 face by family/weight/slant — the floor.
     */
    private PDFont pickFont(PDDocument doc, Element el, String newText, Map<String, PDFont> cache) {
        PDFont original = null;
        if (el.originalText() != null && !el.originalText().isBlank()) {
            original = FontResolver.resolve(doc, el.page(), el.originalText());
        }
        // Tier 1 — reuse only if the original is actually embedded (so the result is self-contained).
        if (original != null && isEmbedded(original) && canEncode(original, newText)) {
            return original;
        }
        boolean bold = Boolean.TRUE.equals(el.bold());
        boolean italic = Boolean.TRUE.equals(el.italic());
        // Tier 2 — embed the bundled face that matches the original typeface. Prefer the font name
        // the extractor read from the page (accurate even for non-embedded fonts like "Calibri"),
        // falling back to the resolved font's name.
        String originalName = el.originalFontName() != null ? el.originalFontName()
                : (original != null ? safeName(original) : null);
        PDFont matched = fontService.resolveEmbedded(doc, originalName, el.family(), bold, italic, cache);
        if (matched != null && canEncode(matched, newText)) {
            return matched;
        }
        // Tier 3 — Standard-14 fallback.
        return standard14(el.family(), bold, italic);
    }

    private static boolean isEmbedded(PDFont font) {
        try {
            return font.isEmbedded();
        } catch (Exception e) {
            return false;
        }
    }

    private static String safeName(PDFont font) {
        try {
            return font.getName();
        } catch (Exception e) {
            return null;
        }
    }

    private static PDFont standard14(String family, boolean bold, boolean italic) {
        String fam = family == null ? "sans" : family;
        FontName fn = switch (fam) {
            case "serif" -> bold && italic ? FontName.TIMES_BOLD_ITALIC : bold ? FontName.TIMES_BOLD
                    : italic ? FontName.TIMES_ITALIC : FontName.TIMES_ROMAN;
            case "mono" -> bold && italic ? FontName.COURIER_BOLD_OBLIQUE : bold ? FontName.COURIER_BOLD
                    : italic ? FontName.COURIER_OBLIQUE : FontName.COURIER;
            default -> bold && italic ? FontName.HELVETICA_BOLD_OBLIQUE : bold ? FontName.HELVETICA_BOLD
                    : italic ? FontName.HELVETICA_OBLIQUE : FontName.HELVETICA;
        };
        return new PDType1Font(fn);
    }

    private static boolean canEncode(PDFont font, String text) {
        if (text.isEmpty()) return false;
        try {
            font.encode(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static float safeWidth(PDFont font, String text) {
        try {
            return font.getStringWidth(text);
        } catch (Exception e) {
            try {
                return PdfToolService.HELVETICA.getStringWidth(text);
            } catch (Exception ignored) {
                return text.length() * 500f;
            }
        }
    }

    private static String csvCell(String value) {
        String v = value == null ? "" : value;
        return '"' + v.replace("\"", "\"\"") + '"';
    }

    private static float[] hexToRgb(String hex, float dr, float dg, float db) {
        if (hex == null || !hex.matches("#?[0-9a-fA-F]{6}")) return new float[]{dr, dg, db};
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        return new float[]{
                Integer.parseInt(h.substring(0, 2), 16) / 255f,
                Integer.parseInt(h.substring(2, 4), 16) / 255f,
                Integer.parseInt(h.substring(4, 6), 16) / 255f};
    }
}
