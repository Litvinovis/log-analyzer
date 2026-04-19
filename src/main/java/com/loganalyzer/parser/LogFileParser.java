package com.loganalyzer.parser;

import com.loganalyzer.config.LogAnalyzerConfig;
import com.loganalyzer.model.LogEntry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LogFileParser {

    static final String DEFAULT_PATTERN =
            "^(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z?)\\s+(\\w+)\\s+\\[([^\\]]+)\\]\\s+(.*)";

    private final Pattern logPattern;

    @Autowired
    public LogFileParser(LogAnalyzerConfig config) {
        String raw = config.getLogPattern() != null ? config.getLogPattern() : DEFAULT_PATTERN;
        this.logPattern = Pattern.compile(raw);
    }

    public LogFileParser() {
        this.logPattern = Pattern.compile(DEFAULT_PATTERN);
    }

    public Stream<LogEntry> stream(Path filePath, String appName) throws IOException {
        var reader = Files.newBufferedReader(filePath);
        return reader.lines()
                .map(line -> parseLine(line, appName, filePath))
                .filter(Objects::nonNull)
                .onClose(() -> {
                    try { reader.close(); } catch (IOException ignored) {}
                });
    }

    public Stream<LogEntry> streamGz(Path filePath, String appName) throws IOException {
        var is = Files.newInputStream(filePath);
        var gz = new GZIPInputStream(is);
        var reader = new BufferedReader(new InputStreamReader(gz));
        return reader.lines()
                .map(line -> parseLine(line, appName, filePath))
                .filter(Objects::nonNull)
                .onClose(() -> {
                    try { reader.close(); } catch (IOException ignored) {}
                });
    }

    public List<LogEntry> parse(Path filePath, String appName) throws IOException {
        if (filePath.getFileName().toString().endsWith(".gz")) {
            return parseGzFile(filePath, appName);
        }
        try (Stream<LogEntry> s = stream(filePath, appName)) {
            return s.toList();
        }
    }

    public List<LogEntry> parseGzFile(Path filePath, String appName) throws IOException {
        try (Stream<LogEntry> s = streamGz(filePath, appName)) {
            return s.toList();
        }
    }

    LogEntry parseLine(String line, String appName, Path sourceFile) {
        if (line == null || line.isBlank()) {
            return null;
        }

        Matcher matcher = logPattern.matcher(line);
        if (!matcher.matches()) {
            return null;
        }

        Instant timestamp = parseTimestamp(matcher.group(1));
        if (timestamp == null) {
            return null;
        }

        String level = matcher.group(2).toUpperCase();
        String app = (appName != null && !appName.isBlank()) ? appName : matcher.group(3);
        String message = matcher.group(4);

        return new LogEntry(timestamp, level, app, message, sourceFile.toString());
    }

    private Instant parseTimestamp(String timestampStr) {
        if (timestampStr == null || timestampStr.isBlank()) {
            return null;
        }
        try {
            if (timestampStr.endsWith("Z")) {
                return Instant.parse(timestampStr);
            }
            String normalized = timestampStr.replace(' ', 'T');
            return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneId.of("UTC"))
                    .toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
