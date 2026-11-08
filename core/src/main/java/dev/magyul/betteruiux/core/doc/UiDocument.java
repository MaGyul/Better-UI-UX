package dev.magyul.betteruiux.core.doc;

import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.api.UiGfx;
import dev.magyul.betteruiux.core.layout.LayoutBox;
import dev.magyul.betteruiux.core.layout.LayoutEngine;
import dev.magyul.betteruiux.core.render.UiPainter;
import dev.magyul.betteruiux.core.style.StyleResolver;
import dev.magyul.betteruiux.core.style.Stylesheet;
import dev.magyul.betteruiux.core.xml.UiNode;
import dev.magyul.betteruiux.core.xml.UiParseException;
import dev.magyul.betteruiux.core.xml.UiParser;
import java.util.ArrayList;
import java.util.List;

/**
 * 파싱 + 스타일 + 레이아웃 + 그리기 + 입력(호버/클릭)을 묶은 문서 하나. 마인크래프트 비의존.
 * 백엔드의 Screen/HUD 는 이 객체를 감싸 입력을 전달하고 {@link #paint}를 호출한다.
 */
public final class UiDocument {
    /** 버튼 {@code onClick="..."} 이 눌렸을 때 호출된다. */
    @FunctionalInterface
    public interface ActionHandler {
        void onAction(String action, LayoutBox source);
    }

    private final UiNode root;
    private final LayoutEngine layoutEngine;
    private final UiPainter painter;
    private final List<String> warnings;
    private LayoutBox layout;
    private LayoutBox hovered, pressed;
    private ActionHandler actionHandler = (a, s) -> { };

    private final UiPainter.PaintState state = new UiPainter.PaintState() {
        @Override public boolean hovered(LayoutBox b) { return b == hovered; }

        @Override public boolean pressed(LayoutBox b) { return b == pressed && b == hovered; }
    };

    public UiDocument(UiNode root, TextMetrics text, List<Stylesheet> sheets, List<String> warnings) {
        this.root = root;
        this.layoutEngine = new LayoutEngine(text, new StyleResolver(sheets));
        this.painter = new UiPainter(text);
        this.warnings = List.copyOf(warnings);
    }

    public UiDocument(UiNode root, TextMetrics text) {
        this(root, text, inlineSheets(root), List.of());
    }

    /** 문서 안의 {@code <style>} 블록만 읽는다(외부 {@code <link>} 까지 읽으려면 {@link UiDocumentLoader}). */
    public static UiDocument parse(String xml, TextMetrics text) throws UiParseException {
        return new UiDocument(UiParser.parse(xml), text);
    }

    static List<Stylesheet> inlineSheets(UiNode root) {
        List<Stylesheet> sheets = new ArrayList<>();
        collectStyleBlocks(root, sheets);
        return sheets;
    }

    private static void collectStyleBlocks(UiNode n, List<Stylesheet> out) {
        if (n.tag().equals("style")) out.add(Stylesheet.parse(n.text()));
        for (UiNode c : n.children()) collectStyleBlocks(c, out);
    }

    public UiNode root() { return root; }

    public LayoutBox layout() { return layout; }

    /** 로드/스타일 파싱 중 생긴 경고(문서는 정상 표시됨). */
    public List<String> warnings() { return warnings; }

    public void setActionHandler(ActionHandler h) { this.actionHandler = h == null ? (a, s) -> { } : h; }

    /** 화면 크기(GUI 픽셀)에 맞춰 레이아웃을 다시 계산한다. 창 크기/GUI scale 변경 시 호출. */
    public void layout(int viewW, int viewH) {
        this.layout = layoutEngine.layout(root, viewW, viewH);
        this.hovered = null;
        this.pressed = null;
    }

    public void paint(UiGfx gfx, int mouseX, int mouseY) {
        if (layout == null) return;
        hovered = hitButton(mouseX, mouseY);
        painter.paint(layout, gfx, state);
    }

    /** 점 아래의 가장 깊은 박스. */
    public LayoutBox hitTest(int x, int y) {
        return layout == null ? null : hit(layout, x, y);
    }

    private LayoutBox hit(LayoutBox b, int x, int y) {
        if (!b.visible() || !b.contains(x, y)) return null;
        for (int i = b.children.size() - 1; i >= 0; i--) {
            LayoutBox r = hit(b.children.get(i), x, y);
            if (r != null) return r;
        }
        return b;
    }

    private LayoutBox hitButton(int x, int y) {
        for (LayoutBox b = hitTest(x, y); b != null; b = b.parent) {
            if (b.tag().equals("button") && b.enabled()) return b;
        }
        return null;
    }

    public LayoutBox findById(String id) { return layout == null ? null : find(layout, id); }

    private LayoutBox find(LayoutBox b, String id) {
        if (id.equals(b.id())) return b;
        for (LayoutBox c : b.children) {
            LayoutBox r = find(c, id);
            if (r != null) return r;
        }
        return null;
    }

    /**
     * 마우스 버튼 번호는 DOM 관례를 따른다: 0=좌, 1=가운데, 2=우. 백엔드가 게임의 번호를 이 체계로 변환해서 넘긴다.
     *
     * @return 입력을 소비했으면 true (버튼 위 좌클릭).
     */
    public boolean mousePressed(int x, int y, int button) {
        if (button != 0) return false;
        pressed = hitButton(x, y);
        return pressed != null;
    }

    /** 누른 버튼 위에서 뗐을 때 {@code onClick} 액션을 발생시킨다. */
    public boolean mouseReleased(int x, int y, int button) {
        if (button != 0) return false;
        LayoutBox down = pressed;
        pressed = null;
        if (down == null) return false;
        if (hitButton(x, y) == down) {
            String action = down.style.get("onclick");
            if (action != null && !action.isBlank()) actionHandler.onAction(action.trim(), down);
        }
        return true;
    }
}
