package com.loganalyzer.storage;

import com.loganalyzer.config.LogAnalyzerConfig;
import com.loganalyzer.model.LogEntry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LogStore {

    private static final Logger log = LoggerFactory.getLogger(LogStore.class);

    private final long maxCacheFileSizeBytes;
    private final Duration cacheTtl;
    private final int maxCachedFiles;

    private record CachedFile(List<LogEntry> entries, FileTime lastModified, Instant cachedAt) {}

    private final ConcurrentHashMap<Path, CachedFile> cache = new ConcurrentHashMap<>();

    @Autowired
    public LogStore(LogAnalyzerConfig config) {
        this.maxCacheFileSizeBytes = (long) config.getMaxCacheFileSizeMb() * 1024 * 1024;
        this.cacheTtl = Duration.ofSeconds(config.getCacheTtlSeconds());
        this.maxCachedFiles = config.getMaxCachedFiles();
    }

    public LogStore() {
        this.maxCacheFileSizeBytes = 50L * 1024 * 1024;
        this.cacheTtl = Duration.ofMinutes(5);
        this.maxCachedFiles = 20;
    }

    public List<LogEntry> getOrLoad(Path file, Supplier<List<LogEntry>> loader) {
        try {
            long fileSize = Files.size(file);
            if (fileSize > maxCacheFileSizeBytes) {
                log.debug("File {} ({} MB) exceeds cache limit, skipping cache",
                        file.getFileName(), fileSize / 1024 / 1024);
                return loader.get();
            }

            FileTime lastModified = Files.getLastModifiedTime(file);
            CachedFile cached = cache.get(file);

            if (cached != null
                    && cached.lastModified().equals(lastModified)
                    && Instant.now().isBefore(cached.cachedAt().plus(cacheTtl))) {
                log.debug("Cache hit: {}", file.getFileName());
                return cached.entries();
            }

            List<LogEntry> entries = loader.get();
            cache.put(file, new CachedFile(entries, lastModified, Instant.now()));
            log.debug("Cache stored: {} ({} entries, cache size: {} files)",
                    file.getFileName(), entries.size(), cache.size());
            evictIfNeeded();
            return entries;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void invalidate(Path file) {
        cache.remove(file);
    }

    public void clear() {
        cache.clear();
    }

    private void evictIfNeeded() {
        if (cache.size() <= maxCachedFiles) return;
        cache.entrySet().stream()
                .min(Comparator.comparing(e -> e.getValue().cachedAt()))
                .map(Map.Entry::getKey)
                .ifPresent(oldest -> {
                    cache.remove(oldest);
                    log.debug("Cache evicted oldest entry: {} (limit: {} files)", oldest.getFileName(), maxCachedFiles);
                });
    }
}
