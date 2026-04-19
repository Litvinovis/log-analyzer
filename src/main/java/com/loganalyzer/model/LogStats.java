package com.loganalyzer.model;

import java.util.List;
import java.util.Map;

public record LogStats(
        long totalScanned,
        long totalErrors,
        Map<String, Long> byLevel,
        Map<String, Long> byApp,
        List<TopMessage> topMessages,
        Map<String, Long> byHour
) {
    public record TopMessage(String message, long count) {}
}
