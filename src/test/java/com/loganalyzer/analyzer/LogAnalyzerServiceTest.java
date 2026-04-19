package com.loganalyzer.analyzer;

import com.loganalyzer.config.LogAnalyzerConfig;
import com.loganalyzer.model.LogAnalysisResult;
import com.loganalyzer.model.LogStats;
import com.loganalyzer.parser.LogFileParser;
import com.loganalyzer.storage.LogStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LogAnalyzerServiceTest {

    @TempDir
    Path tempDir;

    private LogAnalyzerService createService() throws IOException {
        Path appLogsDir = tempDir.resolve("app1/logs");
        Files.createDirectories(appLogsDir);

        Files.writeString(appLogsDir.resolve("app.log"),
                "2025-01-15T10:30:00 ERROR [app1] Error one\n" +
                "2025-01-15T10:31:00 ERROR [app1] Error in range\n" +
                "2025-01-15T10:31:00 INFO [app1] Info message\n" +
                "2025-01-15T10:32:00 ERROR [app1] Error two\n"
        );

        LogAnalyzerConfig config = new LogAnalyzerConfig();
        config.setLogPaths(List.of(tempDir.toString()));

        return new LogAnalyzerService(config, new LogFileParser(), new LogStore());
    }

    @Test
    void shouldFindErrorsInLogFile() throws IOException {
        LogAnalyzerService service = createService();
        List<LogAnalysisResult> results = service.analyzeErrors(List.of("app1"), null, null, null, null);

        assertEquals(1, results.size());
        assertEquals("app1", results.get(0).app());
        assertEquals(3, results.get(0).errorCount());
    }

    @Test
    void shouldReportCorrectTotalLinesScanned() throws IOException {
        LogAnalyzerService service = createService();
        List<LogAnalysisResult> results = service.analyzeErrors(List.of("app1"), null, null, null, null);

        assertEquals(1, results.size());
        assertEquals(4, results.get(0).totalLinesScanned());
        assertEquals(3, results.get(0).errorCount());
    }

    @Test
    void shouldFilterByAppName() throws IOException {
        LogAnalyzerService service = createService();
        List<LogAnalysisResult> results = service.analyzeErrors(List.of("nonexistent"), null, null, null, null);

        assertTrue(results.isEmpty());
    }

    @Test
    void shouldFilterByTimeRange() throws IOException {
        LogAnalyzerService service = createService();

        Instant from = Instant.parse("2025-01-15T10:31:00Z");
        Instant to   = Instant.parse("2025-01-15T10:31:30Z");

        List<LogAnalysisResult> results = service.analyzeErrors(null, from, to, null, null);

        assertEquals(1, results.size());
        assertEquals(1, results.get(0).errorCount());
    }

    @Test
    void shouldFilterByLevel() throws IOException {
        LogAnalyzerService service = createService();
        List<LogAnalysisResult> results = service.analyzeErrors(null, null, null, List.of("INFO"), null);

        assertEquals(1, results.size());
        assertEquals(1, results.get(0).errorCount());
        assertEquals("INFO", results.get(0).errors().get(0).level());
    }

    @Test
    void shouldFilterByContains() throws IOException {
        LogAnalyzerService service = createService();
        List<LogAnalysisResult> results = service.analyzeErrors(null, null, null, null, "in range");

        assertEquals(1, results.size());
        assertEquals(1, results.get(0).errorCount());
        assertTrue(results.get(0).errors().get(0).message().contains("in range"));
    }

    @Test
    void shouldReturnEmptyForNonexistentPath() {
        LogAnalyzerConfig config = new LogAnalyzerConfig();
        config.setLogPaths(List.of("/nonexistent/path"));

        LogAnalyzerService service = new LogAnalyzerService(config, new LogFileParser(), new LogStore());
        List<LogAnalysisResult> results = service.analyzeErrors(null, null, null, null, null);

        assertTrue(results.isEmpty());
    }

    @Test
    void shouldReturnStats() throws IOException {
        LogAnalyzerService service = createService();
        LogStats stats = service.getStats(List.of("app1"), null, null);

        assertEquals(4, stats.totalScanned());
        assertEquals(3, stats.totalErrors());
        assertTrue(stats.byLevel().containsKey("ERROR"));
        assertEquals(3L, stats.byLevel().get("ERROR"));
        assertFalse(stats.topMessages().isEmpty());
    }

    @Test
    void shouldFindErrorsInGzFile() throws IOException {
        Path gzDir = tempDir.resolve("gz-app/logs");
        Files.createDirectories(gzDir);
        Path gzFile = gzDir.resolve("app.log.gz");

        try (var os = Files.newOutputStream(gzFile);
             var gzOs = new GZIPOutputStream(os)) {
            String content = "2025-01-15T10:30:00 ERROR [gz-app] Gzipped error\n" +
                             "2025-01-15T10:31:00 INFO [gz-app] Gzipped info\n";
            gzOs.write(content.getBytes());
        }

        LogAnalyzerConfig config = new LogAnalyzerConfig();
        config.setLogPaths(List.of(tempDir.toString()));

        LogAnalyzerService service = new LogAnalyzerService(config, new LogFileParser(), new LogStore());
        List<LogAnalysisResult> results = service.analyzeErrors(List.of("gz-app"), null, null, null, null);

        assertTrue(results.stream().anyMatch(r -> "gz-app".equals(r.app()) && r.errorCount() == 1));
    }
}
