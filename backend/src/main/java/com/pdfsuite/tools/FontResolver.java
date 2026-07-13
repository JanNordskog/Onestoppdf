package com.pdfsuite.tools;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.util.ArrayList;
import java.util.List;

/**
 * Locates the live {@link PDFont} object a given word is drawn with on a page, so a
 * text replacement can be rendered in the document's own embedded font (identical look)
 * when that font can encode the new characters. Returns null if the word can't be found.
 */
final class FontResolver {

    private FontResolver() {}

    static PDFont resolve(PDDocument doc, int pageNo, String word) {
        if (word == null || word.isBlank()) return null;
        try {
            final PDFont[] found = {null};
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    if (found[0] != null) return;
                    List<TextPosition> current = new ArrayList<>();
                    for (TextPosition p : positions) {
                        if (p.getUnicode().isBlank()) {
                            if (matches(current)) return;
                            current.clear();
                        } else {
                            current.add(p);
                        }
                    }
                    matches(current);
                }

                private boolean matches(List<TextPosition> chars) {
                    if (chars.isEmpty()) return false;
                    StringBuilder sb = new StringBuilder();
                    for (TextPosition p : chars) sb.append(p.getUnicode());
                    if (sb.toString().equals(word)) {
                        found[0] = chars.get(0).getFont();
                        return true;
                    }
                    return false;
                }
            };
            stripper.setSortByPosition(true);
            stripper.setStartPage(pageNo);
            stripper.setEndPage(pageNo);
            stripper.getText(doc);
            return found[0];
        } catch (Exception e) {
            return null;
        }
    }
}
