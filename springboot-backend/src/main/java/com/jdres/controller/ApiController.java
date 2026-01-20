package com.jdres.controller;

import com.jdres.service.FaissClientService;
import com.jdres.service.MatchCalculatorService;
import com.jdres.service.SkillExtractorService;
import com.jdres.service.TextExtractorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API Controller for JD-Resume Matching Engine
 * Provides REST endpoints matching the Express.js routes
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final TextExtractorService textExtractorService;
    private final SkillExtractorService skillExtractorService;
    private final MatchCalculatorService matchCalculatorService;
    private final FaissClientService faissClientService;

    @Autowired
    public ApiController(
            TextExtractorService textExtractorService,
            SkillExtractorService skillExtractorService,
            MatchCalculatorService matchCalculatorService,
            FaissClientService faissClientService) {
        this.textExtractorService = textExtractorService;
        this.skillExtractorService = skillExtractorService;
        this.matchCalculatorService = matchCalculatorService;
        this.faissClientService = faissClientService;
    }

    /**
     * POST /api/extract-text
     * Extract text from uploaded file
     */
    @PostMapping("/extract-text")
    public ResponseEntity<Map<String, Object>> extractText(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (file.isEmpty()) {
                response.put("error", "No file uploaded");
                return ResponseEntity.badRequest().body(response);
            }

            String text = textExtractorService.extractText(file);

            response.put("success", true);
            response.put("filename", file.getOriginalFilename());
            response.put("text", text);
            response.put("textLength", text.length());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Text extraction error: {}", e.getMessage());
            response.put("error", "Failed to extract text from file: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * POST /api/extract-multiple-texts
     * Extract text from multiple uploaded files
     */
    @PostMapping("/extract-multiple-texts")
    public ResponseEntity<Map<String, Object>> extractMultipleTexts(@RequestParam("files") List<MultipartFile> files) {
        Map<String, Object> response = new HashMap<>();

        try {
            log.info("=== MULTIPLE TEXT EXTRACTION REQUEST ===");
            log.info("Files received: {}", files.size());

            if (files.isEmpty()) {
                response.put("error", "No files uploaded");
                return ResponseEntity.badRequest().body(response);
            }

            Map<String, TextExtractorService.ExtractionResult> extractionResults = textExtractorService
                    .extractMultipleTexts(files);

            Map<String, String> successful = new HashMap<>();
            Map<String, String> failed = new HashMap<>();

            extractionResults.forEach((filename, result) -> {
                if (result.success()) {
                    successful.put(filename, result.text());
                } else {
                    failed.put(filename, result.errorMessage());
                }
            });

            response.put("success", true);
            response.put("results", successful);
            response.put("errors", failed);
            response.put("totalFiles", files.size());
            response.put("successCount", (long) successful.size());
            response.put("failedCount", (long) failed.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Multiple text extraction error: {}", e.getMessage());
            response.put("error", "Failed to extract texts from files: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * POST /api/extract-skills
     * Extract skills from text using OpenAI
     */
    @PostMapping("/extract-skills")
    public ResponseEntity<Map<String, Object>> extractSkills(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            String text = (String) request.get("text");
            String type = (String) request.getOrDefault("type", "unknown");

            if (text == null || text.isEmpty()) {
                response.put("error", "Text is required");
                return ResponseEntity.badRequest().body(response);
            }

            List<String> skills;
            String candidateName = null;
            Map<String, Object> details = null;
            int experience = 0;

            // Try to extract structured details first
            try {
                details = skillExtractorService.extractResumeDetails(text);
                skills = skillExtractorService.flattenSkills(details);

                if (details != null && !details.isEmpty()) {
                    if (details.containsKey("candidate_profile")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> profile = (Map<String, Object>) details.get("candidate_profile");
                        if (profile != null) {
                            candidateName = (String) profile.get("name");
                        }
                    }
                    if (details.containsKey("total_experience_years")) {
                        Object exp = details.get("total_experience_years");
                        if (exp instanceof Number) {
                            experience = ((Number) exp).intValue();
                        }
                    }
                }
            } catch (Exception ex) {
                // Fallback to simple extraction
                skills = skillExtractorService.extractSkills(text);

                // Heuristic for name since API failed or verification
                if (candidateName == null) {
                    try {
                        String[] lines = text.split("\n");
                        for (int i = 0; i < Math.min(lines.length, 10); i++) {
                            String line = lines[i].trim();
                            // Look for short lines that contain only letters (and maybe dots/hyphens)
                            // Avoid lines like "Resume", "Curriculum Vitae", Email addresses
                            if (!line.isEmpty() && line.length() > 2 && line.length() < 40 &&
                                    !line.contains("@") &&
                                    !line.toLowerCase().contains("resume") &&
                                    !line.toLowerCase().contains("curriculum") &&
                                    line.matches("^[a-zA-Z\\s.-]+$")) {
                                candidateName = line;
                                break;
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            }

            response.put("success", true);
            response.put("type", type);
            response.put("skills", skills);
            response.put("candidateName", candidateName);
            response.put("candidateExperience", experience);
            response.put("parsedDetails", details);
            response.put("skillCount", skills.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Skill extraction error: {}", e.getMessage());
            response.put("error", "Failed to extract skills: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * POST /api/match-skills
     * Calculate skill-based match percentage
     */
    @PostMapping("/match-skills")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> matchSkills(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            Object jdSkillsObj = request.get("jdSkills");
            List<String> resumeSkills = (List<String>) request.get("resumeSkills");
            boolean weighted = Boolean.TRUE.equals(request.get("weighted"));

            if (jdSkillsObj == null || resumeSkills == null) {
                response.put("error", "jdSkills and resumeSkills are required");
                return ResponseEntity.badRequest().body(response);
            }

            Map<String, Object> matchResult;

            if (weighted && jdSkillsObj instanceof List<?> jdList && !jdList.isEmpty()
                    && jdList.get(0) instanceof Map) {
                // Weighted matching
                List<Map<String, Object>> jdSkillsWithWeights = (List<Map<String, Object>>) jdSkillsObj;
                matchResult = matchCalculatorService.calculateWeightedMatch(jdSkillsWithWeights, resumeSkills);
            } else {
                // Simple skill matching
                List<String> jdSkills = (List<String>) jdSkillsObj;
                matchResult = matchCalculatorService.calculateSkillMatch(jdSkills, resumeSkills);
            }

            response.put("success", true);
            response.putAll(matchResult);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Match calculation error: {}", e.getMessage());
            response.put("error", "Failed to calculate match: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * POST /api/rank-resumes
     * Rank resumes using FAISS-based similarity
     */
    @PostMapping("/rank-resumes")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> rankResumes(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            String jdText = (String) request.get("jdText");
            Map<String, String> resumeData = (Map<String, String>) request.get("resumeData");
            int topK = request.containsKey("topK") ? ((Number) request.get("topK")).intValue() : 10;

            if (jdText == null || resumeData == null) {
                response.put("error", "jdText and resumeData are required");
                return ResponseEntity.badRequest().body(response);
            }

            // Check if text ranking service is healthy
            if (!faissClientService.isHealthy()) {
                response.put("error", "Text ranking service unavailable. Please start the Python microservice.");
                return ResponseEntity.status(503).body(response);
            }

            Map<String, Object> results = faissClientService.rankResumes(jdText, resumeData, topK);

            response.put("success", true);
            response.putAll(results);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Resume ranking error: {}", e.getMessage());
            response.put("error", "Failed to rank resumes: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * GET /api/health
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        boolean rankingHealthy = faissClientService.isHealthy();

        Map<String, Object> services = new HashMap<>();
        services.put("api", "healthy");
        services.put("ranking", rankingHealthy ? "healthy" : "unavailable");

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("services", services);

        return ResponseEntity.ok(response);
    }
}
