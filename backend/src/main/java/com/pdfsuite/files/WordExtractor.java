package com.pdfsuite.files;

import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColorN;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColorSpace;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceCMYKColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceGrayColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceRGBColor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Extracts each word on a page with its normalized bounding box (0..1 fractions,
 * origin top-left, bottom edge = text baseline) plus font styling — so the editor
 * can offer double-click-to-edit and re-render the replacement to look identical.
 */
@Service
public class WordExtractor {

    public record Word(String text, float x, float y, float w, float h, float fontSize,
                       String fontName, boolean bold, boolean italic, String family, String color) {}

    public List<Word> words(byte[] pdfBytes, int pageNo) {
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdfBytes)) {
            if (pageNo < 1 || pageNo > doc.getNumberOfPages()) {
                throw com.pdfsuite.common.ApiException.badRequest("Page " + pageNo + " is out of range");
            }
            List<Word> out = new ArrayList<>();
            // Colour is captured per glyph during streaming (processTextPosition), because with
            // sort-by-position enabled writeString runs only after the whole page is parsed — by
            // then the graphics state no longer reflects each word's colour.
            Map<TextPosition, String> colorByGlyph = new IdentityHashMap<>();
            PDFTextStripper stripper = new PDFTextStripper() {
                // The text engine ignores colour operators by default; register them so the
                // non-stroking colour in the graphics state reflects the actual text colour.
                {
                    addOperator(new SetNonStrokingColorSpace(this));
                    addOperator(new SetNonStrokingColor(this));
                    addOperator(new SetNonStrokingColorN(this));
                    addOperator(new SetNonStrokingDeviceRGBColor(this));
                    addOperator(new SetNonStrokingDeviceGrayColor(this));
                    addOperator(new SetNonStrokingDeviceCMYKColor(this));
                }

                @Override
                protected void processTextPosition(TextPosition text) {
                    try {
                        PDColor c = getGraphicsState().getNonStrokingColor();
                        colorByGlyph.put(text, toHex(c.getColorSpace().toRGB(c.getComponents())));
                    } catch (Exception ignored) {
                        // leave unmapped -> flush() defaults to black
                    }
                    super.processTextPosition(text);
                }

                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    List<TextPosition> current = new ArrayList<>();
                    for (TextPosition p : positions) {
                        if (p.getUnicode().isBlank()) {
                            flush(current, colorByGlyph, out);
                        } else {
                            current.add(p);
                        }
                    }
                    flush(current, colorByGlyph, out);
                }
            };
            stripper.setSortByPosition(true);
            stripper.setStartPage(pageNo);
            stripper.setEndPage(pageNo);
            stripper.getText(doc);
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("Word extraction failed", e);
        }
    }

    private static void flush(List<TextPosition> chars, Map<TextPosition, String> colorByGlyph, List<Word> out) {
        if (chars.isEmpty()) return;
        String color = colorByGlyph.getOrDefault(chars.get(0), "#000000");
        float pageW = chars.get(0).getPageWidth();
        float pageH = chars.get(0).getPageHeight();
        float minX = Float.MAX_VALUE, maxX = 0, baseline = 0, height = 0, fontSize = 0;
        StringBuilder text = new StringBuilder();
        for (TextPosition p : chars) {
            text.append(p.getUnicode());
            minX = Math.min(minX, p.getXDirAdj());
            maxX = Math.max(maxX, p.getXDirAdj() + p.getWidthDirAdj());
            baseline = Math.max(baseline, p.getYDirAdj());
            height = Math.max(height, p.getHeightDir());
            fontSize = Math.max(fontSize, p.getFontSizeInPt());
        }
        if (height <= 0) height = fontSize > 0 ? fontSize : 10;

        PDFont font = chars.get(0).getFont();
        Style style = styleOf(font);

        // Box: top = baseline - glyph height, bottom = baseline (editor draws new text on the baseline).
        out.add(new Word(text.toString(),
                minX / pageW,
                (baseline - height) / pageH,
                (maxX - minX) / pageW,
                height / pageH,
                fontSize > 0 ? fontSize : height,
                style.name, style.bold, style.italic, style.family, color));
        chars.clear();
    }

    private record Style(String name, boolean bold, boolean italic, String family) {}

    /** Reads weight/slant/family from the font's PostScript name and descriptor. */
    static Style styleOf(PDFont font) {
        String name = font == null ? "" : (font.getName() == null ? "" : font.getName());
        // Embedded subset fonts carry a "ABCDEF+" tag prefix; drop it for readability.
        String clean = name.contains("+") ? name.substring(name.indexOf('+') + 1) : name;
        String lower = clean.toLowerCase(Locale.ROOT);

        boolean bold = lower.contains("bold") || lower.contains("black") || lower.contains("heavy")
                || lower.contains("semibold");
        boolean italic = lower.contains("italic") || lower.contains("oblique");

        PDFontDescriptor fd = font == null ? null : font.getFontDescriptor();
        boolean serif = lower.contains("times") || lower.contains("serif") || lower.contains("georgia")
                || lower.contains("garamond") || lower.contains("roman") || lower.contains("minion")
                || lower.contains("cambria");
        boolean mono = lower.contains("courier") || lower.contains("mono") || lower.contains("consol");
        if (fd != null) {
            if (fd.getFontWeight() >= 600) bold = true;
            if (fd.isForceBold()) bold = true;
            if (fd.isItalic()) italic = true;
            int flags = fd.getFlags();
            if ((flags & 1) != 0) mono = true;   // FixedPitch
            if ((flags & 2) != 0) serif = true;  // Serif
            if ((flags & 64) != 0) italic = true; // Italic
        }
        String family = mono ? "mono" : serif ? "serif" : "sans";
        return new Style(clean, bold, italic, family);
    }

    private static String toHex(float[] rgb) {
        int r = clamp(rgb.length > 0 ? rgb[0] : 0);
        int g = clamp(rgb.length > 1 ? rgb[1] : 0);
        int b = clamp(rgb.length > 2 ? rgb[2] : 0);
        return String.format("#%02x%02x%02x", r, g, b);
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255)));
    }
}
