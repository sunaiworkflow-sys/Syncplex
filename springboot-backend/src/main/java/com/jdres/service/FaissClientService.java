package com.jdres.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Text Ranking Client Service
 * Node.js wrapper to communicate with Python text ranking microservice
 */
@Service
public class FaissClientService {

    private final WebClient webClient;

    @Value("${ranking.service.url}")
    private String rankingServiceUrl;

    public FaissClientService(@Value("${ranking.service.url:http://localhost:5001}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Check if text ranking service is healthy
     * 
     * @return true if service is healthy
     */
    @SuppressWarnings("unchecked")
    public boolean isHealthy() {
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            return response != null && "healthy".equals(response.get("status"));
        } catch (Exception e) {
            System.err.println("Text ranking service health check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Build text index with resume data
     * 
     * @param resumeTexts - Array of resume text content
     * @param resumeIds   - Array of resume identifiers
     * @return Build result
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> buildIndex(List<String> resumeTexts, List<String> resumeIds) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("resume_texts", resumeTexts);
            requestBody.put("resume_ids", resumeIds);

            return webClient.post()
                    .uri("/build-index")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build text index: " + e.getMessage(), e);
        }
    }

    /**
     * Search for similar resumes using job description
     * 
     * @param jdText - Job description text
     * @param topK   - Number of top results to return
     * @return Search results
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> searchSimilarResumes(String jdText, int topK) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("jd_text", jdText);
            requestBody.put("top_k", topK);

            return webClient.post()
                    .uri("/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();
        } catch (Exception e) {
            throw new RuntimeException("Failed to search resumes: " + e.getMessage(), e);
        }
    }

    /**
     * Batch process multiple resumes and rank them
     * 
     * @param jdText     - Job description text
     * @param resumeData - Map with resume filenames as keys and text as values
     * @param topK       - Number of top results to return
     * @return Ranked results
     */
    public Map<String, Object> rankResumes(String jdText, Map<String, String> resumeData, int topK) {
        List<String> resumeIds = resumeData.keySet().stream().toList();
        List<String> resumeTexts = resumeData.values().stream().toList();

        // Build index
        buildIndex(resumeTexts, resumeIds);

        // Search for similar resumes
        Map<String, Object> searchResults = searchSimilarResumes(jdText, topK);

        // Add additional info
        Map<String, Object> result = new HashMap<>(searchResults);
        result.put("query", jdText);
        result.put("total_resumes", resumeIds.size());

        return result;
    }
}
