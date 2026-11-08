package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.render.ImageDecoder;
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
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 외부 이미지(png/jpg/bmp/gif)를 마인크래프트 동적 텍스처로 올린다. 문서에서는 {@code <image src="file:이름.png"/>} 로 쓴다.
 *
 * <ul>
 *   <li>파일은 {@code config/better-uiux/images/<이름>}, 없으면 기본 제공 {@code assets/better_uiux/images/<이름>}. 폴더 밖 경로는 거부한다.</li>
 *   <li>해독은 백그라운드 스레드({@link ImageDecoder}), 텍스처 올리기는 렌더 스레드. 준비되면 알림 콜백으로 문서가 다시 배치된다.</li>
 *   <li>GIF 는 시간에 따라 프레임이 바뀌며, 프레임이 바뀔 때만 한 번 올린다(공식 {@link DynamicTexture} 경로만 사용).</li>
 *   <li>캐시: 최대 {@value #MAX_ENTRIES}개, 전체 {@code MAX_PIXEL_BUDGET} 픽셀-프레임. 넘으면 오래 안 쓴 것부터 텍스처를 닫는다.
 *       파일이 바뀌면(수정 시각) 다시 불러온다.</li>
 * </ul>
 */
final class ExternalImages {
    private static final Logger LOG = LoggerFactory.getLogger("better_uiux");
    static final int MAX_ENTRIES = 32;
    private static final long MAX_PIXEL_BUDGET = 32_000_000L;
    private static final long RECHECK_MS = 2000;
    private static final String PREFIX = "file:";

    private static Path baseDir;
    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "better-uiux-image-decoder");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    private enum State { LOADING, READY, FAILED }

    private static final class Entry {
        final String name;
        State state = State.LOADING;
        long stamp;                       // 불러온 파일의 수정 시각
        long checkedAt;                   // 마지막으로 수정 시각을 확인한 때
        ImageDecoder.Decoded image;
        DynamicTexture texture;
        Identifier id;
        int shownFrame = -1;
        long startMs = System.currentTimeMillis();
        final List<Runnable> onReady = new ArrayList<>();

        Entry(String name) { this.name = name; }

        long cost() { return image == null ? 0 : (long) image.width() * image.height() * image.frames().size(); }
    }

    /** 최근에 쓴 순서(접근 순서)로 유지한다. */
    private static final Map<String, Entry> CACHE = new LinkedHashMap<>(16, 0.75f, true);

    private ExternalImages() {}

    static void init(Path configBaseDir) { baseDir = configBaseDir; }

    static boolean handles(String src) { return src != null && src.startsWith(PREFIX); }

    /** 이미지의 원래 크기 {w, h}. 아직 불러오는 중이거나 실패했으면 null({@code onReady} 는 준비되면 렌더 스레드에서 한 번 불린다). */
    static int[] size(String src, Runnable onReady) {
        if (!handles(src)) return null;
        Entry e = entry(src.substring(PREFIX.length()).trim(), onReady);
        return e.state == State.READY ? new int[] {e.image.width(), e.image.height()} : null;
    }

    /** 지금 그릴 텍스처 id 와 이미지 크기. 준비되지 않았으면 null(그 자리는 비어 있다). */
    static Frame texture(String src) {
        if (!handles(src)) return null;
        Entry e = entry(src.substring(PREFIX.length()).trim(), null);
        if (e.state != State.READY) return null;
        if (e.texture == null) {
            e.texture = new DynamicTexture(() -> "better_uiux:" + e.name, e.image.width(), e.image.height(), false);
            e.id = Identifier.fromNamespaceAndPath("better_uiux", "dyn/" + hash(e.name + "@" + e.stamp));
            Minecraft.getInstance().getTextureManager().register(e.id, e.texture);
            e.shownFrame = -1;
        }
        int frame = e.image.frameAt(System.currentTimeMillis() - e.startMs);
        if (frame != e.shownFrame) {                                 // 프레임이 바뀔 때만 올린다
            upload(e, frame);
            e.shownFrame = frame;
        }
        return new Frame(e.id, e.image.width(), e.image.height());
    }

    record Frame(Identifier id, int width, int height) {}

    private static void upload(Entry e, int frame) {
        int w = e.image.width(), h = e.image.height();
        int[] px = e.image.frames().get(frame);
        var pixels = e.texture.getPixels();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) pixels.setPixel(x, y, px[y * w + x]);
        }
        e.texture.upload();
    }

    private static Entry entry(String name, Runnable onReady) {
        Entry e = CACHE.get(name);
        long now = System.currentTimeMillis();
        if (e != null && e.state != State.LOADING && now - e.checkedAt > RECHECK_MS) {
            e.checkedAt = now;
            if (fileStamp(name) != e.stamp) {                        // 파일이 바뀌었다: 버리고 다시 불러온다
                discard(name, e);
                e = null;
            }
        }
        if (e == null) {
            e = new Entry(name);
            e.checkedAt = now;
            CACHE.put(name, e);
            evictIfNeeded(name);
            load(e);
        }
        if (onReady != null && e.state == State.LOADING) e.onReady.add(onReady);
        return e;
    }

    private static void load(Entry e) {
        Minecraft mc = Minecraft.getInstance();
        DECODER.execute(() -> {
            ImageDecoder.Decoded decoded = null;
            long stamp = 0;
            try {
                byte[] bytes = readBytes(e.name);
                stamp = fileStamp(e.name);
                decoded = ImageDecoder.decode(bytes);
            } catch (IOException | RuntimeException ex) {
                LOG.warn("[better-uiux] image '{}' 을(를) 불러오지 못했습니다: {}", e.name, ex.getMessage());
            }
            ImageDecoder.Decoded result = decoded;
            long st = stamp;
            mc.execute(() -> {
                e.image = result;
                e.stamp = st;
                e.state = result == null ? State.FAILED : State.READY;
                e.startMs = System.currentTimeMillis();
                List<Runnable> callbacks = new ArrayList<>(e.onReady);
                e.onReady.clear();
                for (Runnable r : callbacks) r.run();
                evictIfNeeded(e.name);
            });
        });
    }

    private static void evictIfNeeded(String keep) {
        long total = 0;
        for (Entry x : CACHE.values()) total += x.cost();
        Iterator<Map.Entry<String, Entry>> it = CACHE.entrySet().iterator();
        while ((CACHE.size() > MAX_ENTRIES || total > MAX_PIXEL_BUDGET) && it.hasNext()) {
            Map.Entry<String, Entry> oldest = it.next();
            if (oldest.getKey().equals(keep) || oldest.getValue().state == State.LOADING) continue;
            total -= oldest.getValue().cost();
            release(oldest.getValue());
            it.remove();
        }
    }

    private static void discard(String name, Entry e) {
        release(e);
        CACHE.remove(name);
    }

    private static void release(Entry e) {
        if (e.texture != null) {
            Minecraft.getInstance().getTextureManager().release(e.id);
            e.texture.close();
            e.texture = null;
        }
    }

    // ---- 파일 ----

    private static boolean safe(String name) {
        return !name.isBlank() && name.length() <= 160 && !name.contains("..") && !name.startsWith("/") && !name.startsWith("\\")
                && name.matches("[A-Za-z0-9_./ -]+");
    }

    private static Path file(String name) {
        return baseDir == null || !safe(name) ? null : baseDir.resolve("images").resolve(name).normalize();
    }

    private static long fileStamp(String name) {
        try {
            Path f = file(name);
            return f != null && Files.isRegularFile(f) ? Files.getLastModifiedTime(f).toMillis() : 0;
        } catch (IOException ex) {
            return 0;
        }
    }

    private static byte[] readBytes(String name) throws IOException {
        if (!safe(name)) throw new IOException("허용되지 않는 이미지 이름");
        Path f = file(name);
        if (f != null && f.startsWith(baseDir.resolve("images").normalize()) && Files.isRegularFile(f)) {
            if (Files.size(f) > ImageDecoder.MAX_BYTES) throw new IOException("파일이 너무 큽니다");
            return Files.readAllBytes(f);
        }
        try (InputStream in = ExternalImages.class.getResourceAsStream("/assets/better_uiux/images/" + name)) {
            if (in == null) throw new IOException("파일을 찾을 수 없습니다");
            return in.readNBytes(ImageDecoder.MAX_BYTES + 1);
        }
    }

    private static String hash(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes())).substring(0, 20);
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
