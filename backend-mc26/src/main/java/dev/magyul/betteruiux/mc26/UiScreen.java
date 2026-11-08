package dev.magyul.betteruiux.mc26;

import com.mojang.blaze3d.platform.InputConstants;
import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.core.doc.ErrorOverlay;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.doc.UiDocumentLoader;
import dev.magyul.betteruiux.core.doc.UiFileWatcher;
import dev.magyul.betteruiux.core.edit.EditorController;
import dev.magyul.betteruiux.core.xml.UiNode;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link UiDocument} 하나를 마인크래프트 Screen 으로 보여주는 어댑터. 레이아웃/그리기/클릭은 코어가 처리한다.
 *
 * <p>핫리로드가 켜져 있으면 UI 폴더의 xml/uicss 변경을 감지해 문서를 다시 읽는다. 다시 읽기에 실패하면
 * 직전의 정상 문서를 유지하고 화면 위쪽에 오류 띠를 표시한다.
 *
 * <p>설정에서 에디터가 켜져 있으면({@code editor.enabled}) F8 로 인게임 에디터를 토글한다. 에디터가 켜져 있는 동안
 * 모든 입력은 {@link EditorController} 가 받고, 파일 변경 감시는 무시한다(편집 중인 내용을 덮어쓰지 않도록).
 * 에디터 클래스는 처음 켤 때에만 로드된다.
 */
public final class UiScreen extends Screen {
    private static final Logger LOG = LoggerFactory.getLogger("better_uiux");
    private static final long RELOAD_QUIET_MS = 120;
    /** -Dbetteruiux.debug=true : 입력 이벤트를 로그로 남긴다(개발용). */
    private static final boolean DEBUG = Boolean.getBoolean("betteruiux.debug");

    /** @param startInEditor 화면이 열리자마자 에디터를 켠다({@code /uiux edit}) */
    public record Options(boolean hotReload, boolean editorEnabled, boolean startInEditor) {}

    private final String id;
    private final Path userDir;
    private final TextMetrics metrics;
    private final Options options;
    private UiDocument doc;
    private UiFileWatcher watcher;
    private EditorController editor;
    private boolean startInEditor;
    private String error;
    private boolean loggedFirstFrame;
    private boolean selfTestDone;
    /** -Dbetteruiux.selftest=true : 에디터가 켜지면 게임 안에서 "속성 클릭 → 입력 → Enter" 를 스스로 수행해 결과를 로그에 남긴다(개발용). */
    private static final boolean SELFTEST = Boolean.getBoolean("betteruiux.selftest");

    /** @param doc 처음 로드에 실패했으면 null(빈 화면 + 오류 띠 → 파일을 고쳐 저장하면 자동으로 나타난다) */
    public UiScreen(Component title, String id, Path userDir, TextMetrics metrics,
                    UiDocument doc, String initialError, Options options) {
        super(title);
        this.id = id;
        this.userDir = userDir;
        this.metrics = metrics;
        this.options = options;
        this.startInEditor = options.startInEditor();
        this.doc = doc != null ? doc : new UiDocument(new UiNode("screen"), metrics);
        this.error = initialError;
        bind(this.doc);
    }

    /** 지금 보여주는 문서(핫리로드/편집 시 교체될 수 있으니 오래 들고 있지 말 것). */
    public UiDocument document() { return activeDoc(); }

    private UiDocument activeDoc() { return editor != null ? editor.document() : doc; }

    private void bind(UiDocument d) {
        d.setOnClose(this::onClose);
    }

    @Override
    protected void init() {
        if (editor != null) editor.setView(this.width, this.height);
        else doc.layout(this.width, this.height);
        if (options.hotReload() && watcher == null) {
            try {
                watcher = UiFileWatcher.start(userDir);
            } catch (IOException e) {
                LOG.warn("[better-uiux] hot reload disabled: {}", e.toString());
            }
        }
        if (startInEditor) {
            startInEditor = false;
            toggleEditor();
        }
    }

    /** 창 크기/GUI scale 변경 시 마인크래프트가 init() 대신 호출한다. */
    @Override
    protected void repositionElements() {
        if (editor != null) editor.setView(this.width, this.height);
        else doc.layout(this.width, this.height);
    }

    @Override
    public void tick() {
        super.tick();
        if (watcher != null && watcher.pollChanged(RELOAD_QUIET_MS) && editor == null) reload();
    }

    @Override
    public void removed() {
        super.removed();
        if (watcher != null) {
            watcher.close();
            watcher = null;
        }
    }

