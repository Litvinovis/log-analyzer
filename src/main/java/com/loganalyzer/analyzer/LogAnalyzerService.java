package com.loganalyzer.analyzer;

import com.loganalyzer.config.LogAnalyzerConfig;
import com.loganalyzer.model.JobResponse;
import com.loganalyzer.model.LogAnalysisResult;
import com.loganalyzer.model.LogEntry;
import com.loganalyzer.model.LogStats;
import com.loganalyzer.parser.LogFileParser;
import com.loganalyzer.storage.LogStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class LogAnalyzerService {

    private final LogAnalyzerConfig config;
    private final LogFileParser parser;
    private final LogStore store;

    private record JobEntry(String status, List<LogAnalysisResult> results) {}
    private final ConcurrentHashMap<String, JobEntry> jobs = new ConcurrentHashMap<>();

    public LogAnalyzerService(LogAnalyzerConfig config, LogFileParser parser, LogStore store) {
        this.config = config;
        this.parser = parser;
        this.store = store;
    }

    public List<LogAnalysisResult> analyzeErrors(
            List<String> apps, Instant from, Instant to, List<String> levels, String contains) {

        List<LogAnalysisResult> results = new ArrayList<>();
        Set<String> appSet = apps != null && !apps.isEmpty() ? Set.copyOf(apps) : null;

        for (String logPath : config.getLogPaths()) {
            Path basePath = Paths.get(logPath);
            if (!Files.exists(basePath)) continue;

            for (Path logFile : findLogFiles(basePath)) {
                String app = extractAppName(logFile, logPath);
                if (appSet != null && !appSet.contains(app)) continue;

                ParseResult parsed = filterEntries(logFile, from, to, levels, contains);
                if (!parsed.errors().isEmpty()) {
                    results.add(new LogAnalysisResult(
                            app, Instant.now(), parsed.errors(), parsed.totalLines(), parsed.errors().size()
                    ));
                }
            }
        }
        return results;
    }

    public LogStats getStats(List<String> apps, Instant from, Instant to) {
        List<LogAnalysisResult> results = analyzeErrors(apps, from, to, null, null);

        long totalScanned = results.stream().mapToLong(LogAnalysisResult::totalLinesScanned).sum();
        long totalErrors  = results.stream().mapToLong(LogAnalysisResult::errorCount).sum();

        Map<String, Long> byLevel = results.stream()
                .flatMap(r -> r.errors().stream())
                .collect(Collectors.groupingBy(LogEntry::level, Collectors.counting()));

        Map<String, Long> byApp = results.stream()
                .collect(Collectors.toMap(LogAnalysisResult::app, LogAnalysisResult::errorCount));

        List<LogStats.TopMessage> topMessages = results.stream()
                .flatMap(r -> r.errors().stream())
                .collect(Collectors.groupingBy(LogEntry::message, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> new LogStats.TopMessage(e.getKey(), e.getValue()))
                .toList();

        Map<String, Long> byHour = results.stream()
                .flatMap(r -> r.errors().stream())
                .collect(Collectors.groupingBy(
                        e -> e.timestamp().truncatedTo(ChronoUnit.HOURS).toString(),
                        Collectors.counting()
                ));

        return new LogStats(totalScanned, totalErrors, byLevel, byApp, topMessages, byHour);
    }

    @Async("logAnalyzerExecutor")
    public void analyzeAsync(String jobId, List<String> apps, Instant from, Instant to,
                             List<String> levels, String contains) {
        jobs.put(jobId, new JobEntry("RUNNING", null));
        try {
            List<LogAnalysisResult> results = analyzeErrors(apps, from, to, levels, contains);
            jobs.put(jobId, new JobEntry("COMPLETED", results));
        } catch (Exception e) {
            jobs.put(jobId, new JobEntry("FAILED", null));
        }
    }

    public Optional<JobResponse> getJob(String jobId) {
        return Optional.ofNullable(jobs.get(jobId))
                .map(j -> new JobResponse(jobId, j.status(), j.results()));
    }

    private List<Path> findLogFiles(Path basePath) {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(basePath)) {
            files.addAll(stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".log") || name.endsWith(".log.gz") || name.endsWith(".gz");
                    })
                    .toList());
        } catch (IOException ignored) {}
        return files;
    }

    private String extractAppName(Path filePath, String basePathStr) {
        String path = filePath.toString();
        String basePath = Paths.get(basePathStr).toString();
        if (path.startsWith(basePath)) {
            String relative = path.substring(basePath.length()).substring(1);
            int sep = relative.indexOf('/');
            if (sep > 0) return relative.substring(0, sep);
            return filePath.getFileName().toString().replaceAll("\\.(log|gz)$", "");
        }
        return filePath.getFileName().toString().replaceAll("\\.(log|gz)$", "");
    }

    private record ParseResult(List<LogEntry> errors, long totalLines) {}

    private ParseResult filterEntries(Path logFile, Instant from, Instant to,
                                      List<String> levels, String contains) {
        String name = logFile.getFileName().toString();
        boolean isGz = name.endsWith(".gz");

        try {
            long fileSize = Files.size(logFile);
            long maxCacheBytes = (long) config.getMaxCacheFileSizeMb() * 1024 * 1024;

            if (fileSize <= maxCacheBytes) {
                List<LogEntry> all = store.getOrLoad(logFile, () -> {
                    try {
                        return isGz ? parser.parseGzFile(logFile, null) : parser.parse(logFile, null);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
                return applyFilters(all, from, to, levels, contains);
            }

            // Large file: stream without caching
            java.util.concurrent.atomic.AtomicLong total = new java.util.concurrent.atomic.AtomicLong(0);
            try (Stream<LogEntry> stream = isGz ? parser.streamGz(logFile, null) : parser.stream(logFile, null)) {
                List<LogEntry> errors = stream
                        .peek(e -> total.incrementAndGet())
                        .filter(e -> matchesLevel(e, levels))
                        .filter(e -> from == null || !e.timestamp().isBefore(from))
                        .filter(e -> to == null || !e.timestamp().isAfter(to))
                        .filter(e -> contains == null || e.message().contains(contains))
                        .toList();
                return new ParseResult(errors, total.get());
            }
        } catch (IOException | UncheckedIOException e) {
            return new ParseResult(List.of(), 0);
        }
    }

    private ParseResult applyFilters(List<LogEntry> all, Instant from, Instant to,
                                     List<String> levels, String contains) {
        List<LogEntry> filtered = all.stream()
                .filter(e -> matchesLevel(e, levels))
                .filter(e -> from == null || !e.timestamp().isBefore(from))
                .filter(e -> to == null || !e.timestamp().isAfter(to))
                .filter(e -> contains == null || e.message().contains(contains))
                .toList();
        return new ParseResult(filtered, all.size());
    }

    private boolean matchesLevel(LogEntry entry, List<String> levels) {
        if (levels == null || levels.isEmpty()) {
            return entry.level().equalsIgnoreCase("ERROR") || entry.level().equalsIgnoreCase("FATAL");
        }
        return levels.stream().anyMatch(l -> l.equalsIgnoreCase(entry.level()));
    }
}
