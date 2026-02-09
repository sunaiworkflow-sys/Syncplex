package com.jdres.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "match_results")
@CompoundIndexes({
        @CompoundIndex(name = "jd_score_idx", def = "{'jdId': 1, 'finalScore': -1}"),
        @CompoundIndex(name = "resume_score_idx", def = "{'resumeId': 1, 'finalScore': -1}")
})
public class MatchResult {

    @Id
    private String id;

    private String jdId;
    private String resumeId;

    // Score components
    private double semanticSimilarity;
    private double skillMatchScore;
    private double experienceScore;
    private double projectsCertificationsScore;
    private double keywordMatchScore; // NEW: score for suggested keywords
    private double gapPenalty;

    // Final weighted score
    private double finalScore;

    // Metadata for display
    private int matchedSkillsCount;
    private int totalRequiredSkills;
    private int candidateExperience;
    private int requiredExperience;
    private boolean hasEmploymentGap;
    private int totalGapMonths;

    // Additional display data
    private String candidateName;
    private String candidateStatus; // accepted, review, rejected
    private java.util.List<String> matchedSkillsList;
    private java.util.List<String> missingSkillsList;
    private java.util.List<String> relevantProjects;

    // NEW: Detailed matching breakdown
    private int preferredSkillsMatched;
    private int totalPreferredSkills;
    private int relevantProjectsCount;
    private int totalProjects;
    private int certificationsCount;
    private boolean domainMatch;
    private String experienceStatus; // EXCEEDS, MEETS, PARTIAL, INSUFFICIENT
    private java.util.List<String> matchedPreferredSkillsList;
    private java.util.List<String> relevantCertifications;
    private java.util.List<String> matchedKeywordsList; // NEW: matched suggested keywords

    // Recruitment Intelligence Scoring Fields
    private double domainFitScore;
    private double executionScore;
    private double deliveryRiskScore;
    private int scaleBonus;
    private int pmoPenalty;
    private double recruitmentFinalScore;
    private String recruitmentRating;
    private java.util.List<String> matchedDomains;
    private java.util.List<String> keyProjectsEvidence;
    private java.util.List<String> riskEventsEvidence;
    private java.util.List<String> scaleIndicators;

    private LocalDateTime matchedAt;

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getJdId() {
        return jdId;
    }

    public void setJdId(String jdId) {
        this.jdId = jdId;
    }

    public String getResumeId() {
        return resumeId;
    }

    public void setResumeId(String resumeId) {
        this.resumeId = resumeId;
    }

    public double getSemanticSimilarity() {
        return semanticSimilarity;
    }

    public void setSemanticSimilarity(double semanticSimilarity) {
        this.semanticSimilarity = semanticSimilarity;
    }

    public double getSkillMatchScore() {
        return skillMatchScore;
    }

    public void setSkillMatchScore(double skillMatchScore) {
        this.skillMatchScore = skillMatchScore;
    }

    public double getExperienceScore() {
        return experienceScore;
    }

    public void setExperienceScore(double experienceScore) {
        this.experienceScore = experienceScore;
    }

    public double getProjectsCertificationsScore() {
        return projectsCertificationsScore;
    }

    public void setProjectsCertificationsScore(double projectsCertificationsScore) {
        this.projectsCertificationsScore = projectsCertificationsScore;
    }

    public double getGapPenalty() {
        return gapPenalty;
    }

    public void setGapPenalty(double gapPenalty) {
        this.gapPenalty = gapPenalty;
    }

    public double getFinalScore() {
        return finalScore;
    }

    public void setFinalScore(double finalScore) {
        this.finalScore = finalScore;
    }

    public int getMatchedSkillsCount() {
        return matchedSkillsCount;
    }

    public void setMatchedSkillsCount(int matchedSkillsCount) {
        this.matchedSkillsCount = matchedSkillsCount;
    }

    public int getTotalRequiredSkills() {
        return totalRequiredSkills;
    }

    public void setTotalRequiredSkills(int totalRequiredSkills) {
        this.totalRequiredSkills = totalRequiredSkills;
    }

    public int getCandidateExperience() {
        return candidateExperience;
    }

    public void setCandidateExperience(int candidateExperience) {
        this.candidateExperience = candidateExperience;
    }

    public int getRequiredExperience() {
        return requiredExperience;
    }

    public void setRequiredExperience(int requiredExperience) {
        this.requiredExperience = requiredExperience;
    }

    public boolean isHasEmploymentGap() {
        return hasEmploymentGap;
    }

    public void setHasEmploymentGap(boolean hasEmploymentGap) {
        this.hasEmploymentGap = hasEmploymentGap;
    }

    public int getTotalGapMonths() {
        return totalGapMonths;
    }

    public void setTotalGapMonths(int totalGapMonths) {
        this.totalGapMonths = totalGapMonths;
    }

    public LocalDateTime getMatchedAt() {
        return matchedAt;
    }

    public void setMatchedAt(LocalDateTime matchedAt) {
        this.matchedAt = matchedAt;
    }

    public String getCandidateName() {
        return candidateName;
    }

    public void setCandidateName(String candidateName) {
        this.candidateName = candidateName;
    }

    public String getCandidateStatus() {
        return candidateStatus;
    }

    public void setCandidateStatus(String candidateStatus) {
        this.candidateStatus = candidateStatus;
    }

