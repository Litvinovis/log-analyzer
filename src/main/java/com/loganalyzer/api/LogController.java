package com.loganalyzer.api;

import com.loganalyzer.analyzer.LogAnalyzerService;
import com.loganalyzer.model.JobResponse;
import com.loganalyzer.model.LogAnalysisResult;
import com.loganalyzer.model.LogQuery;
import com.loganalyzer.model.LogStats;
import com.loganalyzer.model.PagedResult;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public ResponseEntity<PagedResult<LogAnalysisResult>> getErrors(
            @RequestParam(required = false) String app,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) List<String> levels,
            @RequestParam(required = false) String contains,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<String> apps = app != null ? List.of(app.split(",")) : List.of();
        Instant fromInstant = from != null && !from.isBlank() ? Instant.parse(from) : null;
        Instant toInstant   = to   != null && !to.isBlank()   ? Instant.parse(to)   : null;

        List<LogAnalysisResult> all = analyzerService.analyzeErrors(apps, fromInstant, toInstant, levels, contains);
        int total      = all.size();
        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 1;
        List<LogAnalysisResult> paged = all.stream().skip((long) page * size).limit(size).toList();

        return ResponseEntity.ok(new PagedResult<>(paged, page, size, total, totalPages));
    }

    @GetMapping("/stats")
    public ResponseEntity<LogStats> getStats(
            @RequestParam(required = false) String app,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        List<String> apps = app != null ? List.of(app.split(",")) : List.of();
        Instant fromInstant = from != null && !from.isBlank() ? Instant.parse(from) : null;
        Instant toInstant   = to   != null && !to.isBlank()   ? Instant.parse(to)   : null;

        return ResponseEntity.ok(analyzerService.getStats(apps, fromInstant, toInstant));
    }

    @PostMapping("/analyze")
    public ResponseEntity<JobResponse> startAnalysis(@RequestBody LogQuery query) {
        String jobId = UUID.randomUUID().toString();
        analyzerService.analyzeAsync(
                jobId,
                query.apps(),
                query.from(),
                query.to(),
                query.levels(),
                query.contains()
        );
        return ResponseEntity.accepted().body(new JobResponse(jobId, "PENDING", null));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<JobResponse> getJob(@PathVariable String id) {
        return analyzerService.getJob(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
