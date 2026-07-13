package com.pdfsuite.tools;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Removes every glyph whose position falls inside a target rectangle from a page's
 * content stream — the engine behind "edit text in place". Position-targeted, so it
 * works with duplicate words, partial lines and text split across many show operators.
 *
 * Two passes:
 *  1. A text-extraction pass maps each glyph to (show-operator index, glyph index)
 *     with its normalized page coordinates (same space as {@link com.pdfsuite.files.WordExtractor}).
 *  2. A token-rewrite pass splits the targeted show strings, replacing removed glyph
 *     runs with equivalent TJ kerning skips so surrounding text keeps its exact position.
 *
 * Text drawn inside Form XObjects is left alone (pass 1 does not descend into them so
 * the operator indices always line up with the page stream walked in pass 2). Returns
 * true only when every glyph in the region was removed — callers fall back to covering
 * the region visually otherwise.
 */
final class TextRegionRemover {

    private TextRegionRemover() {}

    /** x,y,w,h are 0..1 page fractions, origin top-left, bottom edge = text baseline. */
    static boolean removeRegion(PDDocument doc, PDPage page, float x, float y, float w, float h) {
        try {
            Map<Integer, Set<Integer>> plan = collectGlyphs(doc, page, x, y, w, h);
            if (plan.isEmpty()) return false;
            return rewrite(doc, page, plan);
        } catch (Exception e) {
            return false;
        }
    }

