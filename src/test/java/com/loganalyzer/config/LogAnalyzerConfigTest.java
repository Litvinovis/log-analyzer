package com.loganalyzer.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogAnalyzerConfigTest {

    @Test
    void shouldHaveDefaultEmptyLogPaths() {
        LogAnalyzerConfig config = new LogAnalyzerConfig();
        assertTrue(config.getLogPaths().isEmpty());
    }

    @Test
    void shouldSetAndGetLogPaths() {
        LogAnalyzerConfig config = new LogAnalyzerConfig();
        List<String> paths = List.of("/var/log/app1", "/var/log/app2");
        config.setLogPaths(paths);
        assertEquals(2, config.getLogPaths().size());
        assertEquals("/var/log/app1", config.getLogPaths().get(0));
    }
}
