# PDF Suite — Architecture Contract

This document is the single source of truth for the build. Every module must match
the signatures, endpoints and shapes below EXACTLY so independently-built modules
compile and integrate without rework.

## System overview

| Piece      | Tech                                            | Port |
|------------|--------------------------------------------------|------|
| Database   | PostgreSQL 16 (docker compose, service `postgres`) | host 5434 |
| Backend    | Java 21, Spring Boot 3.4.1, Maven, package `com.pdfsuite` | 8081 |
| Frontend   | React 18 + TypeScript + Vite + Tailwind CSS v4   | 5174 |

- PDF engine: **Apache PDFBox 3.0.3** (`org.apache.pdfbox:pdfbox`).
- DOCX: **Apache POI 5.3.0** (`org.apache.poi:poi-ooxml`).
- JWT: **jjwt 0.12.6** (`io.jsonwebtoken:jjwt-api` + runtime `jjwt-impl`, `jjwt-jackson`).
- JPA with `spring.jpa.hibernate.ddl-auto=update` (no Flyway in MVP).
- DB (dev defaults, overridable by env): `jdbc:postgresql://localhost:5434/pdfsuite`,
  user/password `pdfsuite`/`pdfsuite`.
- Multipart limits: 100MB per file, 200MB per request (generous limits are a product differentiator).

## Product / storage model

- **Anonymous-first**: every tool works without an account. Uploaded files get
  `expiresAt = now + 2h` and a cleanup `@Scheduled` job hard-deletes expired rows + disk files
  every 10 minutes (privacy differentiator: "your files self-destruct").
- **Logged-in**: files persist (`expiresAt = null`), user gets file dashboard + e-sign.
- Files stored on disk under `./storage/{uuid}` (path from `app.storage.dir`, default `./storage`);
  metadata in Postgres. Document IDs are random UUIDs; possession of the UUID grants access
  (documented MVP tradeoff).
- All tool operations are synchronous HTTP (no job queue in MVP). Each tool call records a
  `ToolJob` row for history.

## Security model

- Stateless JWT (HS256, `jwt.secret`, default dev secret `dev-secret-change-me-0123456789abcdef`, 7-day expiry).
- `Authorization: Bearer <token>` parsed by a `OncePerRequestFilter`; sets the user id (UUID) as principal.
- Public (no auth required): `/api/auth/**`, `/api/public/**`, `/api/files/**`, `/api/tools/**`, `/error`.
- Auth required: `/api/me/**`, `/api/sign/**`.
- CORS: allow origins `http://localhost:5174`, `http://localhost:4173`; all methods/headers; no cookies.
- Passwords hashed with BCrypt.

## Backend package layout & shared classes (module B1 owns these)

```
com.pdfsuite
  PdfSuiteApplication.java        // @SpringBootApplication @EnableScheduling
  config/SecurityConfig.java      // filter chain, CORS, BCryptPasswordEncoder bean
  config/JwtAuthFilter.java
  auth/AppUser.java               // entity, table "app_user"
  auth/AppUserRepo.java
  auth/JwtService.java
  auth/AuthController.java
  auth/CurrentUser.java
  files/StoredDocument.java       // entity, table "stored_document"
  files/StoredDocumentRepo.java
  files/StorageService.java
  files/DocumentService.java
  files/FileController.java
  files/CleanupScheduler.java
  jobs/ToolJob.java               // entity, table "tool_job"
  jobs/ToolJobRepo.java
  common/ApiException.java        // extends RuntimeException, carries HttpStatus
  common/GlobalExceptionHandler.java  // @RestControllerAdvice -> {"error": message}
```

### Entities (exact fields)

**AppUser**: `UUID id` (generated), `String email` (unique, lowercase), `String passwordHash`,
`String displayName`, `Instant createdAt`.

**StoredDocument**: `UUID id`, `UUID ownerId` (nullable), `String originalName`,
`String contentType`, `long sizeBytes`, `Integer pageCount` (nullable, set for PDFs),
`Instant createdAt`, `Instant expiresAt` (nullable).

**ToolJob**: `UUID id`, `UUID ownerId` (nullable), `String tool`, `String status`
(`DONE`/`ERROR`), `String inputNames`, `UUID outputDocId` (nullable), `String errorMessage`
(nullable), `Instant createdAt`.

### Shared service signatures (B2/B3 compile against these — do not deviate)

