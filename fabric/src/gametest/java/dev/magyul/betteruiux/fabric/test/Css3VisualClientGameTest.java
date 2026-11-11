package dev.magyul.betteruiux.fabric.test;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.edit.EditorController;
import dev.magyul.betteruiux.core.layout.LayoutBox;
import dev.magyul.betteruiux.mc26.UiOpener;
import dev.magyul.betteruiux.mc26.UiScreen;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 스크린샷의 실제 픽셀로 렌더링을 확인한다(GUI 배율 1·2; 창 854x480 에서는 최대 2): 둥근 모서리의 모서리 픽셀이 비어 있고 가운데는 채워지며,
 * 그라디언트의 위/아래 색이 다르고, 배율이 달라도 같은 모양으로 그려진다. 그리고 View 메뉴의 상태 미리보기(hover/active/focus)를 실제로 눌러 본다.
 */
@SuppressWarnings("UnstableApiUsage")
public class Css3VisualClientGameTest implements FabricClientGameTest {
    private Path base;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        base = FabricLoader.getInstance().getConfigDir().resolve("better-uiux");
        writeFixtures();
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            for (int scale : new int[] {1, 2}) pixels(ctx, scale);
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(1);
                mc.resizeGui();
            });
            ctx.waitTicks(3);
            statePreviewMenu(ctx);
        }
    }

    /** 화면 픽셀로: 단색 둥근 상자(모서리는 비고 가운데는 참)와 그라디언트(위는 빨강 쪽, 아래는 파랑 쪽). */
    private void pixels(ClientGameTestContext ctx, int scale) {
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(scale);
            mc.resizeGui();
        });
        ctx.waitTicks(3);
        open(ctx, "gt_vis", false);
        Path shot = ctx.takeScreenshot("vis-pixels-scale" + scale);
        BufferedImage img = read(shot);
        LayoutBox solid = box(ctx, "solid"), grad = box(ctx, "grad");
        int s = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        check(s == scale, "GUI 배율이 " + scale + " 이어야 한다: " + s);
        double sx = img.getWidth() / (double) ctx.computeOnClient(mc -> mc.getWindow().getWidth());    // 스크린샷 크기와 창 크기가 다를 수 있다
        int[] c = px(img, sx, solid.x + solid.w / 2, solid.y + solid.h / 2, s);
        check(isNear(c, 0x00, 0xDD, 0x00, 30), "scale " + scale + ": 단색 상자 가운데가 초록이어야 한다: " + hex(c));
        int[] corner = px(img, sx, solid.x, solid.y, s);                     // 맨 모서리 한 점: 반지름 10 이면 비어 있어야 한다
        check(!isNear(corner, 0x00, 0xDD, 0x00, 60), "scale " + scale + ": 둥근 모서리 끝 픽셀은 초록이 아니어야 한다: " + hex(corner));
        int[] edge = px(img, sx, solid.x + solid.w / 2, solid.y, s);        // 위쪽 변 가운데는 차 있다
        check(isNear(edge, 0x00, 0xDD, 0x00, 40), "scale " + scale + ": 위쪽 변은 채워져야 한다: " + hex(edge));

        int[] top = px(img, sx, grad.x + grad.w / 2, grad.y + 4, s), bottom = px(img, sx, grad.x + grad.w / 2, grad.y + grad.h - 5, s);
        check(top[0] > top[2] + 60, "scale " + scale + ": 그라디언트 위쪽은 빨강 쪽이어야 한다: " + hex(top));
        check(bottom[2] > bottom[0] + 60, "scale " + scale + ": 그라디언트 아래쪽은 파랑 쪽이어야 한다: " + hex(bottom));
        // 구조: 배율이 달라도 GUI 좌표의 배치는 같다(layout 이 배율에 무관)
        check(solid.w == 80 && solid.h == 30 && grad.h == 40, "scale " + scale + ": 크기가 GUI 좌표 그대로여야 한다: " + solid.w + "x" + solid.h);
    }

    /** View 메뉴 > 상태 미리보기: hover 를 고르면 선택한 요소가 :hover 규칙 색으로 보이고, 없음을 고르면 돌아온다. */
    private void statePreviewMenu(ClientGameTestContext ctx) {
        open(ctx, "gt_vis_editor", true);
        selectById(ctx, "card");
        ctx.waitTicks(2);
        LayoutBox card = box(ctx, "card");
        check(hasFill(paint(ctx), card, 0xFF00AA00), "처음에는 기본(초록)이어야 한다");

        click(ctx, find(ctx, "View"));
        ctx.waitTicks(2);
        ctx.takeScreenshot("vis-view-menu");
        int[] hover = findContaining(ctx, "hover");
        check(hover != null, "View 메뉴에 hover 상태 미리보기가 있어야 한다: " + editorTexts(ctx));
        click(ctx, hover);
        ctx.waitTicks(3);
        ctx.takeScreenshot("vis-preview-hover");
        check(hasFill(paint(ctx), card, 0xFFFF0000), "hover 미리보기에서는 :hover 규칙(빨강)이 보여야 한다");

        click(ctx, find(ctx, "View"));
        ctx.waitTicks(2);
        int[] active = findContaining(ctx, "active");
        check(active != null, "메뉴에 active 가 있어야 한다");
        click(ctx, active);
        ctx.waitTicks(3);
        check(hasFill(paint(ctx), card, 0xFF0000FF), "active 미리보기에서는 :active 규칙(파랑)이 보여야 한다");

        click(ctx, find(ctx, "View"));
        ctx.waitTicks(2);
        int[] focus = findContaining(ctx, "focus");
        check(focus != null, "메뉴에 focus 가 있어야 한다");
        click(ctx, focus);
        ctx.waitTicks(3);
        check(hasFill(paint(ctx), card, 0xFFFFFF00), "focus 미리보기에서는 :focus 규칙(노랑)이 보여야 한다");

        click(ctx, find(ctx, "View"));
        ctx.waitTicks(2);
        int[] none = findContaining(ctx, "없음");
        check(none != null, "메뉴에 '없음' 이 있어야 한다");
        click(ctx, none);
        ctx.waitTicks(3);
        check(hasFill(paint(ctx), card, 0xFF00AA00), "미리보기를 끄면 기본(초록)으로 돌아와야 한다");
    }

    // ---- 준비/도우미 ----

    private void writeFixtures() {
        try {
            Files.createDirectories(base.resolve("ui"));
            Files.writeString(base.resolve("config.toml"), "[editor]\nenabled = true\n");
            Files.writeString(base.resolve("ui/gt_vis.xml"), """
                    <screen>
                      <style>
                        #solid { width: 80; height: 30; background: #00DD00; border-radius: 10; }
                        #grad { width: 80; height: 40; background: linear-gradient(#FF0000, #0000FF); }
                      </style>
                      <column gap="8" anchor="top-left" x="20" y="20">
                        <panel id="solid"/>
                        <panel id="grad"/>
                      </column>
                    </screen>
                    """);
            Files.writeString(base.resolve("ui/gt_vis_editor.xml"), """
                    <screen>
                      <style>
                        #card { width: 80; height: 30; background: #00AA00; }
                        #card:hover { background: #FF0000; }
                        #card:active { background: #0000FF; }
                        #card:focus { background: #FFFF00; }
                      </style>
                      <panel id="card" anchor="center"/>
                    </screen>
                    """);
        } catch (IOException e) {
            throw new AssertionError("테스트 파일을 만들지 못했다: " + e, e);
        }
    }

    private static BufferedImage read(Path p) {
        try {
            return ImageIO.read(p.toFile());
        } catch (IOException e) {
            throw new AssertionError("스크린샷을 읽지 못했다: " + p + " " + e, e);
        }
    }

    /** GUI 좌표 → 스크린샷 픽셀(배율 s, 스크린샷/창 비율 sx)의 [r,g,b]. 상자 안쪽으로 반 칸 들여서 읽는다. */
    private static int[] px(BufferedImage img, double sx, int gx, int gy, int s) {
        int x = (int) Math.min(img.getWidth() - 1, Math.round((gx * s + s / 2.0) * sx)), y = (int) Math.min(img.getHeight() - 1, Math.round((gy * s + s / 2.0) * sx));
        int p = img.getRGB(x, y);
        return new int[] {p >> 16 & 0xFF, p >> 8 & 0xFF, p & 0xFF};
    }

    private static boolean isNear(int[] c, int r, int g, int b, int tol) {
        return Math.abs(c[0] - r) <= tol && Math.abs(c[1] - g) <= tol && Math.abs(c[2] - b) <= tol;
    }

    private static String hex(int[] c) {
        return String.format("#%02X%02X%02X", c[0], c[1], c[2]);
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
        LayoutBox b = ctx.computeOnClient(mc -> findBox(((UiScreen) mc.gui.screen()).document().layout(), id));
        check(b != null, "요소 #" + id + " 없음");
        return b;
    }

    private static LayoutBox findBox(LayoutBox root, String id) {
        if (id.equals(root.id())) return root;
        for (LayoutBox c : root.children) {
            LayoutBox f = findBox(c, id);
            if (f != null) return f;
        }
        return null;
    }

    private List<DrawCommand> paint(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> {
            DrawList l = new DrawList();
            double s = mc.getWindow().getGuiScale();
            ((UiScreen) mc.gui.screen()).document().paint(l, (int) (mc.mouseHandler.xpos() / s), (int) (mc.mouseHandler.ypos() / s));
            return l.commands();
        });
    }

    private List<String> editorTexts(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> {
            DrawList l = new DrawList();
            ((UiScreen) mc.gui.screen()).editor().paint(l);
            return l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText).map(c -> ((DrawCommand.DrawText) c).text()).toList();
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

    private int[] findContaining(ClientGameTestContext ctx, String text) {
        return ctx.computeOnClient(mc -> {
            DrawList l = new DrawList();
            ((UiScreen) mc.gui.screen()).editor().paint(l);
            return l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText d && d.text().contains(text))
                    .map(c -> (DrawCommand.DrawText) c).reduce((a, b) -> b).map(d -> new int[] {d.x(), d.y()}).orElse(null);
        });
    }

    private void click(ClientGameTestContext ctx, int[] at) {
        check(at != null, "누를 대상을 찾지 못했다");
        double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
        var in = ctx.getInput();
        in.setCursorPos((at[0] + 2) * scale, (at[1] + 2) * scale);
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
