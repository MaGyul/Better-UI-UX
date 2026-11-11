package dev.magyul.betteruiux.core.sync;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.sync.SyncMessage.Action;
import dev.magyul.betteruiux.core.sync.SyncMessage.BundleChunk;
import dev.magyul.betteruiux.core.sync.SyncMessage.BundleManifest;
import dev.magyul.betteruiux.core.sync.SyncMessage.BundleNeed;
import dev.magyul.betteruiux.core.sync.SyncMessage.ClientHello;
import dev.magyul.betteruiux.core.sync.SyncMessage.CloseUi;
import dev.magyul.betteruiux.core.sync.SyncMessage.FileEntry;
import dev.magyul.betteruiux.core.sync.SyncMessage.OpenUi;
import dev.magyul.betteruiux.core.sync.SyncMessage.ServerHello;
import dev.magyul.betteruiux.core.sync.SyncMessage.StateClear;
import dev.magyul.betteruiux.core.sync.SyncMessage.StateSet;
import dev.magyul.betteruiux.core.sync.SyncMessage.StateSnapshot;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 서버 동기화 프로토콜: 인코딩 왕복, 변조/초과/경로 탈출 입력 거부, 번들 조립, 액션 스키마, 속도 제한. */
class SyncProtocolTest {
    private static SyncMessage roundTrip(SyncMessage m) throws Exception { return SyncCodec.decode(SyncCodec.encode(m)); }

    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    // ---- 코덱 ----

