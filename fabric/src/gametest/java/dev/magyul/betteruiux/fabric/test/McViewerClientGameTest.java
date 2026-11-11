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
                      cow.setRotation(135, 0);
                      cow.customName = 'Bessie';
                      w.spawnEntity(0.5, 1, 4.5, 'pig');
                      var sheep = w.spawnEntity(4.5, 1, 0.5, 'sheep');
                      sheep.customName = 'Dolly';
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
            Files.writeString(base.resolve("ui/gt_mcv7.uiml"), """
                    <screen>
                      <script src="gt_mcv7.uijs"/>
                      <column anchor="top-left" x="4" y="4" gap="2">
                        <label id="p" text="p"/>
                        <label id="h" text="h"/>
                        <label id="c" text="c"/>
                        <label id="n" text="n"/>
                        <label id="s" text="s"/>
                      </column>
                      <mc-viewer id="view" anchor="center" width="240" height="180"/>
                    </screen>
                    """);
            Files.writeString(base.resolve("ui/gt_mcv7.uijs"), """
                    var w, cow, calf;
                    function onInit() {
                      w = mcv.createWorld(5, 3, 5);
                      w.fill(0, 0, 0, 4, 0, 4, 'white_concrete');
                      cow = w.spawnEntity(2.5, 1, 2.5, 'cow');
                      calf = w.spawnEntity(0.5, 1, 0.5, 'cow', '{Age:-24000}');          // 소환하면서 NBT
                    }
                    function onTick() {
                      var p = w.project(2.5, 1.5, 2.5);
                      if (p) ui.setText('#p', Math.round(p.x) + ',' + Math.round(p.y));
                      var c = calf.getNbt();
                      if (c) ui.setText('#s', c.indexOf('Age:-1') >= 0 ? 'calf' : 'adult');
                      var n = cow.getNbt();
                      if (n) ui.setText('#n', n.indexOf('Age:-1') >= 0 && n.indexOf('Moo') >= 0 ? 'merged' : 'plain');
                    }
                    function onEntityHover(e) { ui.setText('#h', e.entity ? 'hover ' + e.type : 'hover none'); }
                    function onEntityClick(e) { ui.setText('#c', 'click ' + e.entity + ' ' + e.type); cow.mergeNbt('{Age:-24000,CustomName:\"Moo\",CustomNameVisible:1b}'); }
                    """);
            for (String[] v : new String[][] {{"gt_mcv8a", "", ""}, {"gt_mcv8b", "cow.customName = 'Bessie';", ""}, {"gt_mcv8c", "", " light=\"flat\""}}) {
                Files.writeString(base.resolve("ui/" + v[0] + ".uiml"), "<screen><script src=\"" + v[0] + ".uijs\"/>"
                        + "<mc-viewer id=\"view\" anchor=\"center\" width=\"240\" height=\"180\"" + v[2] + "/></screen>");
                Files.writeString(base.resolve("ui/" + v[0] + ".uijs"), "function onInit() {\n"
                        + "  var w = mcv.createWorld(5, 3, 5);\n  w.fill(0, 0, 0, 4, 0, 4, 'white_concrete');\n  w.setBlock(1, 1, 1, 'red_concrete');\n"
                        + "  var cow = w.spawnEntity(3.5, 1, 3.5, 'cow');\n  " + v[1] + "\n}\n");
            }
            Files.writeString(base.resolve("ui/gt_mcv9.uiml"), """
                    <screen>
                      <script src="gt_mcv9.uijs"/>
                      <column anchor="top-left" x="4" y="4" gap="2">
                        <label id="pa" text="pa"/>
                        <label id="pb" text="pb"/>
                        <label id="c" text="c"/>
                      </column>
                      <row anchor="center" gap="8">
                        <mc-viewer id="a" width="150" height="130"/>
                        <mc-viewer id="b" width="150" height="130"/>
                        <mc-viewer id="stray" width="60" height="60"/>
                      </row>
                    </screen>
                    """);
            Files.writeString(base.resolve("ui/gt_mcv9.uijs"), """
                    var w;
                    function onInit() {
                      w = mcv.createWorld(5, 3, 5);
                      w.fill(0, 0, 0, 4, 0, 4, 'white_concrete');
                      w.setBlock(2, 1, 2, 'red_concrete');
                      w.attach('#a');
                      w.attach('b');                                          // # 는 없어도 된다
                      var cam = w.getCamera('#b');                            // 뷰어를 지정하면 그 뷰어의 카메라
                      cam.setYaw(45);
                      cam.setPitch(80);                                       // b 는 거의 위에서 내려다본다
                    }
                    function onTick() {
                      var pa = w.project(2.5, 1.5, 2.5, '#a'), pb = w.project(2.5, 1.5, 2.5, '#b');
                      if (pa) ui.setText('#pa', Math.round(pa.x) + ',' + Math.round(pa.y));
                      if (pb) ui.setText('#pb', Math.round(pb.x) + ',' + Math.round(pb.y));
                    }
                    function onBlockClick(e) { ui.setText('#c', e.block ? 'click ' + e.viewer + ' ' + e.x + ',' + e.y + ',' + e.z + ',' + e.face : 'click ' + e.viewer + ' none'); }
                    """);
            for (String[] v : new String[][] {{"gt_mcv10a", ""}, {"gt_mcv10b", "w.setOpen(1, 1, 1, true);\n  w.setOpen(3, 1, 1, true);"}}) {
                Files.writeString(base.resolve("ui/" + v[0] + ".uiml"), "<screen><script src=\"" + v[0] + ".uijs\"/>"
                        + "<mc-viewer id=\"view\" anchor=\"center\" width=\"240\" height=\"180\"/></screen>");
                Files.writeString(base.resolve("ui/" + v[0] + ".uijs"), "function onInit() {\n"
                        + "  var w = mcv.createWorld(5, 3, 3);\n  w.fill(0, 0, 0, 4, 0, 2, 'white_concrete');\n"
                        + "  w.setBlock(1, 1, 1, 'chest[facing=south]');\n  w.setBlock(3, 1, 1, 'ender_chest[facing=south]');\n  " + v[1] + "\n}\n");
            }
            for (String[] v : new String[][] {{"gt_mcv11a", ""}, {"gt_mcv11b", "w.spawnEntity(1.5, 1, 1.5, 'pig');\n  w.spawnEntity(3.5, 1, 2.2, 'cow');\n  w.setBlock(3, 1, 0, 'chest[facing=south]');"}}) {
                Files.writeString(base.resolve("ui/" + v[0] + ".uiml"), "<screen><script src=\"" + v[0] + ".uijs\"/>"
                        + "<mc-viewer id=\"view\" anchor=\"center\" width=\"240\" height=\"180\"/></screen>");
                Files.writeString(base.resolve("ui/" + v[0] + ".uijs"), "function onInit() {\n"
                        + "  var w = mcv.createWorld(5, 3, 3);\n  w.fill(0, 0, 0, 4, 0, 2, 'white_concrete');\n  " + v[1] + "\n}\n");
            }
            for (String[] v : new String[][] {
                    {"gt_mcv12a", "w.setBlock(2, 1, 1, 'shulker_box');"},
                    {"gt_mcv12b", "w.setBlock(2, 1, 1, 'shulker_box');\n  w.setOpen(2, 1, 1, true);"},
                    {"gt_mcv13a", "w.setBlock(2, 1, 1, 'bell[attachment=floor,facing=north]');"},
                    {"gt_mcv13b", "w.setBlock(2, 1, 1, 'bell[attachment=floor,facing=north]');\n  w.ringBell(2, 1, 1, 'east');"}}) {
                Files.writeString(base.resolve("ui/" + v[0] + ".uiml"), "<screen><script src=\"" + v[0] + ".uijs\"/>"
                        + "<mc-viewer id=\"view\" anchor=\"center\" width=\"240\" height=\"180\"/></screen>");
                Files.writeString(base.resolve("ui/" + v[0] + ".uijs"), "function onInit() {\n"
                        + "  var w = mcv.createWorld(5, 3, 3);\n  w.fill(0, 0, 0, 4, 0, 2, 'white_concrete');\n  " + v[1] + "\n}\n");
            }
            Files.writeString(base.resolve("ui/gt_mcv14.uiml"), """
                    <screen>
                      <script src="gt_mcv14.uijs"/>
                      <column anchor="top-left" x="4" y="4" gap="2">
                        <label id="p" text="p"/>
                        <label id="q" text="q"/>
                        <label id="c" text="c"/>
                      </column>
                      <mc-viewer id="view" anchor="center" width="240" height="180"/>
                    </screen>
                    """);
            Files.writeString(base.resolve("ui/gt_mcv14.uijs"), """
                    var w;
                    function onInit() {
                      w = mcv.createWorld(5, 3, 5, '#view');
                      w.fill(0, 0, 0, 4, 0, 4, 'white_concrete');
                      w.setBlock(2, 1, 2, 'red_concrete');
                      w.spawnEntity(0.5, 1, 0.5, 'pig');
                      var cam = w.getCamera();
                      cam.setProjection('perspective');
                      cam.setYaw(200);
                      cam.setPitch(35);
                    }
                    function onTick() {
                      var p = w.project(2.5, 1.5, 2.5);
                      if (p) ui.setText('#p', Math.round(p.x) + ',' + Math.round(p.y));
                      var q = w.project(0.5, 1.45, 0.5);                         // 돼지(중심에서 벗어난 점): 좌우/상하 반전을 잡아낸다
                      if (q) ui.setText('#q', Math.round(q.x) + ',' + Math.round(q.y));
                    }
                    function onBlockClick(e) { ui.setText('#c', e.block ? 'click ' + e.x + ',' + e.y + ',' + e.z : 'click none'); }
                    """);
            for (String[] v : new String[][] {
                    {"gt_mcv15a", ""},
                    {"gt_mcv15b", "w.setBiome('desert');"},
                    {"gt_mcv15c", "w.fillBiome(0, 0, 0, 3, 1, 3, 'desert');\n  w.fillBiome(4, 0, 0, 7, 1, 3, 'jungle');"}}) {
                Files.writeString(base.resolve("ui/" + v[0] + ".uiml"), "<screen><script src=\"" + v[0] + ".uijs\"/>"
                        + "<mc-viewer id=\"view\" anchor=\"center\" width=\"240\" height=\"180\"/></screen>");
                Files.writeString(base.resolve("ui/" + v[0] + ".uijs"), "function onInit() {\n"
                        + "  var w = mcv.createWorld(8, 2, 4);\n  w.fill(0, 0, 0, 7, 0, 3, 'grass_block');\n  " + v[1] + "\n"
                        + "  var cam = w.getCamera();\n  cam.setYaw(0);\n  cam.setPitch(90);\n}\n");
            }
            for (String[] v : new String[][] {
                    {"gt_mcv16a", "0", "ortho"}, {"gt_mcv16b", "180", "ortho"}, {"gt_mcv16c", "0", "perspective"}, {"gt_mcv16d", "180", "perspective"}}) {
                Files.writeString(base.resolve("ui/" + v[0] + ".uiml"), "<screen><script src=\"" + v[0] + ".uijs\"/>"
                        + "<mc-viewer id=\"view\" anchor=\"center\" width=\"240\" height=\"180\"/></screen>");
                Files.writeString(base.resolve("ui/" + v[0] + ".uijs"), "function onInit() {\n"
                        + "  var w = mcv.createWorld(5, 3, 6);\n  w.fill(0, 0, 0, 4, 0, 5, 'white_concrete');\n"
                        + (v[1].equals("0") ? "  w.fill(0, 1, 5, 4, 2, 5, 'white_concrete');\n" : "  w.fill(0, 1, 0, 4, 2, 0, 'white_concrete');\n")
                        + "  w.setBlock(2, 1, 1, 'red_stained_glass');\n  w.setBlock(2, 1, 4, 'blue_stained_glass');\n"
                        + "  var cam = w.getCamera();\n  cam.setProjection('" + v[2] + "');\n  cam.setYaw(" + v[1] + ");\n  cam.setPitch(0);\n}\n");
            }
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

            // 엔티티 맞히기 + NBT 수정: 소를 호버/클릭하면 이벤트가 오고, 클릭 때 병합한 NBT 가 진짜 엔티티에 적용되어 getNbt 에 보인다.
            ctx.runOnClient(mc -> UiOpener.open("gt_mcv7", base, false));
            ctx.waitFor(mc -> mc.gui.screen() instanceof UiScreen scr && scr.uiId().equals("gt_mcv7"));
            ctx.waitTicks(40);
            String[] cxy = text(ctx, "#p").split(",");
            double gs = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
            var inp = ctx.getInput();
            inp.setCursorPos(Integer.parseInt(cxy[0].trim()) * gs, Integer.parseInt(cxy[1].trim()) * gs);
            ctx.waitTicks(5);
            System.out.println("[mc-viewer test] " + text(ctx, "#h") + " / nbt " + text(ctx, "#n"));
            check(text(ctx, "#h").equals("hover minecraft:cow"), "소 위에서 호버가 와야 한다: " + text(ctx, "#h"));
            check(text(ctx, "#n").equals("plain"), "처음엔 어른 소다: " + text(ctx, "#n"));
            check(text(ctx, "#s").equals("calf"), "소환할 때 준 NBT({Age:-24000})가 적용되어 새끼여야 한다: " + text(ctx, "#s"));
            BufferedImage cowBefore = read(ctx.takeScreenshot("mc-viewer-cow-adult"));
            inp.pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
            ctx.waitTicks(20);
            System.out.println("[mc-viewer test] " + text(ctx, "#c") + " / nbt " + text(ctx, "#n"));
            check(text(ctx, "#c").startsWith("click ") && text(ctx, "#c").endsWith(" minecraft:cow"), "소 클릭 이벤트: " + text(ctx, "#c"));
            check(text(ctx, "#n").equals("merged"), "병합한 NBT(Age, CustomName)가 엔티티에 적용되어 getNbt 에 보여야 한다: " + text(ctx, "#n"));
            inp.setCursorPos(2, 2);
            ctx.waitTicks(5);
            BufferedImage cowAfter = read(ctx.takeScreenshot("mc-viewer-cow-baby"));
            LayoutBox cv = ctx.computeOnClient(mc -> find(((UiScreen) mc.gui.screen()).document().layout(), "view"));
            int cowDiff = 0;
            for (int gy = cv.y; gy < cv.y + cv.h; gy++) {
                for (int gx = cv.x; gx < cv.x + cv.w; gx++) {
                    int x = (int) Math.min(cowBefore.getWidth() - 1, Math.round(gx * s * sx)), y = (int) Math.min(cowBefore.getHeight() - 1, Math.round(gy * s * sx));
                    int a = cowBefore.getRGB(x, y), b = cowAfter.getRGB(x, y);
                    int d = Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) + Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)) + Math.abs((a & 0xFF) - (b & 0xFF));
                    if (d > 60) cowDiff++;
                }
            }
            System.out.println("[mc-viewer test] cow nbt diff=" + cowDiff);
            check(cowDiff > 300, "NBT 로 새끼 소로 바꾸면 화면이 달라져야 한다(달라진 픽셀 " + cowDiff + ")");

            // 이름표: 같은 장면에 이름만 다르면 소 머리 위에 글자(이름표)가 생겨 화면이 조금 달라진다(바닐라의 거리 판정 때문에 한동안 안 보이던 부분).
            BufferedImage unnamed = openAndShoot(ctx, "gt_mcv8a", "mc-viewer-tag-none");
            BufferedImage named = openAndShoot(ctx, "gt_mcv8b", "mc-viewer-tag-named");
            LayoutBox tv = ctx.computeOnClient(mc -> find(((UiScreen) mc.gui.screen()).document().layout(), "view"));
            int tagDiff = diff(unnamed, named, tv, s, sx);
            System.out.println("[mc-viewer test] name tag diff=" + tagDiff);
            check(tagDiff > 60 && tagDiff < 6000, "이름표(작은 글자 상자)만큼만 달라져야 한다(달라진 픽셀 " + tagDiff + ")");
            // 조명 속성: light="flat" 은 기본(3d)과 면 밝기가 달라진다.
            BufferedImage flat = openAndShoot(ctx, "gt_mcv8c", "mc-viewer-light-flat");
            int lightDiff = diff(unnamed, flat, tv, s, sx);
            System.out.println("[mc-viewer test] light flat diff=" + lightDiff);
            check(lightDiff > 500, "light=\"flat\" 이면 조명이 달라져 화면이 달라져야 한다(달라진 픽셀 " + lightDiff + ")");

            // 뷰어 id 로 연결: 한 월드를 뷰어 a, b 가 서로 다른 카메라로 보고, 연결 안 된 뷰어(stray)는 경고가 난다.
            BufferedImage two = openAndShoot(ctx, "gt_mcv9", "mc-viewer-two-viewers");
            LayoutBox va = ctx.computeOnClient(mc -> find(((UiScreen) mc.gui.screen()).document().layout(), "a"));
            LayoutBox vb = ctx.computeOnClient(mc -> find(((UiScreen) mc.gui.screen()).document().layout(), "b"));
            int redA = redCount(two, va, s, sx), redB = redCount(two, vb, s, sx);
            System.out.println("[mc-viewer test] viewers red a=" + redA + " b=" + redB);
            check(redA > 50 && redB > 50, "두 뷰어 모두 같은 월드(빨간 블록)를 그려야 한다: a=" + redA + " b=" + redB);
            int viewDiff = 0;
            for (int gy = 0; gy < va.h; gy++) {
                for (int gx = 0; gx < va.w; gx++) {
                    int x1 = (int) Math.round((va.x + gx) * s * sx), y1 = (int) Math.round((va.y + gy) * s * sx);
                    int x2 = (int) Math.round((vb.x + gx) * s * sx), y2 = (int) Math.round((vb.y + gy) * s * sx);
                    int p = two.getRGB(Math.min(two.getWidth() - 1, x1), Math.min(two.getHeight() - 1, y1)), q = two.getRGB(Math.min(two.getWidth() - 1, x2), Math.min(two.getHeight() - 1, y2));
                    int d = Math.abs((p >> 16 & 0xFF) - (q >> 16 & 0xFF)) + Math.abs((p >> 8 & 0xFF) - (q >> 8 & 0xFF)) + Math.abs((p & 0xFF) - (q & 0xFF));
                    if (d > 60) viewDiff++;
                }
            }
            System.out.println("[mc-viewer test] viewer a vs b diff=" + viewDiff);
            check(viewDiff > 1500, "뷰어마다 카메라가 달라 같은 월드가 다르게 보여야 한다(달라진 픽셀 " + viewDiff + ")");
            java.util.List<String> viewerWarnings = ctx.computeOnClient(mc -> ((UiScreen) mc.gui.screen()).document().warnings());
            System.out.println("[mc-viewer test] warnings=" + viewerWarnings);
            check(viewerWarnings.stream().anyMatch(m -> m.contains("id=\"stray\"")), "연결 안 된 뷰어(stray)에 경고가 나야 한다: " + viewerWarnings);
            // 각 뷰어의 project 좌표를 클릭하면 이벤트에 그 뷰어 id 가 실려 온다.
            double scale9 = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
            var in9 = ctx.getInput();
            for (String id : new String[] {"a", "b"}) {
                String[] xy9 = text(ctx, id.equals("a") ? "#pa" : "#pb").split(",");
                in9.setCursorPos(Integer.parseInt(xy9[0].trim()) * scale9, Integer.parseInt(xy9[1].trim()) * scale9);
                ctx.waitTicks(5);
                in9.pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
                ctx.waitTicks(10);
                String clicked = text(ctx, "#c");
                System.out.println("[mc-viewer test] " + clicked);
                check(clicked.startsWith("click " + id + " 2,1,2,"), "뷰어 " + id + " 의 좌표를 누르면 그 뷰어 id 와 빨간 블록이 와야 한다: " + clicked);
                if (id.equals("b")) check(clicked.endsWith(",up"), "위에서 보는 뷰어 b 는 윗면을 맞힌다: " + clicked);
            }
            in9.setCursorPos(2, 2);

            // 블록 엔티티 동작: world.setOpen 으로 상자/엔더상자 뚜껑이 열린다(월드 틱마다 뚜껑 애니메이션이 돈다).
            BufferedImage closed = openAndShoot(ctx, "gt_mcv10a", "mc-viewer-chest-closed");
            BufferedImage opened = openAndShoot(ctx, "gt_mcv10b", "mc-viewer-chest-open");
            LayoutBox chestView = ctx.computeOnClient(mc -> find(((UiScreen) mc.gui.screen()).document().layout(), "view"));
            int lidDiff = diff(closed, opened, chestView, s, sx);
            System.out.println("[mc-viewer test] chest lid diff=" + lidDiff);
            check(lidDiff > 300, "setOpen 으로 뚜껑이 열리면 화면이 달라져야 한다(달라진 픽셀 " + lidDiff + ")");

            // 셜커 상자: 열면 뚜껑이 올라간다. 종: 울리면 흔들린다(흔들림은 50틱이라 울린 직후 짧게 기다렸다 찍는다).
            BufferedImage shulkerClosed = openAndShoot(ctx, "gt_mcv12a", "mc-viewer-shulker-closed", 40);
            BufferedImage shulkerOpen = openAndShoot(ctx, "gt_mcv12b", "mc-viewer-shulker-open", 40);
            LayoutBox shulkerView = ctx.computeOnClient(mc -> find(((UiScreen) mc.gui.screen()).document().layout(), "view"));
            int shulkerDiff = diff(shulkerClosed, shulkerOpen, shulkerView, s, sx);
            System.out.println("[mc-viewer test] shulker diff=" + shulkerDiff);
            check(shulkerDiff > 150, "setOpen 으로 셜커 상자가 열리면 화면이 달라져야 한다(달라진 픽셀 " + shulkerDiff + ")");
            BufferedImage bellIdle = openAndShoot(ctx, "gt_mcv13a", "mc-viewer-bell-idle", 14);
            BufferedImage bellRung = openAndShoot(ctx, "gt_mcv13b", "mc-viewer-bell-rung", 14);
            int bellDiff = diff(bellIdle, bellRung, shulkerView, s, sx);
            System.out.println("[mc-viewer test] bell diff=" + bellDiff);
            check(bellDiff > 20, "ringBell 로 종이 울리면 흔들려서 화면이 달라져야 한다(달라진 픽셀 " + bellDiff + ")");

            // 원근 투영: 스크립트의 project 가 알려 준 큐브 중심이 실제 화면에서 빨간 픽셀의 무게중심과 맞아야 하고(렌더러와 수식이 같다),
            // 그 점을 클릭하면 그 블록이 맞는다(원근 레이캐스트).
            BufferedImage persp = openAndShoot(ctx, "gt_mcv14", "mc-viewer-perspective");
            LayoutBox pv = ctx.computeOnClient(mc -> find(((UiScreen) mc.gui.screen()).document().layout(), "view"));
            String[] pxy = text(ctx, "#p").split(",");
            double projX = Double.parseDouble(pxy[0].trim()), projY = Double.parseDouble(pxy[1].trim());
            double sumX = 0, sumY = 0;
            int nRed = 0;
            for (int gy = pv.y; gy < pv.y + pv.h; gy++) {
                for (int gx = pv.x; gx < pv.x + pv.w; gx++) {
                    int x = (int) Math.min(persp.getWidth() - 1, Math.round(gx * s * sx)), y = (int) Math.min(persp.getHeight() - 1, Math.round(gy * s * sx));
                    int pp = persp.getRGB(x, y), r = pp >> 16 & 0xFF, g = pp >> 8 & 0xFF, b = pp & 0xFF;
                    if (r > 55 && r > g * 2.2 && r > b * 2.2) {
                        sumX += gx;
                        sumY += gy;
                        nRed++;
                    }
                }
            }
            check(nRed > 200, "원근 투영에서도 빨간 큐브가 그려져야 한다(빨간 픽셀 " + nRed + ")");
            double cenX = sumX / nRed, cenY = sumY / nRed;
            System.out.println("[mc-viewer test] perspective project=" + projX + "," + projY + " red centroid=" + Math.round(cenX) + "," + Math.round(cenY) + " n=" + nRed);
            check(Math.abs(cenX - projX) < 14 && Math.abs(cenY - projY) < 14, "project 좌표(" + projX + "," + projY + ")와 화면의 빨간 큐브 무게중심(" + Math.round(cenX) + "," + Math.round(cenY) + ")이 맞아야 한다");
            // 중심에서 벗어난 돼지: project 좌표와 분홍 픽셀 무게중심이 맞으면 좌우/상하가 뒤집히지 않았다는 뜻이다.
            String[] qxy = text(ctx, "#q").split(",");
            double qx = Double.parseDouble(qxy[0].trim()), qy = Double.parseDouble(qxy[1].trim()), pinkX = 0, pinkY = 0;
            int nPink = 0;
            for (int gy = pv.y; gy < pv.y + pv.h; gy++) {
                for (int gx = pv.x; gx < pv.x + pv.w; gx++) {
                    int x = (int) Math.min(persp.getWidth() - 1, Math.round(gx * s * sx)), y = (int) Math.min(persp.getHeight() - 1, Math.round(gy * s * sx));
                    int pp = persp.getRGB(x, y), r = pp >> 16 & 0xFF, g = pp >> 8 & 0xFF, b = pp & 0xFF;
                    if (r > 170 && r - g > 40 && r - b > 40 && g > 90 && b > 90 && Math.abs(g - b) < 40) {
                        pinkX += gx;
                        pinkY += gy;
                        nPink++;
                    }
                }
            }
            check(nPink > 30, "원근 투영에서 돼지(분홍색)가 그려져야 한다(" + nPink + ")");
            pinkX /= nPink;
            pinkY /= nPink;
            System.out.println("[mc-viewer test] perspective pig project=" + qx + "," + qy + " pink centroid=" + Math.round(pinkX) + "," + Math.round(pinkY) + " n=" + nPink);
            check(Math.abs(pinkX - qx) < 16 && Math.abs(pinkY - qy) < 16, "돼지의 project 좌표(" + qx + "," + qy + ")와 화면의 분홍 무게중심(" + Math.round(pinkX) + "," + Math.round(pinkY) + ")이 맞아야 한다(좌우/상하 반전 확인)");
            double gs14 = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
            ctx.getInput().setCursorPos(projX * gs14, projY * gs14);
            ctx.waitTicks(5);
            ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
            ctx.waitTicks(10);
            System.out.println("[mc-viewer test] perspective " + text(ctx, "#c"));
            check(text(ctx, "#c").equals("click 2,1,2"), "원근 투영에서 project 좌표를 클릭하면 그 블록이 맞아야 한다: " + text(ctx, "#c"));
            ctx.getInput().setCursorPos(2, 2);

            // 바이옴 색조: 같은 잔디 바닥이 바이옴에 따라 다른 색이 된다(기본=초록, 사막=누런 갈색, 정글=짙은 초록). 영역으로 반반 나눌 수도 있다.
            BufferedImage grassDefault = openAndShoot(ctx, "gt_mcv15a", "mc-viewer-biome-default");
            BufferedImage grassDesert = openAndShoot(ctx, "gt_mcv15b", "mc-viewer-biome-desert");
            BufferedImage grassSplit = openAndShoot(ctx, "gt_mcv15c", "mc-viewer-biome-split");
            LayoutBox gv = ctx.computeOnClient(mc -> find(((UiScreen) mc.gui.screen()).document().layout(), "view"));
            double rgDefault = grassRatio(grassDefault, gv, 0, 1, s, sx), rgDesert = grassRatio(grassDesert, gv, 0, 1, s, sx);
            double rgLeft = grassRatio(grassSplit, gv, 0, 0.5, s, sx), rgRight = grassRatio(grassSplit, gv, 0.5, 1, s, sx);
            System.out.println("[mc-viewer test] biome r/g default=" + rgDefault + " desert=" + rgDesert + " split left=" + rgLeft + " right=" + rgRight);
            check(rgDesert > rgDefault + 0.03, "사막의 잔디는 기본(초록)보다 누렇다(r/g 기본 " + rgDefault + ", 사막 " + rgDesert + ")");
            check(Math.abs(rgLeft - rgRight) > 0.08, "영역으로 나눈 바이옴(사막|정글)은 좌우 잔디 색이 달라야 한다(" + rgLeft + " vs " + rgRight + ")");

            // 반투명 정렬: 앞뒤로 겹친 색유리(뒤에는 흰 벽)는 "가까운 쪽 색이 우세"해야 한다. 빨강이 앞인 뷰(yaw 0)가 파랑이 앞인 뷰(yaw 180)보다
            // r − b 가 커야 한다(그리기 순서가 거리와 무관하면 두 뷰 중 하나가 거꾸로 섞여 이 차이가 사라지거나 뒤집힌다). 정사영·원근 모두.
            double[] rbScore = new double[4];
            String[] glassScenes = {"gt_mcv16a", "gt_mcv16b", "gt_mcv16c", "gt_mcv16d"};
            for (int gi = 0; gi < 4; gi++) {
                BufferedImage glass = openAndShoot(ctx, glassScenes[gi], "mc-viewer-glass-" + glassScenes[gi]);
                LayoutBox gl = ctx.computeOnClient(mc -> find(((UiScreen) mc.gui.screen()).document().layout(), "view"));
                double r = 0, b = 0;
                int n = 0;
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dx = -3; dx <= 3; dx++) {
                        int x = (int) Math.round((gl.x + gl.w / 2 + dx) * s * sx), y = (int) Math.round((gl.y + gl.h / 2 + dy) * s * sx);
                        int p = glass.getRGB(x, y);
                        r += p >> 16 & 0xFF;
                        b += p & 0xFF;
                        n++;
                    }
                }
                rbScore[gi] = (r - b) / n;
            }
            System.out.println("[mc-viewer test] glass r-b ortho red-near=" + rbScore[0] + " blue-near=" + rbScore[1] + " | perspective red-near=" + rbScore[2] + " blue-near=" + rbScore[3]);
            check(rbScore[0] > rbScore[1] + 6, "정사영: 빨간 유리가 앞인 뷰가 파란 유리가 앞인 뷰보다 더 붉어야 한다(" + rbScore[0] + " vs " + rbScore[1] + ")");
            check(rbScore[2] > rbScore[3] + 6, "원근: 빨간 유리가 앞인 뷰가 파란 유리가 앞인 뷰보다 더 붉어야 한다(" + rbScore[2] + " vs " + rbScore[3] + ")");

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

        // 월드에 접속하지 않은 상태(타이틀 화면): 가상 레벨로 엔티티(돼지)와 블록 엔티티(상자)가 그려진다.
        ctx.waitFor(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen);
        ctx.runOnClient(mc -> check(mc.level == null, "타이틀 화면에서는 진짜 월드가 없어야 한다"));
        ctx.runOnClient(mc -> UiOpener.open("gt_mcv11a", base, false));
        ctx.waitFor(mc -> mc.gui.screen() instanceof UiScreen scr && scr.uiId().equals("gt_mcv11a"));
        ctx.waitTicks(60);
        BufferedImage titlePlain = read(ctx.takeScreenshot("mc-viewer-title-plain"));
        ctx.runOnClient(mc -> UiOpener.open("gt_mcv11b", base, false));
        ctx.waitFor(mc -> mc.gui.screen() instanceof UiScreen scr && scr.uiId().equals("gt_mcv11b"));
        ctx.waitTicks(240);                                                 // 가상 레벨의 레지스트리를 백그라운드로 불러오는 시간
        BufferedImage titleRich = read(ctx.takeScreenshot("mc-viewer-title-entities"));
        LayoutBox tview = ctx.computeOnClient(mc -> find(((UiScreen) mc.gui.screen()).document().layout(), "view"));
        int ts = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        double tsx = titleRich.getWidth() / (double) ctx.computeOnClient(mc -> mc.getWindow().getWidth());
        int pinkPlain = pinkCount(titlePlain, tview, ts, tsx), pinkRich = pinkCount(titleRich, tview, ts, tsx);
        System.out.println("[mc-viewer test] title screen pig pixels plain=" + pinkPlain + " rich=" + pinkRich);
        check(pinkRich > 60 && pinkRich > pinkPlain * 4 + 20, "타이틀 화면에서도 돼지(분홍색)가 그려져야 한다(없는 장면 " + pinkPlain + ", 있는 장면 " + pinkRich + ")");
        ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));      // 테스트는 타이틀 화면에서 끝나야 한다
        ctx.waitFor(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen);
    }

    /** 스크린샷의 뷰어 영역에서 돼지의 분홍색 픽셀 수(타이틀 화면 배경에는 거의 없는 색). */
    private static int pinkCount(BufferedImage img, LayoutBox v, int guiScale, double sx) {
        int n = 0;
        for (int gy = v.y; gy < v.y + v.h; gy++) {
            for (int gx = v.x; gx < v.x + v.w; gx++) {
                int x = (int) Math.min(img.getWidth() - 1, Math.round(gx * guiScale * sx)), y = (int) Math.min(img.getHeight() - 1, Math.round(gy * guiScale * sx));
                int p = img.getRGB(x, y), r = p >> 16 & 0xFF, g = p >> 8 & 0xFF, b = p & 0xFF;
                if (r > 170 && r - g > 40 && r - b > 40 && g > 90 && b > 90 && Math.abs(g - b) < 40) n++;
            }
        }
        return n;
    }

    /**
     * 뷰어 영역의 가로 구간([from, to] 비율)에서 "잔디색" 픽셀(초록기가 있는 색: 흰 바닥·파란 하늘은 제외)의 평균 r/g 비.
     * 위에서 내려다본(어두운) 장면에서 기본 잔디는 약 0.77, 사막은 약 0.82(누런), 정글은 약 0.68(짙은 초록)로 측정된다.
     */
    private static double grassRatio(BufferedImage img, LayoutBox v, double from, double to, int guiScale, double sx) {
        double sum = 0;
        int n = 0;
        for (int gy = v.y; gy < v.y + v.h; gy++) {
            for (int gx = (int) (v.x + v.w * from); gx < v.x + v.w * to; gx++) {
                int x = (int) Math.min(img.getWidth() - 1, Math.round(gx * guiScale * sx)), y = (int) Math.min(img.getHeight() - 1, Math.round(gy * guiScale * sx));
                int p = img.getRGB(x, y), r = p >> 16 & 0xFF, g = p >> 8 & 0xFF, b = p & 0xFF;
                if (g > b + 30 && g > 60) {
                    sum += (double) r / g;
                    n++;
                }
            }
        }
        if (n < 100) throw new AssertionError("잔디색 픽셀이 너무 적다(" + n + ")");
        return sum / n;
    }

    /** 스크린샷의 뷰어 영역에서 빨간 픽셀 수(빨간 콘크리트). */
    private static int redCount(BufferedImage img, LayoutBox v, int guiScale, double sx) {
        int n = 0;
        for (int gy = v.y; gy < v.y + v.h; gy++) {
            for (int gx = v.x; gx < v.x + v.w; gx++) {
                int x = (int) Math.min(img.getWidth() - 1, Math.round(gx * guiScale * sx)), y = (int) Math.min(img.getHeight() - 1, Math.round(gy * guiScale * sx));
                int p = img.getRGB(x, y), r = p >> 16 & 0xFF, g = p >> 8 & 0xFF, b = p & 0xFF;
                if (r > 55 && r > g * 2.2 && r > b * 2.2) n++;
            }
        }
        return n;
    }

    /** 두 스크린샷의 뷰어 영역에서 눈에 띄게 다른 픽셀 수. */
    private static int diff(BufferedImage a, BufferedImage b, LayoutBox v, int guiScale, double sx) {
        int n = 0;
        for (int gy = v.y; gy < v.y + v.h; gy++) {
            for (int gx = v.x; gx < v.x + v.w; gx++) {
                int x = (int) Math.min(a.getWidth() - 1, Math.round(gx * guiScale * sx)), y = (int) Math.min(a.getHeight() - 1, Math.round(gy * guiScale * sx));
                int p = a.getRGB(x, y), q = b.getRGB(x, y);
                int d = Math.abs((p >> 16 & 0xFF) - (q >> 16 & 0xFF)) + Math.abs((p >> 8 & 0xFF) - (q >> 8 & 0xFF)) + Math.abs((p & 0xFF) - (q & 0xFF));
                if (d > 60) n++;
            }
        }
        return n;
    }

    private BufferedImage openAndShoot(ClientGameTestContext ctx, String id, String shotName) {
        return openAndShoot(ctx, id, shotName, 40);
    }

    private BufferedImage openAndShoot(ClientGameTestContext ctx, String id, String shotName, int waitTicks) {
        ctx.runOnClient(mc -> UiOpener.open(id, base, false));
        ctx.waitFor(mc -> mc.gui.screen() instanceof UiScreen scr && scr.uiId().equals(id));
        ctx.waitTicks(waitTicks);
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
