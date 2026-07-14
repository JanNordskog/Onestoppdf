"""
PDFHarbor conversion sidecar — high-fidelity PDF -> DOCX via pdf2docx.

Runs on the internal Docker network only (bound to 127.0.0.1 on the host); the Spring Boot
backend POSTs raw PDF bytes and gets raw .docx bytes back. Keeping this local is what lets
PDFHarbor keep its "your files never leave your server" promise while still offering
layout-accurate Word conversion. If this service is down, the backend falls back to its
built-in Apache POI extraction path.
"""
import os
import tempfile

from fastapi import FastAPI, HTTPException, Request, Response
from pdf2docx import Converter

DOCX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

app = FastAPI(title="PDFHarbor converter", version="1.0.0")


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/convert")
async def convert(request: Request):
    data = await request.body()
    if not data:
        raise HTTPException(status_code=400, detail="Empty request body")

    with tempfile.TemporaryDirectory() as tmp:
        pdf_path = os.path.join(tmp, "in.pdf")
        docx_path = os.path.join(tmp, "out.docx")
        with open(pdf_path, "wb") as fh:
            fh.write(data)

        try:
            cv = Converter(pdf_path)
            try:
                cv.convert(docx_path)
            finally:
                cv.close()
        except Exception as exc:  # noqa: BLE001 - report any conversion failure to the caller
            raise HTTPException(status_code=422, detail=f"Conversion failed: {exc}") from exc

        with open(docx_path, "rb") as fh:
            out = fh.read()

    return Response(content=out, media_type=DOCX_MEDIA_TYPE)
