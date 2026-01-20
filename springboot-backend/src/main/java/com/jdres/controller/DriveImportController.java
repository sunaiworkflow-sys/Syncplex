package com.jdres.controller;

import com.jdres.model.Resume;
import com.jdres.service.GoogleDriveService;
import com.jdres.repository.ResumeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DriveImportController {

    @Autowired
    private GoogleDriveService googleDriveService;

    @Autowired
    private ResumeRepository resumeRepository; // Injected ResumeRepository

    @PostMapping("/import-drive")
    public ResponseEntity<?> importDrive(@RequestBody Map<String, Object> payload) {
        try {
            String link = (String) payload.get("link");
            Object rawExcludeIds = payload.get("excludeIds");
            List<String> excludeIds = new ArrayList<>();
            if (rawExcludeIds instanceof List<?>) {
                for (Object item : (List<?>) rawExcludeIds) {
                    if (item instanceof String) {
                        excludeIds.add((String) item);
                    }
                }
            }

            List<Resume> newResumes = googleDriveService.importFromLink(link, excludeIds);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("files", newResumes);

            return ResponseEntity.ok(response);
        } catch (Throwable e) {
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getClass().getName() + ": " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/v2/resumes")
    public ResponseEntity<?> getResumes(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            HttpServletRequest request) { // Inject HttpServletRequest

        List<Resume> resumes;

        // Debug: Print all headers
        System.out.println("🔍 Debug: Headers for /api/resumes:");
        java.util.Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String key = headerNames.nextElement();
            System.out.println("  - " + key + ": " + request.getHeader(key));
        }

        // Filter by user - REQUIRED for security
        if (userId != null && !userId.trim().isEmpty()) {
            resumes = resumeRepository.findByRecruiterId(userId);
            System.out.println("📥 Loading resumes for user: " + userId + " - Found: " + resumes.size());
        } else {
            // No userId = No data (security: don't expose all data)
            System.out.println("⚠️ No userId provided for /api/resumes - returning empty list");
            resumes = new java.util.ArrayList<>();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("resumes", resumes);
        return ResponseEntity.ok(response);
    }
}
