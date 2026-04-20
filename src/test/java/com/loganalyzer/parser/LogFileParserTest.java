package com.loganalyzer.parser;

import com.loganalyzer.config.LogFormat;
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

    // ── Ignite format ──────────────────────────────────────────────────────────

    @Test
    void shouldParseIgniteLine() {
        LogEntry e = parser.parseLine(
                "2026-04-20 00:00:08.205 [TRACE][Thread-BackupEventsKafkaLoader][backupEventsLog] Пустой poll",
                "ignite-1", "test.log", LogFormat.IGNITE);

        assertNotNull(e);
        assertEquals("TRACE", e.level());
        assertEquals("ignite-1", e.app());
        assertEquals("Thread-BackupEventsKafkaLoader", e.threadName());
        assertEquals("backupEventsLog", e.loggerName());
        assertEquals("Пустой poll", e.message());
    }

    @Test
    void shouldParseIgniteLineWithPaddedLevel() {
        LogEntry e = parser.parseLine(
                "2026-04-20 00:15:00.000 [INFO ][list-clean-scheduler-1][serviceLogger] Шедулер запущен",
                null, "test.log", LogFormat.IGNITE);

        assertNotNull(e);
        assertEquals("INFO", e.level());
        assertEquals("list-clean-scheduler-1", e.threadName());
    }

    // ── Microservice format ────────────────────────────────────────────────────

    @Test
    void shouldParseMicroserviceLine() {
        LogEntry e = parser.parseLine(
                "2026-04-19 17:39:34.954 [masking-exec-1] TRACE messages.writer.in - Получено сообщение",
                "order-service", "test.log", LogFormat.MICROSERVICE);

        assertNotNull(e);
        assertEquals("TRACE", e.level());
        assertEquals("order-service", e.app());
        assertEquals("masking-exec-1", e.threadName());
        assertEquals("messages.writer.in", e.loggerName());
        assertEquals("Получено сообщение", e.message());
    }

    @Test
    void shouldParseMicroserviceErrorLine() {
        LogEntry e = parser.parseLine(
                "2026-04-18 21:26:23.773 [response-exec-1] ERROR enricher - Ошибка при обогащении транзакции",
                null, "test.log", LogFormat.MICROSERVICE);

        assertNotNull(e);
        assertEquals("ERROR", e.level());
        assertEquals("enricher", e.loggerName());
    }

    @Test
    void shouldParseMicroserviceWarnWithPaddedLevel() {
        LogEntry e = parser.parseLine(
                "2026-04-19 15:30:58.619 [ignite-availability-scheduler] WARN  r.s.f.h.IgniteConnectionManager - Scheduler не остановился",
                null, "test.log", LogFormat.MICROSERVICE);

        assertNotNull(e);
        assertEquals("WARN", e.level());
    }

    // ── AUTO detection ─────────────────────────────────────────────────────────

    @Test
    void shouldAutoDetectIgniteFormat() {
        LogEntry e = parser.parseLine(
                "2026-04-17 00:00:14.656 [INFO ][Thread-OnlineFactsKafkaLoader][ru.sbrf.fraud.profile.OnlineFactRegistryImpl] Поступила команда",
                null, "test.log", LogFormat.AUTO);

        assertNotNull(e);
        assertEquals("INFO", e.level());
    }

    @Test
    void shouldAutoDetectMicroserviceFormat() {
        LogEntry e = parser.parseLine(
                "2026-04-20 00:08:30.701 [p95-monitor-clusters] TRACE ru.sbrf.fraud.service.ClientsHolder - Мониторинг p95",
                null, "test.log", LogFormat.AUTO);

        assertNotNull(e);
        assertEquals("TRACE", e.level());
    }

    @Test
    void shouldReturnNullForUnrecognizedLine() {
        assertNull(parser.parseLine("garbage line", null, "test.log", LogFormat.AUTO));
        assertNull(parser.parseLine("SLF4J(W): No SLF4J providers were found.", null, "test.log", LogFormat.AUTO));
        assertNull(parser.parseLine("   ", null, "test.log", LogFormat.AUTO));
    }

    // ── Multi-line accumulation ────────────────────────────────────────────────

    @Test
    void shouldAccumulateStackTrace() {
        List<String> lines = List.of(
                "2026-04-18 21:22:05.799 [response-exec-2] ERROR availabilityCheckerLog - Кластер стал недоступен",
                "ru.sbrf.fraud.exception.IgniteClusterUnavailableException",
                "\tat ru.sbrf.fraud.service.ClientImpl.internalExecute(ClientImpl.java:323)",
                "\tat ru.sbrf.fraud.service.ClientImpl.execute(ClientImpl.java:222)",
                "",
                "2026-04-18 21:22:06.000 [response-exec-2] INFO availabilityCheckerLog - Восстановление"
        );

        List<LogEntry> entries = parser.parseLines(lines, "svc", "test.log", LogFormat.MICROSERVICE);

        assertEquals(2, entries.size());
        LogEntry error = entries.get(0);
        assertEquals("ERROR", error.level());
        assertNotNull(error.stackTrace());
        assertTrue(error.stackTrace().contains("IgniteClusterUnavailableException"));
        assertTrue(error.stackTrace().contains("ClientImpl.java:323"));
        assertNull(entries.get(1).stackTrace());
    }

    @Test
    void shouldAccumulateJsonContinuation() {
        List<String> lines = List.of(
                "2026-04-17 18:44:36.333 [INFO ][Thread-OnlineFactsKafkaLoader][loaderLogger] Получено сообщение - {",
                "  \"kind\" : \"delete\",",
                "  \"id\" : \"9c7cd866-9598-39c1-91d1-74be58f7bbde\"",
                "}",
                "2026-04-17 18:44:36.400 [INFO ][Thread-OnlineFactsKafkaLoader][loaderLogger] Следующее"
        );

        List<LogEntry> entries = parser.parseLines(lines, "ignite", "test.log", LogFormat.IGNITE);

        assertEquals(2, entries.size());
        assertNotNull(entries.get(0).stackTrace());
        assertTrue(entries.get(0).stackTrace().contains("\"kind\""));
    }

    // ── File parsing ───────────────────────────────────────────────────────────

    @Test
    void shouldParseFileWithMicroserviceFormat(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("svc.log");
        Files.writeString(logFile,
                "2026-04-19 17:39:34.954 [masking-exec-1] TRACE messages.writer.in - Получено сообщение\n" +
                "2026-04-19 17:39:34.969 [ForkJoinPool.commonPool-worker-85] INFO  messages.writer.in - Сохранено в игнайт\n" +
                "2026-04-18 21:26:23.773 [response-exec-1] ERROR enricher - Ошибка при обогащении\n"
        );

        List<LogEntry> entries = parser.parseWithFormat(logFile, "svc", LogFormat.MICROSERVICE);

        assertEquals(3, entries.size());
        assertEquals("TRACE", entries.get(0).level());
        assertEquals("INFO", entries.get(1).level());
        assertEquals("ERROR", entries.get(2).level());
    }

    @Test
    void shouldParseGzFile(@TempDir Path tempDir) throws IOException {
        Path gzFile = tempDir.resolve("app.log.gz");
        String content = "2026-04-19 17:39:34.954 [masking-exec-1] TRACE messages.writer.in - Получено\n" +
                         "2026-04-19 17:39:35.000 [worker-1] ERROR service.Svc - Ошибка\n";
        try (var os = Files.newOutputStream(gzFile);
             var gz = new GZIPOutputStream(os)) {
            gz.write(content.getBytes());
        }

        List<LogEntry> entries = parser.parseGzFile(gzFile, "gz-app", LogFormat.MICROSERVICE);

        assertEquals(2, entries.size());
        assertEquals("gz-app", entries.get(0).app());
    }

    @Test
    void shouldStreamFile(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("app.log");
        Files.writeString(logFile,
                "2026-04-19 17:39:34.954 [masking-exec-1] TRACE messages.writer.in - Msg1\n" +
                "2026-04-19 17:39:35.000 [worker-1] ERROR service.Svc - Msg2\n"
        );

        List<LogEntry> entries;
        try (var stream = parser.stream(logFile, "svc")) {
            entries = stream.toList();
        }

        assertEquals(2, entries.size());
    }
}
