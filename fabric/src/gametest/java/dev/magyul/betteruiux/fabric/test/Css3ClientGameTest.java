package dev.magyul.betteruiux.fabric.test;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.layout.LayoutBox;
import dev.magyul.betteruiux.mc26.UiOpener;
import dev.magyul.betteruiux.mc26.UiScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 실제 게임 창에서 .uicss 의 CSS3 확장(둥근 모서리, 그라디언트, 그림자, flex/grid, 상태 규칙, ::before, 레이아웃 애니메이션)이
 * 그려지고 동작하는지 확인한다. 실행: {@code ./gradlew :fabric:runClientGameTest} — 스크린샷은 {@code fabric/build/run/clientGameTest/screenshots}.
 */
@SuppressWarnings("UnstableApiUsage")
public class Css3ClientGameTest implements FabricClientGameTest {
    private Path base;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        base = FabricLoader.getInstance().getConfigDir().resolve("better-uiux");
        writeFixtures();
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(1);                       // GUI 좌표 = 창 픽셀
                mc.resizeGui();
            });
            ctx.waitTicks(3);
            open(ctx, "gt_css");
            boxDecorations(ctx);
            flexAndGrid(ctx);
            pseudoElement(ctx);
            hoverRule(ctx);
            layoutAnimation(ctx);
            noWarnings(ctx);
        }
    }

    // ---- 시나리오 ----

    /** 둥근 모서리/그라디언트/그림자/아웃라인이 실제로 그려진다(여러 fillRect·fillGradient 로 합성). */
    private void boxDecorations(ClientGameTestContext ctx) {
        ctx.takeScreenshot("css-1-box");
        LayoutBox b = box(ctx, "deco");
        List<DrawCommand> cmds = paint(ctx);
        long inside = cmds.stream().filter(c -> c instanceof DrawCommand.FillRect r && r.x() >= b.x - 2 && r.x() <= b.x + b.w + 2
                && r.y() >= b.y - 2 && r.y() <= b.y + b.h + 2).count();
        check(inside > 6, "둥근 모서리/그림자/아웃라인이 여러 사각형으로 그려져야 한다: " + inside);
        // 둥근 모서리와 함께면 세로 띠(FillGradient) 대신 색이 조금씩 다른 사각형 줄로 합성될 수 있다: 어느 쪽이든 파랑 계열 색이 둘 이상이어야 한다
        long blues = cmds.stream().filter(c -> c instanceof DrawCommand.FillRect r && r.x() >= b.x && r.x() < b.x + b.w
                && r.y() >= b.y && r.y() < b.y + b.h && (r.argb() & 0xFF) > (r.argb() >> 16 & 0xFF) && r.argb() >>> 24 == 0xFF)
                .map(c -> ((DrawCommand.FillRect) c).argb()).distinct().count()
                + cmds.stream().filter(c -> c instanceof DrawCommand.FillGradient).count();
        check(blues >= 2, "그라디언트(파랑 → 남색)가 그려져야 한다: " + blues);
        // 둥근 모서리: 맨 위 줄은 모서리 때문에 박스 폭보다 좁다
        check(cmds.stream().noneMatch(c -> c instanceof DrawCommand.FillRect r && r.y() == b.y && r.x() == b.x && r.w() == b.w
                && r.argb() == 0xFF2255AA), "모서리가 둥글면 맨 위 줄이 전체 폭이 아니어야 한다");
    }

    /** flex-wrap 으로 줄이 바뀌고, grid fr 트랙이 폭을 나눈다. */
    private void flexAndGrid(ClientGameTestContext ctx) {
        LayoutBox a = box(ctx, "f1"), c = box(ctx, "f3");
        check(c.y > a.y, "flex-wrap 으로 셋째 칸이 다음 줄에 있어야 한다: " + a.y + " / " + c.y);
        LayoutBox g1 = box(ctx, "g1"), g2 = box(ctx, "g2");
        check(g1.h == 10 && hasFill(paint(ctx), g1, 0xFF666666), "grid 칸이 높이 10 으로 배경과 함께 그려져야 한다: h=" + g1.h);
        check(g2.x > g1.x && Math.abs(g2.w - g1.w * 2) <= 2, "grid 1fr 2fr 이면 둘째 칸이 두 배 넓어야 한다: " + g1.w + " / " + g2.w);
    }

    /** ::before 로 붙인 글자가 실제로 그려진다. */
    private void pseudoElement(ClientGameTestContext ctx) {
        check(paint(ctx).stream().anyMatch(c -> c instanceof DrawCommand.DrawText t && t.text().startsWith("► ")),
                "::before 글자가 그려져야 한다");
    }

    /** 진짜 마우스를 올리면 :hover 규칙이 그려진다(떠나면 원래대로). */
    private void hoverRule(ClientGameTestContext ctx) {
        LayoutBox h = box(ctx, "hov");
        int cx = h.x + h.w / 2, cy = h.y + h.h / 2;
        check(!hasFill(paint(ctx), h, 0xFFFF0000), "마우스 전에는 hover 색이 없어야 한다");
        ctx.getInput().setCursorPos(cx, cy);                        // GUI 배율 1
        ctx.waitTicks(3);
        ctx.takeScreenshot("css-2-hover");
        check(hasFill(paint(ctx), h, 0xFFFF0000), "마우스를 올리면 :hover 배경(빨강)이 그려져야 한다");
        ctx.getInput().setCursorPos(2, 2);
        ctx.waitTicks(3);
        check(!hasFill(paint(ctx), h, 0xFFFF0000), "마우스를 치우면 원래대로 돌아와야 한다");
    }

    /** 레이아웃 속성(width) 애니메이션: 옆 요소가 함께 밀리고, 끝나면 최종 값에 멈춘다. */
    private void layoutAnimation(ClientGameTestContext ctx) {
        LayoutBox grow = box(ctx, "grow"), next = box(ctx, "after");
        int w0 = grow.w, x0 = next.x;
        ctx.runOnClient(mc -> ((UiScreen) mc.gui.screen()).document().query("#grow").addClass("wide"));
        ctx.waitTicks(4);
        int mid = box(ctx, "grow").w;
        ctx.waitTicks(40);                                          // 0.5s 전환 + 여유
        LayoutBox g2 = box(ctx, "grow"), n2 = box(ctx, "after");
        ctx.takeScreenshot("css-3-layout-anim");
        check(g2.w == 120 && w0 < 120, "전환이 끝나면 width 가 120 이어야 한다: " + w0 + " → " + g2.w);
        check(n2.x - x0 == g2.w - w0, "옆 요소가 늘어난 만큼 밀려야 한다: " + x0 + " → " + n2.x);
        check(mid >= w0 && mid <= 120, "중간 값이 범위 안이어야 한다: " + mid);
    }

    private void noWarnings(ClientGameTestContext ctx) {
        List<String> warnings = ctx.computeOnClient(mc -> ((UiScreen) mc.gui.screen()).document().warnings());
        check(warnings.isEmpty(), "스타일 경고가 없어야 한다: " + warnings);
    }

    // ---- 준비/도우미 ----

    private void writeFixtures() {
        try {
            Files.createDirectories(base.resolve("ui"));
            Files.writeString(base.resolve("config.toml"), "[editor]\nenabled = true\n");
            Files.writeString(base.resolve("ui/gt_css.uiml"), """
                    <screen>
                      <style>
                        #deco { width: 120; height: 40; background: linear-gradient(#2255AA, #112244);
                                border-radius: 8; border: 2 solid #FFFFFF; box-shadow: 0 2 4 #000000AA; outline: 1 solid #FFAA00; }
                        #hov { width: 80; height: 24; background: #00AA00; }
                        #hov:hover { background: #FF0000; }
                        .row { flex-wrap: wrap; width: 100; gap: 2; }
                        .row > panel { width: 40; height: 10; background: #888888; }
                        .grid { grid-template-columns: 1fr 2fr; width: 150; gap: 0; }
                        .grid > panel { width: fill; height: 10; background: #666666; }
                        #tag::before { content: "► "; }
                        #grow { width: 40; height: 10; background: #3366CC; transition: width 0.5s linear; }
                        #grow.wide { width: 120; }
                        #after { width: 20; height: 10; background: #CC6633; }
                      </style>
                      <column gap="6" anchor="top-left" x="10" y="10">
                        <panel id="deco"/>
                        <panel id="hov"/>
                        <row class="row"><panel id="f1"/><panel id="f2"/><panel id="f3"/></row>
                        <grid class="grid"><panel id="g1"/><panel id="g2"/></grid>
                        <label id="tag" text="tag"/>
                        <row><panel id="grow"/><panel id="after"/></row>
                      </column>
                    </screen>
                    """);
        } catch (IOException e) {
            throw new AssertionError("테스트 파일을 만들지 못했다: " + e, e);
        }
    }

    private void open(ClientGameTestContext ctx, String id) {
        ctx.runOnClient(mc -> UiOpener.open(id, base, false));
        ctx.waitFor(mc -> mc.gui.screen() instanceof UiScreen s && s.uiId().equals(id));
        ctx.waitTicks(5);
    }

    private LayoutBox box(ClientGameTestContext ctx, String id) {
        LayoutBox b = ctx.computeOnClient(mc -> ((UiScreen) mc.gui.screen()).document().findById(id));
        check(b != null, "요소 #" + id + " 없음");
        return b;
    }

    private List<DrawCommand> paint(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> {
            DrawList l = new DrawList();
            double s = mc.getWindow().getGuiScale();                // 실제 마우스 위치(GUI 좌표)로 상태를 판정한다
            ((UiScreen) mc.gui.screen()).document().paint(l, (int) (mc.mouseHandler.xpos() / s), (int) (mc.mouseHandler.ypos() / s));
            return l.commands();
        });
    }

    private static boolean hasFill(List<DrawCommand> cmds, LayoutBox b, int argb) {
        return cmds.stream().anyMatch(c -> c instanceof DrawCommand.FillRect r && r.argb() == argb
                && r.x() >= b.x && r.x() < b.x + b.w && r.y() >= b.y && r.y() < b.y + b.h);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
