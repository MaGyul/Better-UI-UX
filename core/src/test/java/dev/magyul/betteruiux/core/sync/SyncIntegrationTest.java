package dev.magyul.betteruiux.core.sync;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.sync.SyncMessage.BundleManifest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 클라이언트 ↔ 서버 코어를 실제 바이너리 코덱으로 이어 붙여 시나리오 단위로 검증한다(네트워크만 메모리). */
class SyncIntegrationTest {
    /** 메모리 캐시. */
    static final class MemCache implements ClientSyncCore.BundleCache {
        String hash = "";
        final Map<String, byte[]> files = new HashMap<>();
        int stores;

        public String bundleHash() { return hash; }

        public Map<String, byte[]> fileHashes() {
            Map<String, byte[]> m = new HashMap<>();
            files.forEach((k, v) -> m.put(k, Bundle.sha256(v)));
            return m;
        }

        public void store(BundleManifest manifest, Map<String, byte[]> newFiles) {
            stores++;
            files.keySet().retainAll(manifest.files().stream().map(SyncMessage.FileEntry::path).toList());
            files.putAll(newFiles);
            hash = manifest.hash();
        }
    }

    static final class Rec implements ClientSyncCore.Listener {
        int ready, disabled;
        String disabledReason;
        final List<String> opened = new ArrayList<>(), closed = new ArrayList<>();
        Map<String, Object> state = Map.of();

        public void onBundleReady(BundleManifest m, int policy) { ready++; }
        public void onOpenUi(String id, Map<String, Object> p) { opened.add(id); }
        public void onCloseUi(String id) { closed.add(id); }
        public void onState(Map<String, Object> v) { state = v; }
        public void onDisabled(String r) { disabled++; disabledReason = r; }
        final List<String> publishResults = new ArrayList<>();
        public void onPublishResult(int stage, boolean ok, String message) { publishResults.add(stage + ":" + ok); }
    }

    /** 한 연결: 양쪽 메시지를 인코딩→디코딩해서 큐로 전달한다. */
    static final class Link {
        final ServerSyncCore server;
        final MemCache cache = new MemCache();
        final Rec rec = new Rec();
        final List<SyncMessage> toClient = new ArrayList<>(), toServer = new ArrayList<>();
        long now;
        ServerSyncCore.ServerSession ss;
        ClientSyncCore client;

        Link(ServerSyncCore server) {
            this.server = server;
            ss = server.connect("p1", m -> toClient.add(wire(m)), () -> now);
            client = new ClientSyncCore(m -> toServer.add(wire(m)), cache, rec);
        }

        static SyncMessage wire(SyncMessage m) {
            try {
                return SyncCodec.decode(SyncCodec.encode(m));
            } catch (SyncException e) {
                throw new AssertionError(e);
            }
        }

        /** 양쪽에 쌓인 메시지를 더 없을 때까지(서버 틱 포함) 주고받는다. */
        void pump() {
            for (int guard = 0; guard < 100000; guard++) {
                if (!toServer.isEmpty()) {
                    ss.onMessage(toServer.remove(0));
                    continue;
                }
                if (!toClient.isEmpty()) {
                    client.onMessage(toClient.remove(0));
                    continue;
                }
                if (ss.pendingChunks() > 0) {
                    ss.tick();
                    continue;
                }
                client.tick();
                if (!toServer.isEmpty()) continue;
                return;
            }
            throw new AssertionError("pump 이 끝나지 않습니다");
        }
    }

