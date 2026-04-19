package com.loganalyzer.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class LogEntryTest {

    @Test
    void shouldCreateLogEntry() {
        Instant now = Instant.now();
        LogEntry entry = new LogEntry(now, "ERROR", "my-app", "Something failed", "/var/log/app.log");

        assertEquals(now, entry.timestamp());
        assertEquals("ERROR", entry.level());
        assertEquals("my-app", entry.app());
        assertEquals("Something failed", entry.message());
        assertEquals("/var/log/app.log", entry.sourceFile());
    }
}
