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
    private final TokenUsageTracker tokenUsageTracker;

    @Value("${openai.api-key}")
    private String openaiApiKey;

    @Value("${openai.model}")
    private String openaiModel;

    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    public SkillExtractorService(TokenUsageTracker tokenUsageTracker) {
        this.tokenUsageTracker = tokenUsageTracker;
        
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
        if (details == null || details.isEmpty()) {
            return new ArrayList<>();
        }
        return flattenSkills(details);
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

                // Log token usage and track it
                JsonNode usage = root.path("usage");
                if (!usage.isMissingNode()) {
                    int promptTokens = usage.path("prompt_tokens").asInt();
                    int completionTokens = usage.path("completion_tokens").asInt();
                    int totalTokens = usage.path("total_tokens").asInt();
                    log.info("📊 Token Usage: {} prompt + {} completion = {} total tokens",
                            promptTokens, completionTokens, totalTokens);
                    
                    // Track token usage
                    tokenUsageTracker.recordUsage(promptTokens, completionTokens, totalTokens);
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
     * Log comprehensive extracted Resume details for debugging
     */
    @SuppressWarnings("unchecked")
    private void logExtractedDetails(Map<String, Object> details, String type) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("\n╔══════════════════════════════════════════════════════════════╗\n");
            sb.append("║           📄 RESUME EXTRACTION - CANDIDATE INTELLIGENCE      ║\n");
            sb.append("╠══════════════════════════════════════════════════════════════╣\n");

            // Candidate Profile
            if (details.containsKey("candidate_profile")) {
                Map<String, Object> profile = (Map<String, Object>) details.get("candidate_profile");
                sb.append("║ 👤 Name: ").append(profile.getOrDefault("name", "N/A")).append("\n");
                sb.append("║ 📧 Email: ").append(profile.getOrDefault("email", "N/A")).append("\n");
                sb.append("║ 📍 Location: ").append(profile.getOrDefault("location", "N/A")).append("\n");
            }

            // Experience
            Object expYears = details.getOrDefault("total_experience_years", 0);
            sb.append("║ 💼 Total Experience: ").append(expYears).append(" years\n");

            // Domain Experience (NEW)
            List<String> domains = (List<String>) details.getOrDefault("domain_experience", List.of());
            if (!domains.isEmpty()) {
                sb.append("║ 🏢 Domain Experience: ").append(String.join(", ", domains)).append("\n");
            }

            // Methodology Experience (NEW)
            List<String> methodologies = (List<String>) details.getOrDefault("methodology_experience", List.of());
            if (!methodologies.isEmpty()) {
                sb.append("║ 📊 Methodologies: ").append(String.join(", ", methodologies)).append("\n");
            }

            sb.append("╠══════════════════════════════════════════════════════════════╣\n");
            sb.append("║                         SKILLS                               ║\n");
            sb.append("╠══════════════════════════════════════════════════════════════╣\n");

            // Skills - handle both array and map formats
            if (details.containsKey("skills")) {
                Object skillsObj = details.get("skills");
                List<String> allSkills = new ArrayList<>();
                if (skillsObj instanceof List) {
                    allSkills.addAll((List<String>) skillsObj);
                } else if (skillsObj instanceof Map) {
                    Map<String, Object> skillsMap = (Map<String, Object>) skillsObj;
                    // Show categorized skills
                    for (Map.Entry<String, Object> entry : skillsMap.entrySet()) {
                        if (entry.getValue() instanceof List) {
                            List<String> catSkills = (List<String>) entry.getValue();
                            if (!catSkills.isEmpty()) {
                                sb.append("║    ").append(capitalize(entry.getKey())).append(": ")
                                        .append(String.join(", ", catSkills.subList(0, Math.min(8, catSkills.size()))));
                                if (catSkills.size() > 8) sb.append("...");
                                sb.append("\n");
                                allSkills.addAll(catSkills);
                            }
                        }
                    }
                }
                sb.append("║ 🛠️ Total Skills: ").append(allSkills.size()).append("\n");
            }

            sb.append("╠══════════════════════════════════════════════════════════════╣\n");
            sb.append("║                    WORK EXPERIENCE                           ║\n");
            sb.append("╠══════════════════════════════════════════════════════════════╣\n");

            // Work Experience - detailed with delivery type
            if (details.containsKey("work_experience")) {
                List<Map<String, Object>> workExp = (List<Map<String, Object>>) details.get("work_experience");
                sb.append("║ 🏢 Total Positions: ").append(workExp.size()).append("\n");
                int count = 0;
                for (Map<String, Object> exp : workExp) {
                    if (count >= 3) {
                        sb.append("║    ... and ").append(workExp.size() - 3).append(" more positions\n");
                        break;
                    }
                    String company = String.valueOf(exp.getOrDefault("company", "Unknown"));
                    String title = String.valueOf(exp.getOrDefault("title", "Unknown"));
                    String deliveryType = String.valueOf(exp.getOrDefault("delivery_type", "hands-on"));
                    int teamSize = ((Number) exp.getOrDefault("team_size", 0)).intValue();
                    
                    sb.append("║    • ").append(title).append(" @ ").append(company);
                    sb.append(" [").append(deliveryType).append("]");
                    if (teamSize > 0) sb.append(" 👥").append(teamSize);
                    sb.append("\n");
                    count++;
                }
            } else {
                sb.append("║ 🏢 Work Experience: None extracted\n");
            }

            sb.append("╠══════════════════════════════════════════════════════════════╣\n");
            sb.append("║                       PROJECTS                               ║\n");
            sb.append("╠══════════════════════════════════════════════════════════════╣\n");

            // Projects - detailed with delivery intelligence
            if (details.containsKey("projects")) {
                List<Map<String, Object>> projects = (List<Map<String, Object>>) details.get("projects");
                sb.append("║ 📁 Total Projects: ").append(projects.size()).append("\n");
                int count = 0;
                int totalRiskEvents = 0;
                int productionLaunches = 0;
                for (Map<String, Object> project : projects) {
                    // Count risk events and launches
                    List<String> riskEvents = (List<String>) project.getOrDefault("risk_events_handled", List.of());
                    totalRiskEvents += riskEvents.size();
                    if (Boolean.TRUE.equals(project.get("was_production_launch"))) productionLaunches++;
                    
                    if (count >= 4) {
                        sb.append("║    ... and ").append(projects.size() - 4).append(" more projects\n");
                        break;
                    }
                    String name = String.valueOf(project.getOrDefault("project_name", "Unnamed"));
                    String deliveryType = String.valueOf(project.getOrDefault("delivery_type", "hands-on"));
                    int teamSize = ((Number) project.getOrDefault("team_size", 0)).intValue();
                    int budget = ((Number) project.getOrDefault("budget_managed", 0)).intValue();
                    boolean isProdLaunch = Boolean.TRUE.equals(project.get("was_production_launch"));
                    
                    sb.append("║    • ").append(name);
                    sb.append(" [").append(deliveryType).append("]");
                    if (isProdLaunch) sb.append(" 🚀");
                    if (teamSize > 0) sb.append(" 👥").append(teamSize);
                    if (budget > 0) sb.append(" 💰$").append(budget).append("K");
                    sb.append("\n");
                    
                    if (!riskEvents.isEmpty()) {
                        sb.append("║      ⚠️ Risk: ").append(String.join(", ", riskEvents)).append("\n");
                    }
                    count++;
                }
                sb.append("║ 📊 Summary: ").append(productionLaunches).append(" prod launches, ")
                        .append(totalRiskEvents).append(" risk events handled\n");
            } else {
                sb.append("║ 📁 Projects: None extracted\n");
            }

            sb.append("╠══════════════════════════════════════════════════════════════╣\n");
            sb.append("║                   CAREER SUMMARY                             ║\n");
            sb.append("╠══════════════════════════════════════════════════════════════╣\n");

            // Career Summary (NEW)
            if (details.containsKey("career_summary")) {
                Map<String, Object> summary = (Map<String, Object>) details.get("career_summary");
                int prodLaunches = ((Number) summary.getOrDefault("total_production_launches", 0)).intValue();
                int largestTeam = ((Number) summary.getOrDefault("largest_team_managed", 0)).intValue();
                int largestBudget = ((Number) summary.getOrDefault("largest_budget_managed", 0)).intValue();
                boolean enterprise = Boolean.TRUE.equals(summary.get("enterprise_experience"));
                boolean multiYear = Boolean.TRUE.equals(summary.get("multi_year_program_experience"));
                
                sb.append("║ 🚀 Production Launches: ").append(prodLaunches).append("\n");
                sb.append("║ 👥 Largest Team: ").append(largestTeam).append("\n");
                if (largestBudget > 0) sb.append("║ 💰 Largest Budget: $").append(largestBudget).append("K\n");
                sb.append("║ 🏢 Enterprise Experience: ").append(enterprise ? "Yes" : "No").append("\n");
                sb.append("║ 📅 Multi-Year Programs: ").append(multiYear ? "Yes" : "No").append("\n");
            }

            // Education
            sb.append("╠══════════════════════════════════════════════════════════════╣\n");
            sb.append("║                      EDUCATION                               ║\n");
            sb.append("╠══════════════════════════════════════════════════════════════╣\n");

            if (details.containsKey("education")) {
                List<Map<String, Object>> edu = (List<Map<String, Object>>) details.get("education");
                sb.append("║ 🎓 Total Degrees: ").append(edu.size()).append("\n");
                for (Map<String, Object> e : edu) {
                    String degree = String.valueOf(e.getOrDefault("degree", "Unknown"));
                    String institution = String.valueOf(e.getOrDefault("institution", "Unknown"));
                    Object year = e.get("year");
                    sb.append("║    • ").append(degree).append(" - ").append(institution);
                    if (year != null && !year.toString().equals("0")) sb.append(" (").append(year).append(")");
                    sb.append("\n");
                }
            } else {
                sb.append("║ 🎓 Education: None extracted\n");
            }

            // Certifications
            List<String> certs = (List<String>) details.getOrDefault("certifications", List.of());
            if (!certs.isEmpty()) {
                sb.append("║ 📜 Certifications: ").append(String.join(", ", certs)).append("\n");
            }

            sb.append("╚══════════════════════════════════════════════════════════════╝");
            log.info(sb.toString());
        } catch (Exception e) {
            log.warn("Could not log extracted details: {}", e.getMessage());
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    /**
     * Log comprehensive JD extraction results
     */
    @SuppressWarnings("unchecked")
    private void logJDExtraction(Map<String, Object> details) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("\n╔══════════════════════════════════════════════════════════════╗\n");
            sb.append("║           📋 JD EXTRACTION - RECRUITMENT INTELLIGENCE        ║\n");
            sb.append("╠══════════════════════════════════════════════════════════════╣\n");

            // Title
            sb.append("║ 📌 Title: ").append(details.getOrDefault("jd_title", "N/A")).append("\n");

            // Domains
            List<String> domains = (List<String>) details.getOrDefault("jd_domains", List.of());
            sb.append("║ 🏢 Domains (").append(domains.size()).append("): ")
                    .append(domains.isEmpty() ? "None detected" : String.join(", ", domains)).append("\n");

            // Business Context
            List<String> contextKeywords = (List<String>) details.getOrDefault("business_context_keywords", List.of());
            if (!contextKeywords.isEmpty()) {
                sb.append("║ 🎯 Business Context: ").append(String.join(", ", contextKeywords)).append("\n");
            }

            sb.append("╠══════════════════════════════════════════════════════════════╣\n");
            sb.append("║                         SKILLS                               ║\n");
            sb.append("╠══════════════════════════════════════════════════════════════╣\n");

            // Mandatory Skills
            List<String> mandatorySkills = (List<String>) details.getOrDefault("mandatory_skills", List.of());
            sb.append("║ ✅ Mandatory Skills (").append(mandatorySkills.size()).append("): ")
                    .append(mandatorySkills.isEmpty() ? "None" : String.join(", ", mandatorySkills)).append("\n");

            // Preferred Skills
            List<String> preferredSkills = (List<String>) details.getOrDefault("preferred_skills", List.of());
            sb.append("║ ➕ Preferred Skills (").append(preferredSkills.size()).append("): ")
                    .append(preferredSkills.isEmpty() ? "None" : String.join(", ", preferredSkills)).append("\n");

            // Tools & Platforms
            List<String> tools = (List<String>) details.getOrDefault("tools_platforms", List.of());
            sb.append("║ 🔧 Tools/Platforms (").append(tools.size()).append("): ")
                    .append(tools.isEmpty() ? "None" : String.join(", ", tools)).append("\n");

            // Methodologies
            List<String> methodologies = (List<String>) details.getOrDefault("methodologies", List.of());
            sb.append("║ 📊 Methodologies (").append(methodologies.size()).append("): ")
                    .append(methodologies.isEmpty() ? "None" : String.join(", ", methodologies)).append("\n");

            // Architecture Keywords
            List<String> archKeywords = (List<String>) details.getOrDefault("architecture_keywords", List.of());
            if (!archKeywords.isEmpty()) {
                sb.append("║ 🏗️ Architecture: ").append(String.join(", ", archKeywords)).append("\n");
            }

            sb.append("╠══════════════════════════════════════════════════════════════╣\n");
            sb.append("║                    DELIVERY & RISK                           ║\n");
            sb.append("╠══════════════════════════════════════════════════════════════╣\n");

            // Delivery Style
            sb.append("║ 🎨 Delivery Style: ").append(details.getOrDefault("jd_delivery_style", "hands-on")).append("\n");

            // Critical Deliveries
            int criticalDeliveries = ((Number) details.getOrDefault("critical_deliveries_required", 0)).intValue();
            sb.append("║ 🚀 Critical Deliveries Required: ").append(criticalDeliveries).append("\n");

            // Delivery Expectations
            List<String> deliveryExp = (List<String>) details.getOrDefault("delivery_expectations", List.of());
            if (!deliveryExp.isEmpty()) {
                sb.append("║ 📦 Delivery Expectations: ").append(String.join(", ", deliveryExp)).append("\n");
            }

            // Risk Areas
            int riskAreas = ((Number) details.getOrDefault("risk_areas_expected", 0)).intValue();
            sb.append("║ ⚠️ Risk Areas Expected: ").append(riskAreas).append("\n");

            // Risk Types
            List<String> riskTypes = (List<String>) details.getOrDefault("risk_types_expected", List.of());
            if (!riskTypes.isEmpty()) {
                sb.append("║ 🛡️ Risk Types: ").append(String.join(", ", riskTypes)).append("\n");
            }

            sb.append("╠══════════════════════════════════════════════════════════════╣\n");
            sb.append("║                    SCALE REQUIREMENTS                        ║\n");
            sb.append("╠══════════════════════════════════════════════════════════════╣\n");

            // Scale Requirements
            Object scaleObj = details.get("scale_requirements");
            if (scaleObj instanceof Map) {
                Map<String, Object> scale = (Map<String, Object>) scaleObj;
                sb.append("║ 💰 Large Budget Expected: ").append(Boolean.TRUE.equals(scale.get("large_budget_expected")) ? "Yes" : "No").append("\n");
                sb.append("║ 🏢 Enterprise Scale: ").append(Boolean.TRUE.equals(scale.get("enterprise_scale")) ? "Yes" : "No").append("\n");
                sb.append("║ 📅 Multi-Year Program: ").append(Boolean.TRUE.equals(scale.get("multi_year_program")) ? "Yes" : "No").append("\n");
                int teamSize = ((Number) scale.getOrDefault("team_size_expected", 0)).intValue();
                if (teamSize > 0) {
                    sb.append("║ 👥 Team Size Expected: ").append(teamSize).append("\n");
                }
            }

            sb.append("╚══════════════════════════════════════════════════════════════╝");
            log.info(sb.toString());
        } catch (Exception e) {
            log.warn("Could not log JD extraction details: {}", e.getMessage());
        }
    }

    /**
     * Get ENHANCED JSON schema for resume extraction - Recruitment Intelligence aligned
     */
    private String getResumeSchema() {
        // Enhanced schema - captures fields needed for Recruitment Intelligence matching
        return "{" +
                "\"candidate_profile\":{\"name\":\"\",\"email\":\"\",\"phone\":\"\",\"location\":\"\",\"linkedin\":\"\"}," +
                "\"total_experience_years\":0," +
                "\"domain_experience\":[]," +  // fintech, healthcare, ecommerce, saas, etc.
                "\"skills\":{" +
                    "\"technical\":[],\"tools\":[],\"frameworks\":[],\"databases\":[],\"cloud\":[],\"languages\":[]" +
                "}," +
                "\"methodology_experience\":[]," + // agile, scrum, safe, waterfall, devops
                "\"work_experience\":[{" +
                    "\"company\":\"\"," +
                    "\"title\":\"\"," +
                    "\"start\":\"YYYY-MM\"," +
                    "\"end\":\"YYYY-MM\"," +
                    "\"domain\":\"\"," +  // Industry domain for this role
                    "\"tech\":[]," +
                    "\"delivery_type\":\"hands-on\"," +  // hands-on, hybrid, governance
                    "\"team_size\":0" +  // Size of team managed/worked with
                "}]," +
                "\"projects\":[{" +
                    "\"project_name\":\"\"," +
                    "\"domain\":\"\"," +
                    "\"technologies_used\":[]," +
                    "\"delivery_type\":\"hands-on\"," +  // hands-on, hybrid, governance
                    "\"team_size\":0," +
                    "\"budget_managed\":0," +  // in thousands USD (0 if not applicable)
                    "\"duration_months\":0," +
                    "\"was_production_launch\":false," +  // Did they launch to production?
                    "\"risk_events_handled\":[]" +  // migration, security, compliance, go-live, incident
                "}]," +
                "\"certifications\":[]," +
                "\"education\":[{\"degree\":\"\",\"field\":\"\",\"institution\":\"\",\"year\":0}]," +
                "\"career_summary\":{" +
                    "\"total_production_launches\":0," +
                    "\"largest_team_managed\":0," +
                    "\"largest_budget_managed\":0," +
                    "\"enterprise_experience\":false," +
                    "\"multi_year_program_experience\":false" +
                "}" +
                "}";
    }

    /**
     * Build ENHANCED prompt for resume extraction with Recruitment Intelligence context
     */
    private String buildResumePrompt(String schema, String text) {
        return "You are an Advanced Recruitment Resume Intelligence Engine.\n\n" +
                "Your task is to analyze the given RESUME text and extract structured candidate data.\n\n" +
                "You must:\n" +
                "- Output ONLY valid JSON.\n" +
                "- Do NOT include explanations, markdown, or extra text.\n" +
                "- Extract ALL skills mentioned anywhere in the resume.\n" +
                "- Infer domain_experience from companies/projects (fintech, healthcare, ecommerce, saas, telecom, banking, insurance, logistics, manufacturing, retail, government, education, media).\n" +
                "- For each project, determine delivery_type: 'hands-on' (wrote code, built), 'hybrid' (led small team + contributed), 'governance' (managed/supervised only).\n" +
                "- Identify risk_events_handled from keywords: migration, go-live, security, compliance, integration, incident, disaster-recovery.\n" +
                "- Calculate career_summary based on all work experience and projects.\n\n" +
                "Schema:\n" + schema + "\n\n" +
                "Rules:\n" +
                "- All arrays must exist even if empty.\n" +
                "- Booleans must be true or false.\n" +
                "- Numbers must be integers (budget in thousands USD).\n" +
                "- delivery_type must be one of: hands-on, hybrid, governance.\n\n" +
                "RESUME:\n" + text;
    }

    /**
     * Extract structured details from Job Description text using OpenAI API
     * Uses Advanced Recruitment Job Description Intelligence schema
     */
    public Map<String, Object> extractJobDescriptionDetails(String text) {
        try {
            // Advanced Recruitment Intelligence JD Schema
            String schema = "{" +
                    "\"jd_title\":\"\"," +
                    "\"jd_domains\":[]," +
                    "\"business_context_keywords\":[]," +
                    "\"mandatory_skills\":[]," +
                    "\"preferred_skills\":[]," +
                    "\"tools_platforms\":[]," +
                    "\"methodologies\":[]," +
                    "\"architecture_keywords\":[]," +
                    "\"critical_deliveries_required\":0," +
                    "\"delivery_expectations\":[]," +
                    "\"risk_areas_expected\":0," +
                    "\"risk_types_expected\":[]," +
                    "\"jd_delivery_style\":\"hands-on\"," +
                    "\"scale_requirements\":{" +
                    "\"large_budget_expected\":false," +
                    "\"enterprise_scale\":false," +
                    "\"multi_year_program\":false," +
                    "\"team_size_expected\":0" +
                    "}" +
                    "}";

            String prompt = "You are an Advanced Recruitment Job Description Intelligence Engine.\n\n" +
                    "Your task is to analyze the given JOB DESCRIPTION text and extract structured benchmark data.\n\n" +
                    "You must:\n" +
                    "- Output ONLY valid JSON.\n" +
                    "- Do NOT include explanations, markdown, or extra text.\n" +
                    "- Use only explicit or strongly implied evidence.\n" +
                    "- If data is missing, use null, false, or 0.\n" +
                    "- Do NOT guess numbers.\n" +
                    "- Normalize skills and domains to lowercase.\n" +
                    "- Remove duplicates.\n\n" +
                    "=====================================================\n" +
                    "OUTPUT SCHEMA (STRICT JSON)\n" +
                    "=====================================================\n\n" +
                    schema + "\n\n" +
                    "=====================================================\n" +
                    "FIELD EXTRACTION RULES\n" +
                    "=====================================================\n\n" +
                    "jd_domains:\n" +
                    "- Extract industries or sectors such as fintech, healthcare, ecommerce, saas, telecom, edtech, government, ai/ml.\n\n" +
                    "business_context_keywords:\n" +
                    "- Extract phrases like digital transformation, platform modernization, enterprise rollout, automation, optimization.\n\n" +
                    "mandatory_skills:\n" +
                    "- Skills explicitly required or marked as \"must have\".\n\n" +
                    "preferred_skills:\n" +
                    "- Skills marked as \"good to have\", \"plus\", or optional.\n\n" +
                    "tools_platforms:\n" +
                    "- Tools, platforms, and software such as jira, docker, kubernetes, terraform, kafka, gitlab.\n\n" +
                    "methodologies:\n" +
                    "- agile, scrum, safe, waterfall, itil, devops practices.\n\n" +
                    "architecture_keywords:\n" +
                    "- microservices, distributed systems, serverless, event-driven, monolith-to-microservices.\n\n" +
                    "critical_deliveries_required:\n" +
                    "- Count explicit large initiatives such as migration, modernization, global rollout, platform rewrite.\n\n" +
                    "delivery_expectations:\n" +
                    "- Extract phrases indicating ownership like end-to-end delivery, go-live ownership, release leadership.\n\n" +
                    "risk_areas_expected:\n" +
                    "- Count mentions of high-risk responsibilities.\n\n" +
                    "risk_types_expected:\n" +
                    "- Extract risk types such as security compliance, disaster recovery, high availability, data migration, regulatory audits.\n\n" +
                    "jd_delivery_style:\n" +
                    "- \"hands-on\" if implementation or architecture is emphasized.\n" +
                    "- \"hybrid\" if both leadership and implementation appear.\n" +
                    "- \"governance\" if reporting, steering, audit, or oversight dominate.\n\n" +
                    "scale_requirements:\n" +
                    "- large_budget_expected = true if multimillion budgets mentioned.\n" +
                    "- enterprise_scale = true if global, enterprise, or cross-region systems appear.\n" +
                    "- multi_year_program = true if multi-year roadmap or program mentioned.\n" +
                    "- team_size_expected = number if team size explicitly mentioned, else 0.\n\n" +
                    "=====================================================\n" +
                    "OUTPUT CONSTRAINTS\n" +
                    "=====================================================\n\n" +
                    "- Output STRICT JSON only.\n" +
                    "- No comments.\n" +
                    "- No trailing commas.\n" +
                    "- All arrays must exist even if empty.\n" +
                    "- Booleans must be true or false.\n" +
                    "- Numbers must be integers.\n\n" +
                    "JOB DESCRIPTION:\n" + text;

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
                logJDExtraction(result);
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
                
                // Track token usage for JD extraction
                JsonNode usage = root.path("usage");
                if (!usage.isMissingNode()) {
                    int promptTokens = usage.path("prompt_tokens").asInt();
                    int completionTokens = usage.path("completion_tokens").asInt();
                    int totalTokens = usage.path("total_tokens").asInt();
                    log.info("📊 JD Token Usage: {} prompt + {} completion = {} total",
                            promptTokens, completionTokens, totalTokens);
                    tokenUsageTracker.recordUsage(promptTokens, completionTokens, totalTokens);
                }
                
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
