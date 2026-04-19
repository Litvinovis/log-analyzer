package com.loganalyzer.model;

import java.util.List;

public record JobResponse(
        String jobId,
        String status,
        List<LogAnalysisResult> results
) {
}
