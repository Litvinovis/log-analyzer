package com.loganalyzer.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "log-analyzer")
public class LogAnalyzerConfig {

    private List<String> logPaths = new ArrayList<>();
    private String logPattern;
    private int cacheTtlSeconds = 300;
    private int maxCacheFileSizeMb = 50;

    public List<String> getLogPaths() { return logPaths; }
    public void setLogPaths(List<String> logPaths) { this.logPaths = logPaths; }

    public String getLogPattern() { return logPattern; }
    public void setLogPattern(String logPattern) { this.logPattern = logPattern; }

    public int getCacheTtlSeconds() { return cacheTtlSeconds; }
    public void setCacheTtlSeconds(int cacheTtlSeconds) { this.cacheTtlSeconds = cacheTtlSeconds; }

    public int getMaxCacheFileSizeMb() { return maxCacheFileSizeMb; }
    public void setMaxCacheFileSizeMb(int maxCacheFileSizeMb) { this.maxCacheFileSizeMb = maxCacheFileSizeMb; }
}
