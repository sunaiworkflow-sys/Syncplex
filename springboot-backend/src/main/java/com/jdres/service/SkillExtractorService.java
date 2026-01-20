package com.jdres.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.YearMonth;

/**
 * Skill Extractor Service
 * Extracts skills from text using OpenAI API with fallback to keyword matching
 */
@Service
public class SkillExtractorService {

    private static final Logger log = LoggerFactory.getLogger(SkillExtractorService.class);

    private final WebClient openaiWebClient;
    private final WebClient geminiWebClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key}")
    private String openaiApiKey;

    @Value("${openai.model}")
    private String openaiModel;

    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    private static final List<String> COMMON_SKILLS = Arrays.asList(
            "JavaScript", "Python", "Java", "React", "Node.js", "SQL", "MongoDB",
            "AWS", "Docker", "Kubernetes", "Git", "REST API", "GraphQL", "HTML", "CSS",
            "TypeScript", "Angular", "Vue.js", "Express", "Spring", "Django", "Flask",
            "PostgreSQL", "MySQL", "Redis", "Elasticsearch", "Jenkins", "CI/CD",
            "Microservices", "API", "JSON", "XML", "Linux", "Windows", "MacOS",
            "Agile", "Scrum", "DevOps", "Cloud", "Azure", "GCP", "Terraform");

    public SkillExtractorService() {
        // OpenAI WebClient
        this.openaiWebClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();

        // Gemini WebClient
        this.geminiWebClient = WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();

        this.objectMapper = new ObjectMapper();
    }

    /**
     * Extract skills from text using OpenAI API
     * 
     * @param text - Text to extract skills from
     * @return List of extracted skills
     */
    public List<String> extractSkills(String text) {
        Map<String, Object> details = extractResumeDetails(text);
        // If details are empty (API failed), fallback to keyword extraction directly
        // here
        if (details == null || details.isEmpty()) {
            return mockSkillExtraction(text);
        }

        List<String> flattened = flattenSkills(details);
        if (flattened.isEmpty()) {
            return mockSkillExtraction(text);
        }
        return flattened;
    }

    /**
     * Extract structured details from resume text using OpenAI API (with Gemini
     * fallback)
     */
    public Map<String, Object> extractResumeDetails(String text) {
        String schema = getResumeSchema();
        String prompt = buildResumePrompt(schema, text);

        // Try OpenAI first
        Map<String, Object> result = tryOpenAI(prompt);

        // If OpenAI failed, try Gemini as fallback
        if (result == null || result.isEmpty()) {
            if (geminiApiKey != null && !geminiApiKey.isEmpty() && !geminiApiKey.equals("your_gemini_api_key_here")) {
                log.info("🔄 OpenAI failed, trying Gemini fallback...");
                result = tryGemini(prompt);
            }
        }

        // Log extracted details
        if (result != null && !result.isEmpty()) {
            logExtractedDetails(result, "RESUME");
            calculateEmploymentGaps(result);
            return result;
        }

        log.warn("⚠️ Both OpenAI and Gemini failed to extract resume details");
        return new HashMap<>();
    }

    /**
     * Try extracting with OpenAI API
     */
    private Map<String, Object> tryOpenAI(String prompt) {
        try {
            log.info("📤 Calling OpenAI API ({})...", openaiModel);

            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", openaiModel);
            requestBody.put("messages", List.of(message));
            requestBody.put("max_tokens", 1500); // Enough for full resume extraction
            requestBody.put("temperature", 0.0);

            String response = openaiWebClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);

                // Log token usage
                JsonNode usage = root.path("usage");
                if (!usage.isMissingNode()) {
                    int promptTokens = usage.path("prompt_tokens").asInt();
                    int completionTokens = usage.path("completion_tokens").asInt();
                    int totalTokens = usage.path("total_tokens").asInt();
                    log.info("📊 Token Usage: {} prompt + {} completion = {} total tokens",
                            promptTokens, completionTokens, totalTokens);
                }

                String content = root.path("choices").get(0).path("message").path("content").asText().trim();
                content = content.replaceAll("```json\\s*|```\\s*", "");

                Map<String, Object> parsedDetails = objectMapper.readValue(content,
                        new TypeReference<Map<String, Object>>() {
                        });

                log.info("✅ OpenAI extraction successful");
                return parsedDetails;
            }
        } catch (Exception e) {
            log.error("❌ OpenAI API error: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Try extracting with Gemini API (fallback)
     */
    private Map<String, Object> tryGemini(String prompt) {
        try {
            log.info("📤 Calling Gemini API...");

            // Gemini API request format
            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(part));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", List.of(content));

            // Generation config for JSON output
            Map<String, Object> genConfig = new HashMap<>();
            genConfig.put("temperature", 0.0);
            genConfig.put("maxOutputTokens", 4096);
            requestBody.put("generationConfig", genConfig);

            String response = geminiWebClient.post()
                    .uri("/models/gemini-2.5-flash-lite:generateContent?key=" + geminiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                String textContent = root.path("candidates").get(0)
                        .path("content").path("parts").get(0).path("text").asText().trim();
                textContent = textContent.replaceAll("```json\\s*|```\\s*", "");

                Map<String, Object> parsedDetails = objectMapper.readValue(textContent,
                        new TypeReference<Map<String, Object>>() {
                        });

                log.info("✅ Gemini extraction successful");
                return parsedDetails;
            }
        } catch (Exception e) {
            // Sanitize error message to remove API key
            String safeMessage = e.getMessage() != null
                    ? e.getMessage().replaceAll("key=[^&\\s]+", "key=***")
                    : "Unknown error";
            log.error("❌ Gemini API error: {}", safeMessage);
        }
        return null;
    }

    /**
     * Log extracted details for debugging
     */
    @SuppressWarnings("unchecked")
    private void logExtractedDetails(Map<String, Object> details, String type) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("\n╔══════════════════════════════════════════════════════════════╗\n");
            sb.append("║           EXTRACTED ").append(type).append(" DETAILS                        ║\n");
            sb.append("╠══════════════════════════════════════════════════════════════╣\n");

            // Candidate Profile
            if (details.containsKey("candidate_profile")) {
                Map<String, Object> profile = (Map<String, Object>) details.get("candidate_profile");
                sb.append("║ 👤 Name: ").append(profile.getOrDefault("name", "N/A")).append("\n");
                sb.append("║ 📧 Email: ").append(profile.getOrDefault("email", "N/A")).append("\n");
                sb.append("║ 📍 Location: ").append(profile.getOrDefault("location", "N/A")).append("\n");
            }

            // Experience
            sb.append("║ 💼 Total Experience: ").append(details.getOrDefault("total_experience_years", 0))
                    .append(" years\n");

            // Skills - handle both array and map formats
            if (details.containsKey("skills")) {
                Object skillsObj = details.get("skills");
                List<String> allSkills = new ArrayList<>();
                if (skillsObj instanceof List) {
                    allSkills.addAll((List<String>) skillsObj);
                } else if (skillsObj instanceof Map) {
                    for (Object value : ((Map<String, Object>) skillsObj).values()) {
                        if (value instanceof List)
                            allSkills.addAll((List<String>) value);
                    }
                }
                sb.append("║ 🛠️ Skills (").append(allSkills.size()).append("): ")
                        .append(String.join(", ", allSkills.subList(0, Math.min(10, allSkills.size()))))
                        .append(allSkills.size() > 10 ? "..." : "").append("\n");
            }

            // Projects
            if (details.containsKey("projects")) {
                List<Map<String, Object>> projects = (List<Map<String, Object>>) details.get("projects");
                sb.append("║ 📁 Projects (").append(projects.size()).append("): ");
                if (!projects.isEmpty()) {
                    List<String> projectNames = new ArrayList<>();
                    for (Map<String, Object> p : projects) {
                        projectNames.add(String.valueOf(p.getOrDefault("project_name", "Unnamed")));
                    }
                    sb.append(String.join(", ", projectNames));
                }
                sb.append("\n");
            }

            // Work Experience
            if (details.containsKey("work_experience")) {
                List<Map<String, Object>> workExp = (List<Map<String, Object>>) details.get("work_experience");
                sb.append("║ 🏢 Work Experience (").append(workExp.size()).append(" positions)\n");
            }

            // Education
            if (details.containsKey("education")) {
                List<Map<String, Object>> edu = (List<Map<String, Object>>) details.get("education");
                sb.append("║ 🎓 Education (").append(edu.size()).append(" entries)\n");
            }

            sb.append("╚══════════════════════════════════════════════════════════════╝");
            log.info(sb.toString());
        } catch (Exception e) {
            log.warn("Could not log extracted details: {}", e.getMessage());
        }
    }

    /**
     * Get MINIMAL JSON schema for resume extraction (optimized for low token usage)
     */
    private String getResumeSchema() {
        // Compact schema - only essential fields for matching
        return "{\"candidate_profile\":{\"name\":\"\",\"email\":\"\",\"location\":\"\"}," +
                "\"skills\":[],\"total_experience_years\":0," +
                "\"work_experience\":[{\"company\":\"\",\"title\":\"\",\"start\":\"YYYY-MM\",\"end\":\"YYYY-MM\",\"tech\":[]}],"
                +
                "\"projects\":[{\"project_name\":\"\",\"technologies_used\":[]}]," +
                "\"education\":[{\"degree\":\"\",\"institution\":\"\",\"year\":0}]}";
    }

    /**
     * Build MINIMAL prompt for resume extraction (optimized for low token usage)
     */
    private String buildResumePrompt(String schema, String text) {
        // Send full resume text - don't truncate to ensure all skills/projects are
        // captured
        return "Extract resume data as JSON. Schema: " + schema +
                "\nRules: Output ONLY valid JSON. No markdown. No explanations. " +
                "Use empty values if data missing.\nResume:\n" + text;
    }

    /**
     * Extract structured details from Job Description text using OpenAI API
     */
    public Map<String, Object> extractJobDescriptionDetails(String text) {
        try {
            // Compact schema separating technical skills from preferred/general skills
            String schema = "{" +
                    "\"job_title\":\"\"," +
                    "\"required_experience_years\":0," +
                    "\"technical_skills\":[]," +
                    "\"preferred_skills\":[]" +
                    "}";

            String prompt = "Extract job requirements as JSON. Schema: " + schema +
                    "\n\nCLASSIFICATION RULES (STRICT):" +
                    "\n- technical_skills: ONLY include specific technologies like: Python, Java, JavaScript, React, Node.js, AWS, Docker, PostgreSQL, MongoDB, Kubernetes, Git, SQL, etc."
                    +
                    "\n- preferred_skills: Include EVERYTHING else like: Data Analysis, Machine Learning concepts, Communication, Agile, Problem Solving, Team collaboration, English, Leadership, etc."
                    +
                    "\n\nIMPORTANT: 'Data Analysis', 'Predictive AI', 'Machine Learning', 'AI' go in preferred_skills, NOT technical_skills!"
                    +
                    "\n\nOutput ONLY valid JSON. No markdown. No explanations." +
                    "\n\nJob Description:\n" + text;
            // Try OpenAI first
            Map<String, Object> result = tryOpenAIForJD(prompt);

            // If OpenAI failed, try Gemini fallback
            if (result == null || result.isEmpty()) {
                if (geminiApiKey != null && !geminiApiKey.isEmpty()
                        && !geminiApiKey.equals("your_gemini_api_key_here")) {
                    log.info("🔄 OpenAI failed for JD, trying Gemini fallback...");
                    result = tryGemini(prompt);
                }
            }

            if (result != null && !result.isEmpty()) {
                log.info("✅ JD extraction successful - technical_skills: {}", result.get("technical_skills"));
                return result;
            }
        } catch (Exception e) {
            log.error("JD extraction error: {}", e.getMessage());
        }
        return new HashMap<>();
    }

    /**
     * Try extracting JD with OpenAI API
     */
    private Map<String, Object> tryOpenAIForJD(String prompt) {
        try {
            log.info("📤 Calling OpenAI API for JD ({})...", openaiModel);

            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", openaiModel);
            requestBody.put("messages", List.of(message));
            requestBody.put("max_tokens", 500); // JD extraction needs less tokens
            requestBody.put("temperature", 0.1);

            String response = openaiWebClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                String content = root.path("choices").get(0).path("message").path("content").asText().trim();
                content = content.replaceAll("```json\\s*|```\\s*", "");
                log.info("✅ OpenAI JD extraction successful");
                return objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
                });
            }
        } catch (Exception e) {
            log.error("❌ OpenAI API error for JD: {}", e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public List<String> flattenSkills(Map<String, Object> details) {
        List<String> allSkills = new ArrayList<>();
        if (details == null || !details.containsKey("skills"))
            return allSkills;

        try {
            Object skillsObj = details.get("skills");
            // New format: skills is directly a list
            if (skillsObj instanceof List) {
                allSkills.addAll((List<String>) skillsObj);
            }
            // Old format: skills is a map with categories
            else if (skillsObj instanceof Map) {
                ((Map<String, Object>) skillsObj).values().forEach(val -> {
                    if (val instanceof List)
                        allSkills.addAll((List<String>) val);
                });
            }
        } catch (Exception e) {
            log.warn("Error flattening skills: {}", e.getMessage());
        }

        return allSkills.stream().distinct().toList();
    }

    /**
     * Analyze skill gap between JD and Resume skills
     * 
     * @param jdSkills     - Required skills from job description
     * @param resumeSkills - Skills from candidate resume
     * @return Skill gap analysis result
     */
    public Map<String, Object> analyzeSkillGap(List<String> jdSkills, List<String> resumeSkills) {
        try {
            String prompt = String.format(
                    "Analyze skill gap between:\nRequired: %s\nCandidate: %s\n\nReturn JSON with matched, missing, and analysis:\n{\"matchedSkills\": [], \"missingSkills\": [], \"gapAnalysis\": \"text\"}",
                    String.join(", ", jdSkills),
                    String.join(", ", resumeSkills));

            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", openaiModel);
            requestBody.put("messages", List.of(message));
            requestBody.put("max_tokens", 300);
            requestBody.put("temperature", 0.3);

            String response = openaiWebClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                String content = root.path("choices").get(0).path("message").path("content").asText().trim();

                // Remove markdown code blocks if present
                content = content.replaceAll("```json\\s*|```\\s*", "");

                return objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
                });
            }
        } catch (Exception e) {
            log.error("OpenAI API error: {}", e.getMessage());
        }

        // Fallback logic
        return fallbackSkillGapAnalysis(jdSkills, resumeSkills);
    }

    /**
     * Parse "YYYY-MM" or "PRESENT" or invalid
     */
    private YearMonth parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty())
            return null;
        if (dateStr.toUpperCase().contains("PRESENT") || dateStr.toUpperCase().contains("CURRENT")
                || dateStr.toUpperCase().contains("NOW")) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM"));
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void calculateEmploymentGaps(Map<String, Object> resumeData) {
        try {
            // 1. Get Work Experience
            List<Map<String, Object>> workExperience = (List<Map<String, Object>>) resumeData.get("work_experience");
            if (workExperience == null)
                workExperience = new ArrayList<>();

            // Sort by start_date
            workExperience.sort((a, b) -> {
                YearMonth da = parseDate((String) a.get("start_date"));
                YearMonth db = parseDate((String) b.get("start_date"));
                if (da == null && db == null)
                    return 0;
                if (da == null)
                    return 1;
                if (db == null)
                    return -1;
                return da.compareTo(db);
            });

            long totalGapMonths = 0;
            List<Map<String, Object>> gapDetails = new ArrayList<>();

            // 2. Check POST_COLLEGE gap
            List<Map<String, Object>> education = (List<Map<String, Object>>) resumeData.get("education");
            if (education != null && !education.isEmpty() && !workExperience.isEmpty()) {
                // Find latest graduation date
                YearMonth latestGrad = null;
                for (Map<String, Object> edu : education) {
                    String endDateStr = (String) edu.get("end_date");
                    // Sometimes graduation_year is number
                    if (endDateStr == null && edu.get("graduation_year") != null) {
                        endDateStr = edu.get("graduation_year") + "-06"; // Assume June
                    }
                    YearMonth end = parseDate(endDateStr);
                    if (end != null) {
                        if (latestGrad == null || end.isAfter(latestGrad)) {
                            latestGrad = end;
                        }
                    }
                }

                YearMonth firstJobStart = parseDate((String) workExperience.get(0).get("start_date"));

                if (latestGrad != null && firstJobStart != null) {
                    long months = ChronoUnit.MONTHS.between(latestGrad, firstJobStart);
                    if (months >= 6) {
                        Map<String, Object> gap = new HashMap<>();
                        gap.put("gap_type", "POST_COLLEGE");
                        gap.put("start_date", latestGrad.toString());
                        gap.put("end_date", firstJobStart.toString());
                        gap.put("duration_months", months);
                        gapDetails.add(gap);
                        totalGapMonths += months;
                    }
                }
            }

            // 3. Check BETWEEN_JOBS gaps
            for (int i = 0; i < workExperience.size() - 1; i++) {
                Map<String, Object> currentJob = workExperience.get(i);
                Map<String, Object> nextJob = workExperience.get(i + 1);

                YearMonth currentEnd = parseDate((String) currentJob.get("end_date"));
                YearMonth nextStart = parseDate((String) nextJob.get("start_date"));

                if (currentEnd != null && nextStart != null) {
                    long months = ChronoUnit.MONTHS.between(currentEnd, nextStart);
                    if (months >= 6) {
                        Map<String, Object> gap = new HashMap<>();
                        gap.put("gap_type", "BETWEEN_JOBS");
                        gap.put("start_date", currentEnd.toString());
                        gap.put("end_date", nextStart.toString());
                        gap.put("duration_months", months);
                        gapDetails.add(gap);
                        totalGapMonths += months;
                    }
                }
            }

            Map<String, Object> gaps = new HashMap<>();
            gaps.put("has_gap", totalGapMonths > 0);
            gaps.put("total_gap_months", totalGapMonths);
            gaps.put("gap_details", gapDetails);

            resumeData.put("employment_gaps", gaps);

        } catch (Exception e) {
            log.error("Error calculating employment gaps: " + e.getMessage());
        }
    }

    /**
     * Fallback skill extraction using keyword matching
     */
    private List<String> mockSkillExtraction(String text) {
        String textLower = text.toLowerCase();
        List<String> foundSkills = new ArrayList<>();

        for (String skill : COMMON_SKILLS) {
            String skillLower = skill.toLowerCase();
            if (textLower.contains(skillLower) ||
                    textLower.contains(skillLower.replace(".", "")) ||
                    textLower.contains(skillLower.replace(" ", ""))) {
                foundSkills.add(skill);
            }
        }

        // If no skills are found via keyword matching, return an empty list
        if (foundSkills.isEmpty()) {
            log.warn("Fallback keyword skill extraction found no skills; returning empty list.");
            return Collections.emptyList();
        }

        return foundSkills;
    }

    /**
     * Fallback skill gap analysis
     */
    private Map<String, Object> fallbackSkillGapAnalysis(List<String> jdSkills, List<String> resumeSkills) {
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String jdSkill : jdSkills) {
            boolean found = resumeSkills.stream()
                    .anyMatch(resumeSkill -> resumeSkill.toLowerCase().contains(jdSkill.toLowerCase()));
            if (found) {
                matched.add(jdSkill);
            } else {
                missing.add(jdSkill);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("matchedSkills", matched);
        result.put("missingSkills", missing);
        result.put("gapAnalysis", "Fallback analysis");
        return result;
    }
}
