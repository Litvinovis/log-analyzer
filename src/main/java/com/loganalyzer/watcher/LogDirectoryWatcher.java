package com.loganalyzer.watcher;

import com.loganalyzer.config.LogAnalyzerConfig;
import com.loganalyzer.config.LogAnalyzerConfig.Source.ConnectionType;
import com.loganalyzer.storage.LogStore;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LogDirectoryWatcher {

    private static final Logger log = LoggerFactory.getLogger(LogDirectoryWatcher.class);

    private final LogAnalyzerConfig config;
    private final LogStore store;
    private WatchService watchService;

    public LogDirectoryWatcher(LogAnalyzerConfig config, LogStore store) {
        this.config = config;
        this.store = store;
    }

    @PostConstruct
    public void start() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            for (LogAnalyzerConfig.Source source : config.getSources()) {
                if (source.getConnection() == ConnectionType.LOCAL) {
                    registerAll(Paths.get(source.getLogPath()));
                }
            }
            Thread thread = new Thread(this::watch, "log-watcher");
            thread.setDaemon(true);
            thread.start();
        } catch (IOException e) {
            log.warn("Не удалось запустить наблюдение за каталогами логов — кэш будет инвалидироваться только по TTL: {}",
                    e.getMessage());
        }
    }

    private void registerAll(Path root) throws IOException {
        if (!Files.exists(root)) return;
        Files.walk(root)
                .filter(Files::isDirectory)
                .forEach(dir -> {
                    try {
                        dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
                    } catch (IOException e) {
                        log.warn("Не удалось зарегистрировать каталог {}: {}", dir, e.getMessage());
                    }
                });
    }

    private void watch() {
        while (!Thread.currentThread().isInterrupted()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == OVERFLOW) continue;
                Path dir = (Path) key.watchable();
                Path file = dir.resolve((Path) event.context());
                // Новые подкаталоги (ротация логов по датам) тоже берём под наблюдение
                if (event.kind() == ENTRY_CREATE && Files.isDirectory(file)) {
                    try {
                        registerAll(file);
                    } catch (IOException e) {
                        log.warn("Не удалось зарегистрировать новый каталог {}: {}", file, e.getMessage());
                    }
                    continue;
                }
                String name = file.getFileName().toString().toLowerCase();
                if (name.endsWith(".log") || name.endsWith(".gz")) {
                    store.invalidate(file);
                }
            }
            key.reset();
        }
    }

    @PreDestroy
    public void stop() throws IOException {
        if (watchService != null) {
            watchService.close();
        }
    }
}
