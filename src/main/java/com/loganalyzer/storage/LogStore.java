package com.loganalyzer.storage;

import com.loganalyzer.model.LogAnalysisResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LogStore {

    private final Map<String, LogAnalysisResult> cache = new ConcurrentHashMap<>();

    public void store(LogAnalysisResult result) {
        String key = result.app() + ":" + Instant.now().toEpochMilli();
        cache.put(key, result);
    }

    public List<LogAnalysisResult> getAll() {
        return new ArrayList<>(cache.values());
    }

    public List<LogAnalysisResult> getByApp(String app) {
        return cache.values().stream()
                .filter(r -> r.app().equals(app))
                .toList();
    }

    public void clear() {
        cache.clear();
    }
}
