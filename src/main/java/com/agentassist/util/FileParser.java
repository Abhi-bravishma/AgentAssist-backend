package com.agentassist.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

@Slf4j
@Component
public class FileParser {

    /**
     * Parse file content based on file type
     */
    public String parseFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return parseAsText(file);
        }

        String lower = filename.toLowerCase();
        log.info("[FileParser] Parsing file: {}", filename);

        if (lower.endsWith(".pdf")) {
            return parsePdf(file);
        } else if (lower.endsWith(".docx")) {
            return parseDocx(file);
        } else if (lower.endsWith(".doc")) {
            return parseDoc(file);
        } else if (lower.endsWith(".xlsx")) {
            return parseXlsx(file);
        } else if (lower.endsWith(".xls")) {
            return parseXls(file);
        } else {
            return parseAsText(file);
        }
    }

    /**
     * Parse PDF file
     */
    private String parsePdf(MultipartFile file) throws IOException {
        log.info("[FileParser] Parsing PDF file...");
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("[FileParser] PDF parsed, {} pages, {} chars",
                    document.getNumberOfPages(), text.length());
            return text;
        }
    }

    /**
     * Parse Word .docx file (Office 2007+)
     */
    private String parseDocx(MultipartFile file) throws IOException {
        log.info("[FileParser] Parsing DOCX file...");
        try (InputStream is = file.getInputStream();
             XWPFDocument document = new XWPFDocument(is)) {

            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph para : document.getParagraphs()) {
                sb.append(para.getText()).append("\n");
            }
            String text = sb.toString();
            log.info("[FileParser] DOCX parsed, {} paragraphs, {} chars",
                    document.getParagraphs().size(), text.length());
            return text;
        }
    }

    /**
     * Parse Word .doc file (Office 97-2003)
     */
    private String parseDoc(MultipartFile file) throws IOException {
        log.info("[FileParser] Parsing DOC file...");
        try (InputStream is = file.getInputStream();
             HWPFDocument document = new HWPFDocument(is)) {

            WordExtractor extractor = new WordExtractor(document);
            String text = extractor.getText();
            log.info("[FileParser] DOC parsed, {} chars", text.length());
            return text;
        }
    }

    /**
     * Parse Excel .xlsx file (Office 2007+)
     */
    private String parseXlsx(MultipartFile file) throws IOException {
        log.info("[FileParser] Parsing XLSX file...");
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            return parseWorkbook(workbook);
        }
    }

    /**
     * Parse Excel .xls file (Office 97-2003)
     */
    private String parseXls(MultipartFile file) throws IOException {
        log.info("[FileParser] Parsing XLS file...");
        try (InputStream is = file.getInputStream();
             Workbook workbook = new HSSFWorkbook(is)) {
            return parseWorkbook(workbook);
        }
    }

    /**
     * Common workbook parsing logic
     */
    private String parseWorkbook(Workbook workbook) {
        StringBuilder sb = new StringBuilder();
        int totalRows = 0;

        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            sb.append("=== Sheet: ").append(sheet.getSheetName()).append(" ===\n");

            Iterator<Row> rowIterator = sheet.iterator();
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                totalRows++;

                Iterator<Cell> cellIterator = row.cellIterator();
                while (cellIterator.hasNext()) {
                    Cell cell = cellIterator.next();
                    sb.append(getCellValue(cell)).append("\t");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        String text = sb.toString();
        log.info("[FileParser] Excel parsed, {} sheets, {} rows, {} chars",
                workbook.getNumberOfSheets(), totalRows, text.length());
        return text;
    }

    /**
     * Get cell value as string
     */
    private String getCellValue(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toString();
                }
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    return cell.getStringCellValue();
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }

    /**
     * Parse as plain text
     */
    private String parseAsText(MultipartFile file) throws IOException {
        log.info("[FileParser] Parsing as plain text...");
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        log.info("[FileParser] Text file parsed, {} chars", text.length());
        return text;
    }

    /**
     * Detect file type from filename
     */
    public String detectFileType(String filename) {
        if (filename == null) return "text";

        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".docx") || lower.endsWith(".doc")) return "word";
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) return "excel";
        if (lower.endsWith(".md")) return "markdown";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "html";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".csv")) return "csv";
        if (lower.endsWith(".txt")) return "text";

        return "text";
    }
}
