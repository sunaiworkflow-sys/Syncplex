package com.jdres.controller;

import com.jdres.model.JobDescription;
import com.jdres.model.MatchResult;
import com.jdres.model.Resume;
import com.jdres.repository.JobDescriptionRepository;
import com.jdres.repository.MatchResultRepository;
import com.jdres.repository.ResumeRepository;
import com.jdres.service.MatchingService;
import com.jdres.service.SkillExtractorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class JobDescriptionController {

    @Autowired
    private JobDescriptionRepository jobDescriptionRepository;

    @Autowired
    private SkillExtractorService skillExtractorService;

    @Autowired
    private MatchingService matchingService;

    @Autowired
    private MatchResultRepository matchResultRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    /**
     * Create a new Job Description
     */
    @PostMapping("/job-descriptions")
    public ResponseEntity<?> createJobDescription(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        try {
            String jdText = (String) payload.get("jdText");
            String title = (String) payload.get("title");

            if (jdText == null || jdText.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "jdText is required"));
            }

            // 1. Skip Embedding Generation (Using Skill-Based Matching instead!)
            // Embeddings are no longer needed - we use structured skill matching
            List<Double> embedding = Collections.emptyList();

            // 2. Extract structured details using Advanced Recruitment Intelligence
            Map<String, Object> parsedDetails = skillExtractorService.extractJobDescriptionDetails(jdText);

            // 3. Extract fields from new schema
            List<String> mandatorySkills = extractSkillsList(parsedDetails, "mandatory_skills");
            List<String> preferredSkills = extractSkillsList(parsedDetails, "preferred_skills");
            List<String> jdDomains = extractSkillsList(parsedDetails, "jd_domains");
            List<String> businessContextKeywords = extractSkillsList(parsedDetails, "business_context_keywords");
            List<String> toolsPlatforms = extractSkillsList(parsedDetails, "tools_platforms");
            List<String> methodologies = extractSkillsList(parsedDetails, "methodologies");
            List<String> architectureKeywords = extractSkillsList(parsedDetails, "architecture_keywords");
            List<String> deliveryExpectations = extractSkillsList(parsedDetails, "delivery_expectations");
            List<String> riskTypesExpected = extractSkillsList(parsedDetails, "risk_types_expected");
            
            // Extract counts and single values
            int criticalDeliveriesRequired = extractIntValue(parsedDetails, "critical_deliveries_required");
            int riskAreasExpected = extractIntValue(parsedDetails, "risk_areas_expected");
            String jdDeliveryStyle = extractStringValue(parsedDetails, "jd_delivery_style", "hands-on");
            
            // Extract scale requirements map
            @SuppressWarnings("unchecked")
            Map<String, Object> scaleRequirements = parsedDetails.get("scale_requirements") instanceof Map 
                ? (Map<String, Object>) parsedDetails.get("scale_requirements") 
                : new HashMap<>();

            // 4. Save JD with all new fields
            JobDescription jd = new JobDescription();
            String jdId = UUID.randomUUID().toString();
            jd.setJdId(jdId);
            jd.setTitle(title != null ? title : extractStringValue(parsedDetails, "jd_title", "Untitled JD"));
            jd.setText(jdText);
            jd.setSource("manual_upload");
            jd.setCreatedAt(LocalDateTime.now());
            jd.setEmbedding(embedding); // Empty - not used anymore
            jd.setParsedDetails(parsedDetails);
            
            // Legacy fields (for backward compatibility)
            jd.setRequiredSkills(mandatorySkills);
            jd.setPreferredSkills(preferredSkills);
            jd.setSuggestedKeywords(businessContextKeywords); // Map business context to keywords
            jd.setMinExperience(0); // No longer extracted in new schema
            
            // New Advanced Recruitment Intelligence fields
            jd.setJdDomains(jdDomains);
            jd.setBusinessContextKeywords(businessContextKeywords);
            jd.setMandatorySkills(mandatorySkills);
            jd.setToolsPlatforms(toolsPlatforms);
            jd.setMethodologies(methodologies);
            jd.setArchitectureKeywords(architectureKeywords);
            jd.setCriticalDeliveriesRequired(criticalDeliveriesRequired);
            jd.setDeliveryExpectations(deliveryExpectations);
            jd.setRiskAreasExpected(riskAreasExpected);
            jd.setRiskTypesExpected(riskTypesExpected);
            jd.setJdDeliveryStyle(jdDeliveryStyle);
            jd.setScaleRequirements(scaleRequirements);

            // Set recruiterId for user isolation
            if (userId != null && !userId.trim().isEmpty()) {
                jd.setRecruiterId(userId);
            }

            jobDescriptionRepository.save(jd);

            // 5. Trigger matching against all resumes (background/async in production)
            matchingService.matchNewJobDescription(jdId);

            // Build response (Map.of only supports 10 entries, so use HashMap)
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("jdId", jdId);
            response.put("title", jd.getTitle());
            response.put("jdDomains", jdDomains);
            response.put("mandatorySkills", mandatorySkills);
            response.put("preferredSkills", preferredSkills);
            response.put("toolsPlatforms", toolsPlatforms);
            response.put("methodologies", methodologies);
            response.put("deliveryStyle", jdDeliveryStyle);
            response.put("criticalDeliveries", criticalDeliveriesRequired);
            response.put("riskAreas", riskAreasExpected);
            response.put("scaleRequirements", scaleRequirements);
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Trigger matching for a JD manually
     */
    @PostMapping("/job-descriptions/{jdId}/match")
    public ResponseEntity<?> matchJobDescription(@PathVariable String jdId) {
        try {
            matchingService.matchNewJobDescription(jdId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Matching triggered successfully"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Get all job descriptions (filtered by user)
     */
    @GetMapping("/v2/job-descriptions")
    public ResponseEntity<?> getAllJobDescriptions(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        List<JobDescription> jds;

        // Filter by user - REQUIRED for security
        if (userId != null && !userId.trim().isEmpty()) {
            jds = jobDescriptionRepository.findByRecruiterId(userId);
        } else {
            // No userId = No data (security: don't expose all data)
            jds = new java.util.ArrayList<>();
        }

        return ResponseEntity.ok(Map.of("success", true, "jobDescriptions", jds));
    }

    /**
     * Delete a job description by ID
     */
    @DeleteMapping("/job-descriptions/{jdId}")
    public ResponseEntity<?> deleteJobDescription(@PathVariable String jdId) {
        try {
            Optional<JobDescription> jdOpt = jobDescriptionRepository.findByJdId(jdId);
            if (jdOpt.isEmpty()) {
                return ResponseEntity.ok(Map.of("success", false, "error", "Job description not found"));
            }

            // Delete associated match results
            matchResultRepository.deleteByJdId(jdId);

            // Delete the job description
            jobDescriptionRepository.delete(jdOpt.get());

            return ResponseEntity.ok(Map.of("success", true, "message", "Job description deleted"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Update an existing job description (e.g., update skills after extraction)
     */
    @PutMapping("/job-descriptions/{jdId}")
    public ResponseEntity<?> updateJobDescription(
            @PathVariable String jdId,
            @RequestBody Map<String, Object> payload) {
        try {
            Optional<JobDescription> jdOpt = jobDescriptionRepository.findByJdId(jdId);
            if (jdOpt.isEmpty()) {
                return ResponseEntity.ok(Map.of("success", false, "error", "Job description not found"));
            }

            JobDescription jd = jdOpt.get();

            // Update title if provided
            if (payload.containsKey("title")) {
                jd.setTitle((String) payload.get("title"));
            }

            // Update text if provided
            if (payload.containsKey("jdText")) {
                jd.setText((String) payload.get("jdText"));
            } else if (payload.containsKey("text")) {
                jd.setText((String) payload.get("text"));
            }

            // Update required skills if provided
            if (payload.containsKey("requiredSkills")) {
                @SuppressWarnings("unchecked")
                List<String> skills = (List<String>) payload.get("requiredSkills");
                jd.setRequiredSkills(skills);
            }

            // Update suggested keywords if provided
            if (payload.containsKey("suggestedKeywords")) {
                @SuppressWarnings("unchecked")
                List<String> keywords = (List<String>) payload.get("suggestedKeywords");
                jd.setSuggestedKeywords(keywords);
            }

            jobDescriptionRepository.save(jd);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "jdId", jdId,
                    "title", jd.getTitle(),
                    "suggestedKeywords", jd.getSuggestedKeywords() != null ? jd.getSuggestedKeywords() : List.of(),
                    "requiredSkills", jd.getRequiredSkills() != null ? jd.getRequiredSkills() : List.of()));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Save match results for a JD
     */
    @PostMapping("/job-descriptions/{jdId}/matches")
    public ResponseEntity<?> saveMatchesForJD(
            @PathVariable String jdId,
            @RequestBody Map<String, Object> payload) {

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> matches = (List<Map<String, Object>>) payload.get("matches");

            if (matches == null || matches.isEmpty()) {
                return ResponseEntity.ok(Map.of("success", true, "message", "No matches to save"));
            }

            List<MatchResult> savedResults = new ArrayList<>();

            for (Map<String, Object> matchData : matches) {
                String resumeId = (String) matchData.get("resumeId");

                // Try to find existing match result
                MatchResult result = matchResultRepository
                        .findByJdIdAndResumeId(jdId, resumeId)
                        .orElse(new MatchResult());

                result.setJdId(jdId);
                result.setResumeId(resumeId);
                result.setCandidateName((String) matchData.get("candidateName"));

                // Handle scores safely...
                if (matchData.get("matchScore") instanceof Number) {
                    double score = ((Number) matchData.get("matchScore")).doubleValue();
                    // Normalize if > 1.0 (assuming 0-100 input)
                    if (score > 1.0) {
                        score = score / 100.0;
                    }
                    result.setFinalScore(score);
                }

                if (matchData.get("skillMatchScore") instanceof Number) {
                    double score = ((Number) matchData.get("skillMatchScore")).doubleValue();
                    // Normalize if > 1.0
                    if (score > 1.0) {
                        score = score / 100.0;
                    }
                    result.setSkillMatchScore(score);
                }

                // Lists - handle unchecked casts safely
                @SuppressWarnings("unchecked")
                List<String> matchedSkillsCast = matchData.get("matchedSkills") instanceof List
                        ? (List<String>) matchData.get("matchedSkills")
                        : null;
                if (matchedSkillsCast != null)
                    result.setMatchedSkillsList(matchedSkillsCast);

                @SuppressWarnings("unchecked")
                List<String> missingSkillsCast = matchData.get("missingSkills") instanceof List
                        ? (List<String>) matchData.get("missingSkills")
                        : null;
                if (missingSkillsCast != null)
                    result.setMissingSkillsList(missingSkillsCast);

                @SuppressWarnings("unchecked")
                List<String> matchedKeywordsCast = matchData.get("matchedKeywords") instanceof List
                        ? (List<String>) matchData.get("matchedKeywords")
                        : null;
                if (matchedKeywordsCast != null)
                    result.setMatchedKeywordsList(matchedKeywordsCast);

                if (matchData.get("keywordMatchScore") instanceof Number) {
                    double score = ((Number) matchData.get("keywordMatchScore")).doubleValue();
                    if (score > 1.0)
                        score = score / 100.0;
                    result.setKeywordMatchScore(score);
                }

                @SuppressWarnings("unchecked")
                List<String> relevantProjectsCast = matchData.get("relevantProjects") instanceof List
                        ? (List<String>) matchData.get("relevantProjects")
                        : null;
                if (relevantProjectsCast != null)
                    result.setRelevantProjects(relevantProjectsCast);

                // Metadata
                if (matchData.get("candidateExperience") instanceof Number) {
                    result.setCandidateExperience(((Number) matchData.get("candidateExperience")).intValue());
                }

                // Status
                if (matchData.containsKey("status")) {
                    result.setCandidateStatus((String) matchData.get("status"));
                } else if (result.getCandidateStatus() == null) {
                    result.setCandidateStatus("review");
                }

                if (matchData.containsKey("hasGap")) {
                    result.setHasEmploymentGap((Boolean) matchData.get("hasGap"));
                }

                if (matchData.get("gapMonths") instanceof Number) {
                    result.setTotalGapMonths(((Number) matchData.get("gapMonths")).intValue());
                }

                result.setMatchedAt(LocalDateTime.now());
                savedResults.add(matchResultRepository.save(result));
            }

            return ResponseEntity.ok(Map.of("success", true, "savedCount", savedResults.size()));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Get top matches for a JD
     */
    @GetMapping("/job-descriptions/{jdId}/matches")
    public ResponseEntity<?> getMatchesForJD(
            @PathVariable String jdId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        // Security: Verify the JD belongs to the requesting user
        Optional<JobDescription> jdOpt = jobDescriptionRepository.findByJdId(jdId);
        if (jdOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("success", false, "error", "Job description not found"));
        }

        JobDescription jd = jdOpt.get();

        // CRITICAL: Verify ownership
        if (userId == null || !userId.equals(jd.getRecruiterId())) {
            System.out.println("⚠️ Unauthorized access attempt to JD: " + jdId + " by user: " + userId);
            return ResponseEntity.ok(Map.of("success", false, "error", "Unauthorized"));
        }

        List<MatchResult> matches = matchResultRepository.findByJdIdOrderByFinalScoreDesc(jdId);
        List<String> requiredSkills = jd.getRequiredSkills() != null ? jd.getRequiredSkills() : new ArrayList<>();

        // Limit results
        matches = matches.stream().limit(limit).collect(Collectors.toList());

        // Enrich with resume details - ONLY for resumes belonging to THIS user
        List<Map<String, Object>> enrichedResults = new ArrayList<>();
        for (MatchResult match : matches) {
            Optional<Resume> resumeOpt = resumeRepository.findByFileId(match.getResumeId());
            if (resumeOpt.isPresent()) {
                Resume resume = resumeOpt.get();

                // CRITICAL: Only include resumes that belong to this user
                if (resume.getRecruiterId() == null || !resume.getRecruiterId().equals(userId)) {
                    System.out.println("⚠️ Skipping resume " + resume.getFileId() + " - belongs to different user");
                    continue; // Skip this resume
                }

                Map<String, Object> result = new HashMap<>();

                // Name extraction logic - check nested candidate_profile first
                String candidateName = resume.getName(); // Default to filename
                if (resume.getParsedDetails() != null) {
                    // Try candidate_profile.name first (the actual schema structure)
                    Object candidateProfile = resume.getParsedDetails().get("candidate_profile");
                    if (candidateProfile instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> profile = (Map<String, Object>) candidateProfile;
                        Object nameObj = profile.get("name");
                        if (nameObj != null && !nameObj.toString().trim().isEmpty()
                                && !nameObj.toString().equalsIgnoreCase("unknown")) {
                            candidateName = nameObj.toString().trim();
                        }
                    }
                    // Fallback to direct name field
                    if (candidateName.equals(resume.getName())) {
                        Object nameObj = resume.getParsedDetails().get("name");
                        if (nameObj == null)
                            nameObj = resume.getParsedDetails().get("candidate_name");
                        if (nameObj != null && !nameObj.toString().trim().isEmpty()
                                && !nameObj.toString().equalsIgnoreCase("unknown")) {
                            candidateName = nameObj.toString().trim();
                        }
                    }
                }

                // Skill Lists Calculation
                List<String> resumeSkills = resume.getSkills() != null ? resume.getSkills() : new ArrayList<>();
                Set<String> resumeSkillsSet = resumeSkills.stream().map(String::toLowerCase)
                        .collect(Collectors.toSet());

                List<String> matchedSkillsList = new ArrayList<>();
                List<String> missingSkillsList = new ArrayList<>();

                for (String req : requiredSkills) {
                    if (resumeSkillsSet.contains(req.toLowerCase())) {
                        matchedSkillsList.add(req);
                    } else {
                        missingSkillsList.add(req);
                    }
                }

                // Project Relevance Analysis - Show ALL projects with matching tech highlighted
                List<Map<String, Object>> relevantProjects = new ArrayList<>();
                Set<String> requiredSkillsLower = requiredSkills.stream().map(String::toLowerCase)
                        .collect(Collectors.toSet());

                if (resume.getParsedDetails() != null) {
                    Object projectsObj = resume.getParsedDetails().get("projects");
                    if (projectsObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> projects = (List<Map<String, Object>>) projectsObj;

                        for (Map<String, Object> project : projects) {
                            String projectName = project.get("project_name") != null
                                    ? project.get("project_name").toString()
                                    : "";
                            Object techObj = project.get("technologies_used");

                            List<String> allTech = new ArrayList<>();
                            List<String> matchingTechs = new ArrayList<>();
                            if (techObj instanceof List) {
                                @SuppressWarnings("unchecked")
                                List<String> techList = (List<String>) techObj;
                                allTech.addAll(techList);
                                for (String tech : techList) {
                                    if (requiredSkillsLower.contains(tech.toLowerCase())) {
                                        matchingTechs.add(tech);
                                    }
                                }
                            }

                            // Only include project if it has AT LEAST 1 matching technology
                            if (!matchingTechs.isEmpty() && !projectName.isEmpty()) {
                                Map<String, Object> projInfo = new HashMap<>();
                                projInfo.put("name", projectName);
                                projInfo.put("allTech", allTech);
                                projInfo.put("matchingTechs", matchingTechs);
                                relevantProjects.add(projInfo);
                            }
                        }
                    }
                }

                result.put("resumeId", resume.getFileId());
                result.put("candidateName", candidateName);
                result.put("resumeName", resume.getName()); // Keep filename as well
                result.put("s3Url", resume.getS3Url());
                result.put("viewLink", resume.getViewLink());
                result.put("finalScore", Math.round(match.getFinalScore() * 100));
                result.put("semanticSimilarity", Math.round(match.getSemanticSimilarity() * 100));
                result.put("skillMatchScore", Math.round(match.getSkillMatchScore() * 100));
                result.put("experienceScore", Math.round(match.getExperienceScore() * 100));
                result.put("candidateStatus", match.getCandidateStatus());

                // Detailed Lists
                result.put("matchedSkillsList", matchedSkillsList);
                result.put("missingSkillsList", missingSkillsList);
                result.put("matchedKeywordsList",
                        match.getMatchedKeywordsList() != null ? match.getMatchedKeywordsList() : new ArrayList<>());
                result.put("relevantProjects", relevantProjects);
                result.put("allSkills", resumeSkills != null ? resumeSkills : new ArrayList<>());
                // Optimized: Don't send full text
                // result.put("resumeText", resume.getText() != null ? resume.getText() : "");

                // Display strings
                result.put("matchedSkills", matchedSkillsList.size() + "/" + requiredSkills.size());

                result.put("candidateExperience", match.getCandidateExperience() + " years");
                result.put("hasGap", match.isHasEmploymentGap());
                result.put("gapMonths", match.getTotalGapMonths());
                enrichedResults.add(result);
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "jdId", jdId,
                "totalMatches", enrichedResults.size(),
                "matches", enrichedResults));
    }

    @PutMapping("/job-descriptions/{jdId}/resumes/{resumeId}/status")
    public ResponseEntity<?> updateMatchStatus(
            @PathVariable String jdId,
            @PathVariable String resumeId,
            @RequestBody Map<String, String> payload) {
        try {
            Optional<MatchResult> matchOpt = matchResultRepository.findByJdIdAndResumeId(jdId, resumeId);
            if (matchOpt.isPresent()) {
                MatchResult match = matchOpt.get();
                if (payload.containsKey("status")) {
                    match.setCandidateStatus(payload.get("status"));
                    matchResultRepository.save(match);
                    return ResponseEntity.ok(Map.of("success", true, "status", match.getCandidateStatus()));
                }
            } else {
                return ResponseEntity.ok(Map.of("success", false, "error", "Match result not found"));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("success", false));
    }

    @SuppressWarnings("unchecked")
    private List<String> extractSkillsList(Map<String, Object> details, String category) {
        if (details == null)
            return new ArrayList<>();

        // New format: skills are directly at top level (e.g., "technical_skills",
        // "preferred_skills")
        Object skillsObj = details.get(category);
        if (skillsObj instanceof List) {
            return (List<String>) skillsObj;
        }

        // Old format: skills nested under "skills" map
        if (details.containsKey("skills")) {
            Object skillsMapObj = details.get("skills");
            if (skillsMapObj instanceof Map) {
                Map<String, Object> skillsMap = (Map<String, Object>) skillsMapObj;
                Object categoryObj = skillsMap.get(category);
                if (categoryObj instanceof List) {
                    return (List<String>) categoryObj;
                }
            }
        }
        return new ArrayList<>();
    }

    private int extractIntValue(Map<String, Object> details, String key) {
        if (details == null)
            return 0;
        Object value = details.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private String extractStringValue(Map<String, Object> details, String key, String defaultValue) {
        if (details == null)
            return defaultValue;
        Object value = details.get(key);
        if (value instanceof String && !((String) value).isEmpty()) {
            return (String) value;
        }
        return defaultValue;
    }

    private int extractMinExperience(Map<String, Object> details) {
        if (details == null)
            return 0;
        Object exp = details.get("required_experience_years");
        if (exp instanceof Number) {
            return ((Number) exp).intValue();
        }
        return 0;
    }
}
