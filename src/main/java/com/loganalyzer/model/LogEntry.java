package com.loganalyzer.model;

import java.time.Instant;

public record LogEntry(
        Instant timestamp,
        String level,
        String app,
        String message,
        String sourceFile
) {
}
