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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LogStore {

    private final long maxCacheFileSizeBytes;
    private final Duration cacheTtl;

    private record CachedFile(List<LogEntry> entries, FileTime lastModified, Instant cachedAt) {}

    private final ConcurrentHashMap<Path, CachedFile> cache = new ConcurrentHashMap<>();

    @Autowired
    public LogStore(LogAnalyzerConfig config) {
        this.maxCacheFileSizeBytes = (long) config.getMaxCacheFileSizeMb() * 1024 * 1024;
        this.cacheTtl = Duration.ofSeconds(config.getCacheTtlSeconds());
    }

    public LogStore() {
        this.maxCacheFileSizeBytes = 50L * 1024 * 1024;
        this.cacheTtl = Duration.ofMinutes(5);
    }

    /**
     * Returns cached entries for the file if still fresh, otherwise calls loader and caches the result.
     * Large files (above threshold) are never cached and always loaded fresh.
     */
    public List<LogEntry> getOrLoad(Path file, Supplier<List<LogEntry>> loader) {
        try {
            long fileSize = Files.size(file);
            if (fileSize > maxCacheFileSizeBytes) {
                return loader.get();
            }

            FileTime lastModified = Files.getLastModifiedTime(file);
            CachedFile cached = cache.get(file);

            if (cached != null
                    && cached.lastModified().equals(lastModified)
                    && Instant.now().isBefore(cached.cachedAt().plus(cacheTtl))) {
                return cached.entries();
            }

            List<LogEntry> entries = loader.get();
            cache.put(file, new CachedFile(entries, lastModified, Instant.now()));
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
}
