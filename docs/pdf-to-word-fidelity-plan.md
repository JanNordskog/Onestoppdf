# High-fidelity PDF → Word: research + implementation plan

*Researched July 2026. Goal: convert a PDF to an editable `.docx` that stays as close as
possible to the original, self-hosted, without breaking PDFHarbor's "your files never leave
your server" promise.*

## The honest constraint

Every source repeats the same thing: **"exactly like it was" is impossible for a reflow
conversion.** PDF is a fixed-layout format (glyphs at absolute x/y); Word is a flow format
(paragraphs that re-wrap). Converting one to the other is *reconstruction*, and it's a guess.
Two different goals hide inside "keep it exactly like it was":

| Goal | What wins | What you give up |
|---|---|---|
| **Visual fidelity** — looks pixel-identical | LibreOffice's text-box import, or wrapping the rendered page | Editability: text becomes floating boxes, painful to edit |
| **Editable fidelity** — real paragraphs/tables you can retype | pdf2docx, Aspose EnhancedFlow, Adobe/ABBYY | Pixel-perfection: reflow shifts spacing; complex layouts drift |

Where everything breaks, universally: **multi-column layouts, merged table cells, footnotes/
headers, and scanned PDFs** (which have no text layer and need OCR first). This is exactly the
fidelity gap our own market research flagged — nobody, including Adobe, nails complex layouts.

We already own the **visual** side: the in-place editor keeps the original pixel-perfect and
edits words in place. This plan is about the **editable** side — producing a genuine `.docx`.

## The landscape (from research)

