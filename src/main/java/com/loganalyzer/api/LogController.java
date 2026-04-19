package com.loganalyzer.api;

import com.loganalyzer.analyzer.LogAnalyzerService;
import com.loganalyzer.model.LogAnalysisResult;

import java.time.Instant;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogController {

    private final LogAnalyzerService analyzerService;

    public LogController(LogAnalyzerService analyzerService) {
        this.analyzerService = analyzerService;
    }

    @GetMapping("/api/logs/errors")
    public ResponseEntity<List<LogAnalysisResult>> getErrors(
            @RequestParam(required = false) String app,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        List<String> apps = app != null ? List.of(app.split(",")) : List.of();

        Instant fromInstant = null;
        if (from != null && !from.isBlank()) {
            fromInstant = Instant.parse(from);
        }

        Instant toInstant = null;
        if (to != null && !to.isBlank()) {
            toInstant = Instant.parse(to);
        }

        List<LogAnalysisResult> results = analyzerService.analyzeErrors(apps, fromInstant, toInstant);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/api/logs/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