    /** Pass 1: which (opIndex, glyphIndex) pairs fall inside the region? */
    private static Map<Integer, Set<Integer>> collectGlyphs(PDDocument doc, PDPage page,
                                                            float x, float y, float w, float h) throws IOException {
        int pageNo = 1 + doc.getPages().indexOf(page);
        Map<Integer, Set<Integer>> plan = new HashMap<>();
        float bottom = y + h;              // baseline of the clicked box
        float bandTop = bottom - h * 0.45f;
        float bandBottom = bottom + h * 0.45f;
        float left = x - 0.004f, right = x + w + 0.004f;

        PDFTextStripper collector = new PDFTextStripper() {
            private int opIndex = -1;
            private int glyphInOp = 0;

            @Override
            protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
                String name = operator.getName();
                // Only Tj/TJ: the ' and " processors re-enter processOperator with a
                // synthetic Tj, so counting them here too would double-count. Pass 2 walks
                // raw tokens (where ' and " appear as themselves), keeping indices aligned.
                if ("Tj".equals(name) || "TJ".equals(name)) {
                    opIndex++;
                    glyphInOp = 0;
                }
                super.processOperator(operator, operands);
            }

            @Override
            public void showForm(PDFormXObject form) {
                // Skip: pass 2 only rewrites the page stream, so indices must match it.
            }

            @Override
            protected void processTextPosition(TextPosition p) {
                int glyph = glyphInOp++;
                float gx = (p.getXDirAdj() + p.getWidthDirAdj() / 2) / p.getPageWidth();
                float gBaseline = p.getYDirAdj() / p.getPageHeight();
                if (gx >= left && gx <= right && gBaseline >= bandTop && gBaseline <= bandBottom) {
                    plan.computeIfAbsent(opIndex, k -> new HashSet<>()).add(glyph);
                }
            }
        };
        collector.setStartPage(pageNo);
        collector.setEndPage(pageNo);
        collector.getText(doc);
        return plan;
    }

    /** Pass 2: rewrite the content stream, dropping planned glyphs. */
    private static boolean rewrite(PDDocument doc, PDPage page, Map<Integer, Set<Integer>> plan) throws IOException {
        PDResources resources = page.getResources();
        if (resources == null) return false;
        List<Object> tokens = new PDFStreamParser(page).parse();
        List<Object> out = new ArrayList<>(tokens.size());

        PDFont font = null;
        float fontSize = 0, charSpacing = 0, wordSpacing = 0;
        int opIndex = -1;
        boolean changed = false, incomplete = false;

        for (Object token : tokens) {
            if (!(token instanceof Operator op)) {
                out.add(token);
                continue;
            }
            switch (op.getName()) {
                case "Tf" -> {
                    if (out.size() >= 2 && out.get(out.size() - 2) instanceof COSName fn
                            && out.get(out.size() - 1) instanceof COSNumber size) {
                        try {
                            font = resources.getFont(fn);
                        } catch (IOException e) {
                            font = null;
                        }
                        fontSize = size.floatValue();
                    }
                    out.add(token);
                }
                case "Tc" -> {
                    if (!out.isEmpty() && out.get(out.size() - 1) instanceof COSNumber n) charSpacing = n.floatValue();
                    out.add(token);
                }
                case "Tw" -> {
                    if (!out.isEmpty() && out.get(out.size() - 1) instanceof COSNumber n) wordSpacing = n.floatValue();
                    out.add(token);
                }
                case "Tj", "TJ", "'", "\"" -> {
                    opIndex++;
                    Set<Integer> kill = plan.get(opIndex);
                    if (kill == null || kill.isEmpty()) {
                        out.add(token);
                        continue;
                    }
                    if (font == null || "\"".equals(op.getName())) {
                        incomplete = true;
                        out.add(token);
                        continue;
                    }
                    try {
                        if ("Tj".equals(op.getName()) || "'".equals(op.getName())) {
                            if (out.isEmpty() || !(out.get(out.size() - 1) instanceof COSString str))
                                throw new IOException("unexpected operand");
                            COSArray parts = splitString(font, fontSize, charSpacing, wordSpacing, str, kill, 0);
                            out.set(out.size() - 1, parts);
                            if ("'".equals(op.getName())) {
                                out.add(out.size() - 1, Operator.getOperator("T*"));
                            }
                            out.add(Operator.getOperator("TJ"));
                        } else { // TJ
                            if (out.isEmpty() || !(out.get(out.size() - 1) instanceof COSArray arr))
                                throw new IOException("unexpected operand");
                            COSArray rebuilt = new COSArray();
                            int glyphBase = 0;
                            for (COSBase el : arr) {
                                if (el instanceof COSString s) {
                                    COSArray parts = splitString(font, fontSize, charSpacing, wordSpacing, s, kill, glyphBase);
                                    glyphBase += countGlyphs(font, s);
                                    rebuilt.addAll(parts);
                                } else {
                                    rebuilt.add(el);
                                }
                            }
                            out.set(out.size() - 1, rebuilt);
                            out.add(token);
                        }
                        changed = true;
                    } catch (Exception e) {
                        incomplete = true;
                        out.add(token);
                    }
                }
                default -> out.add(token);
            }
        }

        if (!changed) return false;
        PDStream newContents = new PDStream(doc);
        try (OutputStream os = newContents.createOutputStream(COSName.FLATE_DECODE)) {
            new ContentStreamWriter(os).writeTokens(out);
        }
        page.setContents(newContents);
        return !incomplete;
    }

    private static int countGlyphs(PDFont font, COSString str) throws IOException {
        int n = 0;
        InputStream in = new ByteArrayInputStream(str.getBytes());
        while (in.available() > 0) {
            font.readCode(in);
            n++;
        }
        return n;
    }

    /**
     * Splits one shown string into [kept-bytes, -skip, kept-bytes …], dropping glyphs whose
     * global index (glyphBase + position) is in {@code kill}. The skip equals the removed
     * glyphs' advance (glyph width plus char/word spacing) in TJ thousandths, so the
     * remaining text doesn't shift.
     */
    private static COSArray splitString(PDFont font, float fontSize, float charSpacing, float wordSpacing,
                                        COSString str, Set<Integer> kill, int glyphBase) throws IOException {
        byte[] bytes = str.getBytes();
        record Glyph(int start, int end, int code) {}
        List<Glyph> glyphs = new ArrayList<>();
        InputStream in = new ByteArrayInputStream(bytes);
        while (in.available() > 0) {
            int before = bytes.length - in.available();
            int code = font.readCode(in);
            int after = bytes.length - in.available();
            glyphs.add(new Glyph(before, after, code));
        }

        COSArray outArr = new COSArray();
        int keepStart = 0; // byte offset where the current kept run began
        float pendingSkip = 0;
        int cursor = 0;    // byte offset reached so far
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph g = glyphs.get(i);
            if (kill.contains(glyphBase + i)) {
                if (g.start() > keepStart) {
                    outArr.add(new COSString(java.util.Arrays.copyOfRange(bytes, keepStart, g.start())));
                }
                float advance = font.getWidth(g.code());
                if (fontSize > 0) {
                    boolean spaceByte = (g.end() - g.start() == 1) && g.code() == 32;
                    advance += (charSpacing + (spaceByte ? wordSpacing : 0)) * 1000f / fontSize;
                }
                pendingSkip += advance;
                keepStart = g.end();
            } else if (pendingSkip > 0) {
                outArr.add(new COSFloat(-pendingSkip));
                pendingSkip = 0;
            }
            cursor = g.end();
        }
        if (cursor > keepStart) {
            outArr.add(new COSString(java.util.Arrays.copyOfRange(bytes, keepStart, cursor)));
        }
        if (pendingSkip > 0) {
            outArr.add(new COSFloat(-pendingSkip));
        }
        return outArr;
    }
}
