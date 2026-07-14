package com.pdfsuite.tools;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a bundled, embeddable font that matches an original PDF font by name / family / weight
 * / slant, so edited text renders in a faithful, self-contained typeface. The match is embedded
 * as a subset {@link PDType0Font} (with a ToUnicode map, so the edited text stays searchable).
 *
 * Bundled open faces — metric-compatible clones of the common originals (all OFL/GPL):
 * <pre>
 *   Carlito         ≈ Calibri            Caladea          ≈ Cambria
 *   Liberation Sans ≈ Arial/Helvetica    Liberation Serif ≈ Times New Roman
 *   Liberation Mono ≈ Courier New
 * </pre>
 */
@Service
public class FontService {

    private static final Logger log = LoggerFactory.getLogger(FontService.class);

    /** face -> {Regular, Bold, Italic, BoldItalic} resource base names under /fonts. */
    private static final Map<String, String[]> FACES = Map.of(
        "carlito", new String[]{"Carlito-Regular", "Carlito-Bold", "Carlito-Italic", "Carlito-BoldItalic"},
        "caladea", new String[]{"Caladea-Regular", "Caladea-Bold", "Caladea-Italic", "Caladea-BoldItalic"},
        "liberation-sans", new String[]{"LiberationSans-Regular", "LiberationSans-Bold", "LiberationSans-Italic", "LiberationSans-BoldItalic"},
        "liberation-serif", new String[]{"LiberationSerif-Regular", "LiberationSerif-Bold", "LiberationSerif-Italic", "LiberationSerif-BoldItalic"},
        "liberation-mono", new String[]{"LiberationMono-Regular", "LiberationMono-Bold", "LiberationMono-Italic", "LiberationMono-BoldItalic"}
    );

    private final Map<String, byte[]> bytesCache = new ConcurrentHashMap<>();

    /** Chooses a bundled face for the given original font name + family, or null if none fits. */
    static String faceFor(String originalName, String family) {
        String n = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        if (n.contains("calibri")) return "carlito";
        if (n.contains("cambria")) return "caladea";
        if (n.contains("courier") || n.contains("cousine") || n.contains("consol") || n.contains("mono"))
            return "liberation-mono";
        if (n.contains("times") || n.contains("tinos") || n.contains("georgia") || n.contains("garamond")
                || n.contains("minion") || n.contains("roman") || n.contains("serif"))
            return "liberation-serif";
        if (n.contains("arial") || n.contains("helvetica") || n.contains("arimo") || n.contains("segoe")
                || n.contains("verdana") || n.contains("tahoma") || n.contains("sans"))
            return "liberation-sans";
        // No strong name signal -> use the family bucket detected from the original.
        if ("serif".equals(family)) return "liberation-serif";
        if ("mono".equals(family)) return "liberation-mono";
        if ("sans".equals(family)) return "liberation-sans";
        return null;
    }

    /**
     * Returns an embedded PDFont for the bundled face matching (originalName, family, bold, italic),
     * or null when no bundled face fits or embedding fails. Reuses one embedded instance per face
     * within the supplied per-document cache (a PDFont is bound to its PDDocument).
     */
    public PDFont resolveEmbedded(PDDocument doc, String originalName, String family,
                                  boolean bold, boolean italic, Map<String, PDFont> cache) {
        String face = faceFor(originalName, family);
        if (face == null) return null;
        String resource = FACES.get(face)[(bold ? 1 : 0) + (italic ? 2 : 0)];
        PDFont cached = cache.get(resource);
        if (cached != null) return cached;
        byte[] bytes = bytesCache.computeIfAbsent(resource, this::loadResource);
        if (bytes == null) return null;
        try {
            PDFont font = PDType0Font.load(doc, new ByteArrayInputStream(bytes), true);
            cache.put(resource, font);
            return font;
        } catch (IOException e) {
            log.warn("Failed to embed bundled font {}: {}", resource, e.getMessage());
            return null;
        }
    }

    private byte[] loadResource(String base) {
        try (InputStream in = getClass().getResourceAsStream("/fonts/" + base + ".ttf")) {
            if (in == null) {
                log.warn("Bundled font missing on classpath: {}", base);
                return null;
            }
            return in.readAllBytes();
        } catch (IOException e) {
            log.warn("Could not read bundled font {}: {}", base, e.getMessage());
            return null;
        }
    }
}
