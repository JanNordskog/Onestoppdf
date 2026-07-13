package com.pdfsuite.tools;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDSimpleFont;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Removes a word's glyphs from a page content stream so edited text disappears from the
 * text layer too (search/copy/extract), not just visually. Only handles the safe case:
 * the word is a contiguous, word-boundary-delimited substring of a single shown string
 * in a simple (single-byte) font. The glyphs are replaced by an equivalent TJ kerning
 * skip so the rest of the line keeps its exact position. Returns false when it can't
 * remove safely — the caller then falls back to covering the word visually.
 */
final class TextRemover {

    private TextRemover() {}

    static boolean removeWord(PDDocument doc, PDPage page, String word) {
        try {
            PDResources resources = page.getResources();
            if (resources == null || word == null || word.isBlank()) return false;
            PDFStreamParser parser = new PDFStreamParser(page);
            List<Object> tokens = parser.parse();
            List<Object> out = new ArrayList<>(tokens.size());
            PDFont font = null;
            boolean removed = false;

            for (Object token : tokens) {
                if (removed || !(token instanceof Operator op)) {
                    out.add(token);
                    continue;
                }
                switch (op.getName()) {
                    case "Tf" -> {
                        if (out.size() >= 2 && out.get(out.size() - 2) instanceof COSName fontName) {
                            try {
                                font = resources.getFont(fontName);
                            } catch (IOException e) {
                                font = null;
                            }
                        }
                        out.add(token);
                    }
                    case "Tj" -> {
                        if (font instanceof PDSimpleFont sf && !out.isEmpty()
                                && out.get(out.size() - 1) instanceof COSString str) {
                            COSArray replacement = tryReplace(sf, str, word);
                            if (replacement != null) {
                                out.set(out.size() - 1, replacement);
                                out.add(Operator.getOperator("TJ"));
                                removed = true;
                                continue;
                            }
                        }
                        out.add(token);
                    }
                    case "TJ" -> {
                        if (font instanceof PDSimpleFont sf && !out.isEmpty()
                                && out.get(out.size() - 1) instanceof COSArray arr) {
                            COSArray rebuilt = new COSArray();
                            boolean hit = false;
                            for (COSBase el : arr) {
                                if (!hit && el instanceof COSString str) {
                                    COSArray replacement = tryReplace(sf, str, word);
                                    if (replacement != null) {
                                        rebuilt.addAll(replacement);
                                        hit = true;
                                        continue;
                                    }
                                }
                                rebuilt.add(el);
                            }
                            if (hit) {
                                out.set(out.size() - 1, rebuilt);
                                removed = true;
                            }
                        }
                        out.add(token);
                    }
                    default -> out.add(token);
                }
            }

            if (!removed) return false;
            PDStream newContents = new PDStream(doc);
            try (OutputStream os = newContents.createOutputStream(COSName.FLATE_DECODE)) {
                new ContentStreamWriter(os).writeTokens(out);
            }
            page.setContents(newContents);
            return true;
        } catch (Exception e) {
            return false; // any surprise -> caller covers the word visually instead
        }
    }

    /**
     * If {@code str} contains {@code word} on word boundaries, returns a TJ operand array
     * [before, -width, after] that shows the same text minus the word, with a kerning skip
     * exactly as wide as the removed glyphs. Null when no safe match.
     */
    private static COSArray tryReplace(PDSimpleFont font, COSString str, String word) {
        try {
            byte[] bytes = str.getBytes();
            String[] perByte = new String[bytes.length];
            StringBuilder decoded = new StringBuilder();
            for (int i = 0; i < bytes.length; i++) {
                String u = font.toUnicode(bytes[i] & 0xff);
                if (u == null) return null;
                perByte[i] = u;
                decoded.append(u);
            }
            String text = decoded.toString();
            int at = text.indexOf(word);
            while (at >= 0) {
                boolean startOk = at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1));
                int end = at + word.length();
                boolean endOk = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
                if (startOk && endOk) break;
                at = text.indexOf(word, at + 1);
            }
            if (at < 0) return null;

            // Map decoded char positions back to byte boundaries (toUnicode may be multi-char).
            int byteStart = -1, byteEnd = -1, charPos = 0;
            for (int i = 0; i < bytes.length; i++) {
                if (charPos == at) byteStart = i;
                charPos += perByte[i].length();
                if (charPos == at + word.length()) { byteEnd = i + 1; break; }
                if (charPos > at + word.length()) return null; // match not glyph-aligned
            }
            if (byteStart < 0 || byteEnd < 0) return null;

            float skip = 0;
            for (int i = byteStart; i < byteEnd; i++) {
                skip += font.getWidth(bytes[i] & 0xff);
            }

            COSArray result = new COSArray();
            if (byteStart > 0) {
                result.add(new COSString(java.util.Arrays.copyOfRange(bytes, 0, byteStart)));
            }
            result.add(new COSFloat(-skip));
            if (byteEnd < bytes.length) {
                result.add(new COSString(java.util.Arrays.copyOfRange(bytes, byteEnd, bytes.length)));
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }
}
