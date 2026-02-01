package com.jdres.service;

import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token Usage Tracker Service
 * Tracks OpenAI API token usage with per-minute statistics
 */
@Service
public class TokenUsageTracker {

    // Record for each API call
    public record TokenRecord(long promptTokens, long completionTokens, long totalTokens, Instant timestamp) {}

    // Store recent token records (rolling window of 60 minutes)
    private final ConcurrentLinkedQueue<TokenRecord> recentRecords = new ConcurrentLinkedQueue<>();

    // Cumulative counters (all-time)
    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);
    private final AtomicLong totalTokensUsed = new AtomicLong(0);
    private final AtomicLong totalApiCalls = new AtomicLong(0);

    // Track start time
    private final Instant startTime = Instant.now();

    /**
     * Record token usage from an API call
     */
    public void recordUsage(int promptTokens, int completionTokens, int totalTokens) {
        TokenRecord record = new TokenRecord(promptTokens, completionTokens, totalTokens, Instant.now());
        recentRecords.add(record);
        
        totalPromptTokens.addAndGet(promptTokens);
        totalCompletionTokens.addAndGet(completionTokens);
        totalTokensUsed.addAndGet(totalTokens);
        totalApiCalls.incrementAndGet();

        // Clean up old records (older than 60 minutes)
        cleanupOldRecords();
    }

    /**
     * Remove records older than 60 minutes
     */
    private void cleanupOldRecords() {
        Instant cutoff = Instant.now().minusSeconds(3600); // 60 minutes
        while (!recentRecords.isEmpty() && recentRecords.peek().timestamp().isBefore(cutoff)) {
            recentRecords.poll();
        }
    }

    /**
     * Get tokens used in the last minute
     */
    public long getTokensLastMinute() {
        Instant oneMinuteAgo = Instant.now().minusSeconds(60);
        return recentRecords.stream()
                .filter(r -> r.timestamp().isAfter(oneMinuteAgo))
                .mapToLong(TokenRecord::totalTokens)
                .sum();
    }

    /**
     * Get tokens used in the last 5 minutes
     */
    public long getTokensLast5Minutes() {
        Instant fiveMinutesAgo = Instant.now().minusSeconds(300);
        return recentRecords.stream()
                .filter(r -> r.timestamp().isAfter(fiveMinutesAgo))
                .mapToLong(TokenRecord::totalTokens)
                .sum();
    }

    /**
     * Get API calls in the last minute
     */
    public long getCallsLastMinute() {
        Instant oneMinuteAgo = Instant.now().minusSeconds(60);
        return recentRecords.stream()
                .filter(r -> r.timestamp().isAfter(oneMinuteAgo))
                .count();
    }

    /**
     * Get comprehensive usage statistics
     */
    public Map<String, Object> getUsageStats() {
        cleanupOldRecords();
        
        long lastMinuteTokens = getTokensLastMinute();
        long last5MinuteTokens = getTokensLast5Minutes();
        long callsLastMinute = getCallsLastMinute();

        // Calculate rate per minute based on total usage
        long uptimeSeconds = Instant.now().getEpochSecond() - startTime.getEpochSecond();
        double uptimeMinutes = Math.max(1, uptimeSeconds / 60.0);
        double avgTokensPerMinute = totalTokensUsed.get() / uptimeMinutes;

        Map<String, Object> stats = new HashMap<>();
        
        // Per-minute stats (most useful)
        stats.put("tokensLastMinute", lastMinuteTokens);
        stats.put("tokensLast5Minutes", last5MinuteTokens);
        stats.put("callsLastMinute", callsLastMinute);
        stats.put("avgTokensPerMinute", Math.round(avgTokensPerMinute));
        
        // All-time totals
        stats.put("totalTokensUsed", totalTokensUsed.get());
        stats.put("totalPromptTokens", totalPromptTokens.get());
        stats.put("totalCompletionTokens", totalCompletionTokens.get());
        stats.put("totalApiCalls", totalApiCalls.get());
        
        // Uptime
        stats.put("uptimeMinutes", Math.round(uptimeMinutes));
        
        // Estimated cost (GPT-4o-mini pricing: $0.15/1M input, $0.6/1M output)
        double inputCost = (totalPromptTokens.get() / 1_000_000.0) * 0.15;
        double outputCost = (totalCompletionTokens.get() / 1_000_000.0) * 0.60;
        stats.put("estimatedCostUSD", String.format("$%.4f", inputCost + outputCost));

        return stats;
    }
}
