package dev.magyul.betteruiux.core.net;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

/**
 * https 로 이미지/영상을 받아 디스크 캐시에 둔다(마인크래프트 비의존). 이 클래스를 부르는 쪽은 백그라운드 스레드여야 한다(네트워크를 기다린다).
 *
 * <p>안전 규칙: https 와 기본 포트만, 도메인 이름만(IP 직접 입력 금지), 이름이 풀린 주소가 하나라도 사설/루프백/링크로컬이면 거부,
 * 리다이렉트는 직접 따라가며 매번 같은 검사(최대 {@value #MAX_REDIRECTS}회), 응답 형식(image/*, video/*)과 크기 상한, 핀(sha256) 확인.
 * 핀이 있으면 받은 내용이 그 해시와 다를 때 버리고, 캐시된 내용도 읽을 때 다시 확인한다.
 */
public final class RemoteMedia {
    public enum Kind { IMAGE, VIDEO }

    /** 한 번의 GET(리다이렉트를 따라가지 않는다). */
    public interface Transport {
        Response get(URI uri, int timeoutMillis, long maxBytes) throws IOException, RemoteException;
    }

    public record Response(int status, String contentType, String location, byte[] body) {}

    public interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    public static final int MAX_REDIRECTS = 3;
    public static final int TIMEOUT_MS = 15_000;
    /** 핀 없는 항목의 캐시 유효 시간. */
    public static final long UNPINNED_TTL_MS = 24L * 3600 * 1000;
    /** 캐시 폴더 전체 상한(넘으면 오래된 것부터 지운다). */
    public static final long CACHE_LIMIT_BYTES = 256L * 1024 * 1024;

    private final Path cacheDir;
    private final Transport transport;
    private final Resolver resolver;
    private final LongSupplier nowMillis;

    public RemoteMedia(Path cacheDir, Transport transport, Resolver resolver, LongSupplier nowMillis) {
        this.cacheDir = cacheDir;
        this.transport = transport;
        this.resolver = resolver;
        this.nowMillis = nowMillis;
    }

    public static RemoteMedia withDefaults(Path cacheDir) {
        return new RemoteMedia(cacheDir, new JdkTransport(), InetAddress::getAllByName, System::currentTimeMillis);
    }

