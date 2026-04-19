package com.loganalyzer.model;

import java.time.Instant;
import java.util.List;

public record LogAnalysisResult(
        String app,
        Instant analyzedAt,
        List<LogEntry> errors,
        long totalLinesScanned,
        long errorCount
) {
}
