package com.loganalyzer.model;

import java.time.Instant;

public record LogEntry(
        Instant timestamp,
        String level,
        String app,
        String message,
        String sourceFile,
        String threadName,
        String loggerName,
        String stackTrace
) {
    public LogEntry(Instant timestamp, String level, String app, String message, String sourceFile) {
        this(timestamp, level, app, message, sourceFile, null, null, null);
    }
}
