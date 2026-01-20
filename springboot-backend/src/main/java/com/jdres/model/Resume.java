package com.jdres.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "resumes")
public class Resume {

    @Id
    private String id;

    private String fileId; // Google Drive ID
    private String name;
    private String text;
    private String viewLink;
    private String downloadLink;
    private String source; // "google_drive"
    private LocalDateTime importedAt;

    // AWS S3
    private String s3Key;
    private String s3Url;

    // Vector
    private List<Double> embedding;

    // Extracted/Parsed fields
    private List<String> skills;
    private double matchScore;

    // Detailed structured data from LLM
    private java.util.Map<String, Object> parsedDetails;

    // Multi-tenant support
    private String recruiterId; // Firebase UID

    // Candidate details
    private String candidateName;
    private int candidateExperience;

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getViewLink() {
        return viewLink;
    }

    public void setViewLink(String viewLink) {
        this.viewLink = viewLink;
    }

    public String getDownloadLink() {
        return downloadLink;
    }

    public void setDownloadLink(String downloadLink) {
        this.downloadLink = downloadLink;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(LocalDateTime importedAt) {
        this.importedAt = importedAt;
    }

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    public String getS3Url() {
        return s3Url;
    }

    public void setS3Url(String s3Url) {
        this.s3Url = s3Url;
    }

    public List<Double> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Double> embedding) {
        this.embedding = embedding;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public double getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(double matchScore) {
        this.matchScore = matchScore;
    }

    public java.util.Map<String, Object> getParsedDetails() {
        return parsedDetails;
    }

    public void setParsedDetails(java.util.Map<String, Object> parsedDetails) {
        this.parsedDetails = parsedDetails;
    }

    public String getRecruiterId() {
        return recruiterId;
    }

    public void setRecruiterId(String recruiterId) {
        this.recruiterId = recruiterId;
    }

    public String getCandidateName() {
        return candidateName;
    }

    public void setCandidateName(String candidateName) {
        this.candidateName = candidateName;
    }

    public int getCandidateExperience() {
        return candidateExperience;
    }

    public void setCandidateExperience(int candidateExperience) {
        this.candidateExperience = candidateExperience;
    }
}
