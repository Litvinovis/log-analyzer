package com.loganalyzer.analyzer;

import com.loganalyzer.config.LogAnalyzerConfig;
import com.loganalyzer.model.LogAnalysisResult;
import com.loganalyzer.model.LogEntry;
import com.loganalyzer.parser.LogFileParser;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

@Service
public class LogAnalyzerService {

    private final LogAnalyzerConfig config;
    private final LogFileParser parser = new LogFileParser();

    public LogAnalyzerService(LogAnalyzerConfig config) {
        this.config = config;
    }

    public List<LogAnalysisResult> analyzeErrors(List<String> apps, Instant from, Instant to) {
        List<LogAnalysisResult> results = new ArrayList<>();
        Set<String> appSet = apps != null && !apps.isEmpty() ? Set.copyOf(apps) : null;

        for (String logPath : config.getLogPaths()) {
            Path basePath = Paths.get(logPath);
            if (!Files.exists(basePath)) {
                continue;
            }

            List<Path> logFiles = findLogFiles(basePath);
            for (Path logFile : logFiles) {
                String app = extractAppName(logFile, logPath);
                if (appSet != null && !appSet.contains(app)) {
                    continue;
                }
                List<LogEntry> errors = filterErrors(logFile, from, to);
                if (!errors.isEmpty()) {
                    results.add(new LogAnalysisResult(
                            app,
                            Instant.now(),
                            errors,
                            errors.size(),
                            errors.size()
                    ));
                }
            }
        }
        return results;
    }

    private List<Path> findLogFiles(Path basePath) {
        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(basePath)) {
            files.addAll(stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".log") || name.endsWith(".log.gz") || name.endsWith(".gz");
                    })
                    .toList());
        } catch (IOException e) {
            // skip problematic paths
        }
        return files;
    }

    private String extractAppName(Path filePath, String basePathStr) {
        String path = filePath.toString();
        String basePath = Paths.get(basePathStr).toString();
        if (path.startsWith(basePath)) {
            String relative = path.substring(basePath.length()).substring(1);
            int sep = relative.indexOf('/');
            if (sep > 0) {
                return relative.substring(0, sep);
            }
            String name = filePath.getFileName().toString();
            return name.replaceAll("\\.(log|gz)$", "");
        }
        return filePath.getFileName().toString().replaceAll("\\.(log|gz)$", "");
    }

    private List<LogEntry> filterErrors(Path logFile, Instant from, Instant to) {
        try {
            List<LogEntry> allEntries;
            String name = logFile.getFileName().toString();
            if (name.endsWith(".gz")) {
                allEntries = parser.parseGzFile(logFile, null);
            } else {
                allEntries = parser.parse(logFile, null);
            }

            return allEntries.stream()
                    .filter(e -> e.level().equalsIgnoreCase("ERROR") || e.level().equalsIgnoreCase("FATAL"))
                    .filter(e -> from == null || !e.timestamp().isBefore(from))
                    .filter(e -> to == null || !e.timestamp().isAfter(to))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