```java
public class CurrentUser {                    // auth/CurrentUser.java, static helpers
  public static UUID idOrNull();              // from SecurityContextHolder, null if anonymous
  public static UUID idOrThrow();             // 401 ApiException if anonymous
}

@Service public class StorageService {
  public Path pathFor(UUID docId);                         // storage dir + uuid
  public byte[] readBytes(UUID docId);                     // 404 ApiException if missing
  public void writeBytes(UUID docId, byte[] data);
  public void delete(UUID docId);
}

@Service public class DocumentService {
  // Persists metadata + bytes. ownerId nullable. Sets pageCount when contentType is
  // application/pdf (PDFBox Loader.loadPDF). expiresAt = ownerId==null ? now+2h : null.
  public StoredDocument create(UUID ownerId, String originalName, String contentType, byte[] bytes);
  public StoredDocument get(UUID id);                      // 404 if missing or expired
  public byte[] bytes(UUID id);                            // get() + StorageService.readBytes
  // Renders page (1-based) of a PDF document to PNG at the given dpi using PDFBox PDFRenderer.
  public byte[] renderPagePng(UUID id, int page, float dpi);
}
```

### DTO shape used everywhere a document is returned (`files/DocumentDto` record, B1)

```json
{ "id": "uuid", "name": "invoice.pdf", "contentType": "application/pdf",
  "sizeBytes": 12345, "pageCount": 3, "createdAt": "...", "expiresAt": "..." }
```
`DocumentDto.from(StoredDocument)` static factory. `downloadUrl` is derived on the
frontend as `/api/files/{id}/download`.

## REST API

Errors: non-2xx returns `{"error": "human readable message"}`.

### Auth & files (B1)

| Method/Path | Body → Response |
|---|---|
| POST `/api/auth/register` | `{email,password,displayName}` → `{token, user:{id,email,displayName}}` |
| POST `/api/auth/login` | `{email,password}` → same shape |
| GET `/api/me` | → `{id,email,displayName}` |
| GET `/api/me/files` | → `DocumentDto[]` (owned, newest first) |
| GET `/api/me/jobs` | → `[{id,tool,status,inputNames,outputDocId,createdAt}]` |
| POST `/api/files` | multipart field `file` (repeatable) → `DocumentDto[]` (owner = current user or null) |
| GET `/api/files/{id}` | → `DocumentDto` |
| GET `/api/files/{id}/download` | → bytes, `Content-Disposition: attachment; filename="..."` |
| GET `/api/files/{id}/pages/{n}` | query `dpi` (default 96) → `image/png` of page n (1-based) |
| GET `/api/files/{id}/pages/{n}/words` | → `[{text,x,y,w,h,fontSize,fontName,bold,italic,family,color}]` — every word's normalized box (bottom edge = baseline) + styling; powers double-click text editing with font matching |
| DELETE `/api/files/{id}` | owner or anonymous-with-id → 204 |

### PDF tools (B2 — `tools/ToolController` + `tools/PdfToolService`, plus `tools/ConvertService` for POI)

All POST, JSON body, return `DocumentDto` of the produced file unless noted. Every call
also inserts a ToolJob. Common field: `documentId` / `documentIds` (UUIDs of uploaded files).

| Path | Body extras | Behavior |
|---|---|---|
| `/api/tools/merge` | `{documentIds:[..]}` | PDFMergerUtility, order preserved → `merged.pdf` |
| `/api/tools/split` | `{documentId, ranges:"1-3,5"}` | extract listed pages into one PDF; if `ranges` omitted → ZIP with one PDF per page (`application/zip`) |
| `/api/tools/organize` | `{documentId, pages:[3,1,2]}` | rebuild with given 1-based page order (drop = omit) |
| `/api/tools/rotate` | `{documentId, degrees:90\|180\|270, pages:[..] or null=all}` | set page rotation |
| `/api/tools/compress` | `{documentId, level:"low"\|"medium"\|"high"}` | re-encode embedded images as JPEG (quality .75/.5/.3, downscale >1600px to fit) |
| `/api/tools/watermark` | `{documentId, text, fontSize:48, opacity:0.25}` | diagonal grey text each page (PDExtendedGraphicsState alpha) |
| `/api/tools/page-numbers` | `{documentId, startAt:1}` | "n / total" bottom-center footer |
| `/api/tools/protect` | `{documentId, password}` | StandardProtectionPolicy AES-256 |
| `/api/tools/unlock` | `{documentId, password}` | open with password, save decrypted (`setAllSecurityToBeRemoved(true)`) |
| `/api/tools/pdf-to-word` | `{documentId}` | PDFTextStripper per page → XWPFDocument paragraphs, page breaks between pages → `.docx` |
| `/api/tools/pdf-to-text` | `{documentId}` | → `.txt` |
| `/api/tools/pdf-to-images` | `{documentId, dpi:150}` | ZIP of `page-1.png..n` |
| `/api/tools/images-to-pdf` | `{documentIds:[..]}` | each JPG/PNG scaled onto its own A4-or-image-sized page |
| `/api/tools/edit` | `{documentId, elements:[Element]}` | bake overlay elements (the "editor") |
| `/api/tools/extract-form-data` | `{documentId, format:"json"\|"csv"}` | AcroForm fields → `{name:value}` JSON or 2-col CSV file (headline differentiator — no competitor has it) |
| `/api/tools/strip-metadata` | `{documentId}` | blank out title/author/subject/keywords/producer/creator + XMP |

