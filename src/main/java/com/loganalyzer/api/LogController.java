package com.loganalyzer.api;

import com.loganalyzer.analyzer.LogAnalyzerService;
import com.loganalyzer.model.LogAnalysisResult;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogAnalyzerService analyzerService;

    public LogController(LogAnalyzerService analyzerService) {
        this.analyzerService = analyzerService;
    }

    @GetMapping("/errors")
    public ResponseEntity<List<LogAnalysisResult>> getErrors(
            @RequestParam(required = false) String app,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        List<String> apps = app != null ? List.of(app.split(",")) : List.of();

        Instant fromInstant = from != null && !from.isBlank() ? Instant.parse(from) : null;
        Instant toInstant   = to   != null && !to.isBlank()   ? Instant.parse(to)   : null;

        List<LogAnalysisResult> results = analyzerService.analyzeErrors(apps, fromInstant, toInstant);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<String> handleDateParseError(DateTimeParseException e) {
        return ResponseEntity.badRequest().body("Invalid date format: " + e.getParsedString());
    }
}
