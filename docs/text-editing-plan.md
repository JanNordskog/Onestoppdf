# Editing text in a PDF: research + implementation plan

*Researched July 2026. Goal: move PDFHarbor's editor from "replace one word" to "click a
paragraph and type," while keeping it looking like the original — the way Acrobat / Sejda /
PDF-XChange work.*

## How PDF text editing actually works (the reality)

A PDF has **no paragraphs, lines, or even words.** A page is a content stream of drawing
operators: `BT … ET` text blocks containing `Tf` (set font+size), positioning, and `Tj`/`TJ`
(show glyphs). Every editor "emulates" paragraph structure — *"paragraphs don't actually exist
as real objects in PDF format."* So step one of any text editor is **reconstructing** lines and
blocks from glyph positions.

**The hard part is fonts, not text.** Embedded fonts are almost always *subset* — only the
glyphs the document actually uses, encoded as `0,1,2…` (not ASCII). Consequences, straight from
the PDFBox maintainers (who **removed** their `ReplaceText` example because it "gave the
incorrect illusion that text can be replaced easily"):

- You often can't even *find* the text ("abc" may be stored as bytes `0 1 2`).
- You can't type a new character whose glyph isn't in the subset (want a "d" that was never used? it isn't in the font).
- Ligatures ("fi", "ffl") are single codes.

So typing genuinely new text means you must **supply a font that has the new glyphs** — either
reuse the embedded font (only if it already contains them), re-embed a fuller font, or
**substitute** a matching one.

**What Acrobat does** (the gold-standard behavior we should mirror):
- Edit only if the font is **installed on the system OR embedded**; if it's embedded-but-not-
  installed you can only change color/size; if it's neither, you can't edit at all.
- When a font must be substituted, Acrobat swaps in a **Multiple Master** face (AdobeSerifMM /
  AdobeSansMM) that **stretches or condenses to preserve the original line and page breaks.**
  That "substitute a matched face + fit it to the original box" move is exactly what we already
  do with Standard-14 fallback + fit-to-box.

**Block editing & reflow** (Sejda, PDF-XChange, Foxit reflow, Qoppa PDF Studio): editors detect
text blocks and let you edit with **reflow *within* a box** — but **text cannot reflow across
pages.** PDF-XChange calls it "Edit Text Elements as Blocks"; with it off you can only edit "a
few words… awkward spacing." Advanced editors "link all text strings" so you type without
manually rewrapping lines. This block-with-reflow model is the target.

**Searchability:** to keep edited text extractable/searchable you must maintain the **ToUnicode
CMap**. PDFBox's `PDType0Font` (CIDFontType2) is the recommended path for full Unicode + subset
embedding.

## Where PDFHarbor already is

We already ship a real word-level editor (see `EditService`, `WordExtractor`, `FontResolver`,
`TextRemover`, `EditorPage.tsx`):
- Double-click a word → **remove its glyphs from the content stream** (unique-word gated) and
  redraw on the original baseline.
- **Font matching:** reuse the document's embedded font when it can encode the new text
  (pixel-identical), else substitute the closest Standard-14 face by family/weight/slant, at the
  original color, **fit to the original box.**

That is exactly the pragmatic approach the research validates. The gap is that it's *one word at
a time*, substitution is limited to Standard-14, and there's no paragraph reflow.

## The landscape of build options

| Approach | What it gives | Cost / fit |
|---|---|---|
| **Extend our PDFBox editor** (recommended) | Full control, stays in Java, free, self-hosted | Engineering time; we own the hard parts |
| **Aspose.PDF for Java** | `TextFragmentAbsorber` + `TextFragment.setText()` — real find/replace with font handling, `FontRepository` substitution | Commercial license; drops in fast if we pay |
| **Spire.PDF / PDFTron(Apryse)** | Similar commercial text-replace APIs | Commercial license |
| Adobe / Foxit engines | Best fidelity | Cloud/desktop, not embeddable for us |

Recommendation: **extend our own PDFBox editor** (keeps the free, self-hosted, privacy story),
and keep Aspose in our back pocket as a paid "just works" accelerator if we ever want it.

## Implementation plan (mapped to our code)

### Phase A — Block reconstruction + "click a paragraph and type"
1. **Backend block model.** Extend `WordExtractor` (or a new `BlockExtractor`) to group words →
   lines (same baseline) → blocks (consistent left edge + line gap ≈ leading). Return each block
   with: bbox, ordered lines, dominant font family/weight/italic/size/color, and alignment.
2. **New endpoint** `GET /api/files/{id}/pages/{n}/blocks` → `[{id,bbox,lines,font,size,color,align}]`.
3. **Editor UX** (`EditorPage.tsx`): click a block → a `contenteditable` overlay, pre-filled with
   the block text and styled to match (family/weight/italic/color/size). Feels like a text box.
4. **New element type** `replace-block` `{page, bbox, text, font, size, color, align}`. On save,
   `EditService`:
   - **Region removal:** extend `TextRemover` to strip *all* glyphs whose origin falls inside the
     block bbox (robust region delete), instead of only a unique word; cover with white as the
     fallback when stream editing isn't safe.
   - **Re-layout:** word-wrap the new text to the block width using the chosen font at the
     original size and leading, drawing each line — reflow *within the box*, Acrobat-style.

### Phase B — Faithful font embedding (the fidelity unlock)
Today new glyphs fall back to Standard-14. Upgrade so *any* text renders in a faithful face:
1. **Bundle metric-compatible open fonts** in the backend: Liberation Sans/Serif/Mono (≈
   Arial/Times/Courier) + a Noto fallback for wide Unicode coverage.
2. **New `FontService`:** map the original word's family/weight/italic → a bundled TTF, embed it
   once per document as a `PDType0Font` **subset**, and draw the replacement with it. Set a
   **ToUnicode CMap** so edited text stays searchable/selectable.
3. **Reuse-when-possible:** if the original font is *fully* embedded (detect: no `ABCDEF+` subset
   tag) and has the glyphs, keep reusing it directly for pixel-identical results.

### Phase C — Polish & robustness
- Grow-or-shrink when edits overflow the block (condense to fit, like Acrobat's MM faces).
- Better block detection: columns, centered/right/justified alignment, bullet/number lists.
- Insert/delete whole lines; keep the current word-level quick-edit as a "precise" sub-mode.

## Honest limits (set these expectations in the UI)
- **No cross-page reflow** — no editor does this; edits stay within their block/box.
- **Branded subset fonts can't be reproduced exactly** for brand-new glyphs without the original
  font file; we substitute a metric-compatible face (close, not pixel-identical).
- Complex tables, heavily-designed layouts, and scanned PDFs (need OCR first) remain hard.

## Effort
- Phase A: ~1 day (block grouping + region removal + wrapped re-layout + contenteditable UX).
- Phase B: ~1 day (bundle fonts + `PDType0Font` embedding + ToUnicode + family mapping).
- Phase C: incremental.

## Sources
- O'Reilly, *Developing with PDF* ch.4 "Text"; Syncfusion *PDF Succinctly* — text operators
- Apache PDFBox mailing list / `ReplaceText` removal; DeepWiki *PDFBox Font Handling*; `PDType0Font` javadocs — subset/encoding/ToUnicode limits
- helpx.adobe.com — *Edit text in PDFs* & *Font embedding and substitution* (installed/embedded rule, Multiple Master substitution)
- Aspose.PDF for Java — `TextFragmentAbsorber` / `TextFragment` / `FontRepository` (commercial text-replace)
- Sejda, PDF-XChange forum, Foxit reflow KB, Qoppa PDF Studio — block editing & reflow-within-box
