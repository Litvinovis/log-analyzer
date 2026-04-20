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

import jakarta.annotation.PostConstruct;

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
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class LogAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(LogAnalyzerService.class);

    private final LogAnalyzerConfig config;
    private final LogFileParser parser;
    private final LogStore store;
    private final SshLogReader sshLogReader;
    private final Executor executor;

    private record JobEntry(String status, List<LogAnalysisResult> results, String errorMessage) {}
    private final ConcurrentHashMap<String, JobEntry> jobs = new ConcurrentHashMap<>();
    private final Semaphore analysisSlots;

    @org.springframework.beans.factory.annotation.Autowired
    public LogAnalyzerService(LogAnalyzerConfig config, LogFileParser parser, LogStore store,
                              SshLogReader sshLogReader,
                              @Qualifier("logAnalyzerExecutor") Executor executor) {
        this.config = config;
        this.parser = parser;
        this.store = store;
        this.sshLogReader = sshLogReader;
        this.executor = executor;
        this.analysisSlots = new Semaphore(config.getMaxConcurrentAnalyses());
    }

    // Used in tests without SSH/executor
    public LogAnalyzerService(LogAnalyzerConfig config, LogFileParser parser, LogStore store) {
        this(config, parser, store, null, Runnable::run);
    }

    @PostConstruct
    public void logConfig() {
        log.info("=== Log Analyzer configuration ===");
        log.info("SSH key path        : {}", config.getSshKeyPath());
        log.info("Log timezone        : {}", config.getLogTimezone());
        log.info("Cache TTL           : {} s", config.getCacheTtlSeconds());
        log.info("Max cache file      : {} MB", config.getMaxCacheFileSizeMb());
        log.info("Max concurrent anal.: {}", config.getMaxConcurrentAnalyses());
        log.info("Max analysis results: {}", config.getMaxAnalysisResults());
        log.info("Min free heap       : {} MB", config.getMinFreeHeapMb());
        log.info("Sources ({}):", config.getSources().size());
        for (LogAnalyzerConfig.Source s : config.getSources()) {
            log.info("  [{}] connection={} format={} path={}",
                    s.getName(), s.getConnection(), s.getLogFormat(), s.getLogPath());
            if (s.getConnection() == ConnectionType.SSH) {
                log.info("    ssh={}@{}:{}", s.getSshUser(), s.getSshHost(), s.getSshPort());
            } else {
                Path p = Paths.get(s.getLogPath());
                if (Files.exists(p)) {
                    log.info("    path exists: {}", p.toAbsolutePath());
                } else {
                    log.warn("    path NOT FOUND: {}", p.toAbsolutePath());
                }
            }
        }
        log.info("==================================");
    }

    public List<String> getConfiguredApps() {
        return config.getSources().stream()
                .map(LogAnalyzerConfig.Source::getName)
                .toList();
    }

    public List<LogAnalysisResult> analyzeErrors(
            List<String> apps, Instant from, Instant to, List<String> levels, String contains) {

        Set<String> appSet = (apps != null && !apps.isEmpty()) ? Set.copyOf(apps) : null;
        List<LogAnalyzerConfig.Source> sources = config.getSources().stream()
                .filter(s -> appSet == null || appSet.contains(s.getName()))
                .toList();

        log.debug("analyzeErrors: apps={} from={} to={} levels={} contains={} — querying {} source(s)",
                apps, from, to, levels, contains, sources.size());

        List<CompletableFuture<Optional<LogAnalysisResult>>> futures = sources.stream()
                .map(source -> CompletableFuture.supplyAsync(() -> {
                    List<LogEntry> all = loadEntries(source, from, to);
                    log.debug("[{}] loaded {} entries, filtering with levels={}", source.getName(), all.size(), levels);
                    ParseResult parsed = applyFilters(all, from, to, levels, contains);
                    log.debug("[{}] after filter: {} entries matched", source.getName(), parsed.errors().size());
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

    public List<TraceResult> findByTraceId(String traceId, List<String> apps, Instant from, Instant to) {
        List<TraceResult> results = new ArrayList<>();
        Set<String> appSet = (apps != null && !apps.isEmpty()) ? Set.copyOf(apps) : null;

        List<LogAnalyzerConfig.Source> sources = config.getSources().stream()
                .filter(s -> appSet == null || appSet.contains(s.getName()))
                .toList();

        log.debug("findByTraceId: traceId='{}' apps={} from={} to={} — searching {} source(s)",
                traceId, apps, from, to, sources.size());

        for (LogAnalyzerConfig.Source source : sources) {
            List<LogEntry> all = loadEntries(source, from, to);
            log.debug("[{}] loaded {} entries, searching for '{}'", source.getName(), all.size(), traceId);
            List<LogEntry> matching = all.stream()
                    .filter(e -> from == null || !e.timestamp().isBefore(from))
                    .filter(e -> to == null || !e.timestamp().isAfter(to))
                    .filter(e -> mentionsId(e, traceId))
                    .toList();
            log.debug("[{}] found {} entries containing traceId", source.getName(), matching.size());
            if (!matching.isEmpty()) {
                results.add(new TraceResult(source.getName(), matching));
            }
        }
        return results;
    }

    public List<LogEntry> getAllEntries(
            List<String> apps, Instant from, Instant to, List<String> levels, String contains) {

        Set<String> appSet = (apps != null && !apps.isEmpty()) ? Set.copyOf(apps) : null;
        List<String> effectiveLevels = (levels != null && !levels.isEmpty()) ? levels : null;
        List<LogAnalyzerConfig.Source> sources = config.getSources().stream()
                .filter(s -> appSet == null || appSet.contains(s.getName()))
                .toList();

        log.debug("getAllEntries: apps={} from={} to={} levels={} contains={} — querying {} source(s)",
                apps, from, to, levels, contains, sources.size());

        List<CompletableFuture<List<LogEntry>>> futures = sources.stream()
                .map(source -> CompletableFuture.supplyAsync(() -> {
                    List<LogEntry> all = loadEntries(source, from, to);
                    List<LogEntry> filtered = all.stream()
                            .filter(e -> effectiveLevels == null
                                    || effectiveLevels.stream().anyMatch(l -> l.equalsIgnoreCase(e.level())))
                            .filter(e -> from == null || !e.timestamp().isBefore(from))
                            .filter(e -> to == null || !e.timestamp().isAfter(to))
                            .filter(e -> contains == null || mentionsId(e, contains))
                            .toList();
                    log.debug("[{}] loaded {} entries, after filter: {}", source.getName(), all.size(), filtered.size());
                    return filtered;
                }, executor))
                .toList();

        return futures.stream()
                .flatMap(f -> f.join().stream())
                .sorted(java.util.Comparator.comparing(LogEntry::timestamp))
                .toList();
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
        if (!analysisSlots.tryAcquire()) {
            String msg = "Сервер занят: достигнут лимит одновременных анализов ("
                    + config.getMaxConcurrentAnalyses() + "), повторите позже";
            log.warn("analyzeAsync: jobId={} rejected — {}", jobId, msg);
            jobs.put(jobId, new JobEntry("FAILED", null, msg));
            return;
        }
        if (!isHeapSufficient()) {
            analysisSlots.release();
            String msg = "Недостаточно памяти: свободно " + freeHeapMb() + " MB, требуется минимум "
                    + config.getMinFreeHeapMb() + " MB";
            log.warn("analyzeAsync: jobId={} rejected — {}", jobId, msg);
            jobs.put(jobId, new JobEntry("FAILED", null, msg));
            return;
        }
        jobs.put(jobId, new JobEntry("RUNNING", null, null));
        log.debug("analyzeAsync: jobId={} started (slots remaining: {})", jobId, analysisSlots.availablePermits());
        try {
            List<LogAnalysisResult> results = analyzeErrors(apps, from, to, levels, contains);
            jobs.put(jobId, new JobEntry("COMPLETED", results, null));
            log.debug("analyzeAsync: jobId={} completed, {} results", jobId, results.size());
        } catch (Exception e) {
            log.error("analyzeAsync: jobId={} failed", jobId, e);
            jobs.put(jobId, new JobEntry("FAILED", null, e.getMessage()));
        } finally {
            analysisSlots.release();
        }
    }

    private boolean isHeapSufficient() {
        Runtime rt = Runtime.getRuntime();
        long freeBytes = rt.maxMemory() - rt.totalMemory() + rt.freeMemory();
        return freeBytes >= (long) config.getMinFreeHeapMb() * 1024 * 1024;
    }

    private long freeHeapMb() {
        Runtime rt = Runtime.getRuntime();
        return (rt.maxMemory() - rt.totalMemory() + rt.freeMemory()) / 1024 / 1024;
    }

    public Optional<JobResponse> getJob(String jobId) {
        return Optional.ofNullable(jobs.get(jobId))
                .map(j -> new JobResponse(jobId, j.status(), j.results(), j.errorMessage()));
    }

    private List<LogEntry> loadEntries(LogAnalyzerConfig.Source source, Instant from, Instant to) {
        if (source.getConnection() == ConnectionType.SSH) {
            return loadRemoteEntries(source, from);
        }
        return loadLocalEntries(source, from, to);
    }

    private List<LogEntry> loadLocalEntries(LogAnalyzerConfig.Source source, Instant from, Instant to) {
        List<LogEntry> all = new ArrayList<>();
        Path basePath = Paths.get(source.getLogPath());

        if (!Files.exists(basePath)) {
            log.warn("[{}] log path does not exist: {}", source.getName(), basePath.toAbsolutePath());
            return all;
        }
        if (!Files.isDirectory(basePath)) {
            log.warn("[{}] log path is not a directory: {}", source.getName(), basePath.toAbsolutePath());
            return all;
        }

        List<Path> logFiles = findLocalLogFiles(basePath);
        log.debug("[{}] found {} log file(s) in {}", source.getName(), logFiles.size(), basePath);

        for (Path logFile : logFiles) {
            if (from != null && !isFileRelevant(logFile, from)) {
                log.debug("[{}] skip (last modified before window): {}", source.getName(), logFile.getFileName());
                continue;
            }
            log.debug("[{}] parsing: {}", source.getName(), logFile);
            List<LogEntry> entries = cachedOrParse(logFile, source.getName(), source.getLogFormat(), from, to);
            log.debug("[{}] parsed {} entries from {}", source.getName(), entries.size(), logFile.getFileName());
            all.addAll(entries);
        }
        return all;
    }

    private boolean isFileRelevant(Path file, Instant from) {
        try {
            return !Files.getLastModifiedTime(file).toInstant().isBefore(from);
        } catch (IOException e) {
            return true;
        }
    }

    private List<LogEntry> cachedOrParse(Path logFile, String appName, LogFormat format,
                                         Instant from, Instant to) {
        try {
            long fileSize = Files.size(logFile);
            long maxBytes = (long) config.getMaxCacheFileSizeMb() * 1024 * 1024;
            if (fileSize <= maxBytes) {
                // Small file: cache ALL entries, then filter after (cache is reusable across queries)
                return store.getOrLoad(logFile, () -> parseLocalFile(logFile, appName, format, null, null));
            }
            // Large file: parse with inline time filter to avoid holding full content in heap
            log.debug("File {} ({} MB) exceeds cache limit — parsing with inline filter from={} to={}",
                    logFile.getFileName(), fileSize / 1024 / 1024, from, to);
            return parseLocalFile(logFile, appName, format, from, to);
        } catch (IOException | UncheckedIOException e) {
            log.error("Failed to read file {}: {}", logFile, e.getMessage());
            return List.of();
        }
    }

    private List<LogEntry> parseLocalFile(Path file, String appName, LogFormat format,
                                          Instant from, Instant to) {
        try {
            return file.getFileName().toString().endsWith(".gz")
                    ? parser.parseGzFile(file, appName, format, from, to)
                    : parser.parseWithFormat(file, appName, format, from, to);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<LogEntry> loadRemoteEntries(LogAnalyzerConfig.Source source, Instant from) {
        if (sshLogReader == null) return List.of();

        log.debug("[{}] listing remote files at {}", source.getName(), source.getLogPath());
        List<String> remoteFiles = new ArrayList<>(
                sshLogReader.listRemoteFiles(source, source.getLogPath(), from));

        if (source.getLogFormat() == LogFormat.IGNITE || source.getLogFormat() == LogFormat.AUTO) {
            String archivePath = source.getLogPath() + "/archive";
            log.debug("[{}] listing Ignite archive at {}", source.getName(), archivePath);
            remoteFiles.addAll(sshLogReader.listRemoteFiles(source, archivePath, from));
        }

        log.debug("[{}] found {} remote file(s)", source.getName(), remoteFiles.size());
        List<LogEntry> all = new ArrayList<>();
        for (String remotePath : remoteFiles) {
            log.debug("[{}] reading remote file: {}", source.getName(), remotePath);
            List<String> lines = sshLogReader.readRemoteLines(source, remotePath);
            List<LogEntry> entries = parser.parseLines(lines, source.getName(), remotePath, source.getLogFormat());
            log.debug("[{}] parsed {} entries from {}", source.getName(), entries.size(), remotePath);
            all.addAll(entries);
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
                .limit(config.getMaxAnalysisResults())
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
