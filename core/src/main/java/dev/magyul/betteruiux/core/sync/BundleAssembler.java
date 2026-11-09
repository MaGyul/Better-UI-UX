package dev.magyul.betteruiux.core.sync;

import dev.magyul.betteruiux.core.sync.SyncMessage.BundleChunk;
import dev.magyul.betteruiux.core.sync.SyncMessage.BundleManifest;
import dev.magyul.betteruiux.core.sync.SyncMessage.FileEntry;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 클라이언트가 번들 조각을 모아 파일로 조립한다. 요청하지 않은 파일, 순서가 어긋나거나 중복된 조각, 선언한 크기와 맞지 않는 조각,
 * 해시가 다른 완성 파일은 모두 {@link SyncException} 으로 거부한다. 메모리는 매니페스트가 선언한 크기(상한 있음)를 넘지 않는다.
 */
public final class BundleAssembler {
    private static final class Partial {
        final FileEntry entry;
        final byte[] buffer;
        final int total;
        int next;

        Partial(FileEntry entry) {
            this.entry = entry;
            this.buffer = new byte[(int) entry.size()];
            this.total = Bundle.chunkCount(entry.size());
        }
    }

    private final Map<String, FileEntry> wanted = new LinkedHashMap<>();
    private final Map<String, Partial> partial = new HashMap<>();
    private final Map<String, byte[]> done = new LinkedHashMap<>();

    /** @param manifest 검증을 통과한 매니페스트, @param need 요청한 경로들(매니페스트에 있는 것만 허용) */
    public BundleAssembler(BundleManifest manifest, List<String> need) throws SyncException {
        Map<String, FileEntry> all = new HashMap<>();
        for (FileEntry f : manifest.files()) all.put(f.path(), f);
        for (String p : need) {
            FileEntry f = all.get(p);
            if (f == null) throw new SyncException("매니페스트에 없는 파일을 요청했습니다: " + p);
            wanted.put(p, f);
        }
    }

    /** 조각을 받는다. @return 이 조각으로 파일이 완성되면 그 경로(내용은 {@link #files()}), 아니면 null */
    public String accept(BundleChunk c) throws SyncException {
        FileEntry entry = wanted.get(c.path());
        if (entry == null) throw new SyncException("요청하지 않은 파일의 조각: " + c.path());
        if (done.containsKey(c.path())) throw new SyncException("이미 완성된 파일의 조각: " + c.path());
        Partial p = partial.computeIfAbsent(c.path(), k -> new Partial(entry));
        if (c.total() != p.total) throw new SyncException("조각 개수가 선언과 다릅니다: " + c.path());
        if (c.index() != p.next) throw new SyncException("조각 순서가 어긋났습니다: " + c.path() + " (" + c.index() + " != " + p.next + ")");
        int from = c.index() * SyncLimits.CHUNK;
        boolean last = c.index() == p.total - 1;
        int expected = last ? p.buffer.length - from : SyncLimits.CHUNK;
        if (c.data().length != expected) throw new SyncException("조각 크기가 맞지 않습니다: " + c.path());
        System.arraycopy(c.data(), 0, p.buffer, from, c.data().length);
        p.next++;
        if (!last) return null;
        if (!Arrays.equals(Bundle.sha256(p.buffer), entry.sha256())) throw new SyncException("파일 해시가 맞지 않습니다: " + c.path());
        done.put(c.path(), p.buffer);
        partial.remove(c.path());
        return c.path();
    }

    /** 모든 요청한 파일을 받았는가. */
    public boolean complete() { return done.size() == wanted.size(); }

    /** 완성된 파일들(경로 → 내용). */
    public Map<String, byte[]> files() { return done; }
}
