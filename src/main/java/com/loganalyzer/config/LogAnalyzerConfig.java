package com.loganalyzer.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "log-analyzer")
public class LogAnalyzerConfig {

    private String sshKeyPath;
    private int cacheTtlSeconds = 300;
    private int maxCacheFileSizeMb = 50;
    private int maxCachedFiles = 20;
    private String logTimezone = "UTC";
    private String corsAllowedOrigin = "http://localhost:5173";
    private int maxConcurrentAnalyses = 2;
    private int maxAnalysisResults = 10_000;
    private int minFreeHeapMb = 200;

    /**
     * Регэкспы идентификаторов для трассировки/подсветки в UI.
     * По умолчанию — UUID и числовые ID от 6 цифр (hh_id, Discord snowflake).
     * «Строгий UUID-режим» = оставить в конфиге только первый паттерн.
     */
    private List<String> traceIdPatterns = new ArrayList<>(List.of(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
            "\\b\\d{6,20}\\b"
    ));

    private List<Source> sources = new ArrayList<>();

    public String getSshKeyPath() { return sshKeyPath; }
    public void setSshKeyPath(String sshKeyPath) { this.sshKeyPath = sshKeyPath; }

    public int getCacheTtlSeconds() { return cacheTtlSeconds; }
    public void setCacheTtlSeconds(int v) { this.cacheTtlSeconds = v; }

    public int getMaxCacheFileSizeMb() { return maxCacheFileSizeMb; }
    public void setMaxCacheFileSizeMb(int v) { this.maxCacheFileSizeMb = v; }

    public int getMaxCachedFiles() { return maxCachedFiles; }
    public void setMaxCachedFiles(int v) { this.maxCachedFiles = v; }

    public String getLogTimezone() { return logTimezone; }
    public void setLogTimezone(String v) { this.logTimezone = v; }

    public String getCorsAllowedOrigin() { return corsAllowedOrigin; }
    public void setCorsAllowedOrigin(String v) { this.corsAllowedOrigin = v; }

    public int getMaxConcurrentAnalyses() { return maxConcurrentAnalyses; }
    public void setMaxConcurrentAnalyses(int v) { this.maxConcurrentAnalyses = v; }

    public int getMaxAnalysisResults() { return maxAnalysisResults; }
    public void setMaxAnalysisResults(int v) { this.maxAnalysisResults = v; }

    public List<String> getTraceIdPatterns() { return traceIdPatterns; }
    public void setTraceIdPatterns(List<String> v) { this.traceIdPatterns = v; }

    public int getMinFreeHeapMb() { return minFreeHeapMb; }
    public void setMinFreeHeapMb(int v) { this.minFreeHeapMb = v; }

    public List<Source> getSources() { return sources; }
    public void setSources(List<Source> sources) { this.sources = sources; }

    public static class Source {
        private String name;
        private String logPath;
        private ConnectionType connection = ConnectionType.LOCAL;
        private String sshHost;
        private int sshPort = 22;
        private String sshUser;
        private LogFormat logFormat = LogFormat.AUTO;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getLogPath() { return logPath; }
        public void setLogPath(String logPath) { this.logPath = logPath; }

        public ConnectionType getConnection() { return connection; }
        public void setConnection(ConnectionType connection) { this.connection = connection; }

        public String getSshHost() { return sshHost; }
        public void setSshHost(String sshHost) { this.sshHost = sshHost; }

        public int getSshPort() { return sshPort; }
        public void setSshPort(int sshPort) { this.sshPort = sshPort; }

        public String getSshUser() { return sshUser; }
        public void setSshUser(String sshUser) { this.sshUser = sshUser; }

        public LogFormat getLogFormat() { return logFormat; }
        public void setLogFormat(LogFormat logFormat) { this.logFormat = logFormat; }

        public enum ConnectionType { LOCAL, SSH }
    }
}
