package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.LayoutBox;
import dev.magyul.betteruiux.core.doc.UiConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 개발용 자동 시험(-Dbetteruiux.serverselftest=true): 서버에 접속한 클라이언트가 스스로 {@code /serverui open Dev shop} 을 치고,
 * 서버 화면의 버튼을 누르고, 결과를 로그({@code [selftest]})에 남긴 뒤 게임을 끝낸다. 사람이 화면을 조작하지 않아도 서버↔클라이언트를 확인할 수 있다.
 * 서버에 {@code server-ui/shop.xml}(demo:ping / demo:say 버튼)과 이름이 Dev 인 관리자 계정이 있어야 한다.
 */
final class ServerSelfTest {
    private static final Logger LOG = LoggerFactory.getLogger("better_uiux");
    private static final List<String> RESULTS = new ArrayList<>();

    private ServerSelfTest() {}

    static boolean enabled() { return Boolean.getBoolean("betteruiux.serverselftest"); }

    static void start() {
        Thread t = new Thread(ServerSelfTest::run, "better-uiux-selftest");
        t.setDaemon(true);
        t.start();
    }

    private static <T> T main(Supplier<T> s) {
        CompletableFuture<T> f = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try { f.complete(s.get()); } catch (Throwable e) { f.completeExceptionally(e); }
        });
        try { return f.get(10, TimeUnit.SECONDS); } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static boolean waitFor(Supplier<Boolean> cond, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (Boolean.TRUE.equals(main(cond))) return true;
            sleep(150);
        }
        return false;
    }

    private static void check(String name, boolean ok) {
        RESULTS.add((ok ? "PASS " : "FAIL ") + name);
        LOG.info("[selftest] {} {}", ok ? "PASS" : "FAIL", name);
    }

    private static UiDocument serverDoc() {
        return Minecraft.getInstance().gui.screen() instanceof UiScreen s && s.isServerUi() ? s.document() : null;
    }

    private static LayoutBox boxOf(LayoutBox b, dev.magyul.betteruiux.core.xml.UiNode n) {
        if (b.node == n) return b;
        for (LayoutBox c : b.children) {
            LayoutBox r = boxOf(c, n);
            if (r != null) return r;
        }
        return null;
    }

    /** 글자가 text 인 버튼을 눌렀다 뗀다. @return 누를 수 있었으면 true */
    private static boolean click(String text) {
        return main(() -> {
            UiDocument d = serverDoc();
            if (d == null) return false;
            for (var el : d.queryAll("button")) {
                if (!text.equals(el.text())) continue;
                LayoutBox b = boxOf(d.layout(), el.node());
                if (b == null) return false;
                int x = b.x + b.w / 2, y = b.y + b.h / 2;
                d.mousePressed(x, y, 0);
                d.mouseReleased(x, y, 0);
                return true;
            }
            return false;
        });
    }

    private static long num(String key) {
        Object v = main(() -> ClientSync.stateValue(key));
        return v instanceof Long l ? l : -1;
    }

    private static final String SHOPTEST = "<screen><panel anchor=\"center\" width=\"160\" height=\"60\"><column gap=\"4\">"
            + "<label text=\"%s\"/><button text=\"핑\" onClick=\"server:demo:ping\"/></column></panel></screen>";

    private static void setCfg(String key, String value) throws Exception {
        UiConfig.update(ClientSync.baseDir().resolve("config.toml"), Map.of(key, value));
    }

    private static boolean hasLabel(String text) {
        return Boolean.TRUE.equals(main(() -> {
            UiDocument d = serverDoc();
            if (d == null) return false;
            for (var el : d.queryAll("label")) if (text.equals(el.text())) return true;
            return false;
        }));
    }

    private static void openAndExpectLabel(Minecraft mc, String name, String label) {
        main(() -> { mc.getConnection().sendCommand("serverui open Dev shoptest"); return null; });
        boolean opened = waitFor(() -> serverDoc() != null, 10_000);
        sleep(300);
        check(name, opened && hasLabel(label));
        main(() -> { mc.getConnection().sendCommand("serverui close Dev shoptest"); return null; });
        waitFor(() -> serverDoc() == null, 10_000);
    }

    private static void publishAndPolicy(Minecraft mc) throws Exception {
        Path ui = ClientSync.baseDir().resolve("ui");
        Files.createDirectories(ui);
        Files.writeString(ui.resolve("shoptest.xml"), String.format(SHOPTEST, "SERVER VERSION"));
        Path cached = ClientSync.cacheFiles().resolve("shoptest.xml");
        Files.deleteIfExists(cached);

        // 권한 노드: 권한 제공자(개발용 파일, Fabric)가 거부하면 레벨 4 여도 거부, 허용하면 성공. 파일이 없으면 기본 권한 레벨(NeoForge 는 이 부분만 확인).
        String pf = System.getProperty("betteruiux.perm.file");
        Path permFile = pf == null ? null : Path.of(pf);
        if (permFile != null) {
            Files.writeString(permFile, "deny better_uiux:publish\n");
            main(() -> ClientSync.publishUi("shoptest"));
            sleep(2000);
            check("권한 노드가 거부하면(레벨 4 여도) 발행 거부", !Files.exists(cached));
            Files.writeString(permFile, "allow better_uiux:publish\n");
        }
        main(() -> ClientSync.publishUi("shoptest"));
        boolean arrived = waitFor(() -> Files.exists(cached), 10_000);
        check(permFile != null ? "권한 노드가 허용하면 발행 성공 → 서버 번들 갱신 → 캐시에 도착" : "레벨 4 이면 발행 성공 → 서버 번들 갱신 → 캐시에 도착", arrived);
        if (permFile != null) Files.deleteIfExists(permFile);                       // 이후는 기본 권한 레벨로 판단

        // 정책: 같은 id 의 로컬 파일이 있을 때(기본 local-first 라 로컬이 이긴다)
        Files.writeString(ui.resolve("shoptest.xml"), String.format(SHOPTEST, "LOCAL VERSION"));
        openAndExpectLabel(mc, "정책 local-first(기본): 로컬 파일이 이김", "LOCAL VERSION");
        setCfg("server.allowOverride", "true");                                      // 서버(server-first 요청) + 내가 허락 → 서버 파일이 이긴다
        openAndExpectLabel(mc, "정책 server-first + allowOverride: 서버 파일이 이김", "SERVER VERSION");
        setCfg("server.allowOverride", "false");
        Files.deleteIfExists(ui.resolve("shoptest.xml"));

        // 권한 레벨: 관리자 권한을 잃으면(/deop) 발행은 거부된다. ops.json 은 끝에 원래대로 되돌린다(같은 개발용 실행 폴더).
        Path ops = ClientSync.baseDir().getParent().getParent().resolve("ops.json");
        String opsBackup = Files.exists(ops) ? Files.readString(ops) : null;
        try {
            Files.writeString(ui.resolve("shoptest2.xml"), String.format(SHOPTEST, "NO PERMISSION"));
            Path cached2 = ClientSync.cacheFiles().resolve("shoptest2.xml");
            Files.deleteIfExists(cached2);
            main(() -> { mc.getConnection().sendCommand("deop Dev"); return null; });
            sleep(1000);
            main(() -> ClientSync.publishUi("shoptest2"));
            sleep(2500);
            check("관리자(레벨 4) 권한이 없으면 발행 거부", !Files.exists(cached2));
        } finally {
            if (opsBackup != null) Files.writeString(ops, opsBackup);
            Files.deleteIfExists(ui.resolve("shoptest2.xml"));
        }
    }

    private static int[] pollSize(String src, long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            int[] r = main(() -> ExternalImages.size(src, null));
            if (r != null) return r;
            sleep(250);
        }
        return null;
    }

    private static void remoteMedia() throws Exception {
        String png = "https://www.gstatic.com/images/branding/googlelogo/1x/googlelogo_color_272x92dp.png";
        String pin = "#sha256=5776cd87617eacec3bc00ebcf530d1924026033eda852f706c1a675a98915826";
        int[] ok = pollSize(png + pin, 25_000);
        check("https 이미지(핀 있음) 로드 272x92", ok != null && ok[0] == 272 && ok[1] == 92);
        check("핀 없는 주소는 기본(pinned)에서 거부", pollSize(png + "?a=1", 4_000) == null);
        check("핀이 틀리면 거부", pollSize(png + "?b=1#sha256=" + "00".repeat(32), 4_000) == null);
        check("내부 주소는 거부", pollSize("https://127.0.0.1/a.png", 2_000) == null && pollSize("https://localhost/a.png", 2_000) == null);
        setCfg("media.remote", "on");
        int[] unpinned = pollSize(png + "?c=1", 20_000);
        check("media.remote=on 이면 핀 없는 주소도 허용", unpinned != null && unpinned[0] == 272);
        setCfg("media.remote", "off");
        check("media.remote=off 이면 원격 미디어 끔", pollSize(png + "?d=1", 4_000) == null);
        setCfg("media.remote", "pinned");
    }

    private static void run() {
        try {
            Minecraft mc = Minecraft.getInstance();
            check("접속 후 서버 기능 켜짐", waitFor(() -> mc.player != null && ClientSync.active(), 90_000));
            sleep(1500);
            main(() -> { mc.getConnection().sendCommand("serverui open Dev shop"); return null; });
            check("서버가 화면을 열었다", waitFor(() -> serverDoc() != null, 15_000));

            long c0 = Math.max(0, num("clicks"));
            check("핑 버튼 클릭", click("핑"));
            sleep(600);
            check("핑 → 서버 값 clicks 증가", num("clicks") == c0 + 1);

            click("말하기");
            sleep(600);
            Object said = main(() -> ClientSync.stateValue("said"));
            check("말하기(arg-text, arg-n) → said = '안녕 x2'", "안녕 x2".equals(said));

            click("잘못된 인자");
            sleep(600);
            check("범위 밖 인자(n=99)는 서버가 거부(said 그대로)", "안녕 x2".equals(main(() -> ClientSync.stateValue("said"))));

            check("script: 버튼 클릭(기본 차단, 예외 없음)", click("script 시도"));
            sleep(300);

            long before = num("clicks");
            for (int i = 0; i < 40; i++) { click("핑"); sleep(10); }
            sleep(800);
            long after = num("clicks");
            check("연타는 한도까지만 처리(" + (after - before) + "회)", after > before && after - before <= 16);   // 버스트 10 + 연타하는 동안 채워지는 몫
            check("연타 후에도 서버 기능 유지", ClientSync.active());
            sleep(3000);
            long b2 = num("clicks");
            click("핑");
            sleep(600);
            check("연타 뒤 잠시 후 다시 동작", num("clicks") == b2 + 1);

            click("닫기");
            sleep(500);
            check("닫기 버튼으로 화면이 닫힘", main(() -> serverDoc() == null));
            long closedClicks = num("clicks");
            main(() -> ClientSync.core().sendAction("shop", "demo:ping", Map.of()));      // 닫은 화면의 동작은 서버가 이미 알고 있어 무시해야 한다
            sleep(700);
            check("닫은 화면의 서버 동작은 무시됨(UiClosed 통지)", num("clicks") == closedClicks);

            // 서버가 닫기를 지시하는 경우
            main(() -> { mc.getConnection().sendCommand("serverui open Dev shop"); return null; });
            check("다시 열기", waitFor(() -> serverDoc() != null, 10_000));
            main(() -> { mc.getConnection().sendCommand("serverui close Dev shop"); return null; });
            check("서버가 닫으라고 하면 닫힘", waitFor(() -> serverDoc() == null, 10_000));

            publishAndPolicy(mc);
            remoteMedia();
        } catch (Throwable e) {
            check("자동 시험 중 예외: " + e, false);
        }
        long fails = RESULTS.stream().filter(r -> r.startsWith("FAIL")).count();
        LOG.info("[selftest] 끝: {}개 중 실패 {}개", RESULTS.size(), fails);
        sleep(500);
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
    }
}
