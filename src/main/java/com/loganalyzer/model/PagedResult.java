package com.loganalyzer.model;

import java.util.List;

public record PagedResult<T>(
        List<T> content,
        int page,
        int size,
        long total,
        int totalPages
) {
}