    private void reload() {
        try {
            UiDocument next = UiDocumentLoader.load(userDir, id, metrics);
            bind(next);
            UiApi.fireOpened(id, next);   // 핫리로드로 새 문서가 만들어져도 모드의 초기화 훅을 다시 적용한다
            next.layout(this.width, this.height);
            doc = next;
            error = null;
            LOG.info("[better-uiux] reloaded '{}'", id);
        } catch (NoSuchFileException e) {
            error = id + ".xml 파일을 찾을 수 없습니다";
        } catch (UiParseException e) {
            error = "XML 오류 (줄 " + e.line() + "): " + e.getMessage();
        } catch (IOException e) {
            error = "읽기 실패: " + e.getMessage();
        }
        if (error != null) LOG.warn("[better-uiux] reload failed ('{}' 유지): {}", id, error);
    }

    // ---- 에디터 ----

    private void toggleEditor() {
        if (editor != null) {
            leaveEditor();
            return;
        }
        if (!options.editorEnabled()) {
            UiOpener.say(this.minecraft, "에디터가 꺼져 있습니다 (config/better-uiux/config.toml 의 [editor] enabled = true)");
            return;
        }
        try {
            editor = EditorBackend.create(id, userDir, metrics, this, this.width, this.height);   // 이때 에디터 클래스가 로드된다
            error = null;
            LOG.info("[better-uiux] editor on ('{}')", id);
        } catch (NoSuchFileException e) {
            UiOpener.say(this.minecraft, "에디터: " + id + ".xml 을 찾을 수 없습니다");
        } catch (UiParseException e) {
            error = "에디터: XML 오류 (줄 " + e.line() + "): " + e.getMessage();
        } catch (IOException e) {
            UiOpener.say(this.minecraft, "에디터: 읽기 실패: " + e.getMessage());
        }
    }

    /** 에디터의 Exit 버튼/Esc 에서 부른다. */
    void exitEditor() {
        if (editor != null) leaveEditor();
    }

    private void leaveEditor() {
        boolean dirty = editor.isDirty();
        UiDocument edited = editor.document();
        editor.close();   // 편집 중이던 칸 취소 + 글자 입력 끄기
        editor = null;
        edited.setAnimationsEnabled(true);
        bind(edited);
        doc = edited;
        doc.layout(this.width, this.height);
        LOG.info("[better-uiux] editor off ('{}', unsaved={})", id, dirty);
        if (dirty) UiOpener.say(this.minecraft, "저장하지 않은 변경이 화면에만 남아 있습니다. 파일을 고치면 사라집니다.");
    }

    /** 속성 패널 입력 경로를 실제 게임 환경(실제 폰트/좌표)에서 점검한다. 결과는 로그로만 남기고 편집은 되돌린다. */
    private void runEditorSelfTest() {
        var first = editor.document().layout().children.stream().filter(b -> b.node.sourceIndex() >= 0).findFirst();
        if (first.isEmpty()) {
            LOG.info("[better-uiux] selftest: 렌더되는 요소가 없음");
            return;
        }
        int idx = first.get().node.sourceIndex();
        editor.select(idx);
        int fx = this.width - 142 + 60 + 6, fy = 16 + 3 + 3 * 12 + 1;   // 속성 패널의 4번째 행(너비)
        String before = editor.text();
        editor.mousePressed(fx, fy, 0);
        editor.mouseReleased(fx, fy, 0);
        boolean editing = editor.isEditingField();
        editor.charTyped('9');
        editor.keyPressed(dev.magyul.betteruiux.core.edit.EditorKey.ENTER, false, false);
        boolean changed = !editor.text().equals(before);
        LOG.info("[better-uiux] selftest: select({}) field-click({}, {}) -> editing={} -> typed '9' + Enter -> changed={} dirty={} status='{}' (view {}x{})",
                idx, fx, fy, editing, changed, editor.isDirty(), editor.status(), this.width, this.height);
        editor.undo();
    }

    // ---- 그리기 ----