    /**
     * @param allowUnpinned false 면 {@code #sha256=} 핀이 있는 주소만 허용한다
     * @param maxBytes      응답 크기 상한
     */
    public byte[] fetch(String src, Kind kind, boolean allowUnpinned, long maxBytes) throws RemoteException {
        RemoteUrl.Parsed p = RemoteUrl.parse(src);
        if (p.pin() == null && !allowUnpinned) throw new RemoteException("이 설정에서는 #sha256= 핀이 있는 주소만 쓸 수 있습니다");
        String key = HexFormat.of().formatHex(sha256((p.uri() + "#" + (p.pin() == null ? "" : HexFormat.of().formatHex(p.pin()))).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Path file = cacheDir.resolve(key);
        byte[] cached = readCache(file, p.pin());
        if (cached != null) return cached;

        byte[] data = download(p.uri(), kind, maxBytes);
        if (p.pin() != null && !Arrays.equals(sha256(data), p.pin())) throw new RemoteException("받은 파일의 sha256 이 핀과 다릅니다(변조되었거나 파일이 바뀌었습니다)");
        store(file, data);
        return data;
    }

    // ---- 다운로드 ----

    private byte[] download(URI start, Kind kind, long maxBytes) throws RemoteException {
        URI u = start;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            RemoteUrl.check(u);
            guardAddresses(u.getHost());
            Response r;
            try {
                r = transport.get(u, TIMEOUT_MS, maxBytes);
            } catch (IOException e) {
                throw new RemoteException("받지 못했습니다: " + e.getMessage(), e);
            }
            if (r.status() >= 300 && r.status() < 400 && r.location() != null) {
                try {
                    u = u.resolve(r.location());
                } catch (IllegalArgumentException e) {
                    throw new RemoteException("리다이렉트 주소가 올바르지 않습니다");
                }
                continue;
            }
            if (r.status() != 200) throw new RemoteException("서버 응답 " + r.status());
            String type = r.contentType() == null ? "" : r.contentType().toLowerCase(Locale.ROOT);
            String want = kind == Kind.IMAGE ? "image/" : "video/";
            if (!type.startsWith(want)) throw new RemoteException("응답 형식이 " + want + "* 가 아닙니다(" + (type.isEmpty() ? "없음" : type) + ")");
            if (r.body() == null || r.body().length == 0) throw new RemoteException("빈 응답");
            if (r.body().length > maxBytes) throw new RemoteException("파일이 너무 큽니다");
            return r.body();
        }
        throw new RemoteException("리다이렉트가 너무 많습니다");
    }

    private void guardAddresses(String host) throws RemoteException {
        InetAddress[] all;
        try {
            all = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new RemoteException("호스트를 찾을 수 없습니다: " + host);
        }
        if (all == null || all.length == 0) throw new RemoteException("호스트를 찾을 수 없습니다: " + host);
        for (InetAddress a : all) {
            if (!RemoteUrl.isPublic(a)) throw new RemoteException("내부 네트워크 주소로 풀리는 호스트는 쓸 수 없습니다: " + host);
        }
    }

    // ---- 캐시 ----

    private byte[] readCache(Path file, byte[] pin) {
        try {
            if (!Files.isRegularFile(file)) return null;
            if (pin == null && nowMillis.getAsLong() - Files.getLastModifiedTime(file).toMillis() > UNPINNED_TTL_MS) return null;
            byte[] data = Files.readAllBytes(file);
            if (pin != null && !Arrays.equals(sha256(data), pin)) {       // 캐시가 손상/변조됨
                Files.deleteIfExists(file);
                return null;
            }
            return data;
        } catch (IOException e) {
            return null;
        }
    }

    private void store(Path file, byte[] data) {
        try {
            Files.createDirectories(cacheDir);
            Path tmp = Files.createTempFile(cacheDir, ".dl", ".tmp");
            Files.write(tmp, data);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            trim();
        } catch (IOException e) {
            // 캐시를 못 써도 이번 사용은 문제없다
        }
    }

    private void trim() throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.list(cacheDir)) {
            files = s.filter(Files::isRegularFile).toList();
        }
        long total = 0;
        for (Path f : files) total += Files.size(f);
        if (total <= CACHE_LIMIT_BYTES) return;
        List<Path> byAge = files.stream().sorted(java.util.Comparator.comparingLong(f -> {
            try { return Files.getLastModifiedTime(f).toMillis(); } catch (IOException e) { return 0L; }
        })).toList();
        for (Path f : byAge) {
            if (total <= CACHE_LIMIT_BYTES) break;
            total -= Files.size(f);
            Files.deleteIfExists(f);
        }
    }

    static byte[] sha256(byte[] d) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** JDK HttpClient 기반 전송: 리다이렉트를 자동으로 따라가지 않고, 응답을 상한까지만 읽는다. */
    public static final class JdkTransport implements Transport {
        private final HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofMillis(TIMEOUT_MS))
                .build();

        @Override
        public Response get(URI uri, int timeoutMillis, long maxBytes) throws IOException {
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(timeoutMillis))
                    .header("User-Agent", "better-uiux").GET().build();
            try {
                HttpResponse<InputStream> r = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                String type = r.headers().firstValue("Content-Type").orElse(null);
                String loc = r.headers().firstValue("Location").orElse(null);
                long declared = r.headers().firstValueAsLong("Content-Length").orElse(-1);
                if (r.statusCode() != 200) {
                    r.body().close();
                    return new Response(r.statusCode(), type, loc, null);
                }
                if (declared > maxBytes) {
                    r.body().close();
                    throw new IOException("파일이 너무 큽니다");
                }
                try (InputStream in = r.body()) {
                    byte[] data = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
                    if (data.length > maxBytes) throw new IOException("파일이 너무 큽니다");
                    return new Response(200, type, loc, data);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("중단되었습니다");
            }
        }
    }
}
