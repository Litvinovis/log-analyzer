package com.loganalyzer.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogAnalysisResultTest {

    @Test
    void shouldCreateLogAnalysisResult() {
        Instant now = Instant.now();
        LogEntry error = new LogEntry(now, "ERROR", "app", "fail", "file.log");

        LogAnalysisResult result = new LogAnalysisResult("app", now, List.of(error), 100, 1);

        assertEquals("app", result.app());
        assertEquals(now, result.analyzedAt());
        assertEquals(1, result.errors().size());
        assertEquals(100, result.totalLinesScanned());
        assertEquals(1, result.errorCount());
    }
}
