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
     * Main multi-line parser. Accepts any Iterable<String> so callers can pass
     * a List or a Stream::iterator without loading the whole file into memory first.
     * Groups continuation lines (stack traces, JSON) into the stackTrace field.
     */
    public List<LogEntry> parseLines(Iterable<String> lines, String appName, String sourceFile, LogFormat format) {
        List<LogEntry> result = new ArrayList<>();
        LogEntry current = null;
        StringBuilder continuation = new StringBuilder();

        for (String line : lines) {
            if (line == null) continue;
            if (line.isBlank()) {
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

    /**
     * Variant with inline time filtering: entries outside [from, to] are discarded during parse,
     * so they never accumulate in memory. Intended for large files that bypass the cache.
     * Since log files are chronologically ordered, parsing stops early when timestamp > to.
     */
    public List<LogEntry> parseLines(Iterable<String> lines, String appName, String sourceFile,
                                     LogFormat format, Instant from, Instant to) {
        List<LogEntry> result = new ArrayList<>();
        LogEntry current = null;
        StringBuilder continuation = new StringBuilder();

        for (String line : lines) {
            if (line == null) continue;
            if (line.isBlank()) {
                if (current != null) {
                    addIfInRange(result, withStackTrace(current, continuation.toString()), from, to);
                    current = null;
                    continuation.setLength(0);
                }
                continue;
            }

            if (ENTRY_START.matcher(line).find()) {
                if (current != null) {
                    addIfInRange(result, withStackTrace(current, continuation.toString()), from, to);
                    continuation.setLength(0);
                }
                LogEntry next = parseLine(line, appName, sourceFile, format);
                // Early exit: file is sorted chronologically, so nothing after this will be in range
                if (next != null && to != null && next.timestamp().isAfter(to)) break;
                current = next;
            } else if (current != null) {
                if (!continuation.isEmpty()) continuation.append('\n');
                continuation.append(line);
            }
        }
        if (current != null) {
            addIfInRange(result, withStackTrace(current, continuation.toString()), from, to);
        }
        return result;
    }

    private void addIfInRange(List<LogEntry> result, LogEntry e, Instant from, Instant to) {
        if (e == null) return;
        if (from != null && e.timestamp().isBefore(from)) return;
        if (to != null && e.timestamp().isAfter(to)) return;
        result.add(e);
    }

    // Backward-compat overload for List<String>
    public List<LogEntry> parseLines(List<String> lines, String appName, String sourceFile, LogFormat format) {
        return parseLines((Iterable<String>) lines, appName, sourceFile, format);
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

    /**
     * Reads the file line-by-line using a stream (avoids loading all lines into memory first).
     */
    public List<LogEntry> parseWithFormat(Path filePath, String appName, LogFormat format) throws IOException {
        return parseWithFormat(filePath, appName, format, null, null);
    }

    public List<LogEntry> parseWithFormat(Path filePath, String appName, LogFormat format,
                                          Instant from, Instant to) throws IOException {
        if (filePath.getFileName().toString().endsWith(".gz")) {
            return parseGzFile(filePath, appName, format, from, to);
        }
        try (Stream<String> lines = Files.lines(filePath, StandardCharsets.UTF_8)) {
            if (from != null || to != null) {
                return parseLines(lines::iterator, appName, filePath.toString(), format, from, to);
            }
            return parseLines(lines::iterator, appName, filePath.toString(), format);
        }
    }

    public List<LogEntry> parse(Path filePath, String appName) throws IOException {
        return parseWithFormat(filePath, appName, LogFormat.AUTO);
    }

    public List<LogEntry> parseGzFile(Path filePath, String appName, LogFormat format) throws IOException {
        return parseGzFile(filePath, appName, format, null, null);
    }

    public List<LogEntry> parseGzFile(Path filePath, String appName, LogFormat format,
                                      Instant from, Instant to) throws IOException {
        try (var is = Files.newInputStream(filePath);
             var gz = new GZIPInputStream(is);
             var reader = new BufferedReader(new InputStreamReader(gz, StandardCharsets.UTF_8))) {
            if (from != null || to != null) {
                return parseLines(reader.lines()::iterator, appName, filePath.toString(), format, from, to);
            }
            return parseLines(reader.lines()::iterator, appName, filePath.toString(), format);
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