    /**
     * 배경은 {@code <screen use="...">} 가 결정한다(vanilla 기본 배경을 쓰지 않는다).
     * 월드가 없을 때(타이틀 화면 등)는 vanilla 와 같이 파노라마를 먼저 깔아 배경이 비어 보이지 않게 한다(none 제외).
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        UiDocument d = activeDoc();
        if (this.minecraft.level == null && !d.background().isNone()) extractPanorama(graphics, a);
        d.paintBackground(new McGfx(graphics, this.font));
        this.minecraft.gui.hud.extractDeferredSubtitles();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        McGfx gfx = new McGfx(graphics, this.font);
        if (editor != null) {
            editor.paintDocument(gfx);   // 편집 중에는 호버/클릭 효과를 끄고, 화면 이동(팬) 위치에 맞춰 그린다
            editor.mouseMoved(mouseX, mouseY);
            editor.paint(gfx);
            paintPreedit(graphics, mouseX, mouseY, a);
            if (SELFTEST && !selfTestDone) {
                selfTestDone = true;
                runEditorSelfTest();
            }
        } else {
            doc.paint(gfx, mouseX, mouseY);
            List<ErrorOverlay.Message> messages = new ArrayList<>();
            if (error != null) messages.add(new ErrorOverlay.Message(true, error));
            for (String w : doc.warnings()) messages.add(new ErrorOverlay.Message(false, w));
            if (!messages.isEmpty()) ErrorOverlay.paint(gfx, metrics, this.width, messages);
        }
        if (!loggedFirstFrame) {
            loggedFirstFrame = true;
            LOG.info("[better-uiux] first frame painted: {}x{} (warnings={})", this.width, this.height, activeDoc().warnings());
        }
    }

    // ---- IME 조합창 ----

    private net.minecraft.client.input.PreeditEvent preedit;

    /** SDL 이 보내 주는 IME 조합 중인 글자. 비어 있으면 조합이 끝난 것. */
    @Override
    public boolean preeditUpdated(net.minecraft.client.input.PreeditEvent event) {
        if (editor == null || !editor.isEditingField()) return super.preeditUpdated(event);
        preedit = event == null || event.fullText().isEmpty() ? null : event;
        return true;
    }

    private void paintPreedit(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int[] caret = editor.caretScreen();
        if (preedit == null || caret == null) {
            if (caret == null) preedit = null;
            return;
        }
        var overlay = new net.minecraft.client.gui.components.IMEPreeditOverlay(preedit, this.font, caret[2]);
        overlay.updateInputPosition(caret[0], caret[1]);
        overlay.extractRenderState(graphics, mouseX, mouseY, a);
    }

    // ---- 입력 ----

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_F8) {
            toggleEditor();
            return true;
        }
        if (editor != null) {
            var mapped = McEditorKeys.map(event);
            boolean used = editor.keyPressed(mapped, event.hasControlDown(), event.hasShiftDown());
            if (DEBUG) LOG.info("[better-uiux] editor key={} -> {} used={} editing={}", event.key(), mapped, used, editor.isEditingField());
            return true;   // 편집 중에는 게임 단축키(Esc 로 닫기 등)를 막는다. 나가려면 F8.
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (editor != null) {
            boolean used = event.codepoint() <= 0xFFFF && editor.charTyped((char) event.codepoint());
            if (DEBUG) LOG.info("[better-uiux] editor char U+{} used={} editing={}", Integer.toHexString(event.codepoint()), used, editor.isEditingField());
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (editor != null) {
            boolean used = editor.mousePressed((int) event.x(), (int) event.y(), uiButton(event));
            if (DEBUG) LOG.info("[better-uiux] editor press ({}, {}) btn={} selected={} editing={} status='{}'", event.x(), event.y(),
                    uiButton(event), editor.selectedIndex(), editor.isEditingField(), editor.status());
            return used;
        }
        boolean used = doc.mousePressed((int) event.x(), (int) event.y(), uiButton(event));
        if (DEBUG) LOG.info("[better-uiux] press ({}, {}) mc-button={} -> consumed={}", event.x(), event.y(), event.button(), used);
        return used || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (editor != null) return editor.mouseReleased((int) event.x(), (int) event.y(), uiButton(event));
        boolean used = doc.mouseReleased((int) event.x(), (int) event.y(), uiButton(event));
        if (DEBUG) LOG.info("[better-uiux] release ({}, {}) mc-button={} -> consumed={}", event.x(), event.y(), event.button(), used);
        return used || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (editor != null) return editor.mouseDragged((int) event.x(), (int) event.y());
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (editor != null) return editor.mouseScrolled((int) x, (int) y, scrollY);
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    /**
     * 마인크래프트 버튼 번호 → 코어 번호(DOM 관례: 0=좌, 1=가운데, 2=우).
     * 26.x 는 SDL 기반이라 좌클릭이 1 이다(예전 GLFW 는 0). 상수를 기준으로 변환해 번호 체계 변경에 대비한다.
     */
    private static int uiButton(MouseButtonEvent event) {
        return event.button() - InputConstants.MOUSE_BUTTON_LEFT;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return editor == null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
