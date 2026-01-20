package com.jdres.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Text Extraction Service
 * Handles file upload and text extraction from various formats (PDF, DOCX, TXT)
 */
@Service
public class TextExtractorService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractorService.class);

    @Value("${uploads.dir}")
    private String uploadsDir;

    /**
     * Extract text from uploaded file
     * 
     * @param file - Uploaded multipart file
     * @return Extracted text content
     */
    public String extractText(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("File name is required");
        }

        // Explicit file size check (10MB)
        long MAX_FILE_SIZE = 10 * 1024 * 1024;
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IOException("File too large. Maximum size is 10MB");
        }

        String ext = getFileExtension(originalFilename).toLowerCase();

        // Save file temporarily
        Path tempPath = saveTemporaryFile(file);

        try {
            return switch (ext) {
                case ".pdf" -> extractFromPDF(tempPath.toFile());
                case ".txt" -> extractFromTXT(tempPath);
                case ".docx" -> extractFromDOCX(tempPath.toFile());
                default -> throw new IllegalArgumentException("Unsupported file type: " + ext);
            };
        } finally {
            // Clean up temporary file
            Files.deleteIfExists(tempPath);
        }
    }

    /**
     * Extract text from multiple files
     * 
     * @param files - List of uploaded files
     * @return Map with filename as key and extraction result as value
     */
    public Map<String, ExtractionResult> extractMultipleTexts(List<MultipartFile> files) {
        Map<String, ExtractionResult> results = new HashMap<>();
        boolean anySuccess = false;

        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename();
            try {
                String text = extractText(file);
                results.put(filename, new ExtractionResult(true, text, null));
                anySuccess = true;
            } catch (Exception e) {
                log.error("Failed to extract {}: {}", filename, e.getMessage());
                results.put(filename, new ExtractionResult(false, null, e.getMessage()));
            }
        }

        if (!files.isEmpty() && !anySuccess) {
            throw new RuntimeException("Failed to extract text from all " + files.size() + " files");
        }

        return results;
    }

    public record ExtractionResult(boolean success, String text, String errorMessage) {
    }

    /**
     * Save file to temporary location
     */
    private Path saveTemporaryFile(MultipartFile file) throws IOException {
        Path uploadsPath = Paths.get(uploadsDir);
        Files.createDirectories(uploadsPath);

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            originalFilename = "temp_file";
        }

        // Sanitize: ensure only filename is used
        String sanitizedFilename = Paths.get(originalFilename).getFileName().toString();
        String uniqueFilename = UUID.randomUUID() + "_" + sanitizedFilename;

        Path tempPath = uploadsPath.resolve(uniqueFilename).normalize();

        // Extra check for security
        if (!tempPath.startsWith(uploadsPath.normalize())) {
            throw new IOException("Invalid upload path prevented: " + uniqueFilename);
        }

        file.transferTo(tempPath);

        return tempPath;
    }

    /**
     * Extract text from PDF file
     */
    private String extractFromPDF(File file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * Extract text from TXT file
     */
    private String extractFromTXT(Path filePath) throws IOException {
        return Files.readString(filePath);
    }

    /**
     * Extract text from DOCX file
     */
    private String extractFromDOCX(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
                XWPFDocument document = new XWPFDocument(fis);
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    /**
     * Get file extension from filename
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return filename.substring(lastDot);
    }

    /**
     * Check if file type is supported
     */
    public boolean isSupportedFileType(String filename) {
        String ext = getFileExtension(filename).toLowerCase();
        return ext.equals(".pdf") || ext.equals(".txt") || ext.equals(".docx");
    }
}