| Option | Fidelity | Self-host / privacy | Fits our stack | License / cost |
|---|---|---|---|---|
| **Adobe PDF Services API** | Best-in-class (Acrobat's own engine) | ❌ cloud only | REST call | Paid per-doc; breaks privacy story |
| **ABBYY FineReader Engine** | Best-in-class (powers Smallpdf/iLovePDF), great OCR | ✅ on-prem option | SDK | Expensive enterprise license |
| **Aspose.PDF for Java** | High — `EnhancedFlow` recognition mode, improving each release | ✅ fully local | ✅ **native Java, no sidecar** | Commercial license (per-dev/deployment) |
| **pdf2docx** (Python) | Best open-source: real paragraphs, tables, images, styles | ✅ fully local | ⚠️ Python sidecar | Open source (see licensing note) |
| **LibreOffice headless** | Visually close, but imports as text boxes → poor editability | ✅ fully local | ⚠️ heavy binary | MPL/LGPL (free) |
| **Docling / Marker / MinerU** (AI/VLM) | High *structure* fidelity, PDF→Markdown/JSON | ✅ local (heavy, GPU helps) | ⚠️ Python + models | Mixed (Docling MIT; Marker GPL) |
| **Our current POI extraction** | Low — text + images, layout simplified ("extraction-grade") | ✅ local | ✅ already in Java | Apache 2.0 (free) |

Takeaways:
- The **gold standard is a licensed engine** (Adobe/ABBYY). Both conflict with our free +
  self-hosted + privacy positioning, so they're out for the default path.
- Among things that respect "files never leave the server," **pdf2docx gives the best
  editable output**, and **Aspose is the best if we're willing to pay to stay pure-Java**.
- The AI parsers (Docling/Marker/MinerU) are aimed at PDF→Markdown/JSON for LLMs, not
  visually-faithful `.docx`. Interesting for a future "PDF → structured data" tool, not this one.

## Recommendation for PDFHarbor

Ship **pdf2docx as a local Python sidecar** as the new default "Layout-accurate" mode, keep our
POI converter as the offline "Basic" fallback, and add an **OCR pre-step for scanned PDFs**.
This is the best editable fidelity we can get for free while keeping every byte on the server.
Offer **Aspose EnhancedFlow** later as an optional premium/pure-Java mode for anyone who buys the
license.

### Why not just the Java options?
- POI (what we have) is extraction-grade — the honest floor, not the goal.
- Aspose is genuinely good and native, but it's a paid license; make it opt-in, not the default.
- A Python sidecar is a small, isolated container on our internal Docker network — it never
  exposes a public port, so the privacy story holds.

## Implementation plan (mapped to our code)

### Phase 1 — pdf2docx sidecar (the core upgrade)
1. **New service** `pdf-suite/converter/` — tiny FastAPI app: `POST /convert` takes PDF bytes,
   runs `pdf2docx.Converter`, returns `.docx` bytes. `Dockerfile` on `python:3.12-slim`.
2. **docker-compose** — add a `converter` service on the internal network only (e.g. internal
   port 8090, **not** published to the host). Backend reaches it at `http://converter:8090`.
3. **Backend** — `ConvertService.pdfToWordLayout(byte[])` POSTs to `${app.converter.url}` and
   returns the docx. On connection failure → fall back to the existing POI method and mark the
   result "basic". New config key `app.converter.url` (default `http://localhost:8090`).
4. **API** — extend `POST /api/tools/pdf-to-word` body with optional `mode: "layout" | "basic"`
   (default `layout`). Keeps the same `DocumentDto` response.
5. **Frontend** — the PDF→Word tool gains a mode selector: **"Layout-accurate (recommended)"**
   vs **"Basic text (fast, offline)"**, with an honest note that complex tables/columns may need
   cleanup. Update the tool subtitle from "extraction-grade" to reflect the upgrade.

### Phase 2 — OCR for scanned PDFs
- Detect "scanned" in the backend: if PDFBox extracts near-zero text, the PDF is image-only.
- In that case run **OCRmyPDF (Tesseract)** in the sidecar to add a text layer *before*
  pdf2docx. Without this, a scanned PDF converts to uneditable images — this is the difference
  between "works" and "doesn't" for a big class of real documents.

### Phase 3 (optional) — premium / visual modes
- **Aspose.PDF for Java** as an opt-in "Best (Java)" mode — `DocSaveOptions` with
  `RecognitionMode.EnhancedFlow`, no sidecar, gated behind a configured license key.
- **LibreOffice headless** as a "Visual (looks closest, less editable)" mode for users who care
  more about appearance than editing — `soffice --headless --convert-to docx`.

## Licensing & risks (must-check before shipping in a paid product)
- **pdf2docx builds on PyMuPDF, which is AGPL-3.0 (or a paid commercial license from Artifex).**
  Fine for an open-source / self-hosted deployment; a closed commercial SaaS likely needs the
  commercial PyMuPDF license. **Verify before monetizing.**
- Aspose = clean commercial license (predictable, paid). LibreOffice = MPL/LGPL (free, safe).
  Adobe/ABBYY = paid + (for Adobe) cloud.
- Operational risk: adds a Python container to a Java+Node stack. Mitigated by isolation
  (internal-only) and the POI fallback so the tool never hard-fails if the sidecar is down.
- Set expectations in the UI: even the best local option won't be pixel-identical on complex
  layouts. Honesty here is itself a differentiator (our research found every competitor
  disappoints on this silently).

## Effort estimate
- Phase 1: ~half a day (sidecar + compose + backend wiring + mode selector).
- Phase 2: ~half a day (scan detection + OCRmyPDF in the sidecar).
- Phase 3: optional, license-dependent.

## Sources
- howtoconvert.co — *18 Best PDF to Word Converters in 2026*
- pdf4.dev — *How to convert PDF to Word: LibreOffice, Python pdf2docx, and 5 free methods*
- nutrient.io — *PDF-to-Word conversion: complete guide*
- Aspose.PDF for Java release notes 23.4 / 23.5 / 24.10 (EnhancedFlow recognition mode)
- Adobe Experience League — *Using PDF Services API to export PDF to Word*
- themenonlab.blog / jimmysong.io — *Open-source PDF→Markdown: Marker vs Docling vs MinerU (2026)*
- arxiv.org/html/2501.17887 — *Docling toolkit*