    @Test
    void everyMessageTypeRoundTrips() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", "스티브");
        values.put("coins", 120L);
        values.put("ratio", 0.5);
        values.put("on", true);
        assertEquals(new ClientHello(1, "abc"), roundTrip(new ClientHello(1, "abc")));
        assertEquals(new ServerHello(1, "def", 0), roundTrip(new ServerHello(1, "def", 0)));
        assertEquals(new BundleNeed(List.of("a.uiml", "img/b.png")), roundTrip(new BundleNeed(List.of("a.uiml", "img/b.png"))));
        assertEquals(new OpenUi("shop", values), roundTrip(new OpenUi("shop", values)));
        assertEquals(new CloseUi("shop"), roundTrip(new CloseUi("shop")));
        assertEquals(new StateSet("coins", 5L), roundTrip(new StateSet("coins", 5L)));
        assertEquals(new StateSet("label", "안녕"), roundTrip(new StateSet("label", "안녕")));
        assertEquals(new StateClear("coins"), roundTrip(new StateClear("coins")));
        assertEquals(new StateSnapshot(values), roundTrip(new StateSnapshot(values)));
        assertEquals(new Action("shop", "mymod:buy", Map.of("item", "apple", "count", 3L)),
                roundTrip(new Action("shop", "mymod:buy", Map.of("item", "apple", "count", 3L))));
        BundleChunk c = (BundleChunk) roundTrip(new BundleChunk("a.uiml", 2, 5, new byte[] {1, 2, 3}));
        assertEquals("a.uiml", c.path());
        assertArrayEquals(new byte[] {1, 2, 3}, c.data());
        BundleManifest m = Bundle.manifestOf(Map.of("a.uiml", bytes("<screen/>"), "b.png", new byte[] {9}));
        BundleManifest back = (BundleManifest) roundTrip(m);
        assertEquals(m.hash(), back.hash());
        assertEquals(2, back.files().size());
        assertArrayEquals(m.files().get(0).sha256(), back.files().get(0).sha256());
    }

    @Test
    void malformedAndOversizedMessagesAreRejected() {
        assertThrows(SyncException.class, () -> SyncCodec.decode(new byte[0]));
        assertThrows(SyncException.class, () -> SyncCodec.decode(new byte[] {99}), "알 수 없는 종류");
        assertThrows(SyncException.class, () -> SyncCodec.decode(new byte[] {7, 0, 5, 1}), "잘린 문자열");
        assertThrows(SyncException.class, () -> SyncCodec.decode(new byte[SyncLimits.MAX_MESSAGE + 1]));
        // 거대한 길이 선언(문자열 65535 바이트라고 속임)
        assertThrows(SyncException.class, () -> SyncCodec.decode(new byte[] {7, (byte) 0xFF, (byte) 0xFF}));
        // 끝에 남는 바이트
        byte[] ok = assertDoesNotThrow(() -> SyncCodec.encode(new CloseUi("x")));
        byte[] extra = Arrays.copyOf(ok, ok.length + 1);
        assertThrows(SyncException.class, () -> SyncCodec.decode(extra));
    }

    private static <T> T assertDoesNotThrow(java.util.concurrent.Callable<T> c) {
        try {
            return c.call();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void limitsAreEnforcedWhenEncodingAndDecoding() throws Exception {
        assertThrows(SyncException.class, () -> SyncCodec.encode(new CloseUi("x".repeat(SyncLimits.MAX_ID + 1))));
        assertThrows(SyncException.class, () -> SyncCodec.encode(new StateSet("k", "v".repeat(SyncLimits.MAX_VALUE + 1))));
        assertThrows(SyncException.class, () -> SyncCodec.encode(new StateSet("k", new Object())));
        assertThrows(SyncException.class, () -> SyncCodec.encode(new StateSet("k", Double.NaN)));
        Map<String, Object> many = new LinkedHashMap<>();
        for (int i = 0; i <= SyncLimits.MAX_ARGS; i++) many.put("k" + i, 1L);
        assertThrows(SyncException.class, () -> SyncCodec.encode(new Action("ui", "a", many)));
        assertThrows(SyncException.class, () -> SyncCodec.encode(new BundleChunk("a.uiml", 0, 1, new byte[SyncLimits.CHUNK + 1])));
        // 디코딩 쪽: 직접 만든 거짓 조각 길이
        byte[] chunk = SyncCodec.encode(new BundleChunk("a.uiml", 0, 1, new byte[4]));
        chunk[chunk.length - 4 - 1] = 0x7F;                                  // 길이 필드를 크게 바꾼다
        assertThrows(SyncException.class, () -> SyncCodec.decode(chunk));
    }

    // ---- 경로 ----

    @Test
    void bundlePathsMustStayInsideAndUseAnAllowedExtension() {
        assertNull(Bundle.checkPath("shop.uiml"));
        assertNull(Bundle.checkPath("img/icons/coin.png"));
        assertNull(Bundle.checkPath("video/clip.MP4"));
        for (String bad : new String[] {"", "../x.uiml", "a/../b.uiml", "/etc/x.uiml", "C:/x.uiml", "a\\b.uiml", "a//b.uiml", "./a.uiml", "a/./b.uiml",
                "a.exe", "a.class", "noext", "a.xml.", "a b.uiml ", "x".repeat(300) + ".uiml", "a/b.", "..hidden", "con?.uiml"}) {
            assertNotNull(Bundle.checkPath(bad), bad);
        }
    }

    // ---- 번들 ----

    private static Map<String, byte[]> sampleFiles() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("shop.uiml", bytes("<screen><label text=\"hi\"/></screen>"));
        byte[] big = new byte[SyncLimits.CHUNK * 2 + 123];
        for (int i = 0; i < big.length; i++) big[i] = (byte) (i * 31);
        files.put("img/big.png", big);
        files.put("empty.uiml", new byte[0]);
        return files;
    }

    @Test
    void manifestHashIsStableAndDetectsAnyChange() throws Exception {
        BundleManifest a = Bundle.manifestOf(sampleFiles());
        BundleManifest b = Bundle.manifestOf(new java.util.TreeMap<>(sampleFiles()));
        assertEquals(a.hash(), b.hash(), "순서와 무관");
        Map<String, byte[]> changed = sampleFiles();
        changed.get("shop.uiml")[0] ^= 1;
        assertTrue(!Bundle.manifestOf(changed).hash().equals(a.hash()));
        Bundle.validate(a);
        BundleManifest lie = new BundleManifest("0".repeat(64), a.files());
        assertThrows(SyncException.class, () -> Bundle.validate(lie), "선언한 해시가 내용과 다르면 거부");
    }

    @Test
    void manifestValidationRejectsEscapesDuplicatesAndOversize() throws Exception {
        assertThrows(SyncException.class, () -> Bundle.manifestOf(Map.of("../evil.uiml", bytes("x"))));
        assertThrows(SyncException.class, () -> Bundle.manifestOf(Map.of("a.exe", bytes("x"))));
        FileEntry ok = Bundle.manifestOf(Map.of("a.uiml", bytes("x"))).files().get(0);
        List<FileEntry> dup = List.of(ok, new FileEntry("A.XML", ok.sha256(), 1));
        assertThrows(SyncException.class, () -> Bundle.validate(new BundleManifest(Bundle.bundleHash(dup), dup)), "대소문자만 다른 중복");
        List<FileEntry> huge = List.of(new FileEntry("a.uiml", ok.sha256(), SyncLimits.MAX_FILE_BYTES + 1L));
        assertThrows(SyncException.class, () -> Bundle.validate(new BundleManifest(Bundle.bundleHash(huge), huge)));
        List<FileEntry> tooMany = new ArrayList<>();
        for (int i = 0; i <= SyncLimits.MAX_FILES; i++) tooMany.add(new FileEntry("f" + i + ".uiml", ok.sha256(), 1));
        assertThrows(SyncException.class, () -> Bundle.validate(new BundleManifest(Bundle.bundleHash(tooMany), tooMany)));
        List<FileEntry> sixteen = new ArrayList<>();
        for (int i = 0; i < 3; i++) sixteen.add(new FileEntry("w" + i + ".mp4", ok.sha256(), SyncLimits.MAX_FILE_BYTES));
        assertDoesNotThrow(() -> {
            Bundle.validate(new BundleManifest(Bundle.bundleHash(sixteen.subList(0, 2)), sixteen.subList(0, 2)));
            return null;
        });
        assertThrows(SyncException.class, () -> Bundle.validate(new BundleManifest(Bundle.bundleHash(sixteen), sixteen)), "합계 상한(16MB) 초과");
    }

    @Test
    void onlyChangedFilesAreRequestedAndAssembledFilesAreVerified() throws Exception {
        Map<String, byte[]> files = sampleFiles();
        BundleManifest m = Bundle.manifestOf(files);
        Map<String, byte[]> cached = new LinkedHashMap<>();
        cached.put("shop.uiml", Bundle.sha256(files.get("shop.uiml")));                    // 같음
        cached.put("img/big.png", Bundle.sha256(bytes("old")));                          // 다름
        List<String> need = Bundle.missing(m, cached);
        assertEquals(List.of("empty.uiml", "img/big.png"), need.stream().sorted().toList());

        BundleAssembler asm = new BundleAssembler(m, need);
        for (String path : need) {
            for (BundleChunk c : Bundle.chunks(path, files.get(path))) asm.accept(c);
        }
        assertTrue(asm.complete());
        assertArrayEquals(files.get("img/big.png"), asm.files().get("img/big.png"));
        assertEquals(0, asm.files().get("empty.uiml").length);
    }

    @Test
    void assemblerRejectsTamperedUnrequestedAndMisorderedChunks() throws Exception {
        Map<String, byte[]> files = sampleFiles();
        BundleManifest m = Bundle.manifestOf(files);
        assertThrows(SyncException.class, () -> new BundleAssembler(m, List.of("not-in-manifest.uiml")));

        BundleAssembler a = new BundleAssembler(m, List.of("img/big.png"));
        List<BundleChunk> chunks = Bundle.chunks("img/big.png", files.get("img/big.png"));
        assertEquals(3, chunks.size());
        assertThrows(SyncException.class, () -> a.accept(new BundleChunk("shop.uiml", 0, 1, new byte[1])), "요청하지 않은 파일");
        assertThrows(SyncException.class, () -> a.accept(chunks.get(1)), "순서 어긋남");
        assertNull(a.accept(chunks.get(0)));
        assertThrows(SyncException.class, () -> a.accept(chunks.get(0)), "중복 조각");
        BundleChunk shortChunk = new BundleChunk("img/big.png", 1, 3, new byte[10]);
        assertThrows(SyncException.class, () -> a.accept(shortChunk), "크기가 맞지 않는 조각");
        assertThrows(SyncException.class, () -> a.accept(new BundleChunk("img/big.png", 1, 99, chunks.get(1).data())), "조각 개수 위조");

        BundleAssembler b = new BundleAssembler(m, List.of("img/big.png"));
        b.accept(chunks.get(0));
        b.accept(chunks.get(1));
        byte[] tampered = chunks.get(2).data().clone();
        tampered[0] ^= 1;
        assertThrows(SyncException.class, () -> b.accept(new BundleChunk("img/big.png", 2, 3, tampered)), "내용이 바뀐 파일은 해시에서 걸린다");
        assertFalse(b.complete());
    }

    // ---- 액션 스키마 / 속도 제한 ----

    @Test
    void actionSchemaChecksTypesRangesLengthsAndUnknownArgs() {
        ActionSchema s = ActionSchema.none()
                .with("item", ActionSchema.Arg.string(16))
                .with("count", ActionSchema.Arg.integer(1, 64))
                .with("ratio", ActionSchema.Arg.number(0, 1).optional())
                .with("confirm", ActionSchema.Arg.bool().optional());
        assertNull(s.validate(Map.of("item", "apple", "count", 3L)));
        assertNull(s.validate(Map.of("item", "apple", "count", 3L, "ratio", 1L, "confirm", true)));
        assertNotNull(s.validate(Map.of("item", "apple")), "필수 인자 없음");
        assertNotNull(s.validate(Map.of("item", "apple", "count", 100L)), "범위 초과");
        assertNotNull(s.validate(Map.of("item", "apple", "count", 3.5)), "정수가 아님");
        assertNotNull(s.validate(Map.of("item", 5L, "count", 3L)), "문자열이 아님");
        assertNotNull(s.validate(Map.of("item", "x".repeat(17), "count", 3L)), "너무 긴 문자열");
        assertNotNull(s.validate(Map.of("item", "apple", "count", 3L, "extra", "x")), "알 수 없는 인자");
        assertNotNull(s.validate(Map.of("item", "apple", "count", 3L, "ratio", 2.0)));
        assertNull(ActionSchema.none().validate(Map.of()));
        assertNotNull(ActionSchema.none().validate(Map.of("a", 1L)));
    }

    @Test
    void rateLimiterAllowsBurstsThenRefills() {
        long[] now = {0};
        RateLimiter r = new RateLimiter(10, 3, () -> now[0]);
        assertTrue(r.allow() && r.allow() && r.allow());
        assertFalse(r.allow(), "버스트(3)를 넘으면 거부");
        now[0] += 250_000_000L;                                                         // 0.25초 = 2.5개 충전
        assertTrue(r.allow() && r.allow());
        assertFalse(r.allow());
        now[0] += 10_000_000_000L;
        assertTrue(r.allow(), "오래 쉬면 다시 가득(최대 3)");
        assertTrue(r.allow() && r.allow());
        assertFalse(r.allow());
    }
}
