package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.LayoutBox;
import java.util.ArrayList;
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

            // 서버가 닫기를 지시하는 경우
            main(() -> { mc.getConnection().sendCommand("serverui open Dev shop"); return null; });
            check("다시 열기", waitFor(() -> serverDoc() != null, 10_000));
            main(() -> { mc.getConnection().sendCommand("serverui close Dev shop"); return null; });
            check("서버가 닫으라고 하면 닫힘", waitFor(() -> serverDoc() == null, 10_000));
        } catch (Throwable e) {
            check("자동 시험 중 예외: " + e, false);
        }
        long fails = RESULTS.stream().filter(r -> r.startsWith("FAIL")).count();
        LOG.info("[selftest] 끝: {}개 중 실패 {}개", RESULTS.size(), fails);
        sleep(500);
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
    }
}
