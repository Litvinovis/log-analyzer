package com.loganalyzer.model;

import java.time.Instant;
import java.util.List;

public record LogQuery(
        List<String> apps,
        Instant from,
        Instant to,
        List<String> levels
) {
}
