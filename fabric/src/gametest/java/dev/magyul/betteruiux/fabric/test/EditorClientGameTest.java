package dev.magyul.betteruiux.fabric.test;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.edit.EditorController;
import dev.magyul.betteruiux.mc26.UiOpener;
import dev.magyul.betteruiux.mc26.UiScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 실제 게임 창/월드/입력(마우스·키보드)으로 인게임 에디터의 새 기능을 확인한다.
 * 실행: {@code ./gradlew :fabric:runClientGameTest} — 스크린샷은 {@code fabric/build/run/clientGameTest/screenshots}.
 */
@SuppressWarnings("UnstableApiUsage")
public class EditorClientGameTest implements FabricClientGameTest {
    // 26.x 는 SDL 키 번호(GLFW 와 다르다): 상수를 그대로 쓴다.
    private static final int KEY_ENTER = com.mojang.blaze3d.platform.InputConstants.KEY_RETURN,
            KEY_ESCAPE = com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE,
            KEY_Z = com.mojang.blaze3d.platform.InputConstants.KEY_Z,
            KEY_Y = com.mojang.blaze3d.platform.InputConstants.KEY_Y,
            KEY_S = com.mojang.blaze3d.platform.InputConstants.KEY_S;

    private Path base;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        base = FabricLoader.getInstance().getConfigDir().resolve("better-uiux");
        writeFixtures();
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            ctx.runOnClient(mc -> {                                     // 속성 패널이 길어서 GUI 배율을 1 로: 한 화면에 모든 행이 들어온다
                mc.options.guiScale().set(1);
                mc.resizeGui();
            });
            ctx.waitTicks(3);
            externalStylesheet(ctx);
            keyframes(ctx);
            fileMenuSwitchesScreens(ctx);
            sizePreview(ctx);
            blankEditorAndLazyNewFile(ctx);
            autosaveRecovery(ctx);
        }
    }

    // ---- 시나리오 ----

    /** 외부 .uicss 규칙을 패널에서 고치면 파일이 바뀌고, Ctrl+Z / Ctrl+Y 로 파일이 되돌아간다. */
    private void externalStylesheet(ClientGameTestContext ctx) {
        openEditor(ctx, "gt_ext");
        selectById(ctx, "box");
        ctx.waitTicks(2);
        ctx.takeScreenshot("1-external-style-panel");
        check(find(ctx, "- 스타일시트: gt_ext.uicss") != null, "외부 스타일시트 구역이 보여야 한다");

        int[] value = find(ctx, "8");                                   // padding 의 값 칸
        check(value != null, "padding 값(8) 칸을 찾지 못했다");
        click(ctx, value[0] + 1, value[1] + 1);
        ctx.takeScreenshot("1b-after-value-click");
        String dbg = ctx.computeOnClient(mc -> {
            var ed = ((UiScreen) mc.gui.screen()).editor();
            return "click=(" + (value[0] + 1) + "," + (value[1] + 1) + ") editing=" + ed.isEditingField() + " status='" + ed.status() + "' sel=" + ed.selectedIndex()
                    + " cursor=" + mc.mouseHandler.xpos() + "," + mc.mouseHandler.ypos() + " scale=" + mc.getWindow().getGuiScale() + " win=" + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight();
        });
        check(editor(ctx, ed -> ed.isEditingField()), "값 칸을 누르면 편집 상태가 된다: " + dbg);
        ctx.getInput().typeChars("16");
        ctx.getInput().pressKey(KEY_ENTER);
        ctx.waitTicks(2);
        ctx.takeScreenshot("2-external-style-edited");
        check(read("ui/gt_ext.uicss").contains(".card { padding: 16;"), "파일에 16 이 저장돼야 한다: " + read("ui/gt_ext.uicss"));
        check(!editor(ctx, EditorController::isDirty), "외부 파일 편집은 문서를 더럽히지 않는다");

        ctrl(ctx, KEY_Z);
        String st = ctx.computeOnClient(mc -> ((UiScreen) mc.gui.screen()).editor().status());
        check(read("ui/gt_ext.uicss").contains(".card { padding: 8;"), "Ctrl+Z 로 파일이 8 로 돌아와야 한다: " + read("ui/gt_ext.uicss") + " / status='" + st + "'");
        ctrl(ctx, KEY_Y);
        check(read("ui/gt_ext.uicss").contains(".card { padding: 16;"), "Ctrl+Y 로 다시 16 이어야 한다");
        ctrl(ctx, KEY_Z);                                                  // 다음 시나리오를 위해 원래대로
        ctx.takeScreenshot("3-external-style-undone");
    }

    /** 요소가 쓰는 @keyframes 가 패널에 나오고, 지점의 값을 고치면 문서에 반영되며 Ctrl+Z 로 되돌아간다. */
    private void keyframes(ClientGameTestContext ctx) {
        openEditor(ctx, "gt_kf");
        selectById(ctx, "box");
        ctx.waitTicks(2);
        ctx.takeScreenshot("3b-keyframes-panel");
        check(find(ctx, "- 키프레임: pulse") != null, "키프레임 구역이 보여야 한다");
        int[] value = find(ctx, "1");                                   // 마지막 지점(to) 의 opacity 값
        check(value != null, "to 지점의 opacity 값을 찾지 못했다");
        click(ctx, value[0] + 1, value[1] + 1);
        check(editor(ctx, ed -> ed.isEditingField()), "값 칸을 누르면 편집 상태가 된다");
        ctx.getInput().typeChars("0.5");
        ctx.getInput().pressKey(KEY_ENTER);
        ctx.waitTicks(2);
        ctx.takeScreenshot("3c-keyframes-edited");
        check(editor(ctx, ed -> ed.text().contains("to { opacity: 0.5; }")), "지점 값이 문서에 반영돼야 한다");
        ctrl(ctx, KEY_Z);
        check(editor(ctx, ed -> ed.text().contains("to { opacity: 1; }")), "Ctrl+Z 로 되돌아가야 한다");
    }

    /** File 메뉴에서 다른 화면을 열 수 있다. */
    private void fileMenuSwitchesScreens(ClientGameTestContext ctx) {
        openEditor(ctx, "gt_ext");
        int[] file = find(ctx, "File");
        check(file != null, "툴바의 File 버튼이 보여야 한다");
        click(ctx, file[0] + 2, file[1] + 2);
        ctx.waitTicks(2);
        ctx.takeScreenshot("4-file-menu");
        int[] item = find(ctx, "열기: gt_other");
        check(item != null, "File 메뉴에 '열기: gt_other' 가 있어야 한다");
        click(ctx, item[0] + 2, item[1] + 2);
        ctx.waitFor(mc -> mc.gui.screen() instanceof UiScreen s && s.uiId().equals("gt_other") && s.editor() != null);
        ctx.takeScreenshot("5-switched-to-other");
    }

    /** 보기 > 크기 미리보기: 480x270 으로 배치하고 크기 라벨이 그려진다. */
    private void sizePreview(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> ((UiScreen) mc.gui.screen()).editor().setPreviewSize(480, 270));
        ctx.waitTicks(2);
        ctx.takeScreenshot("6-size-preview-480x270");
        check(find(ctx, "480x270") != null, "미리보기 크기 라벨이 보여야 한다");
        ctx.runOnClient(mc -> ((UiScreen) mc.gui.screen()).editor().setPreviewSize(0, 0));
    }

    /**
     * /uiux editor: 열린 화면 없는 빈 에디터가 뜨고 화면 목록이 보인다. 거기서 새 화면을 만들어도 저장하기 전에는 파일이 없고,
     * 변경 없이 닫으면 아무 파일도 남지 않는다. 고친 뒤 Ctrl+S 를 하면 그때 파일이 생긴다.
     */
    private void blankEditorAndLazyNewFile(ClientGameTestContext ctx) {
        Path newFile = base.resolve("ui/new_1.xml");
        check(!Files.exists(newFile), "시작할 때 new_1.xml 이 없어야 한다");

        ctx.runOnClient(mc -> dev.magyul.betteruiux.mc26.UiApi.editor());
        ctx.waitFor(mc -> mc.gui.screen() instanceof UiScreen sc && sc.uiId().isEmpty() && sc.editor() != null);
        ctx.waitTicks(3);
        ctx.takeScreenshot("6b-blank-editor");
        check(find(ctx, "편집할 화면을 고르세요") != null, "빈 에디터의 안내가 보여야 한다");
        check(find(ctx, "열기: gt_ext") != null && find(ctx, "열기: gt_kf") != null, "화면 목록이 보여야 한다");
        check(find(ctx, "Save") == null, "빈 에디터에는 Save 가 없다");

        // 1) 새 화면을 만들어도 변경이 없으면 파일이 생기지 않는다
        int[] fresh = find(ctx, "새 화면: 빈 화면");
        click(ctx, fresh[0] + 2, fresh[1] + 2);
        ctx.waitFor(mc -> mc.gui.screen() instanceof UiScreen sc && sc.uiId().equals("new_1") && sc.editor() != null);
        ctx.waitTicks(3);
        ctx.takeScreenshot("6c-new-unsaved");
        check(!Files.exists(newFile), "새 화면은 저장하기 전에는 파일이 없어야 한다");
        check(find(ctx, "new_1.xml (새 파일 - 저장 전)") != null, "제목에 저장 전 표시가 있어야 한다");
        check(!editor(ctx, EditorController::isDirty), "고치지 않았으면 변경 없음");
        ctx.getInput().pressKey(KEY_ESCAPE);                                   // 변경 없이 닫기: 묻지 않고 닫힌다
        ctx.waitFor(mc -> !(mc.gui.screen() instanceof UiScreen));
        check(!Files.exists(newFile), "변경 없이 닫으면 아무 파일도 남지 않아야 한다");

        // 2) 새로 만들어 고친 뒤 Ctrl+S 를 하면 그때 파일이 생긴다
        ctx.runOnClient(mc -> dev.magyul.betteruiux.mc26.UiApi.editor());
        ctx.waitFor(mc -> mc.gui.screen() instanceof UiScreen sc && sc.uiId().isEmpty() && sc.editor() != null);
        ctx.waitTicks(3);
        int[] dialog = find(ctx, "새 화면: 대화상자");
        click(ctx, dialog[0] + 2, dialog[1] + 2);
        ctx.waitFor(mc -> mc.gui.screen() instanceof UiScreen sc && sc.uiId().equals("new_1") && sc.editor() != null);
        ctx.waitTicks(3);
        check(!Files.exists(newFile), "아직 저장 전이다");
        ctx.runOnClient(mc -> ((UiScreen) mc.gui.screen()).editor().add("label"));
        check(editor(ctx, EditorController::isDirty), "요소를 더하면 변경이 생긴다");
        check(!Files.exists(newFile), "고쳤어도 저장 전에는 파일이 없다");
        ctrl(ctx, KEY_S);
        ctx.waitTicks(3);
        ctx.takeScreenshot("6d-new-saved");
        check(Files.exists(newFile), "Ctrl+S 를 하면 파일이 생겨야 한다");
        check(read("ui/new_1.xml").contains("<label"), "저장된 파일에 추가한 요소가 있어야 한다: " + read("ui/new_1.xml"));
        check(find(ctx, "new_1.xml (새 파일 - 저장 전)") == null && find(ctx, "new_1.xml") != null, "저장 뒤에는 저장 전 표시가 사라진다");
        for (int i = 0; i < 3 && ctx.computeOnClient(mc -> mc.gui.screen() instanceof UiScreen); i++) {   // 선택이 있으면 첫 Esc 는 선택만 푼다
            ctx.getInput().pressKey(KEY_ESCAPE);
            ctx.waitTicks(3);
        }
        check(!ctx.computeOnClient(mc -> mc.gui.screen() instanceof UiScreen), "저장한 뒤에는 묻지 않고 닫혀야 한다");
        try {
            Files.deleteIfExists(newFile);                                    // 다음 시나리오에 영향이 없게
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** 저장 안 하고 끝난 편집이 남아 있으면 열 때 복구 대화상자가 뜨고, Enter 로 복구된다. */
    private void autosaveRecovery(ClientGameTestContext ctx) {
        openEditor(ctx, "gt_auto");
        ctx.waitTicks(2);
        ctx.takeScreenshot("7-autosave-dialog");
        check(find(ctx, "복구") != null && find(ctx, "버리기") != null, "복구 대화상자의 버튼이 보여야 한다");
        ctx.getInput().pressKey(KEY_ENTER);
        ctx.waitTicks(2);
        check(editor(ctx, ed -> ed.text().contains("recovered")), "Enter 로 자동 저장본이 복구돼야 한다");
        check(editor(ctx, EditorController::isDirty), "복구하면 저장 전 상태(더러움)여야 한다");
        ctx.takeScreenshot("8-autosave-recovered");
        ctx.getInput().pressKey(KEY_ESCAPE);                              // 저장 안 한 채 닫기 요청 → 확인 대화상자
        ctx.waitTicks(2);
        ctx.takeScreenshot("9-exit-confirm");
    }

    // ---- 준비 ----

    private void writeFixtures() {
        try {
            Files.createDirectories(base.resolve("ui"));
            Files.createDirectories(base.resolve("autosave"));
            Files.writeString(base.resolve("config.toml"), "[editor]\nenabled = true\n");
            Files.writeString(base.resolve("ui/gt_ext.xml"),
                    "<screen>\n  <link rel=\"style\" href=\"gt_ext.uicss\"/>\n  <panel id=\"box\" class=\"card\" anchor=\"center\" width=\"160\" height=\"70\">\n"
                            + "    <label text=\"external style\"/>\n  </panel>\n</screen>\n");
            Files.writeString(base.resolve("ui/gt_ext.uicss"), ".card { padding: 8; background: #E0181A24; }\n");
            Files.writeString(base.resolve("ui/gt_kf.xml"),
                    "<screen>\n  <style>\n    .p { animation: pulse 1s infinite; }\n    @keyframes pulse {\n      from { opacity: 0; }\n      to { opacity: 1; }\n    }\n  </style>\n"
                            + "  <panel id=\"box\" class=\"p\" anchor=\"center\" width=\"120\" height=\"50\" background=\"#E0181A24\"/>\n</screen>\n");
            Files.writeString(base.resolve("ui/gt_other.xml"), "<screen>\n  <label text=\"other\"/>\n</screen>\n");
            Files.writeString(base.resolve("ui/gt_auto.xml"), "<screen>\n  <label text=\"saved\"/>\n</screen>\n");
            Files.writeString(base.resolve("autosave/gt_auto.xml"), "<screen>\n  <label text=\"recovered\"/>\n</screen>\n");
        } catch (IOException e) {
            throw new AssertionError("테스트 파일을 만들지 못했다: " + e, e);
        }
    }

    private String read(String rel) {
        try {
            return Files.readString(base.resolve(rel));
        } catch (IOException e) {
            throw new AssertionError(rel + " 를 읽지 못했다: " + e, e);
        }
    }

    // ---- 도우미 ----

    private void openEditor(ClientGameTestContext ctx, String id) {
        ctx.runOnClient(mc -> UiOpener.open(id, base, true));
        ctx.waitFor(mc -> mc.gui.screen() instanceof UiScreen s && s.uiId().equals(id) && s.editor() != null);
        ctx.waitTicks(3);
    }

    private void selectById(ClientGameTestContext ctx, String elementId) {
        ctx.runOnClient(mc -> {
            EditorController ed = ((UiScreen) mc.gui.screen()).editor();
            int idx = ed.document().layout().children.stream().filter(b -> elementId.equals(b.id())).findFirst()
                    .orElseThrow(() -> new AssertionError("요소 #" + elementId + " 없음")).node.sourceIndex();
            ed.select(idx);
        });
    }

    private boolean editor(ClientGameTestContext ctx, java.util.function.Predicate<EditorController> p) {
        return ctx.computeOnClient(mc -> p.test(((UiScreen) mc.gui.screen()).editor()));
    }

    /** 지금 에디터가 그리는 글자 중 {@code text} 와 같은 마지막 것의 GUI 좌표(없으면 null). */
    private int[] find(ClientGameTestContext ctx, String text) {
        return ctx.computeOnClient(mc -> {
            DrawList l = new DrawList();
            ((UiScreen) mc.gui.screen()).editor().paint(l);
            return l.commands().stream().filter(c -> c instanceof DrawCommand.DrawText d && d.text().equals(text))
                    .map(c -> (DrawCommand.DrawText) c).reduce((a, b) -> b).map(d -> new int[] {d.x(), d.y()}).orElse(null);
        });
    }

    /** GUI 좌표 (x,y) 를 실제 마우스로 누른다(창 픽셀 = GUI 좌표 × GUI 배율). */
    private void click(ClientGameTestContext ctx, int x, int y) {
        double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
        TestInput in = ctx.getInput();
        in.setCursorPos(x * scale, y * scale);
        ctx.waitTick();
        in.pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);   // 26.x(SDL) 는 좌클릭이 1
        ctx.waitTick();
    }

    /** Ctrl+키: Fabric 의 holdControl 은 GLFW 키 번호라 SDL 기반 26.x 에서 Ctrl 로 인식되지 않아, 화면에 Ctrl 수정키가 붙은 키 이벤트를 직접 보낸다. */
    private void ctrl(ClientGameTestContext ctx, int key) {
        ctx.runOnClient(mc -> mc.gui.screen().keyPressed(
                new net.minecraft.client.input.KeyEvent(key, 0, com.mojang.blaze3d.platform.InputConstants.MOD_CONTROL)));
        ctx.waitTicks(2);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
