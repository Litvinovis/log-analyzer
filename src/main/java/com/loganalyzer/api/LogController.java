package com.loganalyzer.api;

import com.loganalyzer.analyzer.LogAnalyzerService;
import com.loganalyzer.model.JobResponse;
import com.loganalyzer.model.LogAnalysisResult;
import com.loganalyzer.model.LogEntry;
import com.loganalyzer.model.LogQuery;
import com.loganalyzer.model.LogStats;
import com.loganalyzer.model.PagedResult;
import com.loganalyzer.model.TraceResult;

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

        List<LogAnalysisResult> all = analyzerService.analyzeErrors(
                parseApps(app), parseInstant(from), parseInstant(to), levels, contains);
        return ResponseEntity.ok(toPage(all, page, size));
    }

    @GetMapping("/stats")
    public ResponseEntity<LogStats> getStats(
            @RequestParam(required = false) String app,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        return ResponseEntity.ok(
                analyzerService.getStats(parseApps(app), parseInstant(from), parseInstant(to)));
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

    /**
     * Trace a transaction UUID across all (or specified) applications.
     * Returns all log entries (any level) that mention the given ID,
     * grouped by application — useful for following a request end-to-end.
     */
    @GetMapping("/trace/{traceId}")
    public ResponseEntity<List<TraceResult>> traceById(
            @PathVariable String traceId,
            @RequestParam(required = false) String app,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        List<TraceResult> results = analyzerService.findByTraceId(
                traceId, parseApps(app), parseInstant(from), parseInstant(to));
        return ResponseEntity.ok(results);
    }

    @GetMapping("/all")
    public ResponseEntity<PagedResult<LogEntry>> getAllEntries(
            @RequestParam(required = false) String app,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) List<String> levels,
            @RequestParam(required = false) String contains,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        List<LogEntry> all = analyzerService.getAllEntries(
                parseApps(app), parseInstant(from), parseInstant(to), levels, contains);
        return ResponseEntity.ok(toPage(all, page, size));
    }

    @GetMapping("/apps")
    public ResponseEntity<List<String>> getApps() {
        return ResponseEntity.ok(analyzerService.getConfiguredApps());
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<String> handleDateParseError(DateTimeParseException e) {
        return ResponseEntity.badRequest().body("Invalid date format: " + e.getParsedString());
    }

    private static List<String> parseApps(String app) {
        return app != null ? List.of(app.split(",")) : List.of();
    }

    private static Instant parseInstant(String s) {
        return s != null && !s.isBlank() ? Instant.parse(s) : null;
    }

    private static <T> PagedResult<T> toPage(List<T> all, int page, int size) {
        int total      = all.size();
        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 1;
        List<T> paged  = all.stream().skip((long) page * size).limit(size).toList();
        return new PagedResult<>(paged, page, size, total, totalPages);
    }
}
