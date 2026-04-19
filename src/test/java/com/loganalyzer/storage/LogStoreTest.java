package com.loganalyzer.storage;

import com.loganalyzer.model.LogAnalysisResult;
import com.loganalyzer.model.LogEntry;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogStoreTest {

    @Test
    void shouldStoreAndRetrieveResults() {
        LogStore store = new LogStore();
        Instant now = Instant.now();

        LogEntry entry = new LogEntry(now, "ERROR", "app", "fail", "file.log");
        LogAnalysisResult result = new LogAnalysisResult("app", now, List.of(entry), 10, 1);

        store.store(result);

        List<LogAnalysisResult> all = store.getAll();
        assertEquals(1, all.size());
        assertEquals("app", all.get(0).app());
    }

    @Test
    void shouldFilterByApp() {
        LogStore store = new LogStore();
        Instant now = Instant.now();

        LogEntry entry1 = new LogEntry(now, "ERROR", "app1", "msg1", "f.log");
        LogAnalysisResult r1 = new LogAnalysisResult("app1", now, List.of(entry1), 5, 1);
        store.store(r1);

        LogEntry entry2 = new LogEntry(now, "ERROR", "app2", "msg2", "f.log");
        LogAnalysisResult r2 = new LogAnalysisResult("app2", now, List.of(entry2), 5, 1);
        store.store(r2);

        List<LogAnalysisResult> app1Results = store.getByApp("app1");
        List<LogAnalysisResult> app2Results = store.getByApp("app2");

        assertEquals(1, app1Results.size());
        assertEquals("app1", app1Results.get(0).app());
        assertEquals(1, app2Results.size());
        assertEquals("app2", app2Results.get(0).app());
    }

    @Test
    void shouldClearAll() {
        LogStore store = new LogStore();
        Instant now = Instant.now();

        LogEntry entry = new LogEntry(now, "ERROR", "app", "msg", "f.log");
        LogAnalysisResult result = new LogAnalysisResult("app", now, List.of(entry), 5, 1);
        store.store(result);

        store.clear();
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    void shouldReturnEmptyByDefault() {
        LogStore store = new LogStore();
        assertTrue(store.getAll().isEmpty());
        assertTrue(store.getByApp("app").isEmpty());
    }
}
