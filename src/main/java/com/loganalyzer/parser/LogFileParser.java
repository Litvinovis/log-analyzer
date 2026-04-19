package com.loganalyzer.parser;

import com.loganalyzer.model.LogEntry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogFileParser {

    // Common log pattern: timestamp level [app] message
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z?)\\s+(\\w+)\\s+\\[([^\\]]+)\\]\\s+(.*)"
    );

    public List<LogEntry> parse(Path filePath, String appName) throws IOException {
        List<LogEntry> entries = new ArrayList<>();
        String fileName = filePath.getFileName().toString();

        if (fileName.endsWith(".gz")) {
            return parseGzFile(filePath, appName);
        }

        try (var reader = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                LogEntry entry = parseLine(line, appName, filePath);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    public List<LogEntry> parseGzFile(Path filePath, String appName) throws IOException {
        List<LogEntry> entries = new ArrayList<>();
        try (InputStream is = Files.newInputStream(filePath);
             var gzStream = new java.util.zip.GZIPInputStream(is);
             var reader = new BufferedReader(new InputStreamReader(gzStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LogEntry entry = parseLine(line, appName, filePath);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    LogEntry parseLine(String line, String appName, Path sourceFile) {
        if (line == null || line.isBlank()) {
            return null;
        }

        Matcher matcher = LOG_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return null;
        }

        Instant timestamp = parseTimestamp(matcher.group(1));
        if (timestamp == null) {
            return null;
        }

        String level = matcher.group(2).toUpperCase();
        String app = appName;
        if (app == null || app.isBlank()) {
            app = matcher.group(3);
        }
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
