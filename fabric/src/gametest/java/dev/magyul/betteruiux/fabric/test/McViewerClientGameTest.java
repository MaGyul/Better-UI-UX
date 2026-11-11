package dev.magyul.betteruiux.fabric.test;

import dev.magyul.betteruiux.core.layout.LayoutBox;
import dev.magyul.betteruiux.mc26.UiOpener;
import dev.magyul.betteruiux.mc26.UiScreen;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * {@code <mc-viewer>}: 스크립트(mcv)가 만든 가상 월드가 실제로 화면에 그려지는지 스크린샷 픽셀로 확인한다.
 * 장면에 빨간 콘크리트 블록이 있고, 뷰어 영역 안에서 빨간 픽셀이 보여야 한다(자연 지형에는 거의 없는 색).
 */
@SuppressWarnings("UnstableApiUsage")
public class McViewerClientGameTest implements FabricClientGameTest {
    private Path base;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        base = FabricLoader.getInstance().getConfigDir().resolve("better-uiux");
        try {
            Files.createDirectories(base.resolve("ui"));
            Files.writeString(base.resolve("config.toml"), "[editor]\nenabled = true\n[scripting]\nenabled = true\n");
            Files.writeString(base.resolve("ui/gt_mcv.uiml"), """
                    <screen>
                      <script src="gt_mcv.uijs"/>
                      <mc-viewer id="view" anchor="center" width="240" height="180"/>
                    </screen>
                    """);
            Files.writeString(base.resolve("ui/gt_mcv2.uiml"), """
                    <screen>
                      <script src="gt_mcv2.uijs"/>
                      <mc-viewer id="view" anchor="center" width="240" height="180"/>
                    </screen>
                    """);
            Files.writeString(base.resolve("ui/gt_mcv2.uijs"), """
                    function onInit() {
                      var w = mcv.createWorld(5, 4, 5);
                      w.fill(0, 0, 0, 4, 0, 4, 'white_concrete');
                      w.fill(1, 1, 1, 3, 3, 3, 'red_concrete');
                      w.setBlock(0, 1, 0, 'grass_block');
                      var cow = w.spawnEntity(4.5, 1, 4.5, 'cow');
                      cow.methods.setRotation(135, 0);
                      w.spawnEntity(0.5, 1, 4.5, 'pig');
                      var sheep = w.spawnEntity(4.5, 1, 0.5, 'sheep');
                      sheep.fields.customName = 'Dolly';
                    }
                    """);
            Files.writeString(base.resolve("ui/gt_mcv3.uiml"), """
                    <screen>
                      <script src="gt_mcv3.uijs"/>
                      <label id="t" anchor="top-left" x="4" y="4" text="0"/>
                      <mc-viewer id="view" anchor="center" width="160" height="120"/>
                    </screen>
                    """);
            Files.writeString(base.resolve("ui/gt_mcv3.uijs"), """
                    function onInit() {
                      var w = mcv.createWorld(3, 2, 3);
                      w.fill(0, 0, 0, 2, 0, 2, 'stone');
                      w.setTickRate(20);
                    }
                    function onTick(n, worldId) { ui.setText('#t', String(n)); }
                    """);
            Files.writeString(base.resolve("ui/gt_mcv4.uiml"), """
                    <screen>
                      <script src="gt_mcv4.uijs"/>
                      <column anchor="top-left" x="4" y="4" gap="2">
                        <label id="p" text="p"/>
                        <label id="h" text="h"/>
                        <label id="c" text="c"/>
                      </column>
                      <mc-viewer id="view" anchor="center" width="240" height="180"/>
                    </screen>
                    """);
            Files.writeString(base.resolve("ui/gt_mcv4.uijs"), """
                    var w;
                    function onInit() {
                      w = mcv.createWorld(5, 3, 5);
                      w.fill(0, 0, 0, 4, 0, 4, 'white_concrete');
                      w.setBlock(2, 1, 2, 'red_concrete');
                    }
                    function onTick() {
                      var p = w.project(2.5, 1.5, 2.5);
                      if (p) ui.setText('#p', Math.round(p.x) + ',' + Math.round(p.y));
                    }
                    function onBlockHover(e) { ui.setText('#h', e.block ? 'hover ' + e.x + ',' + e.y + ',' + e.z : 'hover none'); }
                    function onBlockClick(e) { ui.setText('#c', e.block ? 'click ' + e.x + ',' + e.y + ',' + e.z + ',' + e.face : 'click none'); }
                    """);
            Files.writeString(base.resolve("ui/gt_mcv5.uiml"), """
                    <screen>
                      <script src="gt_mcv5.uijs"/>
                      <mc-viewer id="view" anchor="center" width="260" height="200"/>
                    </screen>
                    """);
            Files.writeString(base.resolve("ui/gt_mcv5.uijs"), """
                    function onInit() {
                      var w = mcv.createWorld(6, 3, 6);
                      w.fill(0, 0, 0, 5, 0, 5, 'white_concrete');
                      w.setBlock(1, 1, 1, 'chest[facing=south]');
                      w.setBlock(3, 1, 1, 'ender_chest[facing=south]');
                      w.setBlock(1, 1, 3, 'red_bed[facing=south,part=foot]');
                      w.setBlock(1, 1, 4, 'red_bed[facing=south,part=head]');
                      w.setBlock(4, 1, 4, 'oak_sign[rotation=8]', '{front_text:{messages:["Hello","viewer","",""]}}');
                      w.setBlock(4, 1, 2, 'shulker_box');
                    }
                    """);
            Files.writeString(base.resolve("ui/gt_mcv6.uiml"), """
                    <screen>
                      <script src="gt_mcv6.uijs"/>
                      <mc-viewer id="view" anchor="center" width="260" height="200"/>
                    </screen>
                    """);
            Files.writeString(base.resolve("ui/gt_mcv6.uijs"), """
                    function onInit() {
                      var w = mcv.createWorld(6, 3, 6);
                      w.fill(0, 0, 0, 5, 0, 5, 'white_concrete');
                    }
                    """);
            Files.writeString(base.resolve("ui/gt_mcv.uijs"), """
                    function onInit() {
                      var w = mcv.createWorld(5, 4, 5);
                      w.fill(0, 0, 0, 4, 0, 4, 'white_concrete');
                      w.fill(1, 1, 1, 3, 3, 3, 'red_concrete');
                    }
                    """);
        } catch (IOException e) {
            throw new AssertionError("테스트 파일을 만들지 못했다: " + e, e);
        }
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(2);
                mc.resizeGui();
            });
            ctx.waitTicks(3);
            ctx.runOnClient(mc -> UiOpener.open("gt_mcv", base, false));
            ctx.waitFor(mc -> mc.gui.screen() instanceof UiScreen s && s.uiId().equals("gt_mcv"));
            ctx.waitTicks(60);                                              // 스크립트 런타임 준비와 월드 생성은 비동기
            Path shot = ctx.takeScreenshot("mc-viewer");
            BufferedImage img = read(shot);
            LayoutBox view = ctx.computeOnClient(mc -> find(((UiScreen) mc.gui.screen()).document().layout(), "view"));
            check(view != null, "#view 요소가 없다");
            int s = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
            double sx = img.getWidth() / (double) ctx.computeOnClient(mc -> mc.getWindow().getWidth());
            int red = 0, bright = 0, total = 0;
            for (int gy = view.y; gy < view.y + view.h; gy++) {
                for (int gx = view.x; gx < view.x + view.w; gx++) {
                    int x = (int) Math.min(img.getWidth() - 1, Math.round(gx * s * sx)), y = (int) Math.min(img.getHeight() - 1, Math.round(gy * s * sx));
                    int p = img.getRGB(x, y), r = p >> 16 & 0xFF, g = p >> 8 & 0xFF, b = p & 0xFF;
                    total++;
                    if (r > 110 && r > g * 2.2 && r > b * 2.2) red++;
                    if (r > 200 && g > 200 && b > 200) bright++;
                }
            }
            System.out.println("[mc-viewer test] view=" + view.w + "x" + view.h + " red=" + red + " bright=" + bright + " of " + total);
            check(red > 100, "빨간 콘크리트 블록이 뷰어 안에 그려져야 한다(빨간 픽셀 " + red + "/" + total + ")");
            check(bright > 100, "하얀 콘크리트 바닥이 뷰어 안에 그려져야 한다(밝은 픽셀 " + bright + "/" + total + ")");

            // 같은 장면에 소 한 마리와 잔디 블록을 더하면 뷰어 안의 픽셀이 눈에 띄게 달라져야 한다(엔티티가 실제로 그려진다).
            ctx.runOnClient(mc -> UiOpener.open("gt_mcv2", base, false));
            ctx.waitFor(mc -> mc.gui.screen() instanceof UiScreen scr && scr.uiId().equals("gt_mcv2"));
            ctx.waitTicks(60);
            BufferedImage img2 = read(ctx.takeScreenshot("mc-viewer-entity"));
            int diff = 0;
            for (int gy = view.y; gy < view.y + view.h; gy++) {
                for (int gx = view.x; gx < view.x + view.w; gx++) {
                    int x = (int) Math.min(img.getWidth() - 1, Math.round(gx * s * sx)), y = (int) Math.min(img.getHeight() - 1, Math.round(gy * s * sx));
                    int a = img.getRGB(x, y), b = img2.getRGB(x, y);
                    int d = Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) + Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)) + Math.abs((a & 0xFF) - (b & 0xFF));
                    if (d > 60) diff++;
                }
            }
            System.out.println("[mc-viewer test] entity diff=" + diff);
            check(diff > 400, "소와 잔디가 더해진 장면은 달라 보여야 한다(달라진 픽셀 " + diff + ")");

            // 틱 시뮬레이션: 위젯이 그려지는 동안 월드 틱이 흐르고, 스크립트의 onTick 이 불려 문서가 바뀐다(위젯 → 스크립트 스레드 → 렌더 스레드).
            ctx.runOnClient(mc -> UiOpener.open("gt_mcv3", base, false));
            ctx.waitFor(mc -> mc.gui.screen() instanceof UiScreen scr && scr.uiId().equals("gt_mcv3"));
            ctx.waitTicks(80);
            String shown = ctx.computeOnClient(mc -> ((UiScreen) mc.gui.screen()).document().query("#t").text());
            System.out.println("[mc-viewer test] onTick label=" + shown);
            check(Integer.parseInt(shown.trim()) > 20, "onTick 이 계속 불려야 한다(레이블 " + shown + ")");

            // 상호작용: 스크립트가 project 로 알려 준 화면 좌표(빨간 블록 중심)에 마우스를 올리고 클릭하면 그 블록이 맞아야 한다.
            // 블록 엔티티(상자·침대·표지판 ...)는 블록 모델이 비어 있어 전용 렌더러가 그린다: 같은 바닥에 그것들을 놓으면 화면이 눌러 달라진다.
            BufferedImage plain = openAndShoot(ctx, "gt_mcv6", "mc-viewer-be-plain");
            BufferedImage rich = openAndShoot(ctx, "gt_mcv5", "mc-viewer-block-entities");
            LayoutBox bv = ctx.computeOnClient(mc -> find(((UiScreen) mc.gui.screen()).document().layout(), "view"));
            int beDiff = 0;
            for (int gy = bv.y; gy < bv.y + bv.h; gy++) {
                for (int gx = bv.x; gx < bv.x + bv.w; gx++) {
                    int x = (int) Math.min(plain.getWidth() - 1, Math.round(gx * s * sx)), y = (int) Math.min(plain.getHeight() - 1, Math.round(gy * s * sx));
                    int a = plain.getRGB(x, y), b = rich.getRGB(x, y);
                    int d = Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) + Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)) + Math.abs((a & 0xFF) - (b & 0xFF));
                    if (d > 60) beDiff++;
                }
            }
            System.out.println("[mc-viewer test] blockEntity diff=" + beDiff);
            check(beDiff > 1500, "블록 엔티티(상자·침대·표지판)가 그려져야 한다(달라진 픽셀 " + beDiff + ")");

            ctx.runOnClient(mc -> UiOpener.open("gt_mcv4", base, false));
            ctx.waitFor(mc -> mc.gui.screen() instanceof UiScreen scr && scr.uiId().equals("gt_mcv4"));
            ctx.waitTicks(40);
            String proj = text(ctx, "#p");
            System.out.println("[mc-viewer test] project=" + proj);
            String[] xy = proj.split(",");
            check(xy.length == 2, "project 결과가 있어야 한다: " + proj);
            int px = Integer.parseInt(xy[0].trim()), py = Integer.parseInt(xy[1].trim());
            double guiScale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
            var in = ctx.getInput();
            in.setCursorPos(px * guiScale, py * guiScale);
            ctx.waitTicks(5);
            String hover = text(ctx, "#h");
            System.out.println("[mc-viewer test] " + hover);
            check(hover.startsWith("hover 2,1,2"), "마우스 아래 블록이 빨간 블록이어야 한다: " + hover);
            in.pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
            ctx.waitTicks(10);
            String click = text(ctx, "#c");
            System.out.println("[mc-viewer test] " + click);
            check(click.startsWith("click 2,1,2,"), "클릭한 블록이 빨간 블록이어야 한다: " + click);
            in.setCursorPos(px * guiScale, (py - 90) * guiScale);                 // 월드 바깥(위쪽)
            ctx.waitTicks(5);
            check(text(ctx, "#h").equals("hover none"), "빈 곳에서는 호버가 풀려야 한다: " + text(ctx, "#h"));

            // 기본 제공 데모(mc-viewer.uiml/.uijs)가 경고 없이 열리고 그려진다.
            ctx.runOnClient(mc -> UiOpener.open("mc-viewer", base, false));
            ctx.waitFor(mc -> mc.gui.screen() instanceof UiScreen scr && scr.uiId().equals("mc-viewer"));
            ctx.waitTicks(60);
            BufferedImage demo = read(ctx.takeScreenshot("mc-viewer-demo"));
            java.util.List<String> warnings = ctx.computeOnClient(mc -> ((UiScreen) mc.gui.screen()).document().warnings());
            check(warnings.isEmpty(), "데모 문서에 경고가 없어야 한다: " + warnings);
            LayoutBox dv = ctx.computeOnClient(mc -> find(((UiScreen) mc.gui.screen()).document().layout(), "view"));
            int demoRed = 0;
            for (int gy = dv.y; gy < dv.y + dv.h; gy++) {
                for (int gx = dv.x; gx < dv.x + dv.w; gx++) {
                    int x = (int) Math.min(demo.getWidth() - 1, Math.round(gx * s * sx)), y = (int) Math.min(demo.getHeight() - 1, Math.round(gy * s * sx));
                    int p = demo.getRGB(x, y), r = p >> 16 & 0xFF, g = p >> 8 & 0xFF, b = p & 0xFF;
                    if (r > 110 && r > g * 2.2 && r > b * 2.2) demoRed++;
                }
            }
            System.out.println("[mc-viewer test] demo red=" + demoRed);
            check(demoRed > 50, "데모의 빨간 블록이 그려져야 한다(" + demoRed + ")");

        }
    }

    private BufferedImage openAndShoot(ClientGameTestContext ctx, String id, String shotName) {
        ctx.runOnClient(mc -> UiOpener.open(id, base, false));
        ctx.waitFor(mc -> mc.gui.screen() instanceof UiScreen scr && scr.uiId().equals(id));
        ctx.waitTicks(40);
        return read(ctx.takeScreenshot(shotName));
    }

    private static String text(ClientGameTestContext ctx, String selector) {
        return ctx.computeOnClient(mc -> ((UiScreen) mc.gui.screen()).document().query(selector).text());
    }

    private static LayoutBox find(LayoutBox root, String id) {
        if (id.equals(root.id())) return root;
        for (LayoutBox c : root.children) {
            LayoutBox f = find(c, id);
            if (f != null) return f;
        }
        return null;
    }

    private static BufferedImage read(Path p) {
        try {
            return ImageIO.read(p.toFile());
        } catch (IOException e) {
            throw new AssertionError("스크린샷을 읽지 못했다: " + p + " " + e, e);
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
