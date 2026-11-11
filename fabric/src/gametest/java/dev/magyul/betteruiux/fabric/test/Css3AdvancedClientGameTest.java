package dev.magyul.betteruiux.fabric.test;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.edit.EditorController;
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
 * 실제 게임 창에서 {@link Css3ClientGameTest} 가 다루지 않는 것: 에디터 패널의 새 구역과 값 출처 툴팁, 위젯 글자 스타일,
 * {@code @media}(GUI 배율을 바꿔 뷰포트 크기를 바꾼다), 렌더링 비용(상자 수백 개를 그리는 시간).
 */
@SuppressWarnings("UnstableApiUsage")
public class Css3AdvancedClientGameTest implements FabricClientGameTest {
    private Path base;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        base = FabricLoader.getInstance().getConfigDir().resolve("better-uiux");
        writeFixtures();
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            setGuiScale(ctx, 1);
            ctx.waitTicks(3);
            widgetText(ctx);
            mediaQueryFollowsGuiScale(ctx);
            editorPanelAndOriginTooltip(ctx);
            renderCost(ctx);
            firstUseHitch(ctx);
        }
    }

    // ---- 시나리오 ----

    /** checkbox/button 의 글자가 text-transform 을 따른다(위젯 글자 스타일). */
    private void widgetText(ClientGameTestContext ctx) {
        open(ctx, "gt_adv_widgets", false);
        ctx.takeScreenshot("adv-1-widgets");
        // letter-spacing 이면 글자를 하나씩, bold 면 두 번 그리므로 이어 붙여서 본다
        String all = String.join("", texts(paint(ctx)));
        check(all.contains("HELLO"), "checkbox 글자가 text-transform: uppercase 로 그려져야 한다: " + all);
        check(all.contains("LOUD"), "버튼 글자도 대문자여야 한다: " + all);
        check(!all.contains("hello") && !all.contains("loud"), "원문 소문자가 그대로 그려지면 안 된다: " + all);
    }

    /** @media (max-width) 는 GUI 배율을 바꾸면(= 뷰포트 크기가 바뀌면) 켜지고 꺼진다. */
    private void mediaQueryFollowsGuiScale(ClientGameTestContext ctx) {
        open(ctx, "gt_adv_media", false);
        int[] vp = ctx.computeOnClient(mc -> new int[] {mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()});
        check(vp[0] > 600, "배율 1 에서 GUI 폭이 600 보다 커야 한다: " + vp[0]);
        check(hasFill(paint(ctx), box(ctx, "m"), 0xFF00AA00), "넓은 화면에서는 기본(초록)이어야 한다");
        ctx.takeScreenshot("adv-2-media-wide");

        setGuiScale(ctx, 4);                                          // 1920 → 480: GUI 폭이 1/4 → max-width: 600 규칙이 켜진다
        ctx.waitTicks(5);
        int[] vp2 = ctx.computeOnClient(mc -> new int[] {mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()});
        check(vp2[0] <= 600, "배율 4 에서 GUI 폭이 600 이하여야 한다: " + vp2[0]);
        ctx.takeScreenshot("adv-3-media-narrow");
        check(hasFill(paint(ctx), box(ctx, "m"), 0xFFFF0000), "좁은 화면에서는 @media 규칙(빨강)이 적용돼야 한다");

        setGuiScale(ctx, 1);
        ctx.waitTicks(5);
        check(hasFill(paint(ctx), box(ctx, "m"), 0xFF00AA00), "다시 넓어지면 기본으로 돌아와야 한다");
    }

    /** 에디터 패널에 새 구역(크기·배치/글자/효과)이 있고, 값 칸에 마우스를 올리면 "출처:" 툴팁이 뜬다. */
    private void editorPanelAndOriginTooltip(ClientGameTestContext ctx) {
        open(ctx, "gt_adv_editor", true);
        selectById(ctx, "card");
        ctx.waitTicks(2);
        ctx.takeScreenshot("adv-4-editor-sections");
        for (String section : List.of("크기·배치", "효과")) {
            check(findContaining(ctx, section) != null, "속성 패널에 '" + section + "' 구역이 보여야 한다");
        }
        int before = editorTexts(ctx).size();
        int[] fx = findContaining(ctx, "효과");
        click(ctx, fx[0] + 2, fx[1] + 2);                              // 접힌 구역을 펼친다
        ctx.waitTicks(2);
        ctx.takeScreenshot("adv-5-editor-fx-open");
        check(editorTexts(ctx).size() > before, "효과 구역을 펴면 행이 늘어야 한다");

        int[] value = findFirst(ctx, "8");                             // 안쪽여백 행(.card { padding: 8 } 에서 온 값)
        check(value != null, "padding 값(8) 칸을 찾지 못했다: " + editorTexts(ctx));
        ctx.getInput().setCursorPos(value[0] + 1, value[1] + 1);
        ctx.waitTicks(14);                                             // 툴팁은 0.45 초 뒤에 뜬다
        ctx.takeScreenshot("adv-6-origin-tooltip");
        String tip = editorTexts(ctx).stream().filter(t -> t.startsWith("출처:")).findFirst().orElse(null);
        check(tip != null, "값 칸 위에서 '출처:' 툴팁이 떠야 한다: " + editorTexts(ctx));
        check(tip.contains(".card") && tip.contains("padding"), "툴팁이 어느 규칙인지 알려줘야 한다: " + tip);
        ctx.getInput().setCursorPos(2, 2);

        ctx.runOnClient(mc -> {                                        // 글자 구역은 글자를 가진 요소(label)에만 있다
            EditorController ed = ((UiScreen) mc.gui.screen()).editor();
            ed.select(ed.document().layout().children.get(0).children.get(0).node.sourceIndex());
        });
        ctx.waitTicks(3);
        ctx.takeScreenshot("adv-6b-editor-label");
        check(findContaining(ctx, "글자") != null, "label 을 고르면 '글자' 구역이 보여야 한다: " + editorTexts(ctx));
    }

    /** 상자 수백 개(둥근 모서리+그림자+그라디언트)를 그리는 비용: 그리기 명령 수와 한 프레임당 시간을 기록하고 상한을 확인한다. */
    private void renderCost(ClientGameTestContext ctx) {
        open(ctx, "gt_adv_widgets", false);                            // 비교 기준: 거의 아무것도 안 그리는 화면
        ctx.waitTicks(60);
        System.out.println("[PERF] baseline fps (small screen) = " + ctx.computeOnClient(mc -> mc.getFps()));
        for (String v : List.of("gt_adv_perf_plain", "gt_adv_perf_round", "gt_adv_perf_grad", "gt_adv_perf_border", "gt_adv_perf_shadow")) {
            open(ctx, v, false);
            ctx.waitTicks(60);
            System.out.println("[PERF] " + v + " fps = " + ctx.computeOnClient(mc -> mc.getFps()) + ", commands = " + paint(ctx).size());
        }
        open(ctx, "gt_adv_perf", false);
        ctx.takeScreenshot("adv-7-perf-grid");
        double[] r = ctx.computeOnClient(mc -> {
            UiScreen s = (UiScreen) mc.gui.screen();
            DrawList warm = new DrawList();
            for (int i = 0; i < 20; i++) s.document().paint(warm = new DrawList(), 0, 0);
            long t0 = System.nanoTime();
            int n = 100, cmds = 0;
            for (int i = 0; i < n; i++) {
                DrawList l = new DrawList();
                s.document().paint(l, 0, 0);
                cmds = l.commands().size();
            }
            return new double[] {(System.nanoTime() - t0) / 1e6 / n, cmds, s.document().layout().children.size()};
        });
        System.out.printf("[PERF] boxes=%d drawCommands=%d paint=%.3f ms/frame%n", (int) r[2], (int) r[1], r[0]);
        ctx.waitTicks(40);
        int fps = ctx.computeOnClient(mc -> mc.getFps());
        System.out.println("[PERF] fps while showing the grid = " + fps);
        check(r[1] > 1000, "그리기 명령이 충분히 만들어져야 한다(합성 렌더링): " + r[1]);
        check(r[0] < 10.0, "한 프레임 그리기 명령 생성이 10ms 를 넘으면 안 된다: " + r[0] + "ms");
    }

    /** 처음 쓰는 둥근 사각형 파이프라인(반지름·번짐 조합)을 처음 그릴 때 한 프레임이 얼마나 튀는지 잰다(조합마다 셰이더를 컴파일하므로). */
    private void firstUseHitch(ClientGameTestContext ctx) {
        open(ctx, "gt_adv_widgets", false);
        ctx.waitTicks(20);
        long base = 0;
        for (int i = 0; i < 10; i++) {
            long t0 = System.nanoTime();
            ctx.waitTick();
            base = Math.max(base, System.nanoTime() - t0);
        }
        long worst = 0;
        for (int radius : new int[] {13, 17, 19, 23, 29}) {                          // 처음 보는 조합 5개를 하나씩 열어 본다
            String id = "gt_adv_hitch_" + radius;
            try {
                Files.writeString(base().resolve("ui/" + id + ".xml"),
                        "<screen><style>#a { width: 60; height: 40; background: linear-gradient(#FF0000, #0000FF); border-radius: " + radius
                                + "; border: 3 solid #FFFFFF; box-shadow: 0 2 " + (radius % 7 + 3) + " #000000AA; }</style><panel id='a' anchor='center'/></screen>");
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            open(ctx, id, false);
            for (int i = 0; i < 4; i++) {
                long t0 = System.nanoTime();
                ctx.waitTick();
                worst = Math.max(worst, System.nanoTime() - t0);
            }
        }
        System.out.printf("[PERF] first-use pipeline hitch: worst tick %.1f ms (steady tick max %.1f ms)%n", worst / 1e6, base / 1e6);
        check(worst < 1_500_000_000L, "처음 쓰는 파이프라인 때문에 한 프레임이 1.5초를 넘으면 안 된다: " + worst / 1e6 + " ms");
    }

    private Path base() { return base; }

    // ---- 준비/도우미 ----

    private void writeFixtures() {
        try {
            Files.createDirectories(base.resolve("ui"));
            Files.writeString(base.resolve("config.toml"), "[editor]\nenabled = true\n");
            Files.writeString(base.resolve("ui/gt_adv_widgets.xml"), """
                    <screen>
                      <style>
                        checkbox, button { text-transform: uppercase; font-weight: bold; letter-spacing: 1; }
                      </style>
                      <column gap="6" anchor="top-left" x="10" y="10">
                        <checkbox id="cb" text="hello"/>
                        <button id="b" text="loud" width="80" height="20"/>
                      </column>
                    </screen>
                    """);
            Files.writeString(base.resolve("ui/gt_adv_media.xml"), """
                    <screen>
                      <style>
                        #m { width: 60; height: 30; background: #00AA00; }
                        @media (max-width: 600) { #m { background: #FF0000; } }
                      </style>
                      <panel anchor="top-left" x="10" y="10"><panel id="m"/></panel>
                    </screen>
                    """);
            Files.writeString(base.resolve("ui/gt_adv_editor.xml"), """
                    <screen>
                      <style>
                        .card { padding: 8; background: #181A24E0; }
                      </style>
                      <panel id="card" class="card" anchor="center" width="160" height="70"><label text="card"/></panel>
                    </screen>
                    """);
            perfDoc("gt_adv_perf_plain", "background: #4488CC;");
            perfDoc("gt_adv_perf_round", "background: #4488CC; border-radius: 4;");
            perfDoc("gt_adv_perf_grad", "background: linear-gradient(#4488CC, #224466); border-radius: 4;");
            perfDoc("gt_adv_perf_border", "background: #4488CC; border-radius: 4; border: 1 solid #FFFFFF55;");
            perfDoc("gt_adv_perf_shadow", "background: #4488CC; border-radius: 4; box-shadow: 0 1 3 #000000AA;");
            perfDoc("gt_adv_perf", "background: linear-gradient(#4488CC, #224466); border-radius: 4; box-shadow: 0 1 3 #000000AA; border: 1 solid #FFFFFF55;");
        } catch (IOException e) {
            throw new AssertionError("테스트 파일을 만들지 못했다: " + e, e);
        }
    }

    /** 칸 240 개짜리 격자 화면(렌더링 비용 비교용). */
    private void perfDoc(String name, String css) throws IOException {
        StringBuilder grid = new StringBuilder("<screen><style>.c { width: 28; height: 14; margin: 1; " + css + " }</style>"
                + "<grid columns=\"24\" anchor=\"top-left\" x=\"4\" y=\"4\">");
        for (int i = 0; i < 240; i++) grid.append("<panel class=\"c\"/>");
        grid.append("</grid></screen>");
        Files.writeString(base.resolve("ui/" + name + ".xml"), grid.toString());
    }

    private void setGuiScale(ClientGameTestContext ctx, int scale) {
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(scale);
            mc.resizeGui();
        });
    }

    private void open(ClientGameTestContext ctx, String id, boolean editor) {
        ctx.runOnClient(mc -> UiOpener.open(id, base, editor));
        ctx.waitFor(mc -> mc.gui.screen() instanceof UiScreen s && s.uiId().equals(id) && (!editor || s.editor() != null));
        ctx.waitTicks(5);
    }

    private void selectById(ClientGameTestContext ctx, String elementId) {
        ctx.runOnClient(mc -> {
            EditorController ed = ((UiScreen) mc.gui.screen()).editor();
            int idx = ed.document().layout().children.stream().filter(b -> elementId.equals(b.id())).findFirst()
                    .orElseThrow(() -> new AssertionError("요소 #" + elementId + " 없음")).node.sourceIndex();
            ed.select(idx);
        });
    }

    private LayoutBox box(ClientGameTestContext ctx, String id) {
        LayoutBox b = ctx.computeOnClient(mc -> ((UiScreen) mc.gui.screen()).document().findById(id));
        check(b != null, "요소 #" + id + " 없음");
        return b;
    }

    private List<DrawCommand> paint(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> {
            DrawList l = new DrawList();
            double s = mc.getWindow().getGuiScale();
            ((UiScreen) mc.gui.screen()).document().paint(l, (int) (mc.mouseHandler.xpos() / s), (int) (mc.mouseHandler.ypos() / s));
            return l.commands();
        });
    }

    private static List<String> texts(List<DrawCommand> cmds) {
        return cmds.stream().filter(c -> c instanceof DrawCommand.DrawText).map(c -> ((DrawCommand.DrawText) c).text()).toList();
    }

    private List<String> editorTexts(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> {
            DrawList l = new DrawList();
            ((UiScreen) mc.gui.screen()).editor().paint(l);
            return texts(l.commands());
        });
    }

    private int[] find(ClientGameTestContext ctx, String text) {
        return ctx.computeOnClient(mc -> {
            DrawList l = new DrawList();
            ((UiScreen) mc.gui.screen()).editor().paint(l);
            return l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText d && d.text().equals(text))
                    .map(c -> (DrawCommand.DrawText) c).reduce((a, b) -> b).map(d -> new int[] {d.x(), d.y()}).orElse(null);
        });
    }

    private int[] findFirst(ClientGameTestContext ctx, String text) {
        return ctx.computeOnClient(mc -> {
            DrawList l = new DrawList();
            ((UiScreen) mc.gui.screen()).editor().paint(l);
            return l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText d && d.text().equals(text))
                    .map(c -> (DrawCommand.DrawText) c).findFirst().map(d -> new int[] {d.x(), d.y()}).orElse(null);
        });
    }

    private int[] findContaining(ClientGameTestContext ctx, String text) {
        return ctx.computeOnClient(mc -> {
            DrawList l = new DrawList();
            ((UiScreen) mc.gui.screen()).editor().paint(l);
            return l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText d && d.text().contains(text))
                    .map(c -> (DrawCommand.DrawText) c).reduce((a, b) -> b).map(d -> new int[] {d.x(), d.y()}).orElse(null);
        });
    }

    private void click(ClientGameTestContext ctx, int x, int y) {
        double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
        var in = ctx.getInput();
        in.setCursorPos(x * scale, y * scale);
        ctx.waitTick();
        in.pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTick();
    }

    private static boolean hasFill(List<DrawCommand> cmds, LayoutBox b, int argb) {
        return cmds.stream().anyMatch(c -> c instanceof DrawCommand.FillRect r && r.argb() == argb
                && r.x() >= b.x && r.x() < b.x + b.w && r.y() >= b.y && r.y() < b.y + b.h);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
