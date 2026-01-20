package com.jdres.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    @Value("${openai.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<Double> getEmbedding(String text) {
        if (text == null || text.isEmpty())
            return Collections.emptyList();

        String url = "https://api.openai.com/v1/embeddings";
        String embeddingModel = "text-embedding-3-small"; // Efficient and cheap

        // drastic truncation to save tokens/quota
        // 1000 chars is enough for a summary embedding
        String safeText = text.substring(0, Math.min(text.length(), 1000));

        Map<String, Object> body = Map.of(
                "input", safeText,
                "model", embeddingModel);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            if (response != null && response.containsKey("data")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
                if (!data.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    List<Double> embedding = (List<Double>) data.get(0).get("embedding");
                    return embedding;
                }
            }
            return Collections.emptyList();
        } catch (Exception e) {
            System.err.println("Embedding Error: " + e.getMessage());
            return Collections.emptyList();
        }
    }
}
