package dev.magyul.betteruiux.core.doc;

import dev.magyul.betteruiux.api.Cursor;
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
    /** 실행 중 생긴 경고와 마지막으로 발생한 시각(ms). 일정 시간이 지나면 사라진다(같은 경고가 또 나면 다시 보인다). */
    private final Map<String, Long> runtimeWarnings = new LinkedHashMap<>();
    private static final long WARNING_TTL_MS = 6000;
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

    /** 글자 입력 칸이 포커스를 받거나 잃을 때(IME/글자 입력을 켜고 끄기 위해) 백엔드에 알린다. (x,y,w,h)는 입력 칸의 화면 사각형. */
    @FunctionalInterface
    public interface TextInputHandler {
        void textInput(boolean active, int x, int y, int w, int h);
    }

    private UiNode forcedNode;
    private boolean forcedHover, forcedActive, forcedFocus;

    /**
     * 요소의 :hover / :active 모양을 강제로 보여 준다(에디터의 상태 미리보기). {@code sourceIndex} 가 음수이거나 둘 다 false 면 해제.
     */
    public void forcePseudo(int sourceIndex, boolean hover, boolean active) { forcePseudo(sourceIndex, hover, active, false); }

    /** :hover / :active / :focus 를 강제로 보여 준다. */
    public void forcePseudo(int sourceIndex, boolean hover, boolean active, boolean focus) {
        forcedHover = hover;
        forcedActive = active;
        forcedFocus = focus;
        forcedNode = sourceIndex < 0 || !(hover || active || focus) ? null : findBySource(root, sourceIndex);
    }

    private static UiNode findBySource(UiNode n, int index) {
        if (n.sourceIndex() == index) return n;
        for (UiNode c : n.children()) {
            UiNode r = findBySource(c, index);
            if (r != null) return r;
        }
        return null;
    }

    private UiNode focusNode;       // 키보드 포커스를 가진 위젯
    private LayoutBox pressWidget;  // 마우스를 누른 위젯(드래그/뗌을 받는다)
    private UiNode popupNode;       // 팝업(드롭다운)이 열린 위젯
    private long hoverSince;
    private TextInputHandler textInputHandler = (a, x, y, w, h) -> { };
    private String internalClipboard = "";
    private java.util.function.Supplier<String> clipboardGet = () -> internalClipboard;
    private java.util.function.Consumer<String> clipboardSet = s -> internalClipboard = s;

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
        @Override public boolean hovered(UiNode n) {
            UiNode h = forcedNode != null && (forcedHover || forcedActive) ? forcedNode : hoverNode;
            return h != null && n.isAncestorOf(h);
        }

        @Override public boolean pressed(UiNode n) {
            UiNode p = forcedNode != null && forcedActive ? forcedNode : pressNode;
            return p != null && n.isAncestorOf(p) && hovered(n);
        }

        @Override public boolean focused(UiNode n) {
            UiNode f = forcedNode != null && forcedFocus ? forcedNode : focusNode;
            return f != null && n == f;
        }

        @Override public boolean focusWithin(UiNode n) {
            UiNode f = forcedNode != null && forcedFocus ? forcedNode : focusNode;
            return f != null && n.isAncestorOf(f);
        }

        @Override public StyleMap style(LayoutBox b) {
            StyleMap s = b.effective(this);
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
        this.layoutEngine.setImageSizer(imageSizer);
        this.layoutEngine.setStyleAnimator((box, s) -> animationsEnabled ? anim.applyLayout(box.node, s, clock.nowMillis()) : s);
    }

    /** 레이아웃 속성(width, margin …)이 아직 움직이는 중이면 매 프레임 다시 배치한다. */
    private boolean layoutAnimating;

    private java.util.function.Function<String, int[]> imageSizer = src -> null;

    /**
     * 이미지의 원래 크기를 알려 주는 함수를 정한다(외부 이미지처럼 크기를 문서가 모를 때). width/height 가 auto 인 {@code <image>} 가 이 크기로 놓인다.
     * 아직 불러오는 중이면 null 을 돌려주고, 준비되면 {@link #requestRelayout()} 을 불러 다시 배치하게 한다.
     */
    public void setImageSizer(java.util.function.Function<String, int[]> sizer) {
        this.imageSizer = sizer == null ? src -> null : sizer;
        layoutEngine.setImageSizer(imageSizer);
        dirty = true;
    }

    /** 다음 프레임에 레이아웃을 다시 계산하게 한다(백그라운드에서 이미지가 준비되었을 때 등). 렌더 스레드에서 불러야 한다. */
    public void requestRelayout() { dirty = true; }

    private UiWidget widgetFor(UiNode node) {
        if (BuiltinWidgets.has(node.tag())) {
            if (node.userData() instanceof UiWidget w) return w;
            UiWidget w = BuiltinWidgets.create(element(node), this, textMetrics);
            node.setUserData(w);
            return w;
        }
        return UiWidgets.has(node.tag()) ? UiWidgets.instance(element(node)) : null;
    }

    // ---- 백엔드 연결(클립보드, 글자 입력) ----

    public void setClipboardHandler(java.util.function.Supplier<String> get, java.util.function.Consumer<String> set) {
        this.clipboardGet = get;
        this.clipboardSet = set;
    }

    public void setTextInputHandler(TextInputHandler h) { this.textInputHandler = h == null ? (a, x, y, w, ht) -> { } : h; }

    String clipboard() {
        String s = clipboardGet.get();
        return s == null ? "" : s;
    }

    void setClipboard(String s) { clipboardSet.accept(s); }

    void textInput(boolean active, int x, int y, int w, int h) { textInputHandler.textInput(active, x, y, w, h); }

    int viewHeight() { return viewH; }

    // ---- CSS 변수 ----

    /**
     * 문서 전체에서 쓰는 CSS 변수({@code --accent} 같은 이름, 접두사 {@code --} 는 생략해도 된다)를 바꾼다.
     * 스타일시트의 {@code var(--accent)} 를 쓰는 모든 요소에 다음 프레임부터 반영된다. {@code value} 가 null 이면 지운다.
     */
    public void setVariable(String name, String value) {
        element(root).setStyle(varName(name), value);
    }

    /** 루트에 지정된 CSS 변수 값(없으면 null). 레이아웃 전이면 null. */
    public String variable(String name) {
        return layout == null ? null : layout.style.get(varName(name));
    }

    private static String varName(String name) { return name.startsWith("--") ? name : "--" + name; }

    // ---- 기본 접근자 ----

    public UiNode root() { return root; }

    public LayoutBox layout() { return layout; }

    /** 로드/스타일/배경/실행 중 생긴 경고(문서는 정상 표시됨). */
    public List<String> warnings() {
        List<String> all = new ArrayList<>(warnings);
        all.addAll(background.warnings());
        long now = clock.nowMillis();
        runtimeWarnings.values().removeIf(t -> now - t > WARNING_TTL_MS);
        all.addAll(runtimeWarnings.keySet());
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

    private void addRuntimeWarning(String w) { runtimeWarnings.put(w, clock.nowMillis()); }

    /** 실행 중 생긴 경고를 문서 경고 목록(오류 오버레이)에 추가한다. */
    public void warn(String message) { addRuntimeWarning(message); }

    private java.util.function.BiConsumer<UiElement, String> scriptDispatcher;

    /** {@code onClick="script:fn"} 처럼 {@code script:} 로 시작하는 액션을 받을 곳(스크립트 호스트가 등록한다). */
    public void setScriptDispatcher(java.util.function.BiConsumer<UiElement, String> d) { this.scriptDispatcher = d; }

    private java.util.function.BiConsumer<UiElement, String> serverDispatcher;

    /** {@code onClick="server:액션"} 처럼 {@code server:} 로 시작하는 액션을 받을 곳(서버가 연 화면에만 등록된다). */
    public void setServerDispatcher(java.util.function.BiConsumer<UiElement, String> d) { this.serverDispatcher = d; }

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
        anim.beginLayoutPass();
        this.layout = layoutEngine.layout(root, viewW, viewH);
        layoutAnimating = animationsEnabled && anim.layoutActive();
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

    /** 요소의 스타일 값이 어디서 왔는지(inline / 어느 규칙 / 속성 / 기본값 / 상속) 설명한다. 에디터 툴팁용. */
    public String explainStyle(LayoutBox box, String prop) { return resolver.explain(box, prop); }

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
        if (layoutAnimating) dirty = true;
        applyDirty();
        updateHover();
        applyDirty();
        painter.paint(layout, gfx, state);
        paintPopupAndTooltip(gfx);
        gfx.requestCursor(cursorAtHover());
    }

    private void paintPopupAndTooltip(UiGfx gfx) {
        if (popupNode != null) {
            LayoutBox pb = boxes.get(popupNode);
            if (pb == null || pb.widget == null || !pb.widget.popupOpen()) {
                popupNode = null;
            } else {
                pb.widget.paintPopup(gfx, new UiWidget.Frame(mouseX, mouseY, true, frameNow, element(popupNode)));
                return;
            }
        }
        if (hoverNode == null || frameNow - hoverSince < TOOLTIP_DELAY_MS) return;
        String tip = null;
        for (UiNode n = hoverNode; n != null && tip == null; n = n.parent()) {
            String t = n.attr("tooltip");
            if (t != null && !t.isBlank()) tip = interpolate(t);
        }
        if (tip == null) return;
        String[] lines = tip.split("\\n", -1);
        int w = 0;
        for (String l : lines) w = Math.max(w, textMetrics.width(l));
        int lh = textMetrics.lineHeight(), h = lines.length * lh;
        int x = Math.max(2, Math.min(mouseX + 10, viewW - w - 10)), y = Math.max(2, Math.min(mouseY - h - 10, viewH - h - 8));
        if (mouseY - h - 10 < 2) y = Math.min(mouseY + 12, viewH - h - 8);
        gfx.fillRect(x - 4, y - 4, w + 8, h + 8, 0xF0300070);
        gfx.fillRect(x - 3, y - 3, w + 6, h + 6, 0xF0100010);
        for (int i = 0; i < lines.length; i++) gfx.drawText(lines[i], x, y + i * lh, 0xFFFFFFFF, true);
    }

    private static final long TOOLTIP_DELAY_MS = 450;

    /** 마우스 아래 요소의 {@code cursor} 스타일(상속). 없으면 활성 버튼 위에서는 손가락, 그 외에는 기본. */
    private Cursor cursorAtHover() {
        if (hoverNode == null) return Cursor.DEFAULT;
        LayoutBox b = boxes.get(hoverNode);
        Cursor c = b == null ? null : Cursor.parse(b.style.get("cursor"));
        if (c != null) return c;
        for (LayoutBox w = b; w != null; w = w.parent) {
            Cursor wc = w.widget == null ? null : w.widget.cursor();
            if (wc != null) return wc;
        }
        return hoverButton != null ? Cursor.POINTER : Cursor.DEFAULT;
    }

    /** 이 박스의 내용이 잘리지 않는 최소 {너비, 높이}. */
    public int[] minimumSize(LayoutBox b) { return layoutEngine.minimumSize(b); }

    private void updateHover() {
        LayoutBox hit = hitTest(mouseX, mouseY);
        UiNode now = hit == null ? null : hit.node;
        hoverButton = buttonNode(hit);
        if (now == hoverNode) return;
        UiNode old = hoverNode;
        hoverNode = now;
        hoverSince = frameNow;
        if (old != null) dispatch(UiEvent.Type.HOVER_LEAVE, old, mouseX, mouseY, 0);
        if (now != null) dispatch(UiEvent.Type.HOVER_ENTER, now, mouseX, mouseY, 0);
    }

    /** UiAnimation.play() 가 호출한다. */
    AnimationEngine.Imperative startAnimation(UiElement el, java.util.Map<String, String> from,
                                              java.util.Map<String, String> to, double duration, double delay,
                                              Easing easing, Runnable onFinish) {
        long now = clock.nowMillis();
        frameNow = Math.max(frameNow, now);
        dirty = true;                                  // 레이아웃 속성이 들어 있을 수 있으니 다음 프레임에 레이아웃 패스가 보게 한다
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
        java.util.List<LayoutBox> order = b.paintOrder();
        for (int i = order.size() - 1; i >= 0; i--) {
            if (order.get(i).synthetic) continue;                 // ::before/::after 상자는 클릭 대상이 아니다(주인 요소가 받는다)
            LayoutBox r = hit(order.get(i), x, y);
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

    /** 포커스를 옮긴다(null 이면 해제). 이전/새 위젯에 {@link UiWidget#focusChanged} 를 알린다. */
    public void focus(UiElement el) {
        UiNode next = el == null ? null : el.node();
        if (next == focusNode) return;
        UiNode old = focusNode;
        focusNode = next;
        notifyFocus(old, false);
        notifyFocus(next, true);
    }

    private void notifyFocus(UiNode n, boolean focused) {
        LayoutBox b = n == null ? null : boxes.get(n);
        if (b != null && b.widget != null) b.widget.focusChanged(element(n), focused);
    }

    boolean isFocused(UiElement el) { return focusNode != null && focusNode == el.node(); }

    /** 지금 포커스를 가진 요소(없으면 null). */
    public UiElement focusedElement() { return focusNode == null ? null : element(focusNode); }

    /** 글자 입력 칸에 포커스가 있으면 글자 커서의 화면 위치 {x, y, 높이}(IME 조합창 위치), 아니면 null. */
    public int[] caretScreen() {
        LayoutBox b = focusNode == null ? null : boxes.get(focusNode);
        return b != null && b.widget instanceof BuiltinWidgets.TextField t ? t.caret() : null;
    }

    void openPopup(UiElement el) { popupNode = el.node(); }

    private void closePopupNow() {
        LayoutBox b = popupNode == null ? null : boxes.get(popupNode);
        if (b != null && b.widget != null) b.widget.closePopup();
        popupNode = null;
    }

    /** 맨 위에서 입력을 막는 {@code <dialog>} (없으면 null). */
    private LayoutBox modalDialog() { return layout == null ? null : findModal(layout); }

    private LayoutBox findModal(LayoutBox b) {
        LayoutBox found = null;
        for (LayoutBox c : b.children) {
            LayoutBox r = findModal(c);
            if (r != null) found = r;
        }
        if (found != null) return found;
        return b.tag().equals("dialog") && b.visible() && !"false".equalsIgnoreCase(b.node.attr("modal")) ? b : null;
    }

    private static boolean isInside(LayoutBox b, LayoutBox ancestor) {
        for (LayoutBox x = b; x != null; x = x.parent) if (x == ancestor) return true;
        return false;
    }

    /**
     * 마우스 버튼 번호는 DOM 관례를 따른다: 0=좌, 1=가운데, 2=우. 백엔드가 게임의 번호를 이 체계로 변환해서 넘긴다.
     *
     * @return 입력을 소비했으면 true (버튼 위 좌클릭이거나 핸들러가 처리함).
     */
    public boolean mousePressed(int x, int y, int button) {
        if (layout == null) return false;
        if (popupNode != null) {                                   // 열린 드롭다운이 먼저 받는다(어디를 눌러도 닫힌다)
            LayoutBox pb = boxes.get(popupNode);
            if (pb != null && pb.widget != null) pb.widget.popupPressed(element(popupNode), x, y);
            closePopupNow();
            return true;
        }
        LayoutBox hit = hitTest(x, y);
        LayoutBox modal = modalDialog();
        if (modal != null && (hit == null || !isInside(hit, modal))) return true;   // 대화상자 밖은 막는다
        pressNode = hit == null ? null : hit.node;
        pressButton = button == 0 ? buttonNode(hit) : null;
        boolean handled = pressNode != null && dispatch(UiEvent.Type.PRESS, pressNode, x, y, button);
        pressWidget = null;
        LayoutBox focusTarget = null;
        for (LayoutBox b = hit; b != null; b = b.parent) {
            if (b.widget == null) continue;
            if (focusTarget == null && b.widget.focusable()) focusTarget = b;
            if (b.enabled() && b.widget.mousePressed(element(b.node), x, y, button)) {
                pressWidget = b;
                break;
            }
        }
        if (pressWidget != null && pressWidget.widget.focusable()) focusTarget = pressWidget;
        focus(focusTarget == null ? null : element(focusTarget.node));
        applyDirty();
        return pressButton != null || handled || pressWidget != null;
    }

    public boolean mouseDragged(int x, int y) {
        if (pressWidget == null) return false;
        pressWidget.widget.mouseDragged(element(pressWidget.node), x, y);
        applyDirty();                                           // 같은 프레임의 다음 입력이 새 레이아웃을 보도록
        return true;
    }

    /** 휠(양수=위로). 마우스 아래에서 안쪽부터 바깥쪽으로 올라가며 처음 처리하는 위젯(예: scroll)이 받는다. */
    public boolean mouseScrolled(int x, int y, double amount) {
        if (layout == null || popupNode != null) return false;
        for (LayoutBox b = hitTest(x, y); b != null; b = b.parent) {
            if (b.widget != null && b.enabled() && b.widget.mouseScrolled(element(b.node), x, y, amount)) {
                applyDirty();
                return true;
            }
        }
        return false;
    }

    /** 포커스가 있는 위젯에 키를 준다. Tab/Shift+Tab 은 포커스 이동, Esc 는 포커스 해제. @return 처리했으면 true */
    public boolean keyPressed(UiKey key, boolean ctrl, boolean shift) {
        if (layout == null) return false;
        if (popupNode != null) {
            LayoutBox pb = boxes.get(popupNode);
            boolean used = pb != null && pb.widget != null && pb.widget.popupKey(element(popupNode), key);
            if (!used && key == UiKey.ESCAPE) closePopupNow();
            if (pb == null || pb.widget == null || !pb.widget.popupOpen()) popupNode = null;
            applyDirty();
            return true;
        }
        LayoutBox fb = focusNode == null ? null : boxes.get(focusNode);
        if (fb != null && fb.widget != null && fb.widget.keyPressed(element(focusNode), key, ctrl, shift)) {
            applyDirty();
            return true;
        }
        if (fb != null && fb.widget == null && fb.tag().equals("button") && (key == UiKey.ENTER || key == UiKey.SPACE)) {
            fireAction(focusNode);                              // 포커스가 있는 버튼은 Enter/Space 로 누른다
            applyDirty();
            return true;
        }
        if (key == UiKey.TAB) return moveFocus(shift ? -1 : 1);
        if (key == UiKey.ESCAPE && focusNode != null) {
            focus(null);
            return true;
        }
        return false;
    }

    public boolean charTyped(char c) {
        LayoutBox fb = focusNode == null ? null : boxes.get(focusNode);
        boolean used = fb != null && fb.widget != null && fb.widget.charTyped(element(focusNode), c);
        if (used) applyDirty();
        return used;
    }

    private boolean moveFocus(int dir) {
        List<LayoutBox> order = new ArrayList<>();
        collectFocusable(layout, order);
        if (order.isEmpty()) return false;
        int cur = -1;
        for (int i = 0; i < order.size(); i++) if (order.get(i).node == focusNode) cur = i;
        int next = cur < 0 ? (dir > 0 ? 0 : order.size() - 1) : (cur + dir + order.size()) % order.size();
        focus(element(order.get(next).node));
        return true;
    }

    private void collectFocusable(LayoutBox b, List<LayoutBox> out) {
        if (!b.visible()) return;
        if (b.widget != null && b.widget.focusable() && b.enabled()) out.add(b);
        else if (b.widget == null && b.tag().equals("button") && b.enabled()) out.add(b);   // 버튼도 Tab 으로 고를 수 있다
        for (LayoutBox c : b.children) collectFocusable(c, out);
    }

    /** 누른 버튼 위에서 뗐을 때 {@code onClick} 액션을, 같은 요소에서 뗐을 때 CLICK 이벤트를 발생시킨다. */
    public boolean mouseReleased(int x, int y, int button) {
        if (layout == null) return false;
        UiNode downNode = pressNode, downButton = pressButton;
        LayoutBox widgetBox = pressWidget;
        pressNode = null;
        pressButton = null;
        pressWidget = null;
        LayoutBox hit = hitTest(x, y);
        UiNode up = hit == null ? null : hit.node;
        boolean handled = false;
        if (up != null) handled |= dispatch(UiEvent.Type.RELEASE, up, x, y, button);
        if (downNode != null && up == downNode) handled |= dispatch(UiEvent.Type.CLICK, up, x, y, button);
        if (downButton != null && buttonNode(hit) == downButton) fireAction(downButton);
        if (widgetBox != null) {
            widgetBox.widget.mouseReleased(element(widgetBox.node), x, y, button);
            applyDirty();
            handled = true;
        }
        return downButton != null || handled;
    }

    private void fireAction(UiNode buttonNode) {
        LayoutBox box = boxes.get(buttonNode);
        String action = box == null ? null : box.style.get("onclick");
        if (action == null || action.isBlank()) return;
        fireAction(element(buttonNode), action.trim());
    }

    /** 요소의 값이 사용자 조작으로 바뀌었다: CHANGE 이벤트 + {@code onChange} 액션. */
    void fireChange(UiElement el) {
        dispatch(UiEvent.Type.CHANGE, el.node(), 0, 0, 0);
        String action = el.attr("onChange");
        if (action == null) action = el.attr("onchange");
        if (action != null && !action.isBlank()) fireAction(el, action.trim());
    }

    void fireAction(UiElement el, String action) {
        if (action.startsWith("script:")) {
            if (scriptDispatcher != null) scriptDispatcher.accept(el, action.substring(7).trim());
            else addRuntimeWarning("스크립트가 꺼져 있습니다: '" + action + "'");
            return;
        }
        if (action.startsWith("server:")) {
            if (serverDispatcher != null) serverDispatcher.accept(el, action.substring(7).trim());
            else addRuntimeWarning("서버 동작은 서버가 연 화면에서만 쓸 수 있습니다: '" + action + "'");
            return;
        }
        if (UiActions.dispatch(new UiActions.Context(this, el, action))) return;
        LayoutBox box = boxes.get(el.node());
        if (actionHandler != null) actionHandler.onAction(action, box);
        else addRuntimeWarning("등록되지 않은 액션 '" + action + "'");
    }
}
