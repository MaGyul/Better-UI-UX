package dev.magyul.betteruiux.core.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.net.RemoteMedia.Kind;
import dev.magyul.betteruiux.core.net.RemoteMedia.Response;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemoteMediaTest {
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};

    static String hex(byte[] d) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(d)); }

    /** 가짜 네트워크: URL → 응답, 요청 기록. */
    static final class Fake implements RemoteMedia.Transport {
        final Map<String, Response> routes = new java.util.HashMap<>();
        final List<String> requests = new ArrayList<>();

        public Response get(URI uri, int timeoutMillis, long maxBytes) {
            requests.add(uri.toString());
            Response r = routes.get(uri.toString());
            return r != null ? r : new Response(404, null, null, null);
        }
    }

    static RemoteMedia media(Path dir, Fake f, long[] clock) {
        return new RemoteMedia(dir, f, h -> new InetAddress[] {InetAddress.getByAddress(new byte[] {93, (byte) 184, (byte) 216, 34})}, () -> clock[0]);
    }

    @Test
    void urlRules() throws Exception {
        assertEquals("https://cdn.example.com/a.png", RemoteUrl.parse("https://cdn.example.com/a.png").uri().toString());
        String h = "ab".repeat(32);
        assertEquals(32, RemoteUrl.parse("https://cdn.example.com/a.png#sha256=" + h).pin().length);
        for (String bad : new String[] {
                "http://cdn.example.com/a.png", "ftp://x/y", "https://user:pw@cdn.example.com/a.png", "https://cdn.example.com:8443/a.png",
                "https://127.0.0.1/a.png", "https://10.0.0.5/a.png", "https://[::1]/a.png", "https://localhost/a.png", "https://printer.local/a.png",
                "https://cdn.example.com/a.png#sha256=zz", "https://cdn.example.com/a.png#frag", "", "https://" + "a".repeat(600) + ".com/x"}) {
            assertThrows(RemoteException.class, () -> RemoteUrl.parse(bad), bad);
        }
    }

    @Test
    void publicAddressCheck() throws Exception {
        for (String ok : new String[] {"8.8.8.8", "93.184.216.34", "2606:4700:4700::1111"}) assertTrue(RemoteUrl.isPublic(InetAddress.getByName(ok)), ok);
        for (String bad : new String[] {"127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.1.1", "100.64.0.1", "0.0.0.0", "224.0.0.1", "198.18.0.1", "255.255.255.255", "::1", "fc00::1", "fe80::1"}) {
            assertFalse(RemoteUrl.isPublic(InetAddress.getByName(bad)), bad);
        }
    }

    @Test
    void fetchVerifiesPinAndCaches(@TempDir Path dir) throws Exception {
        Fake f = new Fake();
        f.routes.put("https://cdn.example.com/a.png", new Response(200, "image/png", null, PNG));
        long[] clock = {1000};
        RemoteMedia m = media(dir, f, clock);
        String url = "https://cdn.example.com/a.png#sha256=" + hex(PNG);
        assertArrayEquals(PNG, m.fetch(url, Kind.IMAGE, false, 1 << 20));
        assertArrayEquals(PNG, m.fetch(url, Kind.IMAGE, false, 1 << 20));
        assertEquals(1, f.requests.size(), "두 번째는 캐시");

        // 핀이 다르면 거부하고 캐시도 만들지 않는다
        String wrong = "https://cdn.example.com/a.png#sha256=" + "00".repeat(32);
        assertThrows(RemoteException.class, () -> m.fetch(wrong, Kind.IMAGE, false, 1 << 20));

        // 캐시 파일이 변조되면 다시 받는다
        try (var s = Files.list(dir)) {
            Path cached = s.filter(Files::isRegularFile).findFirst().orElseThrow();
            Files.write(cached, new byte[] {9, 9, 9});
        }
        assertArrayEquals(PNG, m.fetch(url, Kind.IMAGE, false, 1 << 20));
        assertEquals(3, f.requests.size(), "첫 요청 + 잘못된 핀 시도 + 변조된 캐시 재요청");
    }

    @Test
    void unpinnedNeedsOptInAndExpires(@TempDir Path dir) throws Exception {
        Fake f = new Fake();
        f.routes.put("https://cdn.example.com/a.png", new Response(200, "image/png", null, PNG));
        long[] clock = {1_000_000_000L};
        RemoteMedia m = media(dir, f, clock);
        assertThrows(RemoteException.class, () -> m.fetch("https://cdn.example.com/a.png", Kind.IMAGE, false, 1 << 20));
        assertEquals(0, f.requests.size());
        m.fetch("https://cdn.example.com/a.png", Kind.IMAGE, true, 1 << 20);
        m.fetch("https://cdn.example.com/a.png", Kind.IMAGE, true, 1 << 20);
        assertEquals(1, f.requests.size());
        // 파일 시각은 실제 시계라 만료를 흉내 내려면 시각을 아주 먼 미래로
        clock[0] = System.currentTimeMillis() + RemoteMedia.UNPINNED_TTL_MS + 10_000;
        m.fetch("https://cdn.example.com/a.png", Kind.IMAGE, true, 1 << 20);
        assertEquals(2, f.requests.size());
    }

    @Test
    void rejectsWrongTypeOversizeAndInternalHosts(@TempDir Path dir) throws Exception {
        Fake f = new Fake();
        f.routes.put("https://cdn.example.com/page.html", new Response(200, "text/html", null, "<html>".getBytes()));
        f.routes.put("https://cdn.example.com/big.png", new Response(200, "image/png", null, new byte[5000]));
        f.routes.put("https://cdn.example.com/v.mp4", new Response(200, "video/mp4", null, PNG));
        RemoteMedia m = media(dir, f, new long[] {0});
        assertThrows(RemoteException.class, () -> m.fetch("https://cdn.example.com/page.html", Kind.IMAGE, true, 1 << 20));
        assertThrows(RemoteException.class, () -> m.fetch("https://cdn.example.com/big.png", Kind.IMAGE, true, 1000));
        assertThrows(RemoteException.class, () -> m.fetch("https://cdn.example.com/v.mp4", Kind.IMAGE, true, 1 << 20));   // 영상을 이미지로
        assertArrayEquals(PNG, m.fetch("https://cdn.example.com/v.mp4", Kind.VIDEO, true, 1 << 20));
        assertThrows(RemoteException.class, () -> m.fetch("https://cdn.example.com/missing.png", Kind.IMAGE, true, 1 << 20));   // 404

        // 이름은 정상인데 사설 주소로 풀리는 호스트(DNS 로 내부망을 가리키는 경우)
        RemoteMedia internal = new RemoteMedia(dir.resolve("x"), f, h -> new InetAddress[] {InetAddress.getByName("192.168.0.10")}, () -> 0);
        assertThrows(RemoteException.class, () -> internal.fetch("https://cdn.example.com/v.mp4", Kind.VIDEO, true, 1 << 20));
        // 여러 주소 중 하나라도 사설이면 거부
        RemoteMedia mixed = new RemoteMedia(dir.resolve("y"), f, h -> new InetAddress[] {InetAddress.getByName("8.8.8.8"), InetAddress.getByName("127.0.0.1")}, () -> 0);
        assertThrows(RemoteException.class, () -> mixed.fetch("https://cdn.example.com/v.mp4", Kind.VIDEO, true, 1 << 20));
    }

    @Test
    void redirectsAreFollowedSafely(@TempDir Path dir) throws Exception {
        Fake f = new Fake();
        f.routes.put("https://cdn.example.com/a", new Response(302, null, "https://img.example.com/real.png", null));
        f.routes.put("https://img.example.com/real.png", new Response(200, "image/png", null, PNG));
        RemoteMedia m = media(dir, f, new long[] {0});
        assertArrayEquals(PNG, m.fetch("https://cdn.example.com/a", Kind.IMAGE, true, 1 << 20));

        // https 가 아닌 곳으로의 리다이렉트, 내부 호스트로의 리다이렉트, 끝없는 리다이렉트
        f.routes.put("https://cdn.example.com/b", new Response(302, null, "http://img.example.com/real.png", null));
        f.routes.put("https://cdn.example.com/c", new Response(302, null, "https://localhost/secret.png", null));
        f.routes.put("https://cdn.example.com/loop", new Response(302, null, "https://cdn.example.com/loop", null));
        assertThrows(RemoteException.class, () -> m.fetch("https://cdn.example.com/b", Kind.IMAGE, true, 1 << 20));
        assertThrows(RemoteException.class, () -> m.fetch("https://cdn.example.com/c", Kind.IMAGE, true, 1 << 20));
        assertThrows(RemoteException.class, () -> m.fetch("https://cdn.example.com/loop", Kind.IMAGE, true, 1 << 20));
    }
}
