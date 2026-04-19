package com.loganalyzer.storage;

import com.loganalyzer.model.LogEntry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogStoreTest {

    private final LogStore store = new LogStore();

    @Test
    void shouldReturnEmptyForUnknownFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("unknown.log");
        Files.writeString(file, "");

        List<LogEntry> entries = store.getOrLoad(file, List::of);
        assertTrue(entries.isEmpty());
    }

    @Test
    void shouldCacheEntriesOnFirstLoad(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("app.log");
        Files.writeString(file, "line");

        LogEntry entry = new LogEntry(Instant.now(), "ERROR", "app", "msg", file.toString());
        int[] callCount = {0};

        List<LogEntry> first = store.getOrLoad(file, () -> {
            callCount[0]++;
            return List.of(entry);
        });

        List<LogEntry> second = store.getOrLoad(file, () -> {
            callCount[0]++;
            return List.of();
        });

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        assertEquals(1, callCount[0], "loader should be called only once if file unchanged");
    }

    @Test
    void shouldReloadAfterInvalidation(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("app.log");
        Files.writeString(file, "line");

        LogEntry entry = new LogEntry(Instant.now(), "ERROR", "app", "msg", file.toString());
        int[] callCount = {0};

        store.getOrLoad(file, () -> { callCount[0]++; return List.of(entry); });
        store.invalidate(file);
        store.getOrLoad(file, () -> { callCount[0]++; return List.of(entry); });

        assertEquals(2, callCount[0], "loader should be called again after invalidation");
    }

    @Test
    void shouldClearAllEntries(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("app.log");
        Files.writeString(file, "line");

        store.getOrLoad(file, List::of);
        store.clear();

        int[] callCount = {0};
        store.getOrLoad(file, () -> { callCount[0]++; return List.of(); });

        assertEquals(1, callCount[0], "loader should be called after clear");
    }
}
