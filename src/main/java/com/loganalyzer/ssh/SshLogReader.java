package com.loganalyzer.ssh;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.loganalyzer.config.LogAnalyzerConfig;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SshLogReader {

    private static final Logger log = LoggerFactory.getLogger(SshLogReader.class);

    private final LogAnalyzerConfig config;

    public SshLogReader(LogAnalyzerConfig config) {
        this.config = config;
    }

    /**
     * Lists remote log files (*.log, *.gz) in the given remote directory.
     * Returns full remote paths. Returns empty list on any error.
     */
    public List<String> listRemoteFiles(LogAnalyzerConfig.Source source, String remotePath, Instant from) {
        log.debug("SSH listRemoteFiles: {}@{}:{}{}", source.getSshUser(), source.getSshHost(), source.getSshPort(), remotePath);
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = createSession(source);
            sftp = openSftp(session);

            List<String> files = new ArrayList<>();
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = sftp.ls(remotePath);
            for (ChannelSftp.LsEntry entry : entries) {
                String name = entry.getFilename();
                if (entry.getAttrs().isDir() || !isLogFile(name)) continue;
                if (from != null) {
                    Instant mtime = Instant.ofEpochSecond(entry.getAttrs().getMTime());
                    if (mtime.isBefore(from)) {
                        log.debug("SSH skip old file (mtime {}): {}/{}", mtime, remotePath, name);
                        continue;
                    }
                }
                files.add(remotePath + "/" + name);
            }
            log.debug("SSH listRemoteFiles: found {} relevant log file(s) in {}", files.size(), remotePath);
            return files;
        } catch (Exception e) {
            log.warn("SSH listRemoteFiles failed at {}:{}{} — {}", source.getSshHost(), source.getSshPort(), remotePath, e.getMessage());
            return List.of();
        } finally {
            disconnect(sftp, session);
        }
    }

    /**
     * Reads all lines from a remote file via SFTP.
     * Supports .gz files. Returns empty list on any error.
     */
    public List<String> readRemoteLines(LogAnalyzerConfig.Source source, String remotePath) {
        log.debug("SSH readRemoteLines: {}@{}:{}{}", source.getSshUser(), source.getSshHost(), source.getSshPort(), remotePath);
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = createSession(source);
            sftp = openSftp(session);

            InputStream raw = sftp.get(remotePath);
            InputStream is = remotePath.endsWith(".gz") ? new GZIPInputStream(raw) : raw;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                List<String> lines = reader.lines().toList();
                log.debug("SSH readRemoteLines: read {} lines from {}", lines.size(), remotePath);
                return lines;
            }
        } catch (Exception e) {
            log.warn("SSH readRemoteLines failed at {}:{}{} — {}", source.getSshHost(), source.getSshPort(), remotePath, e.getMessage());
            return List.of();
        } finally {
            disconnect(sftp, session);
        }
    }

    private Session createSession(LogAnalyzerConfig.Source source) throws Exception {
        JSch jsch = new JSch();
        String keyPath = config.getSshKeyPath();
        if (keyPath != null && !keyPath.isBlank()) {
            String expanded = keyPath.replace("~", System.getProperty("user.home"));
            log.debug("SSH using identity: {}", expanded);
            jsch.addIdentity(expanded);
        } else {
            log.warn("SSH key path is not configured (log-analyzer.ssh-key-path)");
        }
        log.debug("SSH connecting to {}@{}:{}", source.getSshUser(), source.getSshHost(), source.getSshPort());
        Session session = jsch.getSession(source.getSshUser(), source.getSshHost(), source.getSshPort());
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(10_000);
        log.debug("SSH connected to {}:{}", source.getSshHost(), source.getSshPort());
        return session;
    }

    private ChannelSftp openSftp(Session session) throws Exception {
        ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect(5_000);
        return sftp;
    }

    private void disconnect(ChannelSftp sftp, Session session) {
        if (sftp != null && sftp.isConnected()) sftp.disconnect();
        if (session != null && session.isConnected()) session.disconnect();
    }

    private boolean isLogFile(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("gc.log")) return false;
        return lower.endsWith(".log") || lower.endsWith(".log.gz") || lower.endsWith(".gz");
    }
}
