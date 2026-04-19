package com.loganalyzer.parser;

import com.loganalyzer.model.LogEntry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LogFileParserTest {

    private final LogFileParser parser = new LogFileParser();

    @Test
    void shouldParseStandardLogLine() {
        LogEntry entry = parser.parseLine(
                "2025-01-15T10:30:00 ERROR [my-app] Connection timeout",
                null,
                Path.of("test.log")
        );

        assertNotNull(entry);
        assertEquals("ERROR", entry.level());
        assertEquals("my-app", entry.app());
        assertEquals("Connection timeout", entry.message());
        assertNotNull(entry.timestamp());
    }

    @Test
    void shouldParseLogLineWithSpaceTimestamp() {
        LogEntry entry = parser.parseLine(
                "2025-01-15 10:30:00.123 FATAL [auth-service] Authentication failed",
                null,
                Path.of("test.log")
        );

        assertNotNull(entry);
        assertEquals("FATAL", entry.level());
        assertEquals("auth-service", entry.app());
    }

    @Test
    void shouldParseLogLineWithFractionalZTimestamp() {
        LogEntry entry = parser.parseLine(
                "2025-01-15T10:30:00.123Z ERROR [svc] Fractional Z timestamp",
                null,
                Path.of("test.log")
        );

        assertNotNull(entry);
        assertEquals("ERROR", entry.level());
        assertNotNull(entry.timestamp());
    }

    @Test
    void shouldRejectInvalidLine() {
        LogEntry entry = parser.parseLine("garbage log line", null, Path.of("test.log"));
        assertNull(entry);
    }

    @Test
    void shouldRejectBlankLine() {
        LogEntry entry = parser.parseLine("   ", null, Path.of("test.log"));
        assertNull(entry);
    }

    @Test
    void shouldParseFile(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("app.log");
        Files.writeString(logFile,
                "2025-01-15T10:30:00 ERROR [test-app] Error occurred\n" +
                "2025-01-15T10:31:00 INFO [test-app] Info message\n" +
                "2025-01-15T10:32:00 ERROR [test-app] Another error\n"
        );

        List<LogEntry> entries = parser.parse(logFile, null);

        assertEquals(3, entries.size());
        assertEquals("ERROR", entries.get(0).level());
        assertEquals("INFO", entries.get(1).level());
        assertEquals("ERROR", entries.get(2).level());
    }

    @Test
    void shouldParseGzFile(@TempDir Path tempDir) throws IOException {
        Path gzFile = tempDir.resolve("app.log.gz");
        try (var os = Files.newOutputStream(gzFile);
             var gzOs = new GZIPOutputStream(os)) {
            String content = "2025-01-15T10:30:00 ERROR [gz-app] Gzipped error\n" +
                             "2025-01-15T10:31:00 INFO [gz-app] Gzipped info\n";
            gzOs.write(content.getBytes());
        }

        List<LogEntry> entries = parser.parseGzFile(gzFile, null);

        assertEquals(2, entries.size());
        assertEquals("ERROR", entries.get(0).level());
        assertEquals("gz-app", entries.get(0).app());
        assertEquals("INFO", entries.get(1).level());
    }

    @Test
    void shouldUseProvidedAppName() {
        LogEntry entry = parser.parseLine(
                "2025-01-15T10:30:00 ERROR [original-app] Test message",
                "override-app",
                Path.of("test.log")
        );

        assertNotNull(entry);
        assertEquals("override-app", entry.app());
    }

    @Test
    void shouldStreamFile(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("app.log");
        Files.writeString(logFile,
                "2025-01-15T10:30:00 ERROR [svc] Error one\n" +
                "2025-01-15T10:31:00 WARN [svc] Warning\n" +
                "2025-01-15T10:32:00 INFO [svc] Info\n"
        );

        List<LogEntry> entries;
        try (var stream = parser.stream(logFile, null)) {
            entries = stream.toList();
        }

        assertEquals(3, entries.size());
    }
}
