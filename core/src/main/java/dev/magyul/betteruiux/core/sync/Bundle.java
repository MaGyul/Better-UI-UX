package dev.magyul.betteruiux.core.sync;

import dev.magyul.betteruiux.core.sync.SyncMessage.BundleChunk;
import dev.magyul.betteruiux.core.sync.SyncMessage.BundleManifest;
import dev.magyul.betteruiux.core.sync.SyncMessage.FileEntry;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * UI 번들(서버가 클라이언트에게 배포하는 파일 묶음)의 매니페스트 만들기/검증, 경로 검사, 조각 나누기.
 * 마인크래프트와 무관한 순수 Java 라 서버와 클라이언트가 같은 규칙을 쓰고, 단위 테스트가 된다.
 */
public final class Bundle {
    private Bundle() {}

    /** 번들에 넣을 수 있는 파일 형식. */
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of("uiml", "uicss", "uijs", "png", "jpg", "jpeg", "gif", "mp4", "m4v", "mov");

    // ---- 경로 ----

    /** 번들 안의 상대 경로 검사. @return 거부 사유(올바르면 null) */
    public static String checkPath(String path) {
        if (path == null || path.isEmpty()) return "경로가 비어 있습니다";
        if (path.length() > SyncLimits.MAX_PATH) return "경로가 너무 깁니다";
        if (path.startsWith("/") || path.contains("\\") || path.contains(":")) return "허용되지 않는 경로 문자: " + path;
        String[] segments = path.split("/", -1);
        for (String s : segments) {
            if (s.isEmpty() || s.equals(".") || s.equals("..")) return "허용되지 않는 경로: " + path;
            if (!s.matches("[A-Za-z0-9_.\\- ]+")) return "허용되지 않는 경로 문자: " + path;
            if (s.endsWith(".") || s.endsWith(" ")) return "허용되지 않는 경로: " + path;
        }
        String last = segments[segments.length - 1];
        int dot = last.lastIndexOf('.');
        String ext = dot < 0 ? "" : last.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext)) return "허용되지 않는 파일 형식: " + path;
        return null;
    }

    // ---- 해시 ----

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 번들 해시: 경로 순으로 정렬한 "경로\0sha256(hex)\0크기\n" 의 SHA-256. 내용이 같으면 서버/클라이언트가 같은 값을 얻는다. */
    public static String bundleHash(List<FileEntry> files) {
        Map<String, FileEntry> sorted = new TreeMap<>();
        for (FileEntry f : files) sorted.put(f.path(), f);
        StringBuilder sb = new StringBuilder();
        for (FileEntry f : sorted.values()) {
            sb.append(f.path()).append('\0').append(HexFormat.of().formatHex(f.sha256())).append('\0').append(f.size()).append('\n');
        }
        return HexFormat.of().formatHex(sha256(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ---- 매니페스트 ----

    /** 파일 내용들로 매니페스트를 만든다(서버). 검사에 걸리면 {@link SyncException}. */
    public static BundleManifest manifestOf(Map<String, byte[]> files) throws SyncException {
        List<FileEntry> entries = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : new TreeMap<>(files).entrySet()) {
            entries.add(new FileEntry(e.getKey(), sha256(e.getValue()), e.getValue().length));
        }
        BundleManifest m = new BundleManifest(bundleHash(entries), entries);
        validate(m);
        return m;
    }

    /** 받은 매니페스트를 검증한다(클라이언트): 경로, 크기 상한, 파일 수, 중복, 선언한 번들 해시가 실제 내용과 같은지. */
    public static void validate(BundleManifest m) throws SyncException {
        if (m.files().size() > SyncLimits.MAX_FILES) throw new SyncException("번들 파일이 너무 많습니다(" + m.files().size() + ")");
        Set<String> seen = new HashSet<>();
        long total = 0;
        for (FileEntry f : m.files()) {
            String why = checkPath(f.path());
            if (why != null) throw new SyncException(why);
            if (!seen.add(f.path().toLowerCase(Locale.ROOT))) throw new SyncException("중복된 경로: " + f.path());
            if (f.sha256().length != 32) throw new SyncException("해시 길이가 올바르지 않습니다: " + f.path());
            if (f.size() < 0 || f.size() > SyncLimits.MAX_FILE_BYTES) throw new SyncException("파일이 너무 큽니다: " + f.path() + " (" + f.size() + ")");
            total += f.size();
            if (total > SyncLimits.MAX_BUNDLE_BYTES) throw new SyncException("번들이 너무 큽니다(" + total + ")");
        }
        if (!bundleHash(m.files()).equals(m.hash())) throw new SyncException("번들 해시가 내용과 맞지 않습니다");
    }

    /**
     * 캐시에 이미 있는 파일(경로 → sha256)을 빼고 받아야 할 경로만 고른다.
     * @param cached 캐시의 현재 상태(없는 파일은 키 없음)
     */
    public static List<String> missing(BundleManifest m, Map<String, byte[]> cached) {
        List<String> need = new ArrayList<>();
        for (FileEntry f : m.files()) {
            byte[] have = cached.get(f.path());
            if (have == null || !Arrays.equals(have, f.sha256())) need.add(f.path());
        }
        return need;
    }

    // ---- 조각 ----

    public static int chunkCount(long size) { return (int) Math.max(1, (size + SyncLimits.CHUNK - 1) / SyncLimits.CHUNK); }

    /** 파일을 조각으로 나눈다(서버). */
    public static List<BundleChunk> chunks(String path, byte[] data) {
        int total = chunkCount(data.length);
        List<BundleChunk> out = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            int from = i * SyncLimits.CHUNK, to = Math.min(data.length, from + SyncLimits.CHUNK);
            out.add(new BundleChunk(path, i, total, Arrays.copyOfRange(data, from, to)));
        }
        return out;
    }
}
