package com.loganalyzer.watcher;

import com.loganalyzer.config.LogAnalyzerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.FileSystems;
import java.nio.file.WatchService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LogDirectoryWatcherTest {

    @Test
    void closingWatchServiceTerminatesWorkerWithoutUncaughtException() throws Exception {
        LogDirectoryWatcher watcher = new LogDirectoryWatcher(new LogAnalyzerConfig(), null);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        try (WatchService service = FileSystems.getDefault().newWatchService()) {
            ReflectionTestUtils.setField(watcher, "watchService", service);
            Thread worker = new Thread(() -> {
                started.countDown();
                ReflectionTestUtils.invokeMethod(watcher, "watch");
            });
            worker.setDaemon(true);
            worker.setUncaughtExceptionHandler((thread, error) -> failure.set(error));
            worker.start();
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                watcher.stop();
                worker.join(5000);
                assertFalse(worker.isAlive(), "закрытие должно разбудить ожидающий поток");
                assertNull(failure.get(), "штатная остановка не должна выбрасывать исключение");
            } finally {
                worker.interrupt();
                worker.join(5000);
            }
        }
    }

    @Test
    void alreadyClosedWatchServiceIsNormalShutdown() throws Exception {
        LogDirectoryWatcher watcher = new LogDirectoryWatcher(new LogAnalyzerConfig(), null);
        try (WatchService service = FileSystems.getDefault().newWatchService()) {
            ReflectionTestUtils.setField(watcher, "watchService", service);
            watcher.stop();
            assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(watcher, "watch"));
        }
    }
}
