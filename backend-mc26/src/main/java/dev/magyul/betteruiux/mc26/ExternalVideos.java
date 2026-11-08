package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.render.VideoSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 영상({@code <video src="file:이름.mp4"/>}, H.264 mp4)을 재생한다. 소리는 지원하지 않는다.
 *
 * <ul>
 *   <li>파일 위치: {@code config/better-uiux/images/<이름>}(없으면 기본 제공). 폴더 밖 경로는 거부한다.</li>
 *   <li>해독은 영상마다 백그라운드 스레드(순수 Java JCodec)에서 하고, 렌더 스레드는 재생 시각에 맞는 가장 최근 프레임만 {@link DynamicTexture} 에 올린다.</li>
 *   <li>해독은 재생 속도보다 빨리 가지 않는다(대기 프레임 4개). 몇 초 동안 그려지지 않으면 해독 스레드가 멈추고, 다시 그려지면 처음부터 재생한다.</li>
 *   <li>상한: 동시에 최대 {@value #MAX_ACTIVE}개 재생, 해독 크기는 가장 긴 변 {@value #MAX_SIDE}px 이하로 줄인다. 계속 반복(loop) 재생한다.</li>
 * </ul>
 */
final class ExternalVideos {
    private static final Logger LOG = LoggerFactory.getLogger("better_uiux");
    static final int MAX_ACTIVE = 3, MAX_ENTRIES = 6, MAX_SIDE = 960, QUEUE = 4;
    private static final long IDLE_STOP_MS = 2500;
    private static final String PREFIX = "file:";

    private static Path baseDir;

    private ExternalVideos() {}

    static void init(Path configBaseDir) { baseDir = configBaseDir; }

    static boolean handles(String src) {
        if (src == null || !src.startsWith(PREFIX)) return false;
        String s = src.toLowerCase(Locale.ROOT);
        return s.endsWith(".mp4") || s.endsWith(".m4v") || s.endsWith(".mov");
    }

    private record Timed(int[] argb, long ptsMs) {}

    private enum State { OPENING, READY, FAILED }

    private static final class Entry {
        final String name;
        volatile State state = State.OPENING;
        volatile VideoSource.Info info;
        volatile long lastUse = System.currentTimeMillis();
        volatile boolean running, stop;
        final BlockingQueue<Timed> queue = new ArrayBlockingQueue<>(QUEUE);
        volatile long startNs = System.nanoTime();
        final List<Runnable> onReady = new ArrayList<>();
        DynamicTexture texture;
        Identifier id;
        boolean uploaded;

        Entry(String name) { this.name = name; }
    }

    private static final Map<String, Entry> CACHE = new LinkedHashMap<>(8, 0.75f, true);

    // ---- 렌더 스레드 API ----

    /** 영상의 (줄인) 크기 {w, h}. 아직 여는 중이거나 실패하면 null — 준비되면 {@code onReady} 가 렌더 스레드에서 한 번 불린다. */
    static int[] size(String src, Runnable onReady) {
        Entry e = entry(src.substring(PREFIX.length()).trim());
        if (e.state == State.OPENING && onReady != null) e.onReady.add(onReady);
        return e.state == State.READY ? new int[] {e.info.width(), e.info.height()} : null;
    }

    /** 지금 보여 줄 프레임이 올라간 텍스처. 첫 프레임이 아직 없으면 null. */
    static ExternalImages.Frame texture(String src) {
        Entry e = entry(src.substring(PREFIX.length()).trim());
        e.lastUse = System.currentTimeMillis();
        if (e.state != State.READY) return null;
        if (!e.running) start(e);                                    // 한동안 안 그려 멈췄다면 처음부터 다시
        long playheadMs = (System.nanoTime() - e.startNs) / 1_000_000L;
        Timed latest = null;
        for (Timed t = e.queue.peek(); t != null && t.ptsMs() <= playheadMs; t = e.queue.peek()) latest = e.queue.poll();
        if (latest != null) {
            ensureTexture(e);
            ExternalImages.upload(e.texture, e.info.width(), e.info.height(), latest.argb());
            e.uploaded = true;
        }
        return e.uploaded ? new ExternalImages.Frame(e.id, e.info.width(), e.info.height()) : null;
    }

    private static void ensureTexture(Entry e) {
        if (e.texture != null) return;
        e.texture = new DynamicTexture(() -> "better_uiux:video:" + e.name, e.info.width(), e.info.height(), false);
        e.id = Identifier.fromNamespaceAndPath("better_uiux", "dyn/v" + hash(e.name));
        Minecraft.getInstance().getTextureManager().register(e.id, e.texture);
    }

    private static Entry entry(String name) {
        Entry e = CACHE.get(name);
        if (e == null || (e.state == State.FAILED && System.currentTimeMillis() - e.lastUse > 5000)) {
            if (e != null) release(name, e);
            e = new Entry(name);
            CACHE.put(name, e);
            evictIfNeeded(name);
            start(e);
        }
        return e;
    }

    private static int activeCount() {
        int n = 0;
        for (Entry x : CACHE.values()) if (x.running) n++;
        return n;
    }

    private static void evictIfNeeded(String keep) {
        Iterator<Map.Entry<String, Entry>> it = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_ENTRIES && it.hasNext()) {
            Map.Entry<String, Entry> oldest = it.next();
            if (oldest.getKey().equals(keep)) continue;
            freeResources(oldest.getValue());
            it.remove();
        }
    }

    private static void release(String name, Entry e) {
        freeResources(e);
        CACHE.remove(name);
    }

    private static void freeResources(Entry e) {
        e.stop = true;
        if (e.texture != null) {
            Minecraft.getInstance().getTextureManager().release(e.id);
            e.texture.close();
            e.texture = null;
        }
    }

    // ---- 해독 스레드 ----

    private static void start(Entry e) {
        if (e.running) return;
        if (activeCount() >= MAX_ACTIVE) return;                     // 동시에 너무 많이 재생하지 않는다(다음 프레임에 다시 시도)
        e.running = true;
        e.stop = false;
        e.queue.clear();
        e.startNs = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        Thread t = new Thread(() -> decodeLoop(e, mc), "better-uiux-video-" + e.name);
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
    }

    private static void decodeLoop(Entry e, Minecraft mc) {
        try (VideoSource src = VideoSource.open(readBytes(e.name), MAX_SIDE)) {
            VideoSource.Info info = e.info == null ? src.info() : e.info;
            if (e.info == null) {
                e.info = info;
                e.state = State.READY;
                mc.execute(() -> {
                    List<Runnable> callbacks = new ArrayList<>(e.onReady);
                    e.onReady.clear();
                    for (Runnable r : callbacks) r.run();
                });
            }
            double frameMs = 1000.0 / Math.max(1.0, info.fps());
            long loopOffsetMs = 0;
            int index = 0;
            while (!e.stop && System.currentTimeMillis() - e.lastUse < IDLE_STOP_MS) {
                VideoSource.Frame f = src.next();
                if (f == null) {                                     // 끝: 처음부터 반복
                    loopOffsetMs += Math.round(index * frameMs);
                    index = 0;
                    src.rewind();
                    continue;
                }
                Timed timed = new Timed(f.argb(), loopOffsetMs + Math.round(index * frameMs));
                index++;
                while (!e.stop && !e.queue.offer(timed, 50, TimeUnit.MILLISECONDS)) {
                    if (System.currentTimeMillis() - e.lastUse >= IDLE_STOP_MS) break;   // 소비자가 없다(안 그리는 중)
                }
            }
        } catch (IOException | RuntimeException ex) {
            LOG.warn("[better-uiux] video '{}' 을(를) 재생하지 못했습니다: {}", e.name, ex.getMessage());
            e.state = State.FAILED;
            mc.execute(() -> {
                List<Runnable> callbacks = new ArrayList<>(e.onReady);
                e.onReady.clear();
                for (Runnable r : callbacks) r.run();
            });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            e.running = false;
        }
    }

    // ---- 파일 ----

    private static boolean safe(String name) {
        return !name.isBlank() && name.length() <= 160 && !name.contains("..") && !name.startsWith("/") && !name.startsWith("\\")
                && name.matches("[A-Za-z0-9_./ -]+");
    }

    private static byte[] readBytes(String name) throws IOException {
        if (!safe(name)) throw new IOException("허용되지 않는 영상 이름");
        Path images = baseDir == null ? null : baseDir.resolve("images").normalize();
        Path f = images == null ? null : images.resolve(name).normalize();
        if (f != null && f.startsWith(images) && Files.isRegularFile(f)) {
            if (Files.size(f) > VideoSource.MAX_BYTES) throw new IOException("영상 파일이 너무 큽니다");
            return Files.readAllBytes(f);
        }
        try (InputStream in = ExternalVideos.class.getResourceAsStream("/assets/better_uiux/images/" + name)) {
            if (in == null) throw new IOException("파일을 찾을 수 없습니다");
            return in.readNBytes(VideoSource.MAX_BYTES + 1);
        }
    }

    private static String hash(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes())).substring(0, 20);
        } catch (java.security.NoSuchAlgorithmException ex) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