    public java.util.List<String> getMatchedSkillsList() {
        return matchedSkillsList;
    }

    public void setMatchedSkillsList(java.util.List<String> matchedSkillsList) {
        this.matchedSkillsList = matchedSkillsList;
    }

    public java.util.List<String> getMissingSkillsList() {
        return missingSkillsList;
    }

    public void setMissingSkillsList(java.util.List<String> missingSkillsList) {
        this.missingSkillsList = missingSkillsList;
    }

    public java.util.List<String> getRelevantProjects() {
        return relevantProjects;
    }

    public void setRelevantProjects(java.util.List<String> relevantProjects) {
        this.relevantProjects = relevantProjects;
    }

    // NEW Getters and Setters
    public int getPreferredSkillsMatched() {
        return preferredSkillsMatched;
    }

    public void setPreferredSkillsMatched(int preferredSkillsMatched) {
        this.preferredSkillsMatched = preferredSkillsMatched;
    }

    public int getTotalPreferredSkills() {
        return totalPreferredSkills;
    }

    public void setTotalPreferredSkills(int totalPreferredSkills) {
        this.totalPreferredSkills = totalPreferredSkills;
    }

    public int getRelevantProjectsCount() {
        return relevantProjectsCount;
    }

    public void setRelevantProjectsCount(int relevantProjectsCount) {
        this.relevantProjectsCount = relevantProjectsCount;
    }

    public int getTotalProjects() {
        return totalProjects;
    }

    public void setTotalProjects(int totalProjects) {
        this.totalProjects = totalProjects;
    }

    public int getCertificationsCount() {
        return certificationsCount;
    }

    public void setCertificationsCount(int certificationsCount) {
        this.certificationsCount = certificationsCount;
    }

    public boolean isDomainMatch() {
        return domainMatch;
    }

    public void setDomainMatch(boolean domainMatch) {
        this.domainMatch = domainMatch;
    }

    public String getExperienceStatus() {
        return experienceStatus;
    }

    public void setExperienceStatus(String experienceStatus) {
        this.experienceStatus = experienceStatus;
    }

    public java.util.List<String> getMatchedPreferredSkillsList() {
        return matchedPreferredSkillsList;
    }

    public void setMatchedPreferredSkillsList(java.util.List<String> matchedPreferredSkillsList) {
        this.matchedPreferredSkillsList = matchedPreferredSkillsList;
    }

    public java.util.List<String> getRelevantCertifications() {
        return relevantCertifications;
    }

    public void setRelevantCertifications(java.util.List<String> relevantCertifications) {
        this.relevantCertifications = relevantCertifications;
    }

    public double getKeywordMatchScore() {
        return keywordMatchScore;
    }

    public void setKeywordMatchScore(double keywordMatchScore) {
        this.keywordMatchScore = keywordMatchScore;
    }

    public java.util.List<String> getMatchedKeywordsList() {
        return matchedKeywordsList;
    }

    public void setMatchedKeywordsList(java.util.List<String> matchedKeywordsList) {
        this.matchedKeywordsList = matchedKeywordsList;
    }

    // Recruitment Intelligence Getters and Setters
    public double getDomainFitScore() {
        return domainFitScore;
    }

    public void setDomainFitScore(double domainFitScore) {
        this.domainFitScore = domainFitScore;
    }

    public double getExecutionScore() {
        return executionScore;
    }

    public void setExecutionScore(double executionScore) {
        this.executionScore = executionScore;
    }

    public double getDeliveryRiskScore() {
        return deliveryRiskScore;
    }

    public void setDeliveryRiskScore(double deliveryRiskScore) {
        this.deliveryRiskScore = deliveryRiskScore;
    }

    public int getScaleBonus() {
        return scaleBonus;
    }

    public void setScaleBonus(int scaleBonus) {
        this.scaleBonus = scaleBonus;
    }

    public int getPmoPenalty() {
        return pmoPenalty;
    }

    public void setPmoPenalty(int pmoPenalty) {
        this.pmoPenalty = pmoPenalty;
    }

    public double getRecruitmentFinalScore() {
        return recruitmentFinalScore;
    }

    public void setRecruitmentFinalScore(double recruitmentFinalScore) {
        this.recruitmentFinalScore = recruitmentFinalScore;
    }

    public String getRecruitmentRating() {
        return recruitmentRating;
    }

    public void setRecruitmentRating(String recruitmentRating) {
        this.recruitmentRating = recruitmentRating;
    }

    public java.util.List<String> getMatchedDomains() {
        return matchedDomains;
    }

    public void setMatchedDomains(java.util.List<String> matchedDomains) {
        this.matchedDomains = matchedDomains;
    }

    public java.util.List<String> getKeyProjectsEvidence() {
        return keyProjectsEvidence;
    }

    public void setKeyProjectsEvidence(java.util.List<String> keyProjectsEvidence) {
        this.keyProjectsEvidence = keyProjectsEvidence;
    }

    public java.util.List<String> getRiskEventsEvidence() {
        return riskEventsEvidence;
    }

    public void setRiskEventsEvidence(java.util.List<String> riskEventsEvidence) {
        this.riskEventsEvidence = riskEventsEvidence;
    }

    public java.util.List<String> getScaleIndicators() {
        return scaleIndicators;
    }

    public void setScaleIndicators(java.util.List<String> scaleIndicators) {
        this.scaleIndicators = scaleIndicators;
    }
}
