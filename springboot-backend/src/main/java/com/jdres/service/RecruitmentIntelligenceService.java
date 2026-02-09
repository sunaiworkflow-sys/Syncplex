package com.jdres.service;

import com.jdres.model.JobDescription;
import com.jdres.model.Resume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Recruitment Intelligence Engine
 *
 * Implements structured scoring based on:
 * - Domain Fit (30%)
 * - Execution Score (30%)
 * - Delivery Risk Score (25%)
 * - Skill Match Score (with normalization)
 * - Scale Bonus (+10/+5)
 * - PMO Risk Penalty (-10/-20)
 */
@Service
public class RecruitmentIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(RecruitmentIntelligenceService.class);

    @Autowired
    private SkillNormalizationService skillNormalizationService;

    /**
     * Extract structured data from Resume
     */
    @SuppressWarnings("unchecked")
    public ResumeExtractionResult extractResumeData(Resume resume) {
        ResumeExtractionResult result = new ResumeExtractionResult();
        Map<String, Object> parsedDetails = resume.getParsedDetails() != null 
                ? resume.getParsedDetails() : new HashMap<>();

        // Extract candidate name
        result.candidateName = extractCandidateName(parsedDetails, resume.getName());

        // Extract domains
        Object domainObj = parsedDetails.get("domain_experience");
        if (domainObj instanceof List) {
            result.domains = ((List<String>) domainObj).stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());
        }

        // Extract skills (handle both flat array and categorized map formats)
        Object skillsObj = parsedDetails.get("skills");
        List<String> allSkills = new ArrayList<>();
        if (skillsObj instanceof List) {
            allSkills.addAll((List<String>) skillsObj);
        } else if (skillsObj instanceof Map) {
            Map<String, Object> skillsMap = (Map<String, Object>) skillsObj;
            for (Object value : skillsMap.values()) {
                if (value instanceof List) {
                    allSkills.addAll((List<String>) value);
                }
            }
        }
        // Also collect skills from legacy format
        if (resume.getSkills() != null && !resume.getSkills().isEmpty()) {
            allSkills.addAll(resume.getSkills());
        }
        // Normalize skills for better matching
        result.skills = skillNormalizationService != null 
                ? skillNormalizationService.normalizeSkills(allSkills)
                : allSkills.stream().map(String::toLowerCase).distinct().collect(Collectors.toList());

        // Extract methodology experience
        Object methObj = parsedDetails.get("methodology_experience");
        if (methObj instanceof List) {
            result.methodologies = ((List<String>) methObj).stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());
        }

        // Extract projects
        Object projectsObj = parsedDetails.get("projects");
        if (projectsObj instanceof List) {
            List<Map<String, Object>> projects = (List<Map<String, Object>>) projectsObj;
            for (Map<String, Object> project : projects) {
                ProjectInfo pi = extractProjectInfo(project);
                result.projects.add(pi);

                // Track max values
                if (pi.teamSize > result.largestTeamSize) {
                    result.largestTeamSize = pi.teamSize;
                }
                if (pi.budgetManaged > result.maxBudgetManaged) {
                    result.maxBudgetManaged = pi.budgetManaged;
                }
                if (pi.durationMonths > 24) {
                    result.multiYearPrograms++;
                }
                if (pi.productionLaunch) {
                    result.criticalDeliveriesTotal++;
                }
                if (!pi.riskEventsHandled.isEmpty()) {
                    result.highRiskDeliveries++;
                    result.riskAreasManaged += pi.riskEventsHandled.size();
                }
            }
        }

        // Extract career summary if present
        Object careerSummary = parsedDetails.get("career_summary");
        if (careerSummary instanceof Map) {
            Map<String, Object> summary = (Map<String, Object>) careerSummary;
            if (result.criticalDeliveriesTotal == 0) {
                Number launches = (Number) summary.get("total_production_launches");
                if (launches != null) result.criticalDeliveriesTotal = launches.intValue();
            }
            if (result.largestTeamSize == 0) {
                Number team = (Number) summary.get("largest_team_managed");
                if (team != null) result.largestTeamSize = team.intValue();
            }
            if (result.maxBudgetManaged == 0) {
                Number budget = (Number) summary.get("largest_budget_managed");
                if (budget != null) result.maxBudgetManaged = budget.doubleValue() * 1000; // Stored in K
            }
            Boolean enterprise = (Boolean) summary.get("enterprise_experience");
            if (Boolean.TRUE.equals(enterprise)) {
                result.enterpriseScale = true;
            }
            Boolean multiYear = (Boolean) summary.get("multi_year_program_experience");
            if (Boolean.TRUE.equals(multiYear) && result.multiYearPrograms == 0) {
                result.multiYearPrograms = 1;
            }
        }

        // Calculate hands-on vs PMO ratio
        int handsOnCount = 0;
        int pmoCount = 0;
        for (ProjectInfo pi : result.projects) {
            if ("hands-on".equalsIgnoreCase(pi.deliveryType)) {
                handsOnCount++;
            } else if ("governance".equalsIgnoreCase(pi.deliveryType)) {
                pmoCount++;
            } else {
                // hybrid counts as 0.5 each
                handsOnCount++;
            }
        }
        int totalProjects = result.projects.size();
        result.handsOnRatio = totalProjects > 0 ? (double) handsOnCount / totalProjects : 0.5;
        result.pmoRatio = totalProjects > 0 ? (double) pmoCount / totalProjects : 0.0;

        // Enterprise scale (large budget or large team)
        if (!result.enterpriseScale) {
            result.enterpriseScale = result.maxBudgetManaged >= 5_000_000 || result.largestTeamSize >= 25;
        }

        // Identified risk areas from projects
        result.identifiedRiskAreas = countIdentifiedRiskAreas(result.projects);

        return result;
    }

    /**
     * Extract structured requirements from Job Description
     * Uses new model fields first, then parsedDetails as fallback
     */
    @SuppressWarnings("unchecked")
    public JDExtractionResult extractJDData(JobDescription jd) {
        JDExtractionResult result = new JDExtractionResult();
        Map<String, Object> parsedDetails = jd.getParsedDetails() != null 
                ? jd.getParsedDetails() : new HashMap<>();

        // Extract domains - try model field first, then parsedDetails
        if (jd.getJdDomains() != null && !jd.getJdDomains().isEmpty()) {
            result.jdDomains = jd.getJdDomains().stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());
        } else {
            Object domainObj = parsedDetails.get("jd_domains");
            if (domainObj == null) domainObj = parsedDetails.get("domain");
            if (domainObj instanceof List) {
                result.jdDomains = ((List<String>) domainObj).stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.toList());
            }
        }

        // Extract mandatory skills - normalize for matching
        List<String> mandatorySkills = extractStringList(jd.getMandatorySkills(), 
                parsedDetails.get("mandatory_skills"), jd.getRequiredSkills());
        result.mandatorySkills = skillNormalizationService != null 
                ? skillNormalizationService.normalizeSkills(mandatorySkills) : mandatorySkills;

        // Extract preferred skills
        List<String> preferredSkills = extractStringList(jd.getPreferredSkills(), 
                parsedDetails.get("preferred_skills"), null);
        result.preferredSkills = skillNormalizationService != null 
                ? skillNormalizationService.normalizeSkills(preferredSkills) : preferredSkills;

        // Extract tools/platforms
        List<String> tools = extractStringList(jd.getToolsPlatforms(), 
                parsedDetails.get("tools_platforms"), null);
        result.tools = skillNormalizationService != null 
                ? skillNormalizationService.normalizeSkills(tools) : tools;

        // Extract methodologies
        List<String> methodologies = extractStringList(jd.getMethodologies(), 
                parsedDetails.get("methodologies"), null);
        result.methodologies = methodologies.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        // Critical deliveries - try model field first
        if (jd.getCriticalDeliveriesRequired() > 0) {
            result.criticalDeliveriesRequired = jd.getCriticalDeliveriesRequired();
        } else {
            Number criticalDeliveries = (Number) parsedDetails.get("critical_deliveries_required");
            if (criticalDeliveries != null) {
                result.criticalDeliveriesRequired = criticalDeliveries.intValue();
            }
        }

        // Risk areas expected - try model field first
        if (jd.getRiskAreasExpected() > 0) {
            result.riskAreasExpected = jd.getRiskAreasExpected();
        } else {
            Number riskAreas = (Number) parsedDetails.get("risk_areas_expected");
            if (riskAreas != null) {
                result.riskAreasExpected = riskAreas.intValue();
            }
        }

        // Scale requirements - try model field first
        Map<String, Object> scaleMap = jd.getScaleRequirements();
        if (scaleMap == null) {
            scaleMap = (Map<String, Object>) parsedDetails.get("scale_requirements");
        }
        if (scaleMap != null) {
            result.scaleRequirements.enterprise = Boolean.TRUE.equals(scaleMap.get("enterprise_scale")) 
                    || Boolean.TRUE.equals(scaleMap.get("enterprise"));
            result.scaleRequirements.multiYear = Boolean.TRUE.equals(scaleMap.get("multi_year_program")) 
                    || Boolean.TRUE.equals(scaleMap.get("multi_year"));
            result.scaleRequirements.largeBudget = Boolean.TRUE.equals(scaleMap.get("large_budget_expected")) 
                    || Boolean.TRUE.equals(scaleMap.get("large_budget"));
        }

        // Store JD delivery style for PMO penalty context
        String deliveryStyle = jd.getJdDeliveryStyle();
        if (deliveryStyle == null || deliveryStyle.isEmpty()) {
            deliveryStyle = (String) parsedDetails.get("jd_delivery_style");
        }
        result.jdDeliveryStyle = deliveryStyle != null ? deliveryStyle : "hands-on";

        // Infer from JD text if not explicitly set
        if (result.criticalDeliveriesRequired == 0) {
            result.criticalDeliveriesRequired = inferCriticalDeliveries(jd);
        }
        if (result.riskAreasExpected == 0) {
            result.riskAreasExpected = inferRiskAreas(jd);
        }
        if (result.jdDomains.isEmpty()) {
            result.jdDomains = inferDomains(jd);
        }

        return result;
    }

    /**
     * Compute Recruitment Intelligence Score
     */
    public RecruitmentScoreResult computeScore(ResumeExtractionResult resume, JDExtractionResult jd) {
        RecruitmentScoreResult result = new RecruitmentScoreResult();
        result.candidateName = resume.candidateName;

        // ============================================
        // Skill Match Score (15% - NEW)
        // Uses weighted skill matching with normalization
        // ============================================
        double skillMatchScore = 0;
        List<String> matchedSkills = new ArrayList<>();
        if (skillNormalizationService != null && !jd.mandatorySkills.isEmpty()) {
            SkillNormalizationService.WeightedSkillMatchResult skillResult = 
                    skillNormalizationService.calculateWeightedMatch(
                            jd.mandatorySkills, jd.preferredSkills, jd.tools, jd.methodologies,
                            resume.skills);
            skillMatchScore = skillResult.weightedScore;
            matchedSkills.addAll(skillResult.matchedMandatory);
            matchedSkills.addAll(skillResult.matchedPreferred);
            result.matchedMandatorySkills = skillResult.matchedMandatory;
            result.skillMatchScore = skillMatchScore;
        } else {
            // Fallback: simple string matching
            Set<String> resumeSkillsLower = resume.skills.stream()
                    .map(String::toLowerCase).collect(Collectors.toSet());
            for (String mandSkill : jd.mandatorySkills) {
                if (resumeSkillsLower.contains(mandSkill.toLowerCase())) {
                    matchedSkills.add(mandSkill);
                }
            }
            skillMatchScore = jd.mandatorySkills.isEmpty() ? 50.0 
                    : ((double) matchedSkills.size() / jd.mandatorySkills.size()) * 100;
            result.skillMatchScore = skillMatchScore;
        }

        // ============================================
        // Domain Fit Score (25%)
        // (JD_Domain_Areas_Matched / Total_JD_Domain_Areas) × 100
        // ============================================
        List<String> matchedDomains = new ArrayList<>();
        if (!jd.jdDomains.isEmpty()) {
            for (String jdDomain : jd.jdDomains) {
                if (resume.domains.stream().anyMatch(d -> 
                        d.contains(jdDomain) || jdDomain.contains(d))) {
                    matchedDomains.add(jdDomain);
                }
            }
            result.domainFitScore = ((double) matchedDomains.size() / jd.jdDomains.size()) * 100;
        } else {
            result.domainFitScore = 50.0; // Neutral if no domains specified
        }
        result.matchedDomains = matchedDomains;

        // ============================================
        // Execution Score (25%)
        // (High_Risk_Deliveries_Handled / Total_Critical_Deliveries) × 100
        // ============================================
        int totalCriticalDeliveries = Math.max(resume.criticalDeliveriesTotal, jd.criticalDeliveriesRequired);
        if (totalCriticalDeliveries > 0) {
            result.executionScore = ((double) resume.highRiskDeliveries / totalCriticalDeliveries) * 100;
        } else {
            result.executionScore = 50.0; // Neutral if no deliveries tracked
        }

        // ============================================
        // Delivery Risk Score (20%)
        // (Risk_Areas_Managed / Total_Identified_Risk_Areas) × 100
        // ============================================
        int totalIdentifiedRiskAreas = Math.max(resume.identifiedRiskAreas, jd.riskAreasExpected);
        if (totalIdentifiedRiskAreas > 0) {
            result.deliveryRiskScore = ((double) resume.riskAreasManaged / totalIdentifiedRiskAreas) * 100;
        } else {
            result.deliveryRiskScore = 50.0; // Neutral if no risk areas identified
        }

        // ============================================
        // Methodology Match Bonus (+5 max)
        // ============================================
        int methodologyMatchBonus = 0;
        if (!jd.methodologies.isEmpty() && !resume.methodologies.isEmpty()) {
            Set<String> resumeMethodologies = new HashSet<>(resume.methodologies);
            long matchCount = jd.methodologies.stream()
                    .filter(m -> resumeMethodologies.contains(m.toLowerCase()))
                    .count();
            if (matchCount >= jd.methodologies.size() * 0.5) { // At least 50% match
                methodologyMatchBonus = 5;
            } else if (matchCount > 0) {
                methodologyMatchBonus = 2;
            }
        }

        // ============================================
        // PMO Risk Penalty
        // 0   if Hands_On_Ratio ≥ 0.65
        // -10 if 0.40 ≤ Hands_On_Ratio < 0.65
        // -20 if Hands_On_Ratio < 0.40
        // ============================================
        if (resume.handsOnRatio >= 0.65) {
            result.pmoPenalty = 0;
        } else if (resume.handsOnRatio >= 0.40) {
            result.pmoPenalty = -10;
        } else {
            result.pmoPenalty = -20;
        }

        // ============================================
        // Scale Bonus
        // +10 if Budget ≥ 10M OR Multi_Year_Programs ≥ 1
        // +5  if Team_Size ≥ 15 OR Enterprise_Scale = true
        // 0   otherwise
        // ============================================
        if (resume.maxBudgetManaged >= 10_000_000 || resume.multiYearPrograms >= 1) {
            result.scaleBonus = 10;
        } else if (resume.largestTeamSize >= 15 || resume.enterpriseScale) {
            result.scaleBonus = 5;
        } else {
            result.scaleBonus = 0;
        }

        // Build scale indicators
        List<String> scaleIndicators = new ArrayList<>();
        if (resume.maxBudgetManaged >= 10_000_000) {
            scaleIndicators.add("Budget ≥ $10M");
        }
        if (resume.multiYearPrograms >= 1) {
            scaleIndicators.add("Multi-year programs: " + resume.multiYearPrograms);
        }
        if (resume.largestTeamSize >= 15) {
            scaleIndicators.add("Team size: " + resume.largestTeamSize);
        }
        if (resume.enterpriseScale) {
            scaleIndicators.add("Enterprise scale experience");
        }
        result.scaleIndicators = scaleIndicators;

        // ============================================
        // Final Score (Updated weights)
        // (Skill_Match_Score × 0.15) +
        // (Domain_Fit_Score × 0.25) +
        // (Execution_Score × 0.25) +
        // (Delivery_Risk_Score × 0.20) +
        // (Scale_Bonus) +
        // (Methodology_Bonus) +
        // (PMO_Risk_Penalty)
        // ============================================
        double finalScore = 
                (result.skillMatchScore * 0.15) +
                (result.domainFitScore * 0.25) +
                (result.executionScore * 0.25) +
                (result.deliveryRiskScore * 0.20) +
                result.scaleBonus +
                methodologyMatchBonus +
                result.pmoPenalty;

        // Clamp between 0 and 100
        result.finalScore = Math.max(0, Math.min(100, finalScore));

        // ============================================
        // Rating Tier
        // ============================================
        if (result.finalScore >= 90) {
            result.rating = "Top Choice";
        } else if (result.finalScore >= 80) {
            result.rating = "Strong Primary";
        } else if (result.finalScore >= 70) {
            result.rating = "Strong Secondary";
        } else if (result.finalScore >= 60) {
            result.rating = "Backup";
        } else {
            result.rating = "Not Recommended";
        }

        // Build evidence
        result.keyProjects = resume.projects.stream()
                .filter(p -> p.productionLaunch || !p.riskEventsHandled.isEmpty())
                .map(p -> p.projectName)
                .collect(Collectors.toList());
        
        result.riskEvents = resume.projects.stream()
                .flatMap(p -> p.riskEventsHandled.stream())
                .distinct()
                .collect(Collectors.toList());

        log.info("📊 Recruitment Score for {}: Skills={}%, Domain={}%, Execution={}%, Risk={}%, Bonus={}, Penalty={}, Final={} ({})",
                resume.candidateName, 
                String.format("%.1f", result.skillMatchScore),
                String.format("%.1f", result.domainFitScore), 
                String.format("%.1f", result.executionScore),
                String.format("%.1f", result.deliveryRiskScore), 
                result.scaleBonus + methodologyMatchBonus, 
                result.pmoPenalty,
                String.format("%.1f", result.finalScore), result.rating);

        return result;
    }

    // ============================================
    // Helper Methods
    // ============================================

    @SuppressWarnings("unchecked")
    private ProjectInfo extractProjectInfo(Map<String, Object> project) {
        ProjectInfo pi = new ProjectInfo();
        
        pi.projectName = getStringValue(project, "project_name");
        pi.domain = getStringValue(project, "domain");
        pi.role = getStringValue(project, "role");
        
        Object techObj = project.get("technologies_used");
        if (techObj instanceof List) {
            pi.techStack = (List<String>) techObj;
        }
        
        pi.teamSize = getIntValue(project, "team_size");
        pi.budgetManaged = getDoubleValue(project, "budget_managed");
        pi.durationMonths = getIntValue(project, "duration_months");
        pi.productionLaunch = getBoolValue(project, "production_launch");
        
        Object riskObj = project.get("risk_events_handled");
        if (riskObj instanceof List) {
            pi.riskEventsHandled = (List<String>) riskObj;
        }
        
        pi.deliveryType = getStringValue(project, "delivery_type");
        if (pi.deliveryType.isEmpty()) {
            // Infer from role
            String roleLower = pi.role.toLowerCase();
            if (roleLower.contains("pmo") || roleLower.contains("governance")) {
                pi.deliveryType = "governance";
            } else if (roleLower.contains("lead") || roleLower.contains("developer") || roleLower.contains("engineer")) {
                pi.deliveryType = "hands-on";
            } else {
                pi.deliveryType = "hybrid";
            }
        }
        
        return pi;
    }

    @SuppressWarnings("unchecked")
    private String extractCandidateName(Map<String, Object> parsedDetails, String defaultName) {
        if (parsedDetails == null) return defaultName;

        Object profileObj = parsedDetails.get("candidate_profile");
        if (profileObj instanceof Map) {
            Map<String, Object> profile = (Map<String, Object>) profileObj;
            Object nameObj = profile.get("name");
            if (nameObj != null && !nameObj.toString().trim().isEmpty()) {
                return nameObj.toString().trim();
            }
        }
        return defaultName;
    }

    private int countIdentifiedRiskAreas(List<ProjectInfo> projects) {
        Set<String> uniqueRisks = new HashSet<>();
        for (ProjectInfo pi : projects) {
            uniqueRisks.addAll(pi.riskEventsHandled.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toList()));
        }
        return uniqueRisks.size();
    }

    private int inferCriticalDeliveries(JobDescription jd) {
        String text = jd.getText() != null ? jd.getText().toLowerCase() : "";
        int count = 0;
        if (text.contains("production launch")) count++;
        if (text.contains("migration")) count++;
        if (text.contains("go-live")) count++;
        if (text.contains("critical")) count++;
        if (text.contains("enterprise")) count++;
        return Math.max(count, 3); // Minimum expectation
    }

    private int inferRiskAreas(JobDescription jd) {
        String text = jd.getText() != null ? jd.getText().toLowerCase() : "";
        int count = 0;
        if (text.contains("risk")) count++;
        if (text.contains("security")) count++;
        if (text.contains("compliance")) count++;
        if (text.contains("disaster")) count++;
        if (text.contains("backup")) count++;
        return Math.max(count, 2); // Minimum expectation
    }

    @SuppressWarnings("unchecked")
    private List<String> inferDomains(JobDescription jd) {
        List<String> domains = new ArrayList<>();
        String text = jd.getText() != null ? jd.getText().toLowerCase() : "";
        
        if (text.contains("fintech") || text.contains("financial") || text.contains("banking")) {
            domains.add("fintech");
        }
        if (text.contains("healthcare") || text.contains("medical") || text.contains("health")) {
            domains.add("healthcare");
        }
        if (text.contains("saas") || text.contains("software as a service")) {
            domains.add("saas");
        }
        if (text.contains("e-commerce") || text.contains("ecommerce") || text.contains("retail")) {
            domains.add("e-commerce");
        }
        if (text.contains("logistics") || text.contains("supply chain")) {
            domains.add("logistics");
        }
        
        return domains;
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }

    /**
     * Extract skills list from multiple sources with fallback
     */
    @SuppressWarnings("unchecked")
    private List<String> extractStringList(List<String> primary, Object parsedValue, List<String> fallback) {
        // Try primary (model field) first
        if (primary != null && !primary.isEmpty()) {
            return new ArrayList<>(primary);
        }
        // Try parsed value (from parsedDetails)
        if (parsedValue instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) parsedValue) {
                if (item != null) result.add(item.toString());
            }
            if (!result.isEmpty()) return result;
        }
        // Use fallback
        if (fallback != null && !fallback.isEmpty()) {
            return new ArrayList<>(fallback);
        }
        return new ArrayList<>();
    }

    private int getIntValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return 0;
    }

    private double getDoubleValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return 0.0;
    }

    private boolean getBoolValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return Boolean.TRUE.equals(val);
    }

    // ============================================
    // Data Classes
    // ============================================

    public static class ResumeExtractionResult {
        public String candidateName = "";
        public List<String> domains = new ArrayList<>();
        public List<String> skills = new ArrayList<>();  // Normalized skills
        public List<String> methodologies = new ArrayList<>();  // agile, scrum, safe, etc.
        public List<ProjectInfo> projects = new ArrayList<>();
        public double handsOnRatio = 0.5;
        public double pmoRatio = 0.0;
        public double maxBudgetManaged = 0;
        public int largestTeamSize = 0;
        public int multiYearPrograms = 0;
        public boolean enterpriseScale = false;
        public int highRiskDeliveries = 0;
        public int criticalDeliveriesTotal = 0;
        public int riskAreasManaged = 0;
        public int identifiedRiskAreas = 0;
    }

    public static class ProjectInfo {
        public String projectName = "";
        public String domain = "";
        public String role = "";
        public List<String> techStack = new ArrayList<>();
        public int teamSize = 0;
        public double budgetManaged = 0;
        public int durationMonths = 0;
        public boolean productionLaunch = false;
        public List<String> riskEventsHandled = new ArrayList<>();
        public String deliveryType = "hybrid";
    }

    public static class JDExtractionResult {
        public List<String> jdDomains = new ArrayList<>();
        public List<String> mandatorySkills = new ArrayList<>();
        public List<String> preferredSkills = new ArrayList<>();
        public List<String> tools = new ArrayList<>();
        public List<String> methodologies = new ArrayList<>();
        public int criticalDeliveriesRequired = 0;
        public int riskAreasExpected = 0;
        public ScaleRequirements scaleRequirements = new ScaleRequirements();
        public String jdDeliveryStyle = "hands-on"; // hands-on, hybrid, governance
    }

    public static class ScaleRequirements {
        public boolean enterprise = false;
        public boolean multiYear = false;
        public boolean largeBudget = false;
    }

    public static class RecruitmentScoreResult {
        public String candidateName = "";
        public double skillMatchScore = 0;  // NEW: Skill matching score
        public double domainFitScore = 0;
        public double executionScore = 0;
        public double deliveryRiskScore = 0;
        public int scaleBonus = 0;
        public int pmoPenalty = 0;
        public double finalScore = 0;
        public String rating = "";
        
        // Evidence
        public List<String> matchedMandatorySkills = new ArrayList<>();  // NEW
        public List<String> matchedDomains = new ArrayList<>();
        public List<String> keyProjects = new ArrayList<>();
        public List<String> riskEvents = new ArrayList<>();
        public List<String> scaleIndicators = new ArrayList<>();
        
        /**
         * Convert to strict JSON output format as specified
         */
        public Map<String, Object> toOutputJson() {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("candidate_name", candidateName);
            
            Map<String, Object> scores = new LinkedHashMap<>();
            scores.put("skill_match", Math.round(skillMatchScore * 100.0) / 100.0);  // NEW
            scores.put("domain_fit", Math.round(domainFitScore * 100.0) / 100.0);
            scores.put("execution", Math.round(executionScore * 100.0) / 100.0);
            scores.put("delivery_risk", Math.round(deliveryRiskScore * 100.0) / 100.0);
            scores.put("scale_bonus", scaleBonus);
            scores.put("pmo_penalty", pmoPenalty);
            scores.put("final_score", Math.round(finalScore * 100.0) / 100.0);
            scores.put("rating", rating);
            output.put("scores", scores);
            
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("matched_skills", matchedMandatorySkills);  // NEW
            evidence.put("matched_domains", matchedDomains);
            evidence.put("key_projects", keyProjects);
            evidence.put("risk_events", riskEvents);
            evidence.put("scale_indicators", scaleIndicators);
            output.put("evidence", evidence);
            
            return output;
        }
    }
}
