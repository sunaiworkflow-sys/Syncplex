package com.jdres.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Match Percentage Calculator
 * Computes skill-based matching scores without embeddings
 */
@Service
public class MatchCalculatorService {

    private static final Logger log = LoggerFactory.getLogger(MatchCalculatorService.class);

    // Common skill variations for normalization
    private static final Map<String, String> SKILL_VARIATIONS = Map.of(
            "js", "javascript",
            "ts", "typescript",
            "py", "python",
            "ml", "machine learning",
            "ai", "artificial intelligence",
            "c++", "cpp",
            "c#", "csharp");

    /**
     * Calculate match percentage between JD and Resume skills
     * 
     * @param jdSkills     - Required skills from job description
     * @param resumeSkills - Skills from candidate resume
     * @return Match result with score and skill breakdown
     */
    public Map<String, Object> calculateSkillMatch(List<String> jdSkills, List<String> resumeSkills) {
        log.info("Calculating match for {} JD skills and {} resume skills",
                jdSkills != null ? jdSkills.size() : 0,
                resumeSkills != null ? resumeSkills.size() : 0);

        if (jdSkills == null || jdSkills.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("matchScore", 0);
            result.put("matchedSkills", Collections.emptyList());
            result.put("missingSkills", Collections.emptyList());
            result.put("extraSkills", resumeSkills != null ? resumeSkills : Collections.emptyList());
            result.put("totalRequired", 0);
            result.put("totalMatched", 0);
            return result;
        }

        if (resumeSkills == null || resumeSkills.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("matchScore", 0);
            result.put("matchedSkills", Collections.emptyList());
            result.put("missingSkills", new ArrayList<>(jdSkills));
            result.put("extraSkills", Collections.emptyList());
            result.put("totalRequired", jdSkills.size());
            result.put("totalMatched", 0);
            return result;
        }

        List<String> matchedSkills = new ArrayList<>();
        List<String> missingSkills = new ArrayList<>();

        // Find matched skills (case-insensitive partial matching)
        for (String jdSkill : jdSkills) {
            boolean matched = resumeSkills.stream()
                    .anyMatch(resumeSkill -> isSkillMatch(jdSkill, resumeSkill));

            if (matched) {
                matchedSkills.add(jdSkill); // Use JD skill name for consistency
            } else {
                missingSkills.add(jdSkill);
            }
        }

        // Find extra skills not in JD
        List<String> extraSkills = resumeSkills.stream()
                .filter(resumeSkill -> jdSkills.stream()
                        .noneMatch(jdSkill -> isSkillMatch(jdSkill, resumeSkill)))
                .toList();

        // Calculate match percentage
        int matchScore = Math.round((float) matchedSkills.size() / jdSkills.size() * 100);

        Map<String, Object> result = new HashMap<>();
        result.put("matchScore", matchScore);
        result.put("matchedSkills", new ArrayList<>(new HashSet<>(matchedSkills))); // Remove duplicates
        result.put("missingSkills", missingSkills);
        result.put("extraSkills", extraSkills);
        result.put("totalRequired", jdSkills.size());
        result.put("totalMatched", matchedSkills.size());

        log.debug("Match calculation result: {}", result);
        return result;
    }

    /**
     * Calculate weighted match score with skill importance
     * 
     * @param jdSkillsWithWeights - Skills with weights [{skill, weight}]
     * @param resumeSkills        - Resume skills
     * @return Weighted match result
     */
    public Map<String, Object> calculateWeightedMatch(List<Map<String, Object>> jdSkillsWithWeights,
            List<String> resumeSkills) {
        double totalWeight = 0;
        double matchedWeight = 0;
        List<String> matchedSkills = new ArrayList<>();
        List<Map<String, Object>> missingSkills = new ArrayList<>();

        for (Map<String, Object> skillObj : jdSkillsWithWeights) {
            String skill = (String) skillObj.get("skill");
            double weight = skillObj.containsKey("weight") ? ((Number) skillObj.get("weight")).doubleValue() : 1.0;

            totalWeight += weight;

            boolean isMatched = resumeSkills.stream()
                    .anyMatch(resumeSkill -> isSkillMatch(skill, resumeSkill));

            if (isMatched) {
                matchedWeight += weight;
                matchedSkills.add(skill);
            } else {
                Map<String, Object> missing = new HashMap<>();
                missing.put("skill", skill);
                missing.put("weight", weight);
                missingSkills.add(missing);
            }
        }

        int weightedScore = totalWeight > 0 ? (int) Math.round((matchedWeight / totalWeight) * 100) : 0;

        Map<String, Object> result = new HashMap<>();
        result.put("matchScore", weightedScore);
        result.put("weightedScore", weightedScore);
        result.put("matchedSkills", matchedSkills);
        result.put("missingSkills", missingSkills);
        result.put("totalWeight", totalWeight);
        result.put("matchedWeight", matchedWeight);

        return result;
    }

    /**
     * Check if two skills match (improved fuzzy matching)
     * Matches the logic from matchCalculator.js
     */
    private boolean isSkillMatch(String skill1, String skill2) {
        String s1 = skill1.toLowerCase().trim();
        String s2 = skill2.toLowerCase().trim();

        // Exact match
        if (s1.equals(s2))
            return true;

        // Handle common variations
        String normalized1 = SKILL_VARIATIONS.getOrDefault(s1, s1);
        String normalized2 = SKILL_VARIATIONS.getOrDefault(s2, s2);

        if (normalized1.equals(normalized2))
            return true;

        // For very short skills (1-2 chars), only allow exact matches or known
        // variations
        if (s1.length() <= 2 || s2.length() <= 2) {
            return false;
        }

        // Word boundary matching for longer skills
        // Only match if one skill is a complete word within the other
        Pattern pattern1 = createWordBoundaryPattern(s1);
        Pattern pattern2 = createWordBoundaryPattern(s2);

        if (pattern1.matcher(s2).find() || pattern2.matcher(s1).find()) {
            return true;
        }

        // Additional check for normalized versions
        Pattern normalizedPattern1 = createWordBoundaryPattern(normalized1);
        Pattern normalizedPattern2 = createWordBoundaryPattern(normalized2);

        return normalizedPattern1.matcher(normalized2).find() || normalizedPattern2.matcher(normalized1).find();
    }

    /**
     * Create a word boundary regex pattern
     */
    private Pattern createWordBoundaryPattern(String word) {
        String escaped = Pattern.quote(word);
        return Pattern.compile("\\b" + escaped + "\\b", Pattern.CASE_INSENSITIVE);
    }
}
