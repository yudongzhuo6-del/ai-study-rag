package com.yudong.aistudy.rag.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class TextParser {

    public static String parse(String filePath) throws IOException {
        try (InputStream inputStream = new FileInputStream(filePath)) {
            return parse(inputStream, filePath);
        }
    }

    public static String parse(InputStream inputStream, String filename) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("inputStream cannot be null");
        }
        String normalizedFilename = filename == null ? "" : filename.toLowerCase(Locale.ROOT);//把字符串的英文字母全改成小写
        if (normalizedFilename.endsWith(".pdf")) {
            return parsePdf(inputStream);
        }
        if (normalizedFilename.endsWith(".docx")) {
            return parseDocx(inputStream);
        }
        return parsePlainText(inputStream);
    }

    private static String parsePlainText(InputStream inputStream) throws IOException {
        return cleanText(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
    }

    private static String parsePdf(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return cleanPdfText(stripper.getText(document));
        }
    }

    private static String parseDocx(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return cleanText(extractor.getText());
        }
    }

    private static String cleanPdfText(String text) {
        return cleanText(text);
    }

    private static String cleanText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
