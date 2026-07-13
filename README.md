# PDF Suite — one-stop PDF platform

Merge, split, compress, convert, edit and e-sign PDFs. Self-hosted alternative to
iLovePDF / Smallpdf with no file-size paywalls and automatic file expiry for privacy.

## Stack
- **Backend**: Java 21, Spring Boot 3.4, Apache PDFBox 3, Apache POI (DOCX), PostgreSQL
- **Frontend**: React 18 + TypeScript + Vite + Tailwind CSS v4, pdf.js previews
- **Database**: PostgreSQL 16 (Docker, host port **5434**)

## Run (dev)
```bash
docker compose up -d                 # postgres on localhost:5434
cd backend && mvn spring-boot:run    # api on http://localhost:8081
cd frontend && npm install && npm run dev   # ui on http://localhost:5174
```

Anonymous uploads auto-delete after 2 hours. Register an account to keep files,
see job history and send documents out for signature.

See `docs/architecture.md` for the API contract and `docs/competitive-research.md`
for the market research this product plan is based on.