**Element** (editor + sign share this coordinate convention):
`{page:int(1-based), type:"text"|"highlight"|"rect"|"image", x,y,w,h:float normalized 0..1
with origin TOP-LEFT of the page, text?, fontSize?:float(pt), color?:"#rrggbb",
imageDataUrl?:"data:image/png;base64,..."}`.
Backend converts to PDF space: `pdfX = x*pageW`, `pdfY = pageH - (y+h)*pageH`, `pdfW = w*pageW`,
`pdfH = h*pageH`. Text uses Helvetica; highlight = fill rect with alpha 0.35; rect = stroke only.
Extra type `"replace-text"` (double-click word editing): carries `originalText` plus the original
word's `bold`/`italic`/`family` styling. The backend first tries to delete the original word's
glyphs from the content stream (see TextRemover — only when the word is unique on the page and in a
simple font), otherwise covers the box with a padded white fill; then draws `text` on the original
baseline. Font matching: it reuses the document's own embedded font when that font can encode the
new characters (pixel-identical), else falls back to the closest Standard-14 face (Times/Helvetica/
Courier × regular/bold/italic) matching `family` + weight + slant, auto-shrinking to fit the box.

### E-sign (B3 — `sign/` package: entities SignatureRequest, Signer, SignatureField, AuditEvent + repos + SignService + SignController + PublicSignController)

Entities: **SignatureRequest** `id, ownerId, documentId, finalDocumentId(nullable), title,
message(nullable), status DRAFT|SENT|COMPLETED|CANCELLED, createdAt, completedAt(nullable)`;
**Signer** `id, request(ManyToOne), name, email, token(unique random 43-char url-safe),
orderIndex, status PENDING|SIGNED, signedAt, signerIp, userAgent (nullable)`;
**SignatureField** `id, request(ManyToOne), signer(ManyToOne), page, x,y,w,h (normalized,
top-left origin), type SIGNATURE|DATE|TEXT|NAME, value(nullable)`;
**AuditEvent** `id, request(ManyToOne), at, actor, action, detail(nullable), ip(nullable)`.

| Method/Path (auth) | Behavior |
|---|---|
| POST `/api/sign/requests` | `{documentId,title,message,signers:[{name,email}],fields:[{signerIndex,page,type,x,y,w,h}]}` → creates request status SENT, generates signer tokens, emails links if mail enabled, audit "created"+"sent". Response: detail shape below. |
| GET `/api/sign/requests` | list `[{id,title,status,createdAt,signerSummary:"1/2 signed"}]` |
| GET `/api/sign/requests/{id}` | `{id,title,message,status,document:DocumentDto,finalDocument:DocumentDto?,signers:[{id,name,email,status,signedAt,signUrl}],audit:[{at,actor,action,detail}]}` — `signUrl` = `http://localhost:5174/sign/t/{token}` (base from `app.frontend-url`) |
| POST `/api/sign/requests/{id}/cancel` | status → CANCELLED, audit |

| Method/Path (public, token = signer token) | Behavior |
|---|---|
| GET `/api/public/sign/{token}` | `{requestTitle,message,ownerName,status,signerName,signerStatus,pageCount, myFields:[{id,page,type,x,y,w,h}]}` |
| GET `/api/public/sign/{token}/pages/{n}` | page PNG render (dpi 120) of the request's document |
| POST `/api/public/sign/{token}/complete` | `{signatureDataUrl:"data:image/png..", textValues:{fieldId:value}}` → validates PENDING; stamps every field of this signer onto a working copy chained across signers (SIGNATURE/, NAME=name text, DATE=today, TEXT=value); records ip/UA, audit "signed". When last signer completes: append audit-trail certificate page (request id, doc SHA-256, per-signer name/email/time/ip) → save as `finalDocumentId`, status COMPLETED, audit "completed". |
| GET `/api/public/sign/{token}/document` | final (if done) else current working PDF download |

Mail: reuse Spring Mail; `app.mail.enabled=false` default → log links instead of sending.

## Frontend

