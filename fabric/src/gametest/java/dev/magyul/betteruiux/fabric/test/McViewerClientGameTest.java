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
        }
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
