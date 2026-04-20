package com.loganalyzer.analyzer;

import com.loganalyzer.config.LogAnalyzerConfig;
import com.loganalyzer.config.LogFormat;
import com.loganalyzer.model.LogAnalysisResult;
import com.loganalyzer.model.LogStats;
import com.loganalyzer.model.TraceResult;
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

    private static final String UUID_1 = "f47ac10b-58cc-4372-a567-0e02b2c3d479";

    private LogAnalyzerService createService() throws IOException {
        return createService("svc1", LogFormat.MICROSERVICE,
                "2026-04-19 17:39:34.954 [masking-exec-1] TRACE messages.writer.in - Получено сообщение\n" +
                "2026-04-19 17:39:34.969 [ForkJoinPool.commonPool-worker-85] INFO  messages.writer.in - Сохранено\n" +
                "2026-04-18 21:26:23.773 [response-exec-1] ERROR enricher - Ошибка обогащения Id = " + UUID_1 + "\n" +
                "2026-04-18 21:22:05.799 [response-exec-2] ERROR availabilityCheckerLog - Кластер недоступен\n");
    }

    private LogAnalyzerService createService(String appName, LogFormat format, String logContent) throws IOException {
        Path logsDir = tempDir.resolve(appName + "/logs");
        Files.createDirectories(logsDir);
        Files.writeString(logsDir.resolve("app.log"), logContent);

        LogAnalyzerConfig config = new LogAnalyzerConfig();
        LogAnalyzerConfig.Source source = new LogAnalyzerConfig.Source();
        source.setName(appName);
        source.setLogPath(logsDir.toString());
        source.setConnection(LogAnalyzerConfig.Source.ConnectionType.LOCAL);
        source.setLogFormat(format);
        config.setSources(List.of(source));

        return new LogAnalyzerService(config, new LogFileParser(), new LogStore());
    }

    @Test
    void shouldFindErrorsInLogFile() throws IOException {
        LogAnalyzerService service = createService();
        List<LogAnalysisResult> results = service.analyzeErrors(List.of("svc1"), null, null, null, null);

        assertEquals(1, results.size());
        assertEquals("svc1", results.get(0).app());
        assertEquals(2, results.get(0).errorCount());
    }

    @Test
    void shouldReportCorrectTotalLinesScanned() throws IOException {
        LogAnalyzerService service = createService();
        List<LogAnalysisResult> results = service.analyzeErrors(List.of("svc1"), null, null, null, null);

        assertEquals(1, results.size());
        assertEquals(4, results.get(0).totalLinesScanned());
        assertEquals(2, results.get(0).errorCount());
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
        Instant from = Instant.parse("2026-04-19T17:00:00Z");
        Instant to   = Instant.parse("2026-04-19T18:00:00Z");

        // Only TRACE and INFO entries fall in this range — no ERROR in range with default levels
        List<LogAnalysisResult> results = service.analyzeErrors(null, from, to, List.of("TRACE", "INFO"), null);

        assertEquals(1, results.size());
        assertEquals(2, results.get(0).errorCount());
    }

    @Test
    void shouldFilterByLevel() throws IOException {
        LogAnalyzerService service = createService();
        List<LogAnalysisResult> results = service.analyzeErrors(null, null, null, List.of("TRACE"), null);

        assertEquals(1, results.size());
        assertEquals(1, results.get(0).errorCount());
        assertEquals("TRACE", results.get(0).errors().get(0).level());
    }

    @Test
    void shouldFilterByContains() throws IOException {
        LogAnalyzerService service = createService();
        List<LogAnalysisResult> results = service.analyzeErrors(null, null, null, List.of("ERROR"), "обогащения");

        assertEquals(1, results.size());
        assertEquals(1, results.get(0).errorCount());
        assertTrue(results.get(0).errors().get(0).message().contains("обогащения"));
    }

    @Test
    void shouldReturnEmptyForNonexistentPath() {
        LogAnalyzerConfig config = new LogAnalyzerConfig();
        LogAnalyzerConfig.Source source = new LogAnalyzerConfig.Source();
        source.setName("ghost");
        source.setLogPath("/nonexistent/path");
        source.setConnection(LogAnalyzerConfig.Source.ConnectionType.LOCAL);
        config.setSources(List.of(source));

        LogAnalyzerService service = new LogAnalyzerService(config, new LogFileParser(), new LogStore());
        assertTrue(service.analyzeErrors(null, null, null, null, null).isEmpty());
    }

    @Test
    void shouldReturnStats() throws IOException {
        LogAnalyzerService service = createService();
        LogStats stats = service.getStats(List.of("svc1"), null, null);

        assertEquals(4, stats.totalScanned());
        assertEquals(2, stats.totalErrors());
        assertTrue(stats.byLevel().containsKey("ERROR"));
        assertEquals(2L, stats.byLevel().get("ERROR"));
        assertFalse(stats.topMessages().isEmpty());
    }

    @Test
    void shouldFindByTraceId() throws IOException {
        LogAnalyzerService service = createService();
        List<TraceResult> results = service.findByTraceId(UUID_1, null, null, null);

        assertEquals(1, results.size());
        assertEquals("svc1", results.get(0).app());
        assertEquals(1, results.get(0).entries().size());
        assertTrue(results.get(0).entries().get(0).message().contains(UUID_1));
    }

    @Test
    void shouldFindByTraceIdAcrossApps() throws IOException {
        // svc1 has the UUID in an ERROR, svc2 also has it in an INFO
        Path svc1Logs = tempDir.resolve("svc1/logs");
        Path svc2Logs = tempDir.resolve("svc2/logs");
        Files.createDirectories(svc1Logs);
        Files.createDirectories(svc2Logs);
        Files.writeString(svc1Logs.resolve("app.log"),
                "2026-04-19 17:39:34.969 [worker-85] INFO  messages.writer.in - Сохранено Id = " + UUID_1 + "\n");
        Files.writeString(svc2Logs.resolve("app.log"),
                "2026-04-18 21:26:23.773 [response-exec-1] ERROR enricher - Ошибка Id = " + UUID_1 + "\n");

        LogAnalyzerConfig config = new LogAnalyzerConfig();
        LogAnalyzerConfig.Source s1 = new LogAnalyzerConfig.Source();
        s1.setName("svc1"); s1.setLogPath(svc1Logs.toString());
        s1.setLogFormat(LogFormat.MICROSERVICE);
        LogAnalyzerConfig.Source s2 = new LogAnalyzerConfig.Source();
        s2.setName("svc2"); s2.setLogPath(svc2Logs.toString());
        s2.setLogFormat(LogFormat.MICROSERVICE);
        config.setSources(List.of(s1, s2));

        LogAnalyzerService service = new LogAnalyzerService(config, new LogFileParser(), new LogStore());
        List<TraceResult> results = service.findByTraceId(UUID_1, null, null, null);

        assertEquals(2, results.size());
    }

    @Test
    void shouldFindErrorsInGzFile() throws IOException {
        Path gzDir = tempDir.resolve("gz-svc/logs");
        Files.createDirectories(gzDir);
        Path gzFile = gzDir.resolve("app.log.gz");
        String content = "2026-04-19 17:39:34.954 [masking-exec-1] TRACE messages.writer.in - Info msg\n" +
                         "2026-04-18 21:26:23.773 [response-exec-1] ERROR enricher - Gz error\n";
        try (var os = Files.newOutputStream(gzFile); var gz = new GZIPOutputStream(os)) {
            gz.write(content.getBytes());
        }

        LogAnalyzerConfig config = new LogAnalyzerConfig();
        LogAnalyzerConfig.Source source = new LogAnalyzerConfig.Source();
        source.setName("gz-svc");
        source.setLogPath(gzDir.toString());
        source.setLogFormat(LogFormat.MICROSERVICE);
        config.setSources(List.of(source));

        LogAnalyzerService service = new LogAnalyzerService(config, new LogFileParser(), new LogStore());
        List<LogAnalysisResult> results = service.analyzeErrors(List.of("gz-svc"), null, null, null, null);

        assertTrue(results.stream().anyMatch(r -> "gz-svc".equals(r.app()) && r.errorCount() == 1));
    }

    @Test
    void shouldGetAllEntries() throws IOException {
        LogAnalyzerService service = createService();
        List<com.loganalyzer.model.LogEntry> entries = service.getAllEntries(
                List.of("svc1"), null, null, null, null);

        assertEquals(4, entries.size());
    }

    @Test
    void shouldGetAllEntriesFilteredByLevel() throws IOException {
        LogAnalyzerService service = createService();
        List<com.loganalyzer.model.LogEntry> entries = service.getAllEntries(
                null, null, null, List.of("ERROR"), null);

        assertEquals(2, entries.size());
        assertTrue(entries.stream().allMatch(e -> "ERROR".equals(e.level())));
    }

    @Test
    void shouldGetAllEntriesSortedByTimestamp() throws IOException {
        LogAnalyzerService service = createService();
        List<com.loganalyzer.model.LogEntry> entries = service.getAllEntries(
                null, null, null, null, null);

        for (int i = 1; i < entries.size(); i++) {
            assertFalse(entries.get(i).timestamp().isBefore(entries.get(i - 1).timestamp()),
                    "Entries should be sorted ascending by timestamp");
        }
    }

    @Test
    void shouldGetAllEntriesFilteredByContains() throws IOException {
        LogAnalyzerService service = createService();
        List<com.loganalyzer.model.LogEntry> entries = service.getAllEntries(
                null, null, null, null, "Кластер");

        assertEquals(1, entries.size());
        assertTrue(entries.get(0).message().contains("Кластер"));
    }

    @Test
    void shouldParseIgniteFormatSource() throws IOException {
        LogAnalyzerService service = createService("ignite-node", LogFormat.IGNITE,
                "2026-04-20 00:00:08.205 [TRACE][Thread-BackupEventsKafkaLoader][backupEventsLog] Пустой poll\n" +
                "2026-04-17 00:00:14.656 [INFO ][Thread-OnlineFactsKafkaLoader][OnlineFactRegistryImpl] Команда на удаление\n" +
                "2026-04-19 15:34:05.009 [DEBUG][clean-scheduler-1][TemporaryHotProfileCacheImpl] Добавлен ключ\n");

        // with watchLevels empty, default is ERROR+FATAL — no errors in this data
        List<LogAnalysisResult> results = service.analyzeErrors(List.of("ignite-node"), null, null, null, null);
        assertTrue(results.isEmpty());

        // query TRACE explicitly
        results = service.analyzeErrors(List.of("ignite-node"), null, null, List.of("TRACE"), null);
        assertEquals(1, results.size());
        assertEquals(1, results.get(0).errorCount());
        assertEquals("Thread-BackupEventsKafkaLoader", results.get(0).errors().get(0).threadName());
    }
}
