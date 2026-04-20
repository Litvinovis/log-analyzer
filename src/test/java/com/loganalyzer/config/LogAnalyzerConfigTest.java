package com.loganalyzer.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogAnalyzerConfigTest {

    @Test
    void shouldHaveDefaultEmptySources() {
        LogAnalyzerConfig config = new LogAnalyzerConfig();
        assertTrue(config.getSources().isEmpty());
    }

    @Test
    void shouldSetAndGetSources() {
        LogAnalyzerConfig config = new LogAnalyzerConfig();
        LogAnalyzerConfig.Source s = new LogAnalyzerConfig.Source();
        s.setName("test-app");
        s.setLogPath("/var/log/test");
        s.setLogFormat(LogFormat.MICROSERVICE);
        config.setSources(List.of(s));

        assertEquals(1, config.getSources().size());
        assertEquals("test-app", config.getSources().get(0).getName());
        assertEquals(LogFormat.MICROSERVICE, config.getSources().get(0).getLogFormat());
    }

    @Test
    void shouldHaveDefaultCacheTtl() {
        assertEquals(300, new LogAnalyzerConfig().getCacheTtlSeconds());
    }

    @Test
    void shouldHaveDefaultMaxCacheFileSize() {
        assertEquals(50, new LogAnalyzerConfig().getMaxCacheFileSizeMb());
    }

    @Test
    void shouldHaveDefaultSourceConnectionLocal() {
        LogAnalyzerConfig.Source s = new LogAnalyzerConfig.Source();
        assertEquals(LogAnalyzerConfig.Source.ConnectionType.LOCAL, s.getConnection());
    }

    @Test
    void shouldHaveDefaultLogFormatAuto() {
        LogAnalyzerConfig.Source s = new LogAnalyzerConfig.Source();
        assertEquals(LogFormat.AUTO, s.getLogFormat());
    }

    @Test
    void shouldHaveDefaultSshPort() {
        LogAnalyzerConfig.Source s = new LogAnalyzerConfig.Source();
        assertEquals(22, s.getSshPort());
    }

    @Test
    void shouldSetSshKeyPath() {
        LogAnalyzerConfig config = new LogAnalyzerConfig();
        config.setSshKeyPath("~/.ssh/id_rsa");
        assertEquals("~/.ssh/id_rsa", config.getSshKeyPath());
    }
}
