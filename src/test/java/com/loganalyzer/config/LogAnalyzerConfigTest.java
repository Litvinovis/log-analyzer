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

    @Test
    void shouldHaveDefaultCacheTtl() {
        LogAnalyzerConfig config = new LogAnalyzerConfig();
        assertEquals(300, config.getCacheTtlSeconds());
    }

    @Test
    void shouldHaveDefaultMaxCacheFileSize() {
        LogAnalyzerConfig config = new LogAnalyzerConfig();
        assertEquals(50, config.getMaxCacheFileSizeMb());
    }

    @Test
    void shouldSetLogPattern() {
        LogAnalyzerConfig config = new LogAnalyzerConfig();
        assertNull(config.getLogPattern());
        config.setLogPattern("^(.+)$");
        assertEquals("^(.+)$", config.getLogPattern());
    }
}
