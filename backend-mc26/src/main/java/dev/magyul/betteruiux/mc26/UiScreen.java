package dev.magyul.betteruiux.mc26;

import com.mojang.blaze3d.platform.InputConstants;
import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.core.doc.ErrorOverlay;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.doc.UiDocumentLoader;
import dev.magyul.betteruiux.core.doc.UiFileWatcher;
import dev.magyul.betteruiux.core.xml.UiNode;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link UiDocument} 하나를 마인크래프트 Screen 으로 보여주는 어댑터. 레이아웃/그리기/클릭은 코어가 처리한다.
 * 핫리로드가 켜져 있으면 UI 폴더의 xml/uicss 변경을 감지해 문서를 다시 읽는다. 다시 읽기에 실패하면
 * 직전의 정상 문서를 유지하고 화면 위쪽에 오류 띠를 표시한다.
 */
public final class UiScreen extends Screen {
    private static final Logger LOG = LoggerFactory.getLogger("better_uiux");
    private static final long RELOAD_QUIET_MS = 120;
    /** -Dbetteruiux.debug=true : 입력 이벤트를 로그로 남긴다(개발용). */
    private static final boolean DEBUG = Boolean.getBoolean("betteruiux.debug");

    private final String id;
    private final Path userDir;
    private final TextMetrics metrics;
    private final boolean hotReload;
    private UiDocument doc;
    private UiFileWatcher watcher;
    private String error;
    private boolean loggedFirstFrame;

    /** @param doc 처음 로드에 실패했으면 null(빈 화면 + 오류 띠 → 파일을 고쳐 저장하면 자동으로 나타난다) */
    public UiScreen(Component title, String id, Path userDir, TextMetrics metrics,
                    UiDocument doc, String initialError, boolean hotReload) {
        super(title);
        this.id = id;
        this.userDir = userDir;
        this.metrics = metrics;
        this.hotReload = hotReload;
        this.doc = doc != null ? doc : new UiDocument(new UiNode("screen"), metrics);
        this.error = initialError;
        bind(this.doc);
    }

    private void bind(UiDocument d) {
        d.setActionHandler((action, source) -> UiActions.dispatch(action, this));
    }

    @Override
    protected void init() {
        doc.layout(this.width, this.height);
        if (hotReload && watcher == null) {
            try {
                watcher = UiFileWatcher.start(userDir);
            } catch (IOException e) {
                LOG.warn("[better-uiux] hot reload disabled: {}", e.toString());
            }
        }
    }

    /** 창 크기/GUI scale 변경 시 마인크래프트가 init() 대신 호출한다. */
    @Override
    protected void repositionElements() {
        doc.layout(this.width, this.height);
    }

    @Override
    public void tick() {
        super.tick();
        if (watcher != null && watcher.pollChanged(RELOAD_QUIET_MS)) reload();
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        McGfx gfx = new McGfx(graphics, this.font);
        doc.paint(gfx, mouseX, mouseY);

        List<ErrorOverlay.Message> messages = new ArrayList<>();
        if (error != null) messages.add(new ErrorOverlay.Message(true, error));
        for (String w : doc.warnings()) messages.add(new ErrorOverlay.Message(false, w));
        if (!messages.isEmpty()) ErrorOverlay.paint(gfx, metrics, this.width, messages);

        if (!loggedFirstFrame) {
            loggedFirstFrame = true;
            LOG.info("[better-uiux] first frame painted: {}x{}", this.width, this.height);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean used = doc.mousePressed((int) event.x(), (int) event.y(), uiButton(event));
        if (DEBUG) LOG.info("[better-uiux] press ({}, {}) mc-button={} -> consumed={}", event.x(), event.y(), event.button(), used);
        return used || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean used = doc.mouseReleased((int) event.x(), (int) event.y(), uiButton(event));
        if (DEBUG) LOG.info("[better-uiux] release ({}, {}) mc-button={} -> consumed={}", event.x(), event.y(), event.button(), used);
        return used || super.mouseReleased(event);
    }

    /**
     * 마인크래프트 버튼 번호 → 코어 번호(DOM 관례: 0=좌, 1=가운데, 2=우).
     * 26.x 는 SDL 기반이라 좌클릭이 1 이다(예전 GLFW 는 0). 상수를 기준으로 변환해 번호 체계 변경에 대비한다.
     */
    private static int uiButton(MouseButtonEvent event) {
        return event.button() - InputConstants.MOUSE_BUTTON_LEFT;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
