package com.loganalyzer.parser;

import com.loganalyzer.config.LogFormat;
import com.loganalyzer.model.LogEntry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.springframework.stereotype.Component;

@Component
public class LogFileParser {

    // Ignite: 2026-04-20 00:00:08.205 [TRACE][Thread-Loader][backupEventsLog] message
    static final Pattern IGNITE_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d+) \\[(\\w+)\\s*]\\[([^]]+)]\\[([^]]+)] ?(.*)"
    );

    // Microservice: 2026-04-19 17:39:34.954 [masking-exec-1] TRACE messages.writer.in - message
    static final Pattern MICRO_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d+) \\[([^]]+)] (\\w+)\\s+(\\S+) - (.*)"
    );

    // Detects start of a new log entry
    static final Pattern ENTRY_START = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d+"
    );

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public LogFileParser() {}

    /**
     * Main multi-line parser: groups continuation lines (stack traces, JSON bodies)
     * into the stackTrace field of the preceding entry.
     */
    public List<LogEntry> parseLines(List<String> lines, String appName, String sourceFile, LogFormat format) {
        List<LogEntry> result = new ArrayList<>();
        LogEntry current = null;
        StringBuilder continuation = new StringBuilder();

        for (String line : lines) {
            if (line == null) continue;
            if (line.isBlank()) {
                // blank lines separate entries but don't belong to continuation
                if (current != null) {
                    result.add(withStackTrace(current, continuation.toString()));
                    current = null;
                    continuation.setLength(0);
                }
                continue;
            }

            if (ENTRY_START.matcher(line).find()) {
                if (current != null) {
                    result.add(withStackTrace(current, continuation.toString()));
                    continuation.setLength(0);
                }
                current = parseLine(line, appName, sourceFile, format);
            } else if (current != null) {
                if (!continuation.isEmpty()) continuation.append('\n');
                continuation.append(line);
            }
        }
        if (current != null) {
            result.add(withStackTrace(current, continuation.toString()));
        }
        return result;
    }

    private LogEntry withStackTrace(LogEntry e, String st) {
        String trimmed = st.stripTrailing();
        if (trimmed.isEmpty()) return e;
        return new LogEntry(e.timestamp(), e.level(), e.app(), e.message(),
                e.sourceFile(), e.threadName(), e.loggerName(), trimmed);
    }

    LogEntry parseLine(String line, String appName, String sourceFile, LogFormat format) {
        if (line == null || line.isBlank()) return null;

        if (format == LogFormat.IGNITE || format == LogFormat.AUTO) {
            Matcher m = IGNITE_PATTERN.matcher(line);
            if (m.matches()) {
                Instant ts = parseTimestamp(m.group(1));
                if (ts == null) return null;
                String level = m.group(2).trim().toUpperCase();
                String thread = m.group(3);
                String logger = m.group(4);
                String app = (appName != null && !appName.isBlank()) ? appName : logger;
                return new LogEntry(ts, level, app, m.group(5), sourceFile, thread, logger, null);
            }
        }
        if (format == LogFormat.MICROSERVICE || format == LogFormat.AUTO) {
            Matcher m = MICRO_PATTERN.matcher(line);
            if (m.matches()) {
                Instant ts = parseTimestamp(m.group(1));
                if (ts == null) return null;
                String thread = m.group(2);
                String level = m.group(3).trim().toUpperCase();
                String logger = m.group(4);
                String app = (appName != null && !appName.isBlank()) ? appName : logger;
                return new LogEntry(ts, level, app, m.group(5), sourceFile, thread, logger, null);
            }
        }
        return null;
    }

    // Kept for backward compat with tests
    LogEntry parseLine(String line, String appName, Path sourceFile) {
        return parseLine(line, appName, sourceFile.toString(), LogFormat.AUTO);
    }

    public List<LogEntry> parseWithFormat(Path filePath, String appName, LogFormat format) throws IOException {
        if (filePath.getFileName().toString().endsWith(".gz")) {
            return parseGzFile(filePath, appName, format);
        }
        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        return parseLines(lines, appName, filePath.toString(), format);
    }

    public List<LogEntry> parse(Path filePath, String appName) throws IOException {
        return parseWithFormat(filePath, appName, LogFormat.AUTO);
    }

    public List<LogEntry> parseGzFile(Path filePath, String appName, LogFormat format) throws IOException {
        try (var is = Files.newInputStream(filePath);
             var gz = new GZIPInputStream(is);
             var reader = new BufferedReader(new InputStreamReader(gz, StandardCharsets.UTF_8))) {
            List<String> lines = reader.lines().toList();
            return parseLines(lines, appName, filePath.toString(), format);
        }
    }

    public List<LogEntry> parseGzFile(Path filePath, String appName) throws IOException {
        return parseGzFile(filePath, appName, LogFormat.AUTO);
    }

    public Stream<LogEntry> stream(Path filePath, String appName) throws IOException {
        return parseWithFormat(filePath, appName, LogFormat.AUTO).stream();
    }

    public Stream<LogEntry> streamGz(Path filePath, String appName) throws IOException {
        return parseGzFile(filePath, appName, LogFormat.AUTO).stream();
    }

    private Instant parseTimestamp(String ts) {
        if (ts == null || ts.isBlank()) return null;
        try {
            return LocalDateTime.parse(ts, TIMESTAMP_FMT)
                    .toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
