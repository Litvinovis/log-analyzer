package com.loganalyzer.analyzer;

import com.loganalyzer.config.LogAnalyzerConfig;
import com.loganalyzer.config.LogAnalyzerConfig.Source.ConnectionType;
import com.loganalyzer.config.LogFormat;
import com.loganalyzer.model.JobResponse;
import com.loganalyzer.model.LogAnalysisResult;
import com.loganalyzer.model.LogEntry;
import com.loganalyzer.model.LogStats;
import com.loganalyzer.model.TraceResult;
import com.loganalyzer.parser.LogFileParser;
import com.loganalyzer.ssh.SshLogReader;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class LogAnalyzerService {

    private final LogAnalyzerConfig config;
    private final LogFileParser parser;
    private final LogStore store;
    private final SshLogReader sshLogReader;
    private final Executor executor;

    private record JobEntry(String status, List<LogAnalysisResult> results) {}
    private final ConcurrentHashMap<String, JobEntry> jobs = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public LogAnalyzerService(LogAnalyzerConfig config, LogFileParser parser, LogStore store,
                              SshLogReader sshLogReader,
                              @Qualifier("logAnalyzerExecutor") Executor executor) {
        this.config = config;
        this.parser = parser;
        this.store = store;
        this.sshLogReader = sshLogReader;
        this.executor = executor;
    }

    // Used in tests without SSH/executor
    public LogAnalyzerService(LogAnalyzerConfig config, LogFileParser parser, LogStore store) {
        this(config, parser, store, null, Runnable::run);
    }

    public List<LogAnalysisResult> analyzeErrors(
            List<String> apps, Instant from, Instant to, List<String> levels, String contains) {

        Set<String> appSet = (apps != null && !apps.isEmpty()) ? Set.copyOf(apps) : null;
        List<LogAnalyzerConfig.Source> sources = config.getSources().stream()
                .filter(s -> appSet == null || appSet.contains(s.getName()))
                .toList();

        List<CompletableFuture<Optional<LogAnalysisResult>>> futures = sources.stream()
                .map(source -> CompletableFuture.supplyAsync(() -> {
                    List<LogEntry> all = loadEntries(source);
                    List<String> effectiveLevels = (levels != null && !levels.isEmpty())
                            ? levels : source.getWatchLevels();
                    ParseResult parsed = applyFilters(all, from, to, effectiveLevels, contains);
                    if (parsed.errors().isEmpty()) return Optional.<LogAnalysisResult>empty();
                    return Optional.of(new LogAnalysisResult(
                            source.getName(), Instant.now(),
                            parsed.errors(), all.size(), parsed.errors().size()));
                }, executor))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    /**
     * Finds all log entries across sources that contain the given traceId (UUID).
     * Searches message and stackTrace fields. No level filter — returns all levels.
     */
    public List<TraceResult> findByTraceId(String traceId, List<String> apps) {
        List<TraceResult> results = new ArrayList<>();
        Set<String> appSet = (apps != null && !apps.isEmpty()) ? Set.copyOf(apps) : null;

        for (LogAnalyzerConfig.Source source : config.getSources()) {
            if (appSet != null && !appSet.contains(source.getName())) continue;

            List<LogEntry> matching = loadEntries(source).stream()
                    .filter(e -> mentionsId(e, traceId))
                    .toList();

            if (!matching.isEmpty()) {
                results.add(new TraceResult(source.getName(), matching));
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

    public List<LogEntry> getAllEntries(
            List<String> apps, Instant from, Instant to, List<String> levels, String contains) {

        Set<String> appSet = (apps != null && !apps.isEmpty()) ? Set.copyOf(apps) : null;
        List<String> effectiveLevels = (levels != null && !levels.isEmpty()) ? levels : null;
        List<LogAnalyzerConfig.Source> sources = config.getSources().stream()
                .filter(s -> appSet == null || appSet.contains(s.getName()))
                .toList();

        List<CompletableFuture<List<LogEntry>>> futures = sources.stream()
                .map(source -> CompletableFuture.supplyAsync(() -> {
                    List<LogEntry> all = loadEntries(source);
                    return all.stream()
                            .filter(e -> effectiveLevels == null
                                    || effectiveLevels.stream().anyMatch(l -> l.equalsIgnoreCase(e.level())))
                            .filter(e -> from == null || !e.timestamp().isBefore(from))
                            .filter(e -> to == null || !e.timestamp().isAfter(to))
                            .filter(e -> contains == null || mentionsId(e, contains))
                            .toList();
                }, executor))
                .toList();

        return futures.stream()
                .flatMap(f -> f.join().stream())
                .sorted(java.util.Comparator.comparing(LogEntry::timestamp))
                .toList();
    }

    public Optional<JobResponse> getJob(String jobId) {
        return Optional.ofNullable(jobs.get(jobId))
                .map(j -> new JobResponse(jobId, j.status(), j.results()));
    }

    private List<LogEntry> loadEntries(LogAnalyzerConfig.Source source) {
        if (source.getConnection() == ConnectionType.SSH) {
            return loadRemoteEntries(source);
        }
        return loadLocalEntries(source);
    }

    private List<LogEntry> loadLocalEntries(LogAnalyzerConfig.Source source) {
        List<LogEntry> all = new ArrayList<>();
        Path basePath = Paths.get(source.getLogPath());
        if (!Files.exists(basePath)) return all;

        for (Path logFile : findLocalLogFiles(basePath)) {
            List<LogEntry> entries = cachedOrParse(logFile, source.getName(), source.getLogFormat());
            all.addAll(entries);
        }
        return all;
    }

    private List<LogEntry> cachedOrParse(Path logFile, String appName, LogFormat format) {
        try {
            long fileSize = Files.size(logFile);
            long maxBytes = (long) config.getMaxCacheFileSizeMb() * 1024 * 1024;
            if (fileSize <= maxBytes) {
                return store.getOrLoad(logFile, () -> parseLocalFile(logFile, appName, format));
            }
            return parseLocalFile(logFile, appName, format);
        } catch (IOException | UncheckedIOException e) {
            return List.of();
        }
    }

    private List<LogEntry> parseLocalFile(Path file, String appName, LogFormat format) {
        try {
            return file.getFileName().toString().endsWith(".gz")
                    ? parser.parseGzFile(file, appName, format)
                    : parser.parseWithFormat(file, appName, format);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<LogEntry> loadRemoteEntries(LogAnalyzerConfig.Source source) {
        if (sshLogReader == null) return List.of();

        List<String> remoteFiles = new ArrayList<>(
                sshLogReader.listRemoteFiles(source, source.getLogPath()));

        // For Ignite: also check archive subdirectory
        if (source.getLogFormat() == LogFormat.IGNITE || source.getLogFormat() == LogFormat.AUTO) {
            remoteFiles.addAll(
                    sshLogReader.listRemoteFiles(source, source.getLogPath() + "/archive"));
        }

        List<LogEntry> all = new ArrayList<>();
        for (String remotePath : remoteFiles) {
            List<String> lines = sshLogReader.readRemoteLines(source, remotePath);
            all.addAll(parser.parseLines(lines, source.getName(), remotePath, source.getLogFormat()));
        }
        return all;
    }

    private List<Path> findLocalLogFiles(Path basePath) {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(basePath)) {
            files.addAll(stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".log") || name.endsWith(".log.gz") || name.endsWith(".gz");
                    })
                    .toList());
        } catch (IOException ignored) {}
        return files;
    }

    private record ParseResult(List<LogEntry> errors, long totalLines) {}

    private ParseResult applyFilters(List<LogEntry> all, Instant from, Instant to,
                                     List<String> levels, String contains) {
        List<LogEntry> filtered = all.stream()
                .filter(e -> matchesLevel(e, levels))
                .filter(e -> from == null || !e.timestamp().isBefore(from))
                .filter(e -> to == null || !e.timestamp().isAfter(to))
                .filter(e -> contains == null || mentionsId(e, contains))
                .toList();
        return new ParseResult(filtered, all.size());
    }

    private boolean matchesLevel(LogEntry entry, List<String> levels) {
        if (levels == null || levels.isEmpty()) {
            return entry.level().equalsIgnoreCase("ERROR") || entry.level().equalsIgnoreCase("FATAL");
        }
        return levels.stream().anyMatch(l -> l.equalsIgnoreCase(entry.level()));
    }

    private boolean mentionsId(LogEntry e, String id) {
        return (e.message() != null && e.message().contains(id))
                || (e.stackTrace() != null && e.stackTrace().contains(id));
    }
}
