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
    private List<Source> sources = new ArrayList<>();

    public String getSshKeyPath() { return sshKeyPath; }
    public void setSshKeyPath(String sshKeyPath) { this.sshKeyPath = sshKeyPath; }

    public int getCacheTtlSeconds() { return cacheTtlSeconds; }
    public void setCacheTtlSeconds(int v) { this.cacheTtlSeconds = v; }

    public int getMaxCacheFileSizeMb() { return maxCacheFileSizeMb; }
    public void setMaxCacheFileSizeMb(int v) { this.maxCacheFileSizeMb = v; }

    public int getMaxCachedFiles() { return maxCachedFiles; }
    public void setMaxCachedFiles(int v) { this.maxCachedFiles = v; }

    public List<Source> getSources() { return sources; }
    public void setSources(List<Source> sources) { this.sources = sources; }

    public static class Source {
        private String name;
        private String logPath;
        private ConnectionType connection = ConnectionType.LOCAL;
        private String sshHost;
        private int sshPort = 22;
        private String sshUser;
        private List<String> watchLevels = new ArrayList<>();
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

        public List<String> getWatchLevels() { return watchLevels; }
        public void setWatchLevels(List<String> watchLevels) { this.watchLevels = watchLevels; }

        public LogFormat getLogFormat() { return logFormat; }
        public void setLogFormat(LogFormat logFormat) { this.logFormat = logFormat; }

        public enum ConnectionType { LOCAL, SSH }
    }
}
