package dev.magyul.betteruiux.core.doc;

import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.api.UiGfx;
import dev.magyul.betteruiux.core.anim.AnimationEngine;
import dev.magyul.betteruiux.core.anim.Easing;
import dev.magyul.betteruiux.core.layout.LayoutBox;
import dev.magyul.betteruiux.core.layout.LayoutEngine;
import dev.magyul.betteruiux.core.layout.StyleMap;
import dev.magyul.betteruiux.core.render.ScreenBackground;
import dev.magyul.betteruiux.core.render.UiPainter;
import dev.magyul.betteruiux.core.style.Selector;
import dev.magyul.betteruiux.core.style.StyleResolver;
import dev.magyul.betteruiux.core.style.Stylesheet;
import dev.magyul.betteruiux.core.xml.UiNode;
import dev.magyul.betteruiux.core.xml.UiParseException;
import dev.magyul.betteruiux.core.xml.UiParser;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 파싱 + 스타일 + 레이아웃 + 그리기 + 입력 + 이벤트 + 바인딩을 묶은 문서 하나. 마인크래프트 비의존.
 * 백엔드의 Screen/HUD 는 이 객체를 감싸 입력을 전달하고 {@link #paint}를 호출한다.
 *
 * <p>Java API: {@link #query}, {@link #on}, {@link #bind}, {@link UiActions}, {@link UiWidgets}.
 * DOM 을 바꾸면 다음 프레임에 스타일/레이아웃이 자동으로 다시 계산된다.
 */
public final class UiDocument {
    /** 버튼 {@code onClick="..."} 가 등록된 {@link UiActions} 에 없을 때 호출되는 예비 핸들러. */
    @FunctionalInterface
    public interface ActionHandler {
        void onAction(String action, LayoutBox source);
    }

    private record HandlerReg(UiNode node, Selector selector, UiEvent.Type type, UiEvent.Handler handler) {}

    private final UiNode root;
    private final TextMetrics textMetrics;
    private final List<Stylesheet> sheets;
    private final List<String> warnings;
    private final Set<String> runtimeWarnings = new LinkedHashSet<>();
    private final UiPainter painter;
    private LayoutEngine layoutEngine;
    private StyleResolver resolver;
    private AnimationEngine anim;
    private long frameNow;
    private boolean animationsEnabled = true;

    private LayoutBox layout;
    private final Map<UiNode, LayoutBox> boxes = new IdentityHashMap<>();
    private final Map<UiNode, UiElement> elements = new IdentityHashMap<>();
    private int viewW, viewH;
    private boolean dirty;
    private ScreenBackground background = ScreenBackground.defaults();

    private UiNode hoverNode;       // 마우스 아래의 가장 깊은 요소
    private UiNode hoverButton;     // 마우스 아래의 (활성) 버튼
    private UiNode pressNode;       // 마우스를 누른 가장 깊은 요소
    private UiNode pressButton;     // 누른 버튼
    private int mouseX = -1, mouseY = -1;

    private final List<HandlerReg> handlers = new ArrayList<>();
    private final Map<String, Supplier<?>> bindings = new LinkedHashMap<>();
    private final Map<String, String> bindingValues = new LinkedHashMap<>();
    private UiClock clock = UiClock.SYSTEM;
    private ActionHandler actionHandler;
    private Runnable onClose = () -> { };

    private final UiPainter.PaintState state = new UiPainter.PaintState() {
        @Override public boolean hovered(LayoutBox b) { return hoverNode != null && b.node.isAncestorOf(hoverNode); }

        @Override public boolean pressed(LayoutBox b) {
            return pressNode != null && b.node.isAncestorOf(pressNode) && hovered(b);
        }

        @Override public StyleMap style(LayoutBox b) {
            StyleMap s = b.effective(hovered(b), pressed(b));
            return animationsEnabled ? anim.apply(b.node, s, frameNow) : s;
        }

        @Override public int mouseX() { return mouseX; }

        @Override public int mouseY() { return mouseY; }

        @Override public long nowMillis() { return frameNow; }

        @Override public UiElement element(LayoutBox b) { return UiDocument.this.element(b.node); }
    };

    public UiDocument(UiNode root, TextMetrics text, List<Stylesheet> sheets, List<String> warnings) {
        this.root = root;
        this.textMetrics = text;
        this.sheets = List.copyOf(sheets);
        this.warnings = List.copyOf(warnings);
        this.painter = new UiPainter(text, this::interpolate);
        rebuildEngine();
    }

    public UiDocument(UiNode root, TextMetrics text) {
        this(root, text, inlineSheets(root), List.of());
    }

    /** 문서 안의 {@code <style>} 블록만 읽는다(외부 {@code <link>} 까지 읽으려면 {@link UiDocumentLoader}). */
    public static UiDocument parse(String xml, TextMetrics text) throws UiParseException {
        UiNode root = UiParser.parse(xml);
        List<String> warnings = new ArrayList<>();
        UiExpander.expand(root, null, warnings);   // template/use/slot (include 는 기본 제공 파일만)
        return new UiDocument(root, text, inlineSheets(root), warnings);
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

    private void rebuildEngine() {
        this.resolver = new StyleResolver(sheets);
        this.anim = new AnimationEngine(name -> resolver.keyframes(name));
        this.layoutEngine = new LayoutEngine(textMetrics, resolver, this::interpolate, this::widgetFor);
    }

    private UiWidget widgetFor(UiNode node) {
        return UiWidgets.has(node.tag()) ? UiWidgets.instance(element(node)) : null;
    }

    // ---- 기본 접근자 ----

    public UiNode root() { return root; }

    public LayoutBox layout() { return layout; }

    /** 로드/스타일/배경/실행 중 생긴 경고(문서는 정상 표시됨). */
    public List<String> warnings() {
        List<String> all = new ArrayList<>(warnings);
        all.addAll(background.warnings());
        all.addAll(runtimeWarnings);
        return all;
    }

    /** 루트의 {@code use} 로 정해진 배경 레이어. {@link #layout} 이후 유효. */
    public ScreenBackground background() { return background; }

    /**
     * false 면 전환/@keyframes/Java 애니메이션을 적용하지 않고 스타일시트가 정한 최종 모양만 그린다.
     * 에디터는 편집 중 문서가 자주 다시 만들어지므로(드래그마다) 애니메이션이 매번 처음부터 재생되지 않게 끈다.
     */
    public void setAnimationsEnabled(boolean enabled) { this.animationsEnabled = enabled; }

    /** 애니메이션 시간원을 바꾼다(테스트용). */
    public void setClock(UiClock clock) { this.clock = clock == null ? UiClock.SYSTEM : clock; }

    public UiClock clock() { return clock; }

    /** 등록된 액션이 없을 때의 예비 핸들러. */
    public void setActionHandler(ActionHandler h) { this.actionHandler = h; }

    /** {@code close} 액션이나 {@link #close()} 가 호출됐을 때 실행할 동작(백엔드가 화면 닫기를 연결한다). */
    public void setOnClose(Runnable r) { this.onClose = r == null ? () -> { } : r; }

    public void close() { onClose.run(); }

    void markDirty() { dirty = true; }

    private void addRuntimeWarning(String w) { runtimeWarnings.add(w); }

    // ---- 바인딩: 텍스트의 {이름} 을 값으로 치환 ----

    /** {@code {이름}} 에 쓸 값을 공급한다. 값이 바뀌면 다음 프레임에 자동으로 다시 레이아웃된다. */
    public void bind(String name, Supplier<?> value) {
        bindings.put(name, value);
        dirty = true;
    }

    /** 상수 값 바인딩. */
    public void set(String name, Object value) { bind(name, () -> value); }

    public void unbind(String name) {
        bindings.remove(name);
        bindingValues.remove(name);
        dirty = true;
    }

    private void refreshBindings() {
        for (Map.Entry<String, Supplier<?>> e : bindings.entrySet()) {
            String v;
            try {
                v = String.valueOf(e.getValue().get());
            } catch (RuntimeException ex) {
                v = "?";
                addRuntimeWarning("바인딩 '" + e.getKey() + "' 오류: " + ex);
            }
            if (!v.equals(bindingValues.put(e.getKey(), v))) dirty = true;
        }
    }

    /** {@code {이름}} → 마지막으로 읽은 바인딩 값. 정의되지 않은 이름은 그대로 둔다. */
    String interpolate(String s) {
        if (s == null || s.indexOf('{') < 0 || bindingValues.isEmpty()) return s;
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            int open = s.indexOf('{', i);
            if (open < 0) break;
            int close = s.indexOf('}', open + 1);
            if (close < 0) break;
            String name = s.substring(open + 1, close);
            String v = name.matches("[A-Za-z0-9_.\\-]+") ? bindingValues.get(name) : null;
            out.append(s, i, open);
            out.append(v != null ? v : s.substring(open, close + 1));
            i = close + 1;
        }
        out.append(s, i, s.length());
        return out.toString();
    }

    // ---- 레이아웃 ----

    /** 화면 크기(GUI 픽셀)에 맞춰 레이아웃을 다시 계산한다. 창 크기/GUI scale 변경 시 호출. */
    public void layout(int viewW, int viewH) {
        this.viewW = viewW;
        this.viewH = viewH;
        relayout();
    }

    private void relayout() {
        refreshBindings();
        this.layout = layoutEngine.layout(root, viewW, viewH);
        boxes.clear();
        index(layout);
        this.background = ScreenBackground.parse(layout.style.get("use"));
        dirty = false;
    }

    private void index(LayoutBox b) {
        boxes.put(b.node, b);
        for (LayoutBox c : b.children) index(c);
    }

    private void applyDirty() {
        if (dirty && viewW > 0) relayout();
    }

    LayoutBox boxOf(UiNode node) { return boxes.get(node); }

    // ---- 그리기 ----

    /**
     * 화면 배경(blur / dim / block:&lt;id&gt;)을 {@code use} 에 적힌 순서대로 그린다.
     * 문서 내용({@link #paint})보다 먼저, 게임의 배경 단계에서 호출해야 한다.
     */
    public void paintBackground(UiGfx gfx) {
        if (layout == null) return;
        background.paint(gfx, viewW, viewH,
                layout.style.integer("block-size", ScreenBackground.DEFAULT_BLOCK_SIZE),
                layout.style.color("block-tint", ScreenBackground.DEFAULT_BLOCK_TINT));
    }

    public void paint(UiGfx gfx, int mouseX, int mouseY) {
        if (layout == null) return;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.frameNow = clock.nowMillis();   // 한 프레임 안에서는 같은 시각을 쓴다
        refreshBindings();   // 값이 바뀌었으면 dirty 가 켜져 아래에서 다시 레이아웃된다
        applyDirty();
        updateHover();
        applyDirty();
        painter.paint(layout, gfx, state);
    }

    private void updateHover() {
        LayoutBox hit = hitTest(mouseX, mouseY);
        UiNode now = hit == null ? null : hit.node;
        hoverButton = buttonNode(hit);
        if (now == hoverNode) return;
        UiNode old = hoverNode;
        hoverNode = now;
        if (old != null) dispatch(UiEvent.Type.HOVER_LEAVE, old, mouseX, mouseY, 0);
        if (now != null) dispatch(UiEvent.Type.HOVER_ENTER, now, mouseX, mouseY, 0);
    }

    /** UiAnimation.play() 가 호출한다. */
    AnimationEngine.Imperative startAnimation(UiElement el, java.util.Map<String, String> from,
                                              java.util.Map<String, String> to, double duration, double delay,
                                              Easing easing, Runnable onFinish) {
        long now = clock.nowMillis();
        frameNow = Math.max(frameNow, now);
        return anim.start(el.node(), from, to, duration, delay, easing, now,
                el::style, onFinish, (prop, value) -> el.setStyle(prop, value));
    }

    // ---- 조회 ----

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

    private UiNode buttonNode(LayoutBox from) {
        for (LayoutBox b = from; b != null; b = b.parent) {
            if (b.tag().equals("button") && b.enabled()) return b.node;
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

    UiElement element(UiNode node) { return elements.computeIfAbsent(node, n -> new UiElement(this, n)); }

    /** 선택자에 맞는 첫 요소(없으면 null). 레이아웃 전에도 동작한다. @throws IllegalArgumentException 잘못된 선택자 */
    public UiElement query(String selector) { return queryAll(selector).stream().findFirst().orElse(null); }

    /** 선택자에 맞는 모든 요소(문서 순서). */
    public List<UiElement> queryAll(String selector) { return queryFrom(root, selector, true); }

    List<UiElement> queryFrom(UiNode scope, String selector, boolean includeScope) {
        Selector sel = Selector.parse(selector);
        List<UiElement> out = new ArrayList<>();
        collect(scope, sel, includeScope, out);
        return out;
    }

    private void collect(UiNode n, Selector sel, boolean include, List<UiElement> out) {
        if (include && sel.matches(n)) out.add(element(n));
        for (UiNode c : n.children()) collect(c, sel, true, out);
    }

    // ---- 이벤트 ----

    /**
     * 선택자 위임 핸들러: 선택자에 맞는 요소(나중에 추가된 요소 포함)에서 일어난 이벤트를 받는다.
     *
     * @return 등록 해제 함수
     */
    public Runnable on(String selector, UiEvent.Type type, UiEvent.Handler handler) {
        return addHandler(null, Selector.parse(selector), type, handler);
    }

    Runnable addHandler(UiNode node, Selector selector, UiEvent.Type type, UiEvent.Handler handler) {
        HandlerReg reg = new HandlerReg(node, selector, type, handler);
        handlers.add(reg);
        return () -> handlers.remove(reg);
    }

    private boolean dispatch(UiEvent.Type type, UiNode target, int x, int y, int button) {
        if (handlers.isEmpty()) return false;
        UiEvent ev = new UiEvent(type, element(target), x, y, button);
        boolean handled = false;
        for (UiNode n = target; n != null && !ev.isPropagationStopped(); n = n.parent()) {
            ev.setCurrent(element(n));
            for (HandlerReg r : List.copyOf(handlers)) {
                if (r.type() != type) continue;
                boolean match = r.node() != null ? r.node() == n : r.selector().matches(n);
                if (!match) continue;
                handled = true;
                try {
                    r.handler().handle(ev);
                } catch (RuntimeException ex) {
                    addRuntimeWarning("이벤트 핸들러 오류(" + type + "): " + ex);
                }
                if (ev.isPropagationStopped()) break;
            }
        }
        return handled;
    }

    // ---- 입력 ----

    /**
     * 마우스 버튼 번호는 DOM 관례를 따른다: 0=좌, 1=가운데, 2=우. 백엔드가 게임의 번호를 이 체계로 변환해서 넘긴다.
     *
     * @return 입력을 소비했으면 true (버튼 위 좌클릭이거나 핸들러가 처리함).
     */
    public boolean mousePressed(int x, int y, int button) {
        if (layout == null) return false;
        LayoutBox hit = hitTest(x, y);
        pressNode = hit == null ? null : hit.node;
        pressButton = button == 0 ? buttonNode(hit) : null;
        boolean handled = pressNode != null && dispatch(UiEvent.Type.PRESS, pressNode, x, y, button);
        return pressButton != null || handled;
    }

    /** 누른 버튼 위에서 뗐을 때 {@code onClick} 액션을, 같은 요소에서 뗐을 때 CLICK 이벤트를 발생시킨다. */
    public boolean mouseReleased(int x, int y, int button) {
        if (layout == null) return false;
        UiNode downNode = pressNode, downButton = pressButton;
        pressNode = null;
        pressButton = null;
        LayoutBox hit = hitTest(x, y);
        UiNode up = hit == null ? null : hit.node;
        boolean handled = false;
        if (up != null) handled |= dispatch(UiEvent.Type.RELEASE, up, x, y, button);
        if (downNode != null && up == downNode) handled |= dispatch(UiEvent.Type.CLICK, up, x, y, button);
        if (downButton != null && buttonNode(hit) == downButton) fireAction(downButton);
        return downButton != null || handled;
    }

    private void fireAction(UiNode buttonNode) {
        LayoutBox box = boxes.get(buttonNode);
        String action = box == null ? null : box.style.get("onclick");
        if (action == null || action.isBlank()) return;
        action = action.trim();
        if (UiActions.dispatch(new UiActions.Context(this, element(buttonNode), action))) return;
        if (actionHandler != null) actionHandler.onAction(action, box);
        else addRuntimeWarning("등록되지 않은 액션 '" + action + "'");
    }
}
