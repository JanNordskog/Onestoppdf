package com.pdfsuite.tools;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Extraction-grade conversion (honest by design): text and paragraph flow are preserved,
 * complex layouts are simplified. No cloud APIs — files never leave the server.
 */
@Service
public class ConvertService {

    public byte[] pdfToWord(byte[] input) {
        try (PDDocument pdf = PdfToolService.load(input); XWPFDocument docx = new XWPFDocument()) {
            PDFTextStripper stripper = new PDFTextStripper();
            int pages = pdf.getNumberOfPages();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(pdf);
                for (String line : text.split("\r?\n")) {
                    XWPFParagraph p = docx.createParagraph();
                    p.createRun().setText(line);
                }
                if (page < pages) {
                    XWPFParagraph breaker = docx.createParagraph();
                    XWPFRun run = breaker.createRun();
                    run.addBreak(BreakType.PAGE);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            docx.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("PDF to Word failed", e);
        }
    }

    public byte[] pdfToText(byte[] input) {
        try (PDDocument pdf = PdfToolService.load(input)) {
            return new PDFTextStripper().getText(pdf).getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("PDF to text failed", e);
        }
    }
}
