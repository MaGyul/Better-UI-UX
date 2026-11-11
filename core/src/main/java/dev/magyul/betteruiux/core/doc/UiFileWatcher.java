package dev.magyul.betteruiux.core.doc;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

/**
 * UI 폴더의 파일 변경 감시(핫리로드). 감시 스레드는 "변경 있음" 표시만 하고, 실제 리로드는 호출한 쪽(렌더 스레드)이
 * {@link #pollChanged}로 확인해서 수행한다. 에디터가 저장 중 여러 이벤트를 연달아 내므로 조용해질 때까지 기다린다(디바운스).
 */
public final class UiFileWatcher implements AutoCloseable {
    private final WatchService service;
    private final Thread thread;
    private volatile boolean dirty;
    private volatile long lastEventNanos;

    private UiFileWatcher(Path dir) throws IOException {
        this.service = FileSystems.getDefault().newWatchService();
        dir.register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        this.thread = new Thread(this::run, "better-uiux-file-watcher");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    /** 폴더가 없으면 만든다. */
    public static UiFileWatcher start(Path dir) throws IOException {
        Files.createDirectories(dir);
        return new UiFileWatcher(dir);
    }

    private void run() {
        try {
            while (true) {
                WatchKey key = service.take();
                for (WatchEvent<?> ev : key.pollEvents()) {
                    if (ev.kind() == StandardWatchEventKinds.OVERFLOW || relevant(ev.context())) {
                        lastEventNanos = System.nanoTime();
                        dirty = true;
                    }
                }
                if (!key.reset()) break;
            }
        } catch (InterruptedException | ClosedWatchServiceException ignored) {
            // 종료
        }
    }

    private static boolean relevant(Object ctx) {
        if (!(ctx instanceof Path p)) return true;
        String n = p.getFileName().toString().toLowerCase();
        return n.endsWith(".uiml") || n.endsWith(".uicss") || n.endsWith(".uijs");
    }

    /**
     * 마지막 변경 이후 {@code quietMillis} 동안 추가 변경이 없으면 true 를 한 번 반환하고 표시를 지운다.
     */
    public boolean pollChanged(long quietMillis) {
        if (!dirty) return false;
        if ((System.nanoTime() - lastEventNanos) / 1_000_000 < quietMillis) return false;
        dirty = false;
        return true;
    }

    @Override
    public void close() {
        try {
            service.close();
        } catch (IOException ignored) {
            // 이미 닫힘
        }
        thread.interrupt();
    }
}
