package com.jdres.service;

import com.jdres.model.JobDescription;
import com.jdres.model.MatchResult;
import com.jdres.model.Resume;
import com.jdres.repository.JobDescriptionRepository;
import com.jdres.repository.MatchResultRepository;
import com.jdres.repository.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Skill-Based Matching Service (No Embeddings Required!)
 * 
 * Matching Formula:
 * Final Score = (Skill Score × 40%) + (Experience Score × 25%) +
 * (Project Score × 20%) + (Certification Score × 10%) +
 * (Domain Match × 5%) - (Gap Penalty)
 */
@Service
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JobDescriptionRepository jobDescriptionRepository;

    @Autowired
    private MatchResultRepository matchResultRepository;

    // Weights for final score calculation (Total = 100%)
    private static final double WEIGHT_SKILL = 0.40; // 40% - Skills are most important
    private static final double WEIGHT_EXPERIENCE = 0.25; // 25% - Experience matters
    private static final double WEIGHT_PROJECTS = 0.20; // 20% - Relevant projects
    private static final double WEIGHT_CERTIFICATIONS = 0.10; // 10% - Certifications
    private static final double WEIGHT_DOMAIN = 0.05; // 5% - Domain experience

    /**
     * Match a new JD against all existing resumes
     */
    public void matchNewJobDescription(String jdId) {
        Optional<JobDescription> jdOpt = jobDescriptionRepository.findByJdId(jdId);
        if (jdOpt.isEmpty()) {
            log.warn("JD not found: {}", jdId);
            return;
        }

        JobDescription jd = jdOpt.get();
        List<Resume> allResumes = resumeRepository.findAll();

        log.info("Matching JD {} against {} resumes (Skill-Based Matching)", jdId, allResumes.size());

        for (Resume resume : allResumes) {
            MatchResult result = computeSkillBasedMatch(jd, resume);
            matchResultRepository.save(result);
        }
    }

    /**
     * Match a new resume against all existing JDs
     */
    public void matchNewResume(String resumeId) {
        Optional<Resume> resumeOpt = resumeRepository.findByFileId(resumeId);
        if (resumeOpt.isEmpty()) {
            log.warn("Resume not found: {}", resumeId);
            return;
        }

        Resume resume = resumeOpt.get();
        List<JobDescription> allJDs = jobDescriptionRepository.findAll();

        log.info("Matching resume {} against {} JDs (Skill-Based Matching)", resumeId, allJDs.size());

        for (JobDescription jd : allJDs) {
            MatchResult result = computeSkillBasedMatch(jd, resume);
            matchResultRepository.save(result);
        }
    }

    /**
     * Core Skill-Based Matching Logic (No Embeddings!)
     */
    @SuppressWarnings("unchecked")
    private MatchResult computeSkillBasedMatch(JobDescription jd, Resume resume) {
        MatchResult result = new MatchResult();
        result.setJdId(jd.getJdId());
        result.setResumeId(resume.getFileId());
        result.setMatchedAt(LocalDateTime.now());

        // Extract data
        List<String> requiredSkills = jd.getRequiredSkills() != null ? jd.getRequiredSkills() : new ArrayList<>();
        List<String> preferredSkills = jd.getPreferredSkills() != null ? jd.getPreferredSkills() : new ArrayList<>();
        List<String> candidateSkills = resume.getSkills() != null ? resume.getSkills() : new ArrayList<>();
        Map<String, Object> parsedDetails = resume.getParsedDetails() != null ? resume.getParsedDetails()
                : new HashMap<>();

        // Convert to lowercase sets for matching
        Set<String> requiredSkillsLower = requiredSkills.stream().map(String::toLowerCase).collect(Collectors.toSet());
        Set<String> candidateSkillsLower = candidateSkills.stream().map(String::toLowerCase)
                .collect(Collectors.toSet());

        // ============================================
        // 1. SKILL MATCH SCORE (40%)
        // ============================================
        List<String> matchedRequiredSkills = new ArrayList<>();
        List<String> missingRequiredSkills = new ArrayList<>();
        List<String> matchedPreferredSkills = new ArrayList<>();

        for (String reqSkill : requiredSkills) {
            if (skillMatches(reqSkill.toLowerCase(), candidateSkillsLower)) {
                matchedRequiredSkills.add(reqSkill);
            } else {
                missingRequiredSkills.add(reqSkill);
            }
        }

        for (String prefSkill : preferredSkills) {
            if (skillMatches(prefSkill.toLowerCase(), candidateSkillsLower)) {
                matchedPreferredSkills.add(prefSkill);
            }
        }

        double skillScore = 0.0;
        if (!requiredSkills.isEmpty()) {
            skillScore = (double) matchedRequiredSkills.size() / requiredSkills.size();
            // Bonus for preferred skills (up to 20% extra)
            if (!preferredSkills.isEmpty()) {
                double preferredBonus = (double) matchedPreferredSkills.size() / preferredSkills.size() * 0.20;
                skillScore = Math.min(skillScore + preferredBonus, 1.0);
            }
        } else {
            skillScore = 0.5; // Default if no required skills specified
        }

        result.setSkillMatchScore(skillScore);
        result.setMatchedSkillsCount(matchedRequiredSkills.size());
        result.setTotalRequiredSkills(requiredSkills.size());
        result.setMatchedSkillsList(matchedRequiredSkills);
        result.setMissingSkillsList(missingRequiredSkills);
        result.setPreferredSkillsMatched(matchedPreferredSkills.size());
        result.setTotalPreferredSkills(preferredSkills.size());
        result.setMatchedPreferredSkillsList(matchedPreferredSkills);

        // ============================================
        // 2. EXPERIENCE SCORE (25%)
        // ============================================
        int minExp = jd.getMinExperience();
        int candidateExp = extractTotalExperience(parsedDetails);

        double expScore = 0.0;
        String expStatus = "INSUFFICIENT";

        if (minExp <= 0) {
            expScore = 0.5; // No requirement specified
            expStatus = "NO_REQUIREMENT";
        } else if (candidateExp >= minExp + 2) {
            expScore = 1.0;
            expStatus = "EXCEEDS";
        } else if (candidateExp >= minExp) {
            expScore = 1.0;
            expStatus = "MEETS";
        } else if (candidateExp >= minExp * 0.7) {
            expScore = 0.7;
            expStatus = "PARTIAL";
        } else {
            expScore = (double) candidateExp / minExp;
            expStatus = "INSUFFICIENT";
        }

        result.setExperienceScore(expScore);
        result.setCandidateExperience(candidateExp);
        result.setRequiredExperience(minExp);
        result.setExperienceStatus(expStatus);

        // ============================================
        // 3. PROJECT RELEVANCE SCORE (20%)
        // ============================================
        List<String> relevantProjectNames = new ArrayList<>();
        int totalProjects = 0;

        Object projectsObj = parsedDetails.get("projects");
        if (projectsObj instanceof List) {
            List<Map<String, Object>> projects = (List<Map<String, Object>>) projectsObj;
            totalProjects = projects.size();

            for (Map<String, Object> project : projects) {
                String projectName = project.get("project_name") != null ? project.get("project_name").toString() : "";
                Object techObj = project.get("technologies_used");

                if (techObj instanceof List) {
                    List<String> techList = (List<String>) techObj;
                    boolean hasRelevantTech = techList.stream()
                            .anyMatch(tech -> skillMatches(tech.toLowerCase(), requiredSkillsLower));
                    if (hasRelevantTech && !projectName.isEmpty()) {
                        relevantProjectNames.add(projectName);
                    }
                }
            }
        }

        double projectScore = 0.0;
        if (totalProjects > 0) {
            projectScore = Math.min((double) relevantProjectNames.size() / totalProjects, 1.0);
            // Bonus: Having any relevant project is good
            if (!relevantProjectNames.isEmpty()) {
                projectScore = Math.max(projectScore, 0.5);
            }
        }

        result.setProjectsCertificationsScore(projectScore);
        result.setRelevantProjects(relevantProjectNames);
        result.setRelevantProjectsCount(relevantProjectNames.size());
        result.setTotalProjects(totalProjects);

        // ============================================
        // 4. CERTIFICATIONS SCORE (10%)
        // ============================================
        List<String> relevantCerts = new ArrayList<>();
        Object certsObj = parsedDetails.get("certifications");
        if (certsObj instanceof List) {
            List<String> certifications = (List<String>) certsObj;
            for (String cert : certifications) {
                String certLower = cert.toLowerCase();
                // Check if certification relates to required skills
                if (requiredSkillsLower.stream()
                        .anyMatch(skill -> certLower.contains(skill) || skill.contains(certLower))) {
                    relevantCerts.add(cert);
                }
            }
        }

        double certScore = relevantCerts.isEmpty() ? 0.0 : Math.min(relevantCerts.size() * 0.25, 1.0);
        result.setCertificationsCount(relevantCerts.size());
        result.setRelevantCertifications(relevantCerts);

        // ============================================
        // 5. DOMAIN MATCH SCORE (5%)
        // ============================================
        boolean domainMatch = false;
        Object jdDomainObj = jd.getParsedDetails() != null ? jd.getParsedDetails().get("domain") : null;
        Object resumeDomainObj = parsedDetails.get("domain_experience");

        if (jdDomainObj instanceof List && resumeDomainObj instanceof List) {
            Set<String> jdDomains = ((List<String>) jdDomainObj).stream()
                    .map(String::toLowerCase).collect(Collectors.toSet());
            Set<String> resumeDomains = ((List<String>) resumeDomainObj).stream()
                    .map(String::toLowerCase).collect(Collectors.toSet());
            domainMatch = jdDomains.stream().anyMatch(resumeDomains::contains);
        }

        double domainScore = domainMatch ? 1.0 : 0.0;
        result.setDomainMatch(domainMatch);

        // ============================================
        // 6. EMPLOYMENT GAP PENALTY
        // ============================================
        Map<String, Object> gapsData = extractEmploymentGaps(parsedDetails);
        boolean hasGap = (Boolean) gapsData.getOrDefault("has_gap", false);
        int totalGapMonths = ((Number) gapsData.getOrDefault("total_gap_months", 0)).intValue();

        double gapPenalty = 0.0;
        if (hasGap) {
            if (totalGapMonths < 12) {
                gapPenalty = 0.05;
            } else if (totalGapMonths < 24) {
                gapPenalty = 0.10;
            } else {
                gapPenalty = 0.15;
            }
        }

        result.setGapPenalty(gapPenalty);
        result.setHasEmploymentGap(hasGap);
        result.setTotalGapMonths(totalGapMonths);

        // ============================================
        // 7. CALCULATE FINAL SCORE
        // ============================================
        double finalScore = (WEIGHT_SKILL * skillScore) +
                (WEIGHT_EXPERIENCE * expScore) +
                (WEIGHT_PROJECTS * projectScore) +
                (WEIGHT_CERTIFICATIONS * certScore) +
                (WEIGHT_DOMAIN * domainScore) -
                gapPenalty;

        finalScore = Math.max(0, Math.min(1.0, finalScore)); // Clamp to [0, 1]
        result.setFinalScore(finalScore);

        // Set semantic similarity to 0 since we're not using embeddings
        result.setSemanticSimilarity(0.0);

        // Extract and set candidate name
        String candidateName = extractCandidateName(parsedDetails, resume.getName());
        result.setCandidateName(candidateName);

        log.debug("Match: {} vs {} | Skills: {}/{} | Exp: {} | Score: {:.2f}",
                resume.getFileId(), jd.getJdId(),
                matchedRequiredSkills.size(), requiredSkills.size(),
                expStatus, finalScore);

        return result;
    }

    /**
     * Check if a skill matches any candidate skill (with fuzzy matching)
     */
    private boolean skillMatches(String skill, Set<String> candidateSkills) {
        // Direct match
        if (candidateSkills.contains(skill)) {
            return true;
        }

        // Fuzzy matching - check if skill is contained in any candidate skill or vice
        // versa
        for (String candidateSkill : candidateSkills) {
            if (candidateSkill.contains(skill) || skill.contains(candidateSkill)) {
                return true;
            }
            // Handle common variations
            String skillNormalized = skill.replace(".", "").replace(" ", "").replace("-", "");
            String candidateNormalized = candidateSkill.replace(".", "").replace(" ", "").replace("-", "");
            if (skillNormalized.equals(candidateNormalized)) {
                return true;
            }
        }
        return false;
    }

    private int extractTotalExperience(Map<String, Object> parsedDetails) {
        if (parsedDetails == null)
            return 0;

        Object expObj = parsedDetails.get("total_experience_years");
        if (expObj instanceof Number) {
            return ((Number) expObj).intValue();
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractEmploymentGaps(Map<String, Object> parsedDetails) {
        if (parsedDetails == null) {
            return Map.of("has_gap", false, "total_gap_months", 0);
        }

        Object gapsObj = parsedDetails.get("employment_gaps");
        if (gapsObj instanceof Map) {
            return (Map<String, Object>) gapsObj;
        }

        return Map.of("has_gap", false, "total_gap_months", 0);
    }

    @SuppressWarnings("unchecked")
    private String extractCandidateName(Map<String, Object> parsedDetails, String defaultName) {
        if (parsedDetails == null)
            return defaultName;

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
}