Vite + React 18 + TS. Deps: `react-router-dom@6`, `pdfjs-dist` (worker via
`?url` import), Tailwind v4 via `@tailwindcss/vite` plugin (`@import "tailwindcss"` in index.css).
Dev server 5174 with proxy `/api -> http://localhost:8081` (vite.config.ts) so the app uses
relative `/api/...` URLs everywhere.

### Shared infrastructure (module F1 owns)

```
src/main.tsx, src/App.tsx (router + <Layout>), src/index.css
src/brand.ts          // export const BRAND = { name, tagline }
src/lib/api.ts        // api<T>(path, opts): fetch JSON, attaches Bearer token from localStorage("token"),
                      // throws Error(message from {"error"}); apiUpload(files: File[]): Promise<DocumentDto[]>;
                      // downloadUrl(id), pageUrl(id, n, dpi?)
src/lib/types.ts      // DocumentDto, User, Element, SignRequestDetail, PublicSignInfo ... mirror API shapes
src/lib/auth.tsx      // AuthContext: {user, login, register, logout}; <RequireAuth> wrapper
src/components/Layout.tsx      // navbar (brand, Tools dropdown/links, Sign, My files, login state), footer
src/components/FileDrop.tsx    // props {accept?, multiple?, onFiles(File[])}: drag-drop zone + click browse
src/components/ToolPage.tsx    // generic shell: title/desc, FileDrop -> uploads via apiUpload -> file chips,
                               // options slot (render-prop with docs), run button (POST tool), result card with download link + "process another"
src/components/PdfThumb.tsx    // renders page n of doc id via /api/files/{id}/pages/{n} <img>, aspect preserved
src/components/SignaturePad.tsx // canvas draw (mouse+touch), clear, returns PNG dataURL (trim whitespace)
src/pages/Home.tsx             // hero + tool grid (cards link to routes below) + "why us" strip (privacy/free/no limits)
src/pages/Login.tsx, src/pages/Register.tsx, src/pages/MyFiles.tsx (files + job history tabs)
```

### Routes

| Route | Page (owner) |
|---|---|
| `/` Home, `/login`, `/register`, `/files` | F1 |
| `/merge` `/split` `/rotate` `/compress` `/watermark` `/page-numbers` `/protect` `/unlock` `/pdf-to-word` `/pdf-to-text` `/pdf-to-images` `/images-to-pdf` | F2 (each a thin config of `<ToolPage>`; merge adds drag-reorder of file chips; split has ranges input) |
| `/organize` `/edit` | F3 — organize: thumbnail grid, drag to reorder, click to delete, save; edit: page canvas (PdfThumb as background, absolutely-positioned overlay), toolbar add text/highlight/rect/image(+signature via SignaturePad), drag/resize elements, save → `/api/tools/edit` |
| `/sign` (dashboard, RequireAuth), `/sign/new` (3-step wizard: upload → signers → place fields on PdfThumb pages → send), `/sign/r/:id` (detail: status, copy links, audit trail), `/sign/t/:token` (PUBLIC signer view: pages with field markers, click SIGNATURE field → SignaturePad modal, complete button) | F4 |

### Design direction (all frontend modules)

Premium-minimal, NOT a generic bootstrap look: slate-50 background, white cards with
`rounded-2xl` + subtle border + soft shadow, indigo-600 primary, generous whitespace,
`Inter` via system font stack. Tool cards have a small colored icon tile (inline SVG, one
accent color per tool family: organize=indigo, convert=emerald, secure=amber, sign=rose).
Consistent page pattern: centered max-w-5xl, big h1, one-line subtitle. No external icon/CSS
libraries. Loading states on every async button (spinner + disabled). Errors as red inline alert.

## Module ownership (build agents — write ONLY your files)

| Module | Owns |
|---|---|
| B1 | `backend/pom.xml`, `backend/src/main/resources/application.properties`, packages `com.pdfsuite`, `config`, `auth`, `files`, `jobs`, `common` |
| B2 | package `tools` only |
| B3 | package `sign` only |
| F1 | `frontend/*` config files (package.json, vite.config.ts, tsconfig*, index.html), `src/main.tsx`, `App.tsx`, `index.css`, `brand.ts`, `lib/*`, `components/*`, pages Home/Login/Register/MyFiles |
| F2 | `src/pages/tools/*` (one file per tool route) |
| F3 | `src/pages/OrganizePage.tsx`, `src/pages/EditorPage.tsx` |
| F4 | `src/pages/sign/*` |

F2–F4 import ONLY from `lib/*`, `components/*` and react/react-router/pdfjs — nothing from
each other. `App.tsx` (F1) declares ALL routes above, importing the F2–F4 page files by the
exact paths listed (they must use default exports).