    static Map<String, byte[]> files(String... kv) {
        Map<String, byte[]> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1].getBytes(StandardCharsets.UTF_8));
        return m;
    }

    @Test
    void firstJoinDownloadsBundleThenCachedJoinSkipsIt() throws Exception {
        ServerSyncCore s = new ServerSyncCore(0);
        byte[] big = new byte[SyncLimits.CHUNK * 3 + 17];
        for (int i = 0; i < big.length; i++) big[i] = (byte) (i * 31);
        Map<String, byte[]> f = files("shop.uiml", "<ui/>");
        f.put("img/big.png", big);
        s.setBundle(f);

        Link a = new Link(s);
        a.client.connected();
        a.pump();
        assertTrue(a.client.active());
        assertEquals(1, a.rec.ready);
        assertArrayEquals(big, a.cache.files.get("img/big.png"));
        assertEquals(s.bundle().manifest().hash(), a.cache.hash);

        // 같은 캐시로 다시 접속: 파일 전송 없이 준비 완료(서버는 해시가 같으면 매니페스트도 보내지 않는다)
        Link b = new Link(s);
        b.cache.hash = a.cache.hash;
        b.cache.files.putAll(a.cache.files);
        b.client.connected();
        b.pump();
        assertTrue(b.client.active());
        assertEquals(0, b.cache.stores);
    }

    @Test
    void changedFileDownloadsOnlyThatFile() throws Exception {
        ServerSyncCore s = new ServerSyncCore(0);
        s.setBundle(files("a.uiml", "<ui id='a'/>", "b.uiml", "<ui id='b'/>"));
        Link a = new Link(s);
        a.client.connected();
        a.pump();

        s.setBundle(files("a.uiml", "<ui id='a'/>", "b.uiml", "<ui id='b2'/>"));
        Link b = new Link(s);
        b.cache.hash = a.cache.hash;
        b.cache.files.putAll(a.cache.files);
        b.client.connected();
        b.pump();
        assertEquals("<ui id='b2'/>", new String(b.cache.files.get("b.uiml"), StandardCharsets.UTF_8));
        assertEquals(1, b.rec.ready);
    }

    @Test
    void openStateAndAction() throws Exception {
        ServerSyncCore s = new ServerSyncCore(0);
        List<Object> got = new ArrayList<>();
        s.registerAction("buy", ActionSchema.none().with("item", ActionSchema.Arg.string(32)).with("n", ActionSchema.Arg.integer(1, 64)),
            (p, args) -> got.add(p + ":" + args.get("item") + "x" + args.get("n")));
        Link l = new Link(s);
        l.client.connected();
        l.pump();

        l.ss.set("coins", 100L);
        l.ss.open("shop", Map.of("tab", "weapons"));
        l.pump();
        assertEquals(List.of("shop"), l.rec.opened);
        assertEquals(100L, l.rec.state.get("coins"));

        assertTrue(l.client.sendAction("shop", "buy", Map.of("item", "sword", "n", 2L)));
        l.pump();
        assertEquals(List.of("p1:swordx2"), got);

        // 서버가 닫으면 이후 동작은 거부
        l.ss.close("shop");
        l.pump();
        assertEquals(List.of("shop"), l.rec.closed);
        l.client.sendAction("shop", "buy", Map.of("item", "sword", "n", 1L));
        l.pump();
        assertEquals(1, got.size());
    }

    @Test
    void lateStateSnapshotOnReconnect() throws Exception {
        ServerSyncCore s = new ServerSyncCore(0);
        Link l = new Link(s);
        l.ss.set("k", "v");                  // 아직 인사 전: 보내지 않고 저장만
        assertTrue(l.toClient.isEmpty());
        l.client.connected();
        l.pump();
        assertEquals("v", l.rec.state.get("k"));
    }

    @Test
    void serverWithoutClientModeNeverSends() throws Exception {
        ServerSyncCore s = new ServerSyncCore(0);
        Link l = new Link(s);                // 클라이언트가 인사를 안 함 = 모드 없음
        assertFalse(l.ss.open("shop", Map.of()));
        l.ss.set("k", 1L);
        assertTrue(l.toClient.isEmpty());
    }

    @Test
    void protocolMismatchDisablesBothSides() throws Exception {
        ServerSyncCore s = new ServerSyncCore(0);
        Link l = new Link(s);
        l.toServer.add(new SyncMessage.ClientHello(SyncLimits.PROTOCOL + 1, ""));
        l.pump();
        assertFalse(l.ss.active());
        // 서버 인사에 담긴 버전은 서버 것: 같으니 클라이언트가 활성, 서버는 비활성 → open 은 false
        assertFalse(l.ss.open("shop", Map.of()));

        Link m = new Link(s);
        m.client.onMessage(new SyncMessage.ServerHello(SyncLimits.PROTOCOL + 1, "", 0));
        assertFalse(m.client.active());
        assertEquals(1, m.rec.disabled);
        assertFalse(m.client.sendAction("shop", "buy", Map.of()));
    }

    @Test
    void maliciousServerIsRejectedAndSyncDisabled() throws Exception {
        ServerSyncCore s = new ServerSyncCore(0);
        Link l = new Link(s);
        l.client.connected();
        l.pump();

        // 요청하지 않은 조각
        l.client.onMessage(new SyncMessage.BundleChunk("evil.uiml", 0, 1, new byte[] {1}));
        assertEquals(1, l.rec.disabled);
        assertFalse(l.client.active());
        l.client.onMessage(new SyncMessage.OpenUi("shop", Map.of()));
        assertTrue(l.rec.opened.isEmpty());

        // 경로 탈출이 담긴 매니페스트
        Link n = new Link(s);
        n.client.onMessage(new SyncMessage.ServerHello(SyncLimits.PROTOCOL, "", 0));
        BundleManifest bad = new BundleManifest("00", List.of(new SyncMessage.FileEntry("../evil.uiml", Bundle.sha256(new byte[0]), 0)));
        n.client.onMessage(bad);
        assertEquals(1, n.rec.disabled);
        assertTrue(n.cache.files.isEmpty());
    }

    @Test
    void tamperedChunkIsRejected() throws Exception {
        ServerSyncCore s = new ServerSyncCore(0);
        s.setBundle(files("a.uiml", "<ui/>"));
        Link l = new Link(s);
        l.client.connected();
        // 수동으로 진행해 청크 내용을 바꾼다
        l.ss.onMessage(l.toServer.remove(0));
        l.client.onMessage(l.toClient.remove(0));                  // hello
        l.client.onMessage(l.toClient.remove(0));                  // manifest
        l.ss.onMessage(l.toServer.remove(0));                      // need
        l.ss.tick();
        SyncMessage.BundleChunk c = (SyncMessage.BundleChunk) l.toClient.remove(0);
        byte[] d = c.data().clone();
        d[0] ^= 1;
        l.client.onMessage(new SyncMessage.BundleChunk(c.path(), c.index(), c.total(), d));
        assertEquals(1, l.rec.disabled);
        assertTrue(l.cache.files.isEmpty());
    }

    @Test
    void buttonMashingIsDroppedButNotPunished() throws Exception {
        ServerSyncCore s = new ServerSyncCore(0);
        List<Object> got = new ArrayList<>();
        s.registerAction("ping", ActionSchema.none(), (p, a) -> got.add(1));
        Link l = new Link(s);
        l.client.connected();
        l.pump();
        l.ss.open("ui", Map.of());
        l.pump();

        for (int i = 0; i < 100; i++) l.ss.onMessage(new SyncMessage.Action("ui", "ping", Map.of()));
        assertTrue(got.size() <= 10, "burst 한도를 넘어 처리됨: " + got.size());
        assertFalse(l.ss.disabled());
        int before = got.size();
        l.now += 10_000_000_000L;                 // 시간이 지나면 다시 처리된다
        l.ss.onMessage(new SyncMessage.Action("ui", "ping", Map.of()));
        assertEquals(before + 1, got.size());
    }

    @Test
    void repeatedInvalidActionsDisableButOldMistakesDecay() throws Exception {
        ServerSyncCore s = new ServerSyncCore(0);
        Link l = new Link(s);
        l.client.connected();
        l.pump();
        l.ss.open("ui", Map.of());
        l.pump();

        for (int i = 0; i < 5; i++) { l.ss.onMessage(new SyncMessage.Action("ui", "nope", Map.of())); l.now += 1_000_000_000L; }
        assertFalse(l.ss.disabled());
        l.now += 120_000_000_000L;                // 오래 조용했으면 기록이 지워진다
        for (int i = 0; i < 5; i++) { l.ss.onMessage(new SyncMessage.Action("ui", "nope", Map.of())); l.now += 1_000_000_000L; }
        assertFalse(l.ss.disabled());
        for (int i = 0; i < 20; i++) { l.ss.onMessage(new SyncMessage.Action("ui", "nope", Map.of())); l.now += 100_000_000L; }
        assertTrue(l.ss.disabled());
    }

    @Test
    void invalidActionsAreNotDispatched() throws Exception {
        ServerSyncCore s = new ServerSyncCore(0);
        List<Object> got = new ArrayList<>();
        s.registerAction("buy", ActionSchema.none().with("n", ActionSchema.Arg.integer(1, 5)), (p, a) -> got.add(a));
        Link l = new Link(s);
        l.client.connected();
        l.pump();
        l.ss.open("ui", Map.of());
        l.pump();

        l.ss.onMessage(new SyncMessage.Action("ui", "buy", Map.of("n", 99L)));         // 범위 밖
        l.ss.onMessage(new SyncMessage.Action("ui", "buy", Map.of("n", 1L, "x", "y"))); // 모르는 인자
        l.ss.onMessage(new SyncMessage.Action("ui", "nope", Map.of()));                 // 미등록
        l.ss.onMessage(new SyncMessage.Action("other", "buy", Map.of("n", 1L)));        // 열리지 않은 UI
        assertTrue(got.isEmpty());
        l.ss.onMessage(new SyncMessage.Action("ui", "buy", Map.of("n", 3L)));
        assertEquals(1, got.size());
        assertNotNull(l.ss.openUis());
        assertNull(null);
    }

    /** 테스트용 발행 처리기: 권한 여부를 바꿀 수 있고, 받은 파일을 서버 번들에 합친다. */
    static final class TestPublisher implements ServerSyncCore.Publisher {
        boolean allowed = true;
        final ServerSyncCore core;
        final List<ServerSyncCore.ServerSession> sessions = new ArrayList<>();

        TestPublisher(ServerSyncCore core) { this.core = core; }

        public boolean mayPublish(String playerId) { return allowed; }

        public void publish(String playerId, Map<String, byte[]> files) throws SyncException {
            Map<String, byte[]> all = new LinkedHashMap<>();
            core.bundle().files().forEach(all::put);
            all.putAll(files);
            core.setBundle(all);
            for (var s : sessions) s.resync();
        }
    }

    @Test
    void clientClosingScreenStopsServerActions() throws Exception {
        ServerSyncCore s = new ServerSyncCore(0);
        List<Object> got = new ArrayList<>();
        s.registerAction("ping", ActionSchema.none(), (p, a) -> got.add(1));
        Link l = new Link(s);
        l.client.connected();
        l.pump();
        l.ss.open("ui", Map.of());
        l.pump();
        l.client.sendAction("ui", "ping", Map.of());
        l.pump();
        assertEquals(1, got.size());
        l.client.closedUi("ui");
        l.pump();
        assertFalse(l.ss.openUis().contains("ui"));
        l.client.sendAction("ui", "ping", Map.of());
        l.pump();
        assertEquals(1, got.size());
    }

    @Test
    void publishRequiresPermissionAndUpdatesBundleForEveryone() throws Exception {
        ServerSyncCore s = new ServerSyncCore(0);
        s.setBundle(files("a.uiml", "<screen/>"));
        TestPublisher pub = new TestPublisher(s);
        s.setPublisher(pub);
        Link editor = new Link(s), viewer = new Link(s);
        pub.sessions.add(editor.ss);
        pub.sessions.add(viewer.ss);
        for (Link l : List.of(editor, viewer)) { l.client.connected(); l.pump(); }

        // 권한 없음: 거부되고 아무것도 바뀌지 않는다
        pub.allowed = false;
        assertTrue(editor.client.publish(files("b.uiml", "<screen id='b'/>")));
        editor.pump();
        assertEquals(List.of("0:false"), editor.rec.publishResults);
        assertEquals(1, s.bundle().files().size());

        // 권한 있음: 서버 번들에 합쳐지고, 접속 중인 다른 클라이언트도 새 파일을 받는다
        pub.allowed = true;
        editor.rec.publishResults.clear();
        assertTrue(editor.client.publish(files("b.uiml", "<screen id='b'/>", "style.uicss", ".x{}")));
        editor.pump();
        viewer.pump();
        assertEquals(List.of("0:true", "1:true"), editor.rec.publishResults);
        assertEquals(3, s.bundle().files().size());
        assertTrue(viewer.cache.files.containsKey("b.uiml"));
        assertTrue(viewer.cache.files.containsKey("style.uicss"));
        assertTrue(viewer.client.active());
    }

    @Test
    void publishLimitsAndBadFilesAreRejected() throws Exception {
        ServerSyncCore s = new ServerSyncCore(0);
        TestPublisher pub = new TestPublisher(s);
        s.setPublisher(pub);
        Link l = new Link(s);
        pub.sessions.add(l.ss);
        l.client.connected();
        l.pump();

        // 클라이언트가 스스로 큰 발행을 거부한다
        byte[] big = new byte[(int) SyncLimits.MAX_PUBLISH_BYTES + 1];
        boolean threw = false;
        try { l.client.publish(Map.of("big.png", big)); } catch (SyncException e) { threw = true; }
        assertTrue(threw);

        // 악의적인 클라이언트: 경로 탈출 / 조각을 보내지 않고 시작만 / 승인 없이 조각만
        l.ss.onMessage(new SyncMessage.PublishBegin(new BundleManifest("00", List.of(new SyncMessage.FileEntry("../evil.uiml", Bundle.sha256(new byte[0]), 0)))));
        l.ss.onMessage(new SyncMessage.BundleChunk("a.uiml", 0, 1, new byte[] {1}));
        l.pump();
        assertTrue(s.bundle().files().isEmpty());
        assertTrue(l.rec.publishResults.contains("0:false"));
    }
}
