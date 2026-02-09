package com.jdres.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "job_descriptions")
public class JobDescription {

    @Id
    private String id;

    private String jdId; // Unique identifier
    private String title;
    private String text; // Raw JD text
    private String source; // "manual_upload", etc.
    private LocalDateTime createdAt;

    // Vector embedding
    private List<Double> embedding;

    // Structured parsed details from LLM
    private Map<String, Object> parsedDetails;

    // Flattened for quick access (legacy fields maintained for compatibility)
    private List<String> requiredSkills;
    private List<String> preferredSkills;
    private List<String> suggestedKeywords; // User-defined keywords to check
    private int minExperience;

    // ============================================
    // Advanced Recruitment Intelligence Fields
    // ============================================
    
    // Domain context
    private List<String> jdDomains;                    // fintech, healthcare, ecommerce, saas, etc.
    private List<String> businessContextKeywords;      // digital transformation, platform modernization, etc.
    
    // Skills breakdown  
    private List<String> mandatorySkills;              // Must have skills
    private List<String> toolsPlatforms;               // jira, docker, kubernetes, terraform, etc.
    private List<String> methodologies;                // agile, scrum, safe, waterfall, devops
    private List<String> architectureKeywords;         // microservices, distributed, serverless, etc.
    
    // Delivery expectations
    private int criticalDeliveriesRequired;            // Count of large initiatives
    private List<String> deliveryExpectations;         // end-to-end delivery, go-live ownership, etc.
    
    // Risk complexity
    private int riskAreasExpected;                     // Count of high-risk responsibilities
    private List<String> riskTypesExpected;            // security compliance, disaster recovery, etc.
    
    // Governance style
    private String jdDeliveryStyle;                    // hands-on, hybrid, governance
    
    // Scale requirements
    private Map<String, Object> scaleRequirements;     // large_budget_expected, enterprise_scale, etc.

    // Multi-tenant support
    @Indexed
    private String recruiterId; // Firebase UID

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<Double> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Double> embedding) {
        this.embedding = embedding;
    }

    public Map<String, Object> getParsedDetails() {
        return parsedDetails;
    }

    public void setParsedDetails(Map<String, Object> parsedDetails) {
        this.parsedDetails = parsedDetails;
    }

    public List<String> getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(List<String> requiredSkills) {
        this.requiredSkills = requiredSkills;
    }

    public List<String> getPreferredSkills() {
        return preferredSkills;
    }

    public void setPreferredSkills(List<String> preferredSkills) {
        this.preferredSkills = preferredSkills;
    }

    public List<String> getSuggestedKeywords() {
        return suggestedKeywords;
    }

    public void setSuggestedKeywords(List<String> suggestedKeywords) {
        this.suggestedKeywords = suggestedKeywords;
    }

    public int getMinExperience() {
        return minExperience;
    }

    public void setMinExperience(int minExperience) {
        this.minExperience = minExperience;
    }

    public String getRecruiterId() {
        return recruiterId;
    }

    public void setRecruiterId(String recruiterId) {
        this.recruiterId = recruiterId;
    }

    // ============================================
    // Advanced Recruitment Intelligence Getters/Setters
    // ============================================

    public List<String> getJdDomains() {
        return jdDomains;
    }

    public void setJdDomains(List<String> jdDomains) {
        this.jdDomains = jdDomains;
    }

    public List<String> getBusinessContextKeywords() {
        return businessContextKeywords;
    }

    public void setBusinessContextKeywords(List<String> businessContextKeywords) {
        this.businessContextKeywords = businessContextKeywords;
    }

    public List<String> getMandatorySkills() {
        return mandatorySkills;
    }

    public void setMandatorySkills(List<String> mandatorySkills) {
        this.mandatorySkills = mandatorySkills;
    }

    public List<String> getToolsPlatforms() {
        return toolsPlatforms;
    }

    public void setToolsPlatforms(List<String> toolsPlatforms) {
        this.toolsPlatforms = toolsPlatforms;
    }

    public List<String> getMethodologies() {
        return methodologies;
    }

    public void setMethodologies(List<String> methodologies) {
        this.methodologies = methodologies;
    }

    public List<String> getArchitectureKeywords() {
        return architectureKeywords;
    }

    public void setArchitectureKeywords(List<String> architectureKeywords) {
        this.architectureKeywords = architectureKeywords;
    }

    public int getCriticalDeliveriesRequired() {
        return criticalDeliveriesRequired;
    }

    public void setCriticalDeliveriesRequired(int criticalDeliveriesRequired) {
        this.criticalDeliveriesRequired = criticalDeliveriesRequired;
    }

    public List<String> getDeliveryExpectations() {
        return deliveryExpectations;
    }

    public void setDeliveryExpectations(List<String> deliveryExpectations) {
        this.deliveryExpectations = deliveryExpectations;
    }

    public int getRiskAreasExpected() {
        return riskAreasExpected;
    }

    public void setRiskAreasExpected(int riskAreasExpected) {
        this.riskAreasExpected = riskAreasExpected;
    }

    public List<String> getRiskTypesExpected() {
        return riskTypesExpected;
    }

    public void setRiskTypesExpected(List<String> riskTypesExpected) {
        this.riskTypesExpected = riskTypesExpected;
    }

    public String getJdDeliveryStyle() {
        return jdDeliveryStyle;
    }

    public void setJdDeliveryStyle(String jdDeliveryStyle) {
        this.jdDeliveryStyle = jdDeliveryStyle;
    }

    public Map<String, Object> getScaleRequirements() {
        return scaleRequirements;
    }

    public void setScaleRequirements(Map<String, Object> scaleRequirements) {
        this.scaleRequirements = scaleRequirements;
    }
}
