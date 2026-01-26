package com.jdres.controller;

import com.jdres.model.Resume;
import com.jdres.repository.ResumeRepository;
import com.jdres.repository.MatchResultRepository;
import com.jdres.service.S3Service;
import com.jdres.service.SkillExtractorService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ResumeController {

    @Autowired
    private ResumeRepository resumeRepository;
    @Autowired
    private MatchResultRepository matchResultRepository;
    @Autowired
    private S3Service s3Service;
    @Autowired
    private SkillExtractorService skillExtractorService;
    @Autowired
    private com.jdres.service.MatchingService matchingService;

    @PostMapping("/upload-resume")
    public ResponseEntity<?> uploadResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "jdId", required = false) String jdId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        try {
            byte[] content = file.getBytes();
            String fileId = UUID.randomUUID().toString();
            String s3Key = "uploads/" + fileId + "_" + file.getOriginalFilename();
            final String contentType = file.getContentType();

            // Extract text FIRST (fast - local operation)
            // Extract text based on file type
            String text = "";
            String fileNameLower = file.getOriginalFilename().toLowerCase();

            try {
                if (fileNameLower.endsWith(".pdf")) {
                    try (PDDocument document = Loader.loadPDF(content)) {
                        PDFTextStripper stripper = new PDFTextStripper();
                        text = stripper.getText(document);
                    }
                } else if (fileNameLower.endsWith(".docx")) {
                    try (java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(content);
                            XWPFDocument doc = new XWPFDocument(bis);
                            XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
                        text = extractor.getText();
                    }
                } else if (fileNameLower.endsWith(".txt")) {
                    text = new String(content, java.nio.charset.StandardCharsets.UTF_8);
                } else {
                    text = "Unsupported file format (only PDF, DOCX, TXT supported)";
                }
            } catch (Exception e) {
                text = "Error extracting text: " + e.getMessage();
                e.printStackTrace();
            }

            final String finalText = text;

            // PARALLEL: S3 upload and OpenAI extraction at the same time!
            java.util.concurrent.CompletableFuture<String> s3Future = java.util.concurrent.CompletableFuture
                    .supplyAsync(() -> s3Service.uploadBytes(s3Key, content, contentType));

            java.util.concurrent.CompletableFuture<Map<String, Object>> extractFuture = java.util.concurrent.CompletableFuture
                    .supplyAsync(() -> skillExtractorService.extractResumeDetails(finalText));

            // Wait for both to complete
            String s3Url = s3Future.get(60, java.util.concurrent.TimeUnit.SECONDS);
            Map<String, Object> parsedDetails = extractFuture.get(60, java.util.concurrent.TimeUnit.SECONDS);

            // Flatten skills
            List<String> skills;
            if (parsedDetails != null && !parsedDetails.isEmpty()) {
                skills = skillExtractorService.flattenSkills(parsedDetails);
                if (skills.isEmpty()) {
                    skills = skillExtractorService.extractSkills(finalText);
                }
            } else {
                skills = skillExtractorService.extractSkills(finalText);
            }

            // 5. Save to DB
            Resume resume = new Resume();
            resume.setParsedDetails(parsedDetails);
            resume.setFileId(fileId);
            resume.setName(file.getOriginalFilename());
            resume.setText(text);
            resume.setSource("manual_upload");
            resume.setImportedAt(LocalDateTime.now());
            resume.setS3Key(s3Key);
            resume.setS3Url(s3Url);
            resume.setViewLink(s3Url); // Set viewLink
            resume.setEmbedding(Collections.emptyList()); // Empty - not used anymore
            resume.setSkills(skills);

            // Resume Isolation
            if (jdId != null && !jdId.isEmpty() && !jdId.equals("undefined") && !jdId.equals("null")) {
                resume.setJdId(jdId);
            }

            // Extract candidate info from details
            String candidateName = null;
            if (parsedDetails != null && parsedDetails.containsKey("candidate_profile")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> profile = (Map<String, Object>) parsedDetails.get("candidate_profile");
                if (profile != null) {
                    candidateName = (String) profile.get("name");
                    resume.setCandidateName(candidateName);
                }
            }
            if (parsedDetails != null && parsedDetails.containsKey("total_experience_years")) {
                Object exp = parsedDetails.get("total_experience_years");
                if (exp instanceof Number) {
                    resume.setCandidateExperience(((Number) exp).intValue());
                }
            }

            // Embeddings not used anymore

            // Set recruiterId for user isolation
            if (userId != null && !userId.trim().isEmpty()) {
                resume.setRecruiterId(userId);
            }

            resumeRepository.save(resume);

            // Trigger matching against all JDs
            matchingService.matchNewResume(fileId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("text", text);
            response.put("fileId", fileId);
            response.put("viewLink", s3Url);
            response.put("skills", skills);
            response.put("candidateName", candidateName);
            response.put("candidateExperience", resume.getCandidateExperience());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/extract-jd")
    public ResponseEntity<?> extractJD(@RequestBody Map<String, String> payload) {
        String jdText = payload.get("jdText");
        if (jdText == null || jdText.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "jdText is required"));
        }

        Map<String, Object> details = skillExtractorService.extractJobDescriptionDetails(jdText);
        return ResponseEntity.ok(Map.of("success", true, "details", details));
    }

    @PostMapping("/compare-seeker")
    public ResponseEntity<?> compareSeeker(
            @RequestParam("jd") MultipartFile jdFile,
            @RequestParam("resume") MultipartFile resumeFile) {
        try {
            // Extract JD Text
            String jdText = "";
            try (PDDocument document = Loader.loadPDF(jdFile.getBytes())) {
                PDFTextStripper stripper = new PDFTextStripper();
                jdText = stripper.getText(document);
            }

            // Extract Resume Text
            String resumeText = "";
            try (PDDocument document = Loader.loadPDF(resumeFile.getBytes())) {
                PDFTextStripper stripper = new PDFTextStripper();
                resumeText = stripper.getText(document);
            }

            // Extract JD Skills
            Map<String, Object> jdDetails = skillExtractorService.extractJobDescriptionDetails(jdText);
            List<String> jdSkills = skillExtractorService.flattenSkills(jdDetails);
            if (jdSkills.isEmpty()) {
                jdSkills = skillExtractorService.extractSkills(jdText);
            }

            // Extract Resume Skills
            Map<String, Object> resumeDetails = skillExtractorService.extractResumeDetails(resumeText);
            List<String> resumeSkills = skillExtractorService.flattenSkills(resumeDetails);
            if (resumeSkills.isEmpty()) {
                resumeSkills = skillExtractorService.extractSkills(resumeText);
            }

            // Calculate Match
            Set<String> jdSkillsSet = new HashSet<>(jdSkills.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toList()));
            Set<String> resumeSkillsSet = new HashSet<>(resumeSkills.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toList()));

            // Matching Skills
            Set<String> matchingSkills = new HashSet<>(resumeSkillsSet);
            matchingSkills.retainAll(jdSkillsSet);

            // Missing Skills
            Set<String> missingSkills = new HashSet<>(jdSkillsSet);
            missingSkills.removeAll(resumeSkillsSet);

            // Calculate Score
            int matchScore = jdSkillsSet.isEmpty() ? 0
                    : (int) Math.round((matchingSkills.size() * 100.0) / jdSkillsSet.size());

            // Build Response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("matchScore", matchScore);
            response.put("matchingSkills", new ArrayList<>(matchingSkills));
            response.put("missingSkills", new ArrayList<>(missingSkills));
            response.put("totalJdSkills", jdSkillsSet.size());
            response.put("totalResumeSkills", resumeSkillsSet.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/resumes/{fileId}")
    public ResponseEntity<?> deleteResume(
            @PathVariable String fileId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        try {
            System.out.println("🗑️ Delete request for resume: " + fileId);

            // Find the resume
            Optional<Resume> resumeOpt = resumeRepository.findByFileId(fileId);
            if (resumeOpt.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "error", "Resume not found"));
            }

            Resume resume = resumeOpt.get();

            // Optional: Check if user owns this resume
            if (userId != null && resume.getRecruiterId() != null
                    && !resume.getRecruiterId().equals(userId)) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "error", "Unauthorized to delete this resume"));
            }

            // 1. Delete from S3
            String s3Key = resume.getS3Key();
            if (s3Key != null && !s3Key.isEmpty()) {
                boolean s3Deleted = s3Service.deleteFile(s3Key);
                System.out.println("   S3 deletion: " + (s3Deleted ? "✅ Success" : "⚠️ Failed or skipped"));
            }

            // 2. Delete all match results for this resume
            matchResultRepository.deleteByResumeId(fileId);
            System.out.println("   Match results deleted for resume: " + fileId);

            // 3. Delete the resume from MongoDB
            resumeRepository.deleteByFileId(fileId);
            System.out.println("   Resume deleted from MongoDB: " + fileId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Resume deleted successfully"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

}
