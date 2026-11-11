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
 * 실제 게임의 시간·입력으로 확인하는 동작: {@code @keyframes} 가 시간에 따라 움직임, 호버 색 전환(transition)이 중간 값을 거침,
 * 진짜 클릭으로 {@code :focus} 규칙 적용, {@code z-index}/{@code position:absolute} 겹침 순서, 외부 {@code @import},
 * {@code calc()}/{@code var()}, 시트 {@code !important} 대 인라인.
 */
@SuppressWarnings("UnstableApiUsage")
public class Css3BehaviorClientGameTest implements FabricClientGameTest {
    private Path base;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        base = FabricLoader.getInstance().getConfigDir().resolve("better-uiux");
        writeFixtures();
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(1);
                mc.resizeGui();
            });
            ctx.waitTicks(3);
            open(ctx, "gt_beh");
            keyframesMoveOverTime(ctx);
            hoverTransitionPassesThroughMidValue(ctx);
            focusRuleOnRealClick(ctx);
            zIndexOrder(ctx);
            importAndValues(ctx);
        }
    }

    /** @keyframes 로 translate-x 가 시간에 따라 커진다. */
    private void keyframesMoveOverTime(ClientGameTestContext ctx) {
        float a = translateX(ctx);
        ctx.waitTicks(15);
        float b = translateX(ctx);
        ctx.waitTicks(15);
        float c = translateX(ctx);
        ctx.takeScreenshot("beh-1-keyframes");
        check(b > a && c > b, "시간이 지나면 translate-x 가 커져야 한다: " + a + " → " + b + " → " + c);
    }

    private float translateX(ClientGameTestContext ctx) {
        LayoutBox m = box(ctx, "mover");
        List<DrawCommand> cmds = paint(ctx);
        // mover 의 배경 사각형 바로 앞의 PushTransform 의 tx
        for (int i = 0; i < cmds.size(); i++) {
            if (cmds.get(i) instanceof DrawCommand.FillRect r && r.argb() == 0xFF3366CC && r.w() == m.w) {
                for (int k = i - 1; k >= 0; k--) if (cmds.get(k) instanceof DrawCommand.PushTransform t) return t.tx();
            }
        }
        return 0f;
    }

    /** 호버하면 배경이 검정에서 빨강으로 1초 동안 천천히 바뀐다: 중간에 섞인 색이 나온다. */
    private void hoverTransitionPassesThroughMidValue(ClientGameTestContext ctx) {
        LayoutBox t = box(ctx, "fade");
        ctx.getInput().setCursorPos(t.x + t.w / 2, t.y + t.h / 2);
        ctx.waitTicks(12);                                              // 0.6 초 정도
        int mid = fillIn(paint(ctx), t);
        ctx.takeScreenshot("beh-2-transition-mid");
        int red = mid >> 16 & 0xFF;
        check(red > 25 && red < 235, "전환 중에는 빨강 성분이 중간 값이어야 한다: #" + Integer.toHexString(mid));
        ctx.waitTicks(40);
        check((fillIn(paint(ctx), t) >> 16 & 0xFF) >= 250, "전환이 끝나면 완전한 빨강이어야 한다");
        ctx.getInput().setCursorPos(2, 2);
        ctx.waitTicks(40);
    }

    /** 입력칸을 진짜로 누르면 :focus 규칙(테두리 색)이 적용된다. */
    private void focusRuleOnRealClick(ClientGameTestContext ctx) {
        LayoutBox f = box(ctx, "field");
        check(!hasFill(paint(ctx), f, 0xFFFFFF00), "누르기 전에는 :focus 색이 없어야 한다");
        ctx.getInput().setCursorPos(f.x + f.w / 2, f.y + f.h / 2);
        ctx.waitTick();
        ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(3);
        ctx.takeScreenshot("beh-3-focus");
        check(hasFill(paint(ctx), f, 0xFFFFFF00), "입력칸을 누르면 :focus 규칙이 적용돼야 한다");
        ctx.getInput().setCursorPos(2, 2);
        ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);   // 바깥을 눌러 포커스 해제
        ctx.waitTicks(3);
        check(!hasFill(paint(ctx), f, 0xFFFFFF00), "포커스를 잃으면 :focus 규칙이 풀려야 한다");
    }

    /** z-index 가 큰 것이 나중에(위에) 그려진다 — 문서 순서와 반대로 줘도. */
    private void zIndexOrder(ClientGameTestContext ctx) {
        List<DrawCommand> cmds = paint(ctx);
        int low = indexOfFill(cmds, 0xFF00AA00), high = indexOfFill(cmds, 0xFFAA0000);
        check(low >= 0 && high >= 0, "두 겹침 상자가 그려져야 한다: " + low + "," + high);
        check(low < high, "z-index 가 큰 빨강이 나중(위)에 그려져야 한다: 초록@" + low + " 빨강@" + high);
    }

    /** 외부 @import, calc()+var(), 시트 !important 가 인라인을 이긴다. */
    private void importAndValues(ClientGameTestContext ctx) {
        check(hasFill(paint(ctx), box(ctx, "imp"), 0xFFAB00CD), "@import 한 파일의 규칙이 적용돼야 한다");
        LayoutBox c = box(ctx, "calc");
        check(c.w == 70, "calc(var(--w) * 2 + 10px) = 70 이어야 한다: " + c.w);
        check(hasFill(paint(ctx), box(ctx, "imp2"), 0xFFFF0000), "시트 !important 가 인라인 style 을 이겨야 한다");
        List<String> warnings = ctx.computeOnClient(mc -> ((UiScreen) mc.gui.screen()).document().warnings());
        check(warnings.isEmpty(), "경고가 없어야 한다: " + warnings);
    }

    // ---- 준비/도우미 ----

    private void writeFixtures() {
        try {
            Files.createDirectories(base.resolve("ui"));
            Files.writeString(base.resolve("config.toml"), "[editor]\nenabled = true\n");
            Files.writeString(base.resolve("ui/gt_base.uicss"), "#imp { width: 30; height: 10; background: #AB00CD; }\n");
            Files.writeString(base.resolve("ui/gt_imp.uicss"), "@import \"gt_base.uicss\";\n");
            Files.writeString(base.resolve("ui/gt_beh.xml"), """
                    <screen>
                      <link rel="style" href="gt_imp.uicss"/>
                      <style>
                        @keyframes slide { from { translate-x: 0; } to { translate-x: 200; } }
                        #mover { width: 20; height: 10; background: #3366CC; animation: slide 2s linear infinite; }
                        #fade { width: 40; height: 14; background: #000000; transition: background 1s linear; }
                        #fade:hover { background: #FF0000; }
                        #field { width: 80; height: 16; background: #222222; }
                        #field:focus { background: #FFFF00; }
                        #za { position: absolute; x: 300; y: 20; width: 40; height: 40; z-index: 2; background: #AA0000; }
                        #zb { position: absolute; x: 310; y: 30; width: 40; height: 40; z-index: 1; background: #00AA00; }
                        #calc { --w: 30; width: calc(var(--w) * 2 + 10px); height: 6; background: #777777; }
                        #imp2 { width: 20; height: 6; background: #FF0000 !important; }
                      </style>
                      <column gap="4" anchor="top-left" x="10" y="10">
                        <panel id="mover"/>
                        <panel id="fade"/>
                        <textfield id="field" text=""/>
                        <panel id="imp"/>
                        <panel id="calc"/>
                        <panel id="imp2" style="background: #00FF00"/>
                      </column>
                      <panel id="za"/>
                      <panel id="zb"/>
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
            double s = mc.getWindow().getGuiScale();
            ((UiScreen) mc.gui.screen()).document().paint(l, (int) (mc.mouseHandler.xpos() / s), (int) (mc.mouseHandler.ypos() / s));
            return l.commands();
        });
    }

    private static int fillIn(List<DrawCommand> cmds, LayoutBox b) {
        return cmds.stream().filter(c -> c instanceof DrawCommand.FillRect r && r.x() >= b.x && r.x() < b.x + b.w
                && r.y() >= b.y && r.y() < b.y + b.h && r.w() >= b.w - 2).map(c -> ((DrawCommand.FillRect) c).argb()).findFirst().orElse(0);
    }

    private static int indexOfFill(List<DrawCommand> cmds, int argb) {
        for (int i = 0; i < cmds.size(); i++) if (cmds.get(i) instanceof DrawCommand.FillRect r && r.argb() == argb) return i;
        return -1;
    }

    private static boolean hasFill(List<DrawCommand> cmds, LayoutBox b, int argb) {
        return cmds.stream().anyMatch(c -> c instanceof DrawCommand.FillRect r && r.argb() == argb
                && r.x() >= b.x && r.x() < b.x + b.w && r.y() >= b.y && r.y() < b.y + b.h);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
