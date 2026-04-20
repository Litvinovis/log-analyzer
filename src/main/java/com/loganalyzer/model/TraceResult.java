package com.loganalyzer.model;

import java.util.List;

public record TraceResult(String app, List<LogEntry> entries) {}
