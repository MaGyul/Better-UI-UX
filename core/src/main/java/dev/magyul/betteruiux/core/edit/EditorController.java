package dev.magyul.betteruiux.core.edit;

import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.api.UiGfx;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.edit.Geometry.Handle;
import dev.magyul.betteruiux.core.edit.Geometry.Rect;
import dev.magyul.betteruiux.core.edit.XmlSource.Element;
import dev.magyul.betteruiux.core.layout.Insets;
import dev.magyul.betteruiux.core.layout.LayoutBox;
import dev.magyul.betteruiux.core.layout.LayoutEngine;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 인게임 에디터의 두뇌. 마인크래프트를 모르고, 입력(마우스/키)을 받아 XML 텍스트를 고치고, UiGfx 로 에디터 화면을 그린다.
 *
 * <p>모든 편집은 "원본 XML 텍스트 패치 → 다시 로드" 이다({@link EditSession}). 그래서 주석/들여쓰기가 보존되고,
 * Undo/Redo 가 텍스트 되돌리기이며, 핫리로드와 같은 경로로 화면이 갱신된다.
 *
 * <p>스크래치처럼 다룬다: 왼쪽 <b>블록 팔레트</b>에서 요소를 끌어 캔버스나 구조 트리에 놓으면 추가되고,
 * 이미 있는 요소를 구조 트리 안에서 끌어 옮기거나, <b>팔레트 쪽으로 끌어다 놓으면 삭제</b>된다.
 * 구조 트리는 접고 펼 수 있다.
 *
 * <p>화면 이동(팬): 문서는 실제 화면 크기로 배치된 채 그대로 두고, 보는 위치만 옮긴다. 선택한 요소가 양옆 패널에 가려지면
 * 가운데로 자동 이동하고, 마우스 휠 / 가운데 버튼 드래그로도 옮길 수 있다. 요소를 끌어 옮기는 동안에는 양옆 패널이
 * 숨겨져 화면 전체가 보이며, 패널은 가장자리 탭으로 직접 접을 수도 있다.
 */
public final class EditorController {
    // ---- 모양 (테스트가 위치를 계산할 수 있도록 package-private) ----
    static final int TOOLBAR_H = 16, LEFT_W = 140, RIGHT_W = 142, ROW_H = 11;
    static final int PALETTE_H = 62, OUTLINER_HEADER_H = 12;
    /** 속성 패널에서 값 칸이 시작하는 x 오프셋(패널 왼쪽 기준). */
    static final int FIELD_OFFSET = 60;
    static final int TAB_W = 10, TAB_H = 26;
    static final List<String> PALETTE_TAGS = List.of("label", "button", "panel", "column", "row", "image", "spacer");
    static final Map<String, String> PALETTE_LABELS = Map.of("label", "라벨", "button", "버튼", "panel", "패널",
            "column", "세로", "row", "가로", "image", "이미지", "spacer", "여백");
    /** 속성 이름 → 화면에 보이는 한글 이름. */
    private static final Map<String, String> PROP_LABELS = Map.ofEntries(
            Map.entry("tag", "종류"), Map.entry("text", "글자"), Map.entry("onClick", "클릭"), Map.entry("src", "이미지"),
            Map.entry("width", "너비"), Map.entry("height", "높이"), Map.entry("margin", "바깥여백"),
            Map.entry("padding", "안쪽여백"), Map.entry("anchor", "기준점"), Map.entry("gap", "간격"),
            Map.entry("justify", "정렬"), Map.entry("align", "교차정렬"), Map.entry("background", "배경"),
            Map.entry("color", "글자색"), Map.entry("border", "테두리"), Map.entry("border-color", "테두리색"),
            Map.entry("opacity", "투명도"), Map.entry("use", "배경효과"));
    private static final int HANDLE_R = 3, MIN_SIZE = 4, SNAP_THRESHOLD = 4, DRAG_START = 3, GRID = 8, WHEEL_STEP = 16;
    private static final int C_PANEL = 0xF2161820, C_BTN = 0xFF3A3F55, C_BTN_OFF = 0xFF262838, C_SEL = 0xFFFFFF55,
            C_HOVER = 0x9055FFFF, C_GUIDE = 0xFFFF55FF, C_TEXT = 0xFFFFFFFF, C_DIM = 0xFF9090A0, C_ERR = 0xFFFF6060,
            C_OK = 0xFF80FF80, C_ROW_SEL = 0xFF3A4A80, C_ROW_HOVER = 0xFF2A3050, C_FIELD = 0xFF101018,
            C_DROP_OK = 0xFF55FF77, C_DROP_BAD = 0xFFFF5555, C_TRASH = 0x70FF3030, C_TAB = 0xFF2E3350;

    private static final Set<String> ATTR_KEYS = Set.of("id", "class", "onClick", "src", "text");
    private static final Set<String> CONTAINERS = Set.of("screen", "panel", "row", "column", "stack", "fragment");

    private record Region(Rect rect, Runnable action) {}

    private record PropRow(String label, String key, String value, boolean editable) {}

    private record TreeRow(LayoutBox box, int depth, boolean hasKids, boolean collapsed, String key) {}

    /** 놓을 자리. {@code refIndex} 는 그 앞에 넣을 형제의 요소 번호(-1 이면 맨 뒤). 사각형은 문서 좌표(트리/휴지통은 화면 좌표). */
    private record Drop(int parentIndex, int refIndex, Rect highlight, Rect line, boolean valid, boolean trash,
                        boolean onCanvas) {}

    /** 팔레트/트리에서 시작한 끌어놓기. 좌표는 화면 좌표. */
    private static final class Dnd {
        final boolean fromPalette;
        final String tag;
        final int index;
        final int startX, startY;
        boolean started;

        Dnd(boolean fromPalette, String tag, int index, int startX, int startY) {
            this.fromPalette = fromPalette;
            this.tag = tag;
            this.index = index;
            this.startX = startX;
            this.startY = startY;
            this.started = fromPalette;     // 팔레트 블록은 누르는 즉시 들린다
        }
    }

    /** 캔버스에서 요소를 직접 끄는 중(이동/크기/재정렬). 좌표는 문서 좌표. 매 프레임 기준 텍스트에 처음부터 적용한다. */
    private static final class Drag {
        Handle handle;
        int startMx, startMy;
        boolean started;
        int index;
        Rect startRect;
        boolean parentFlow, horizontalFlow;
        int parentIndex;
        String anchor;
        Insets margin;
        Rect parentContent;
        int curX, curY;
        List<Integer> xTargets = List.of(), yTargets = List.of();
        List<LayoutBox> others = List.of();
        final Map<String, Boolean> definedElsewhere = new HashMap<>();
        int resultIndex;
    }

    private final EditorHost host;
    private final TextMetrics tm;
    private final EditSession session;
    private UiDocument doc;
    private int viewW, viewH;

    private int selected = -1;
    private int hoverIndex = -1;
    private int mouseX = -1, mouseY = -1;
    private boolean panelsVisible = true;
    private boolean leftCollapsed, rightCollapsed;
    private boolean grid;
    private int outlinerScroll;
    private final Set<String> collapsed = new HashSet<>();
    private int lastRevealed = -2;
    private int lastPanned = -2;
    private int panX, panY;
    private boolean panning;
    private int panAnchorX, panAnchorY;
    private Drag drag;
    private Dnd dnd;
    private List<Geometry.Guide> guides = List.of();

    private String editKey;
    private final StringBuilder editBuffer = new StringBuilder();
    private int editCursor;
    private Rect editRect;

    private String status = "";
    private boolean statusError;
    private final List<Region> regions = new ArrayList<>();

    public EditorController(EditorHost host, TextMetrics tm, String text, int viewW, int viewH) throws UiParseException {
        this.host = host;
        this.tm = tm;
        this.session = new EditSession(text);
        this.viewW = viewW;
        this.viewH = viewH;
        this.doc = host.load(text);
        this.doc.setAnimationsEnabled(false);
        this.doc.layout(viewW, viewH);
    }

    // ---- 상태 조회 ----

    /** 지금 화면에 보여줄 문서(편집할 때마다 새로 만들어진다). */
    public UiDocument document() { return doc; }

    public String text() { return session.text(); }

    public boolean isDirty() { return session.isDirty(); }

    public int selectedIndex() { return selected; }

    public String status() { return status; }

    public boolean isEditingField() { return editKey != null; }

    public boolean isGridSnap() { return grid; }

    public void setView(int w, int h) {
        this.viewW = w;
        this.viewH = h;
        doc.layout(w, h);
    }

    public void select(int index) {
        selected = index;
        commitFieldQuietly();
    }

    /** 에디터를 닫을 때 호출한다(편집 중인 칸을 취소하고 글자 입력을 끈다). */
    public void close() {
        cancelField();
        dnd = null;
        drag = null;
        panning = false;
    }

    private void setStatus(String s, boolean error) {
        status = s;
        statusError = error;
    }

    // ---- 패널 보이기 상태 ----

    private boolean dragStarted() { return drag != null && drag.started; }

    /** 왼쪽 패널(블록+구조): 접었거나, 캔버스에서 요소를 끄는 중이면 숨긴다. */
    private boolean leftVisible() { return panelsVisible && !leftCollapsed && !dragStarted(); }

    /** 오른쪽 패널(속성): 접었거나, 무엇이든 끄는 중이면 숨긴다. */
    private boolean rightVisible() { return panelsVisible && !rightCollapsed && !dragStarted() && dnd == null; }

    /** 팬 계산용: 끄는 중이 아닐 때의 "가운데 작업 영역"(패널이 접혀 있으면 그만큼 넓다). */
    private Rect centerView() {
        int left = panelsVisible && !leftCollapsed ? LEFT_W : 0, right = panelsVisible && !rightCollapsed ? RIGHT_W : 0;
        int top = panelsVisible ? TOOLBAR_H : 0;
        return new Rect(left, top, Math.max(1, viewW - left - right), Math.max(1, viewH - top));
    }

    // ---- 문서 다시 만들기 / 편집 적용 ----

    private boolean reload() {
        try {
            UiDocument d = host.load(session.text());
            d.setAnimationsEnabled(false);
            d.layout(viewW, viewH);
            doc = d;
            int count = XmlSource.parse(session.text()).all().size();
            if (selected >= count) selected = -1;
            return true;
        } catch (UiParseException e) {
            setStatus("XML 오류 (줄 " + e.line() + ")", true);
            return false;
        }
    }

    /** 텍스트 패치를 Undo 가능한 한 단계로 적용하고 화면을 갱신한다. 실패하면 되돌린다. */
    private boolean applyEdit(Function<XmlSource, String> patch) {
        if (!session.edit(patch)) return false;
        if (!reload()) {
            session.undo();
            reload();
            return false;
        }
        setStatus("", false);
        return true;
    }

    // ---- 박스 조회 (문서 좌표) ----

    private LayoutBox selectedBox() { return selected < 0 ? null : boxByIndex(doc.layout(), selected); }

    private static LayoutBox boxByIndex(LayoutBox b, int idx) {
        if (b.node.sourceIndex() == idx) return b;
        for (LayoutBox c : b.children) {
            LayoutBox r = boxByIndex(c, idx);
            if (r != null) return r;
        }
        return null;
    }

    /** 문서 좌표 (x,y) 아래의 요소 중 원본 XML 에 실제로 있는 가장 깊은 것(확장으로 생긴 요소는 조상으로 올라간다). */
    private LayoutBox hitSource(int x, int y) {
        LayoutBox hit = doc.hitTest(x, y);
        while (hit != null && hit.node.sourceIndex() < 0) hit = hit.parent;
        return hit;
    }

    private static Rect rectOf(LayoutBox b) { return new Rect(b.x, b.y, b.w, b.h); }

    private static boolean isFlow(LayoutBox parent) {
        return parent != null && (parent.tag().equals("row") || parent.tag().equals("column"));
    }

    private static Rect contentRect(LayoutBox parent) {
        Insets pad = Insets.parse(parent.style.get("padding"), Insets.ZERO);
        return new Rect(parent.x + pad.left(), parent.y + pad.top(),
                Math.max(0, parent.w - pad.horizontal()), Math.max(0, parent.h - pad.vertical()));
    }

    /** 원본에 실제로 있는 자식들(템플릿 확장으로 생긴 요소는 그 자식들을 대신 펼쳐서 포함하지 않는다). */
    private static List<LayoutBox> displayChildren(LayoutBox parent) {
        List<LayoutBox> out = new ArrayList<>();
        for (LayoutBox c : parent.children) {
            if (c.node.sourceIndex() >= 0) {
                if (c.visible()) out.add(c);
            } else {
                out.addAll(displayChildren(c));
            }
        }
        return out;
    }

    /** 원본에 있는 가장 가까운 조상. */
    private static LayoutBox displayParent(LayoutBox b) {
        LayoutBox p = b.parent;
        while (p != null && p.node.sourceIndex() < 0) p = p.parent;
        return p;
    }

    private static boolean isInsideOrSelf(LayoutBox box, int movingIndex) {
        for (LayoutBox b = box; b != null; b = b.parent) if (b.node.sourceIndex() == movingIndex) return true;
        return false;
    }

    // ================= 팔레트 / 트리 / 휴지통 기하 (화면 좌표) =================

    private Rect paletteRect() { return new Rect(0, TOOLBAR_H, LEFT_W, PALETTE_H); }

    /** 요소를 끌어다 놓으면 삭제되는 곳: 왼쪽 패널이 보이면 블록 칸, 숨겨져 있으면 왼쪽 아래 구석. */
    private Rect trashRect() {
        return leftVisible() ? paletteRect() : new Rect(0, viewH - 28, 110, 28);
    }

    /** i 번째 팔레트 블록의 사각형(3열). */
    static Rect paletteBlock(int i) {
        int w = (LEFT_W - 8 - 4) / 3, h = 13;
        return new Rect(4 + (i % 3) * (w + 2), TOOLBAR_H + 12 + (i / 3) * (h + 2), w, h);
    }

    static int rowsTop() { return TOOLBAR_H + PALETTE_H + OUTLINER_HEADER_H + 2; }

    private int visibleRows() { return Math.max(1, (viewH - rowsTop()) / ROW_H); }

    private Rect leftTab() { return new Rect(leftCollapsed ? 0 : LEFT_W, TOOLBAR_H + 2, TAB_W, TAB_H); }

    private Rect rightTab() { return new Rect(rightCollapsed ? viewW - TAB_W : viewW - RIGHT_W - TAB_W, TOOLBAR_H + 2, TAB_W, TAB_H); }

    private boolean overPanel(int x, int y) {
        if (!panelsVisible) return false;
        if (y < TOOLBAR_H) return true;
        if (leftVisible() && x < LEFT_W) return true;
        if (rightVisible() && x >= viewW - RIGHT_W) return true;
        return !dragStarted() && (leftTab().contains(x, y) || rightTab().contains(x, y));
    }

    // ================= 입력: 마우스 =================

    public boolean mouseMoved(int x, int y) {
        mouseX = x;
        mouseY = y;
        return true;
    }

    public boolean mousePressed(int x, int y, int button) {
        mouseX = x;
        mouseY = y;
        if (button == 1) {                                  // 가운데 버튼: 화면 이동
            panning = true;
            panAnchorX = x - panX;
            panAnchorY = y - panY;
            return true;
        }
        if (button != 0) return true;
        refreshRegions();
        if (editKey != null && (editRect == null || !editRect.contains(x, y))) commitField();
        refreshRegions();
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (r.rect().contains(x, y)) {
                if (r.action() != null) r.action().run();
                return true;
            }
        }
        int cx = x - panX, cy = y - panY;                   // 이후는 문서 좌표
        LayoutBox sel = selectedBox();
        if (sel != null && sel.parent != null) {
            Handle h = handleAt(sel, cx, cy);
            if (h != null) {
                prepareDrag(sel, h, cx, cy);
                return true;
            }
            if (sel.contains(cx, cy)) {
                prepareDrag(sel, Handle.BODY, cx, cy);
                return true;
            }
        }
        LayoutBox hit = hitSource(cx, cy);
        if (hit != null) {
            selected = hit.node.sourceIndex();
            if (hit.parent != null) prepareDrag(hit, Handle.BODY, cx, cy);
        } else {
            selected = -1;
        }
        return true;
    }

    public boolean mouseDragged(int x, int y) {
        mouseX = x;
        mouseY = y;
        if (panning) {
            panX = x - panAnchorX;
            panY = y - panAnchorY;
            return true;
        }
        if (dnd != null) {
            if (!dnd.started && Math.max(Math.abs(x - dnd.startX), Math.abs(y - dnd.startY)) >= DRAG_START) dnd.started = true;
            return true;
        }
        if (drag == null) return false;
        int cx = x - panX, cy = y - panY;
        int dx = cx - drag.startMx, dy = cy - drag.startMy;
        if (!drag.started) {
            if (Math.max(Math.abs(dx), Math.abs(dy)) < DRAG_START) return true;
            drag.started = true;
            session.beginTransient();
        }
        applyDrag(cx, cy);
        return true;
    }

    public boolean mouseReleased(int x, int y, int button) {
        mouseX = x;
        mouseY = y;
        if (panning) {
            panning = false;
            return true;
        }
        if (dnd != null) {
            Dnd d = dnd;
            dnd = null;
            finishDnd(d, x, y);
            return true;
        }
        if (drag == null) return true;
        Drag d = drag;
        boolean trash = d.started && d.handle == Handle.BODY && d.index > 0 && panelsVisible && trashRect().contains(x, y);
        drag = null;
        guides = List.of();
        if (d.started) {
            if (trash) {
                session.cancelTransient();
                reload();
                deleteIndex(d.index);
                return true;
            }
            session.commitTransient();
            selected = d.resultIndex;
            reload();
            setStatus("", false);
        }
        return true;
    }

    /** 휠: 왼쪽 구조 트리 위에서는 목록 스크롤, 캔버스에서는 화면 이동. */
    public boolean mouseScrolled(int x, int y, double amount) {
        if (leftVisible() && x < LEFT_W && y >= rowsTop() - 2) {
            outlinerScroll = Math.max(0, outlinerScroll - (int) Math.signum(amount) * 3);
            return true;
        }
        if (overPanel(x, y)) return false;
        panY += (int) Math.signum(amount) * WHEEL_STEP;
        return true;
    }

    /** 화면 이동을 처음 위치로 되돌린다. */
    public void resetView() {
        panX = 0;
        panY = 0;
    }

    private Handle handleAt(LayoutBox b, int x, int y) {
        Rect r = rectOf(b);
        for (Handle h : Handle.values()) {
            if (h == Handle.BODY) continue;
            int[] c = Geometry.handleCenter(r, h);
            if (Math.abs(x - c[0]) <= HANDLE_R + 1 && Math.abs(y - c[1]) <= HANDLE_R + 1) return h;
        }
        return null;
    }

    private void prepareDrag(LayoutBox b, Handle h, int mx, int my) {
        Drag d = new Drag();
        d.handle = h;
        d.startMx = mx;
        d.startMy = my;
        d.index = b.node.sourceIndex();
        d.resultIndex = d.index;
        d.startRect = rectOf(b);
        LayoutBox parent = b.parent;
        d.parentFlow = isFlow(parent);
        d.horizontalFlow = parent != null && parent.tag().equals("row");
        d.parentIndex = parent == null ? 0 : parent.node.sourceIndex();
        d.anchor = b.style.get("anchor");
        d.margin = Insets.parse(b.style.get("margin"), Insets.ZERO);
        d.parentContent = parent == null ? new Rect(0, 0, viewW, viewH) : contentRect(parent);
        d.curX = b.style.integer("x", 0);
        d.curY = b.style.integer("y", 0);
        for (String k : List.of("width", "height", "x", "y")) {
            d.definedElsewhere.put(k, b.style.get(k) != null && b.node.attr(k) == null);
        }
        List<LayoutBox> siblings = new ArrayList<>(parent == null ? List.of() : displayChildren(parent));
        siblings.remove(b);
        d.others = siblings;
        List<Rect> rects = new ArrayList<>();
        for (LayoutBox s : siblings) rects.add(rectOf(s));
        Rect parentRect = parent == null ? null : rectOf(parent);
        d.xTargets = Geometry.targets(rects, parentRect, true);
        d.yTargets = Geometry.targets(rects, parentRect, false);
        drag = d;
    }

    // ---- 캔버스 드래그 적용: 매번 기준 텍스트에서 다시 계산한다 ----

    private void applyDrag(int mx, int my) {
        Drag d = drag;
        int dx = mx - d.startMx, dy = my - d.startMy;
        boolean ok;
        if (d.handle == Handle.BODY && d.parentFlow) {
            ok = applyReorderDrag(d, mx, my);
        } else {
            Rect target = snapped(d, Geometry.drag(d.startRect, d.handle, dx, dy, MIN_SIZE));
            Map<String, String> props = Geometry.propsForRect(d.parentFlow, d.anchor, d.margin, d.parentContent,
                    d.startRect, target, d.curX, d.curY);
            ok = session.setTransient(src -> EditorOps.setProps(src.text(), d.index, props,
                    p -> d.definedElsewhere.getOrDefault(p, false)));
            d.resultIndex = d.index;
        }
        if (!ok || !reload()) {
            session.cancelTransient();
            session.beginTransient();
            reload();
        }
    }

    private boolean applyReorderDrag(Drag d, int mx, int my) {
        List<Rect> rects = new ArrayList<>();
        for (LayoutBox s : d.others) rects.add(rectOf(s));
        int k = Geometry.flowInsertIndex(rects, d.horizontalFlow, mx, my);
        int[] result = new int[1];
        boolean ok = session.setTransient(src -> {
            Element parentEl = src.element(d.parentIndex);
            int pos = k < d.others.size()
                    ? parentEl.children().indexOf(src.element(d.others.get(k).node.sourceIndex()))
                    : parentEl.children().size();
            EditorOps.Added r = EditorOps.move(src.text(), d.index, d.parentIndex, pos);
            result[0] = r.index();
            return r.text();
        });
        d.resultIndex = result[0];
        selected = d.resultIndex;
        return ok;
    }

    /** 격자 또는 다른 요소의 가장자리/중심선에 달라붙게 한 사각형. 달라붙은 선은 {@link #guides} 에 기록한다. */
    private Rect snapped(Drag d, Rect r) {
        List<Geometry.Guide> g = new ArrayList<>();
        Rect out;
        if (d.handle == Handle.BODY) {
            if (grid) {
                out = new Rect(Geometry.toGrid(r.x(), GRID), Geometry.toGrid(r.y(), GRID), r.w(), r.h());
            } else {
                Geometry.Snap s = Geometry.snap(r, d.xTargets, d.yTargets, SNAP_THRESHOLD);
                out = r.translate(s.dx(), s.dy());
                g.addAll(s.guides());
            }
        } else {
            boolean mL = d.handle == Handle.W || d.handle == Handle.NW || d.handle == Handle.SW;
            boolean mR = d.handle == Handle.E || d.handle == Handle.NE || d.handle == Handle.SE;
            boolean mT = d.handle == Handle.N || d.handle == Handle.NW || d.handle == Handle.NE;
            boolean mB = d.handle == Handle.S || d.handle == Handle.SW || d.handle == Handle.SE;
            int l = r.x(), rt = r.right(), t = r.y(), b = r.bottom();
            if (mL) l = edge(l, d.xTargets, true, g);
            if (mR) rt = edge(rt, d.xTargets, true, g);
            if (mT) t = edge(t, d.yTargets, false, g);
            if (mB) b = edge(b, d.yTargets, false, g);
            int w = Math.max(MIN_SIZE, rt - l), h = Math.max(MIN_SIZE, b - t);
            out = new Rect(mL ? rt - w : l, mT ? b - h : t, w, h);
        }
        guides = g;
        return out;
    }

    private int edge(int value, List<Integer> targets, boolean vertical, List<Geometry.Guide> guideOut) {
        if (grid) return Geometry.toGrid(value, GRID);
        int best = value, bestD = SNAP_THRESHOLD + 1;
        for (int t : targets) {
            int dist = Math.abs(t - value);
            if (dist < bestD) {
                bestD = dist;
                best = t;
            }
        }
        if (bestD <= SNAP_THRESHOLD) guideOut.add(new Geometry.Guide(vertical, best));
        return best;
    }

    // ================= 끌어놓기(팔레트 → 캔버스/트리, 트리 → 트리/캔버스, 휴지통으로 끌면 삭제) =================

    /**
     * 놓으려는 자리를 계산한다. 놓을 수 있는 곳이 없으면 null. (x,y)는 화면 좌표.
     * {@code movingIndex} 는 옮기는 기존 요소(팔레트 블록이면 -1).
     */
    private Drop dropTarget(int x, int y, int movingIndex) {
        if (panelsVisible && trashRect().contains(x, y) && (leftVisible() || movingIndex > 0)) {
            return new Drop(-1, -1, trashRect(), null, movingIndex > 0, true, false);
        }
        if (leftVisible() && x < LEFT_W && y >= rowsTop() - 1) return treeDrop(x, y, movingIndex);
        if (overPanel(x, y)) return null;
        return canvasDrop(x - panX, y - panY, movingIndex);
    }

    private Drop treeDrop(int x, int y, int movingIndex) {
        List<TreeRow> rows = treeRows();
        int i = (y - rowsTop() + 1) / ROW_H + outlinerScroll;
        if (i < 0 || i >= rows.size() || i - outlinerScroll >= visibleRows()) return null;
        LayoutBox rb = rows.get(i).box();
        Rect rowRect = new Rect(0, rowsTop() + (i - outlinerScroll) * ROW_H - 1, LEFT_W, ROW_H);
        double frac = (y - rowRect.y()) / (double) ROW_H;
        boolean container = CONTAINERS.contains(rb.tag());
        boolean root = rb.parent == null;
        LayoutBox parent;
        int ref;
        Rect line = null;
        Rect highlight = rowRect;
        if (!root && frac < 0.25) {                                   // 이 행 앞
            parent = displayParent(rb);
            ref = rb.node.sourceIndex();
            line = new Rect(0, rowRect.y(), LEFT_W, 2);
            highlight = null;
        } else if (!root && (frac > 0.75 || !container)) {            // 이 행 뒤
            parent = displayParent(rb);
            List<LayoutBox> sib = displayChildren(parent);
            int p = sib.indexOf(rb);
            ref = p + 1 < sib.size() ? sib.get(p + 1).node.sourceIndex() : -1;
            line = new Rect(0, rowRect.bottom() - 1, LEFT_W, 2);
            highlight = null;
        } else {                                                      // 이 요소 안
            parent = rb;
            ref = -1;
        }
        boolean valid = parent != null && (movingIndex < 0 || !isInsideOrSelf(parent, movingIndex));
        return new Drop(parent == null ? 0 : parent.node.sourceIndex(), ref, highlight, line, valid, false, false);
    }

    /** (x,y)는 문서 좌표. */
    private Drop canvasDrop(int x, int y, int movingIndex) {
        LayoutBox b = hitSource(x, y);
        while (b != null && !(CONTAINERS.contains(b.tag()) && (movingIndex < 0 || !isInsideOrSelf(b, movingIndex)))) b = b.parent;
        while (b != null && b.node.sourceIndex() < 0) b = b.parent;
        if (b == null) return null;
        int parentIndex = b.node.sourceIndex();
        if (isFlow(b)) {
            boolean horizontal = b.tag().equals("row");
            List<LayoutBox> kids = new ArrayList<>(displayChildren(b));
            if (movingIndex >= 0) kids.removeIf(k -> k.node.sourceIndex() == movingIndex);
            List<Rect> rects = new ArrayList<>();
            for (LayoutBox k : kids) rects.add(rectOf(k));
            int k = Geometry.flowInsertIndex(rects, horizontal, x, y);
            int ref = k < kids.size() ? kids.get(k).node.sourceIndex() : -1;
            Rect line = null;
            if (!kids.isEmpty()) {
                Rect at = k < kids.size() ? rects.get(k) : rects.get(rects.size() - 1);
                boolean after = k >= kids.size();
                if (horizontal) line = new Rect((after ? at.right() : at.x()) - 1, b.y, 2, b.h);
                else line = new Rect(b.x, (after ? at.bottom() : at.y()) - 1, b.w, 2);
            }
            return new Drop(parentIndex, ref, rectOf(b), line, true, false, true);
        }
        return new Drop(parentIndex, -1, rectOf(b), null, true, false, true);
    }

    private void finishDnd(Dnd d, int x, int y) {
        if (d.fromPalette && !d.started) return;
        if (d.fromPalette && Math.max(Math.abs(x - d.startX), Math.abs(y - d.startY)) < DRAG_START) {
            add(d.tag);                       // 끌지 않고 클릭만 하면 선택한 컨테이너에 추가
            return;
        }
        if (!d.fromPalette && !d.started) return;
        Drop drop = dropTarget(x, y, d.fromPalette ? -1 : d.index);
        if (drop == null) return;
        if (drop.trash()) {
            if (!d.fromPalette) deleteIndex(d.index);
            return;
        }
        if (!drop.valid()) {
            setStatus("여기에는 놓을 수 없습니다", true);
            return;
        }
        XmlSource src = session.source();
        Element parentEl = src.element(drop.parentIndex());
        int pos = drop.refIndex() >= 0
                ? parentEl.children().indexOf(src.element(drop.refIndex()))
                : parentEl.children().size();
        boolean place = drop.onCanvas();
        try {
            EditorOps.Added r = d.fromPalette
                    ? EditorOps.addChild(session.text(), drop.parentIndex(), pos, d.tag)
                    : EditorOps.move(session.text(), d.index, drop.parentIndex(), pos);
            String text = place ? placeAt(r.text(), r.index(), drop.parentIndex(), x - panX, y - panY) : r.text();
            if (applyEdit(s -> text)) selected = r.index();
        } catch (IllegalArgumentException e) {
            setStatus("여기에는 놓을 수 없습니다", true);
        }
    }

    /**
     * stack 계열 부모 안에 놓았다면 요소의 가운데가 놓은 지점(문서 좌표)에 오도록 x/y 오프셋을 정한다.
     * (row/column 은 배치가 위치를 정하므로 손대지 않는다.)
     */
    private String placeAt(String text, int index, int parentIndex, int x, int y) {
        try {
            UiDocument tmp = host.load(text);
            tmp.layout(viewW, viewH);
            LayoutBox box = boxByIndex(tmp.layout(), index), parent = boxByIndex(tmp.layout(), parentIndex);
            if (box == null || parent == null || isFlow(parent)) return text;
            Rect before = rectOf(box);
            Rect desired = new Rect(x - before.w() / 2, y - before.h() / 2, before.w(), before.h());
            Map<String, String> props = Geometry.propsForRect(false, box.style.get("anchor"),
                    Insets.parse(box.style.get("margin"), Insets.ZERO), contentRect(parent), before, desired,
                    box.style.integer("x", 0), box.style.integer("y", 0));
            return EditorOps.setProps(text, index, props, p -> false);
        } catch (UiParseException | IllegalArgumentException e) {
            return text;
        }
    }

    // ================= 입력: 키보드 =================

    public boolean charTyped(char c) {
        if (editKey == null || c < 32 || c == 127) return false;
        editBuffer.insert(editCursor++, c);
        return true;
    }

    public boolean keyPressed(EditorKey key, boolean ctrl, boolean shift) {
        if (editKey != null) return editKeyPressed(key);
        if (key == EditorKey.ESCAPE && dnd != null) {
            dnd = null;
            return true;
        }
        if (drag != null && drag.started && key == EditorKey.ESCAPE) {
            session.cancelTransient();
            drag = null;
            guides = List.of();
            reload();
            return true;
        }
        if (ctrl) {
            switch (key) {
                case Z -> {
                    if (shift) redo();
                    else undo();
                    return true;
                }
                case Y -> {
                    redo();
                    return true;
                }
                case S -> {
                    save();
                    return true;
                }
                case D -> {
                    duplicate();
                    return true;
                }
                default -> { }
            }
        }
        switch (key) {
            case DELETE -> {
                deleteSelected();
                return true;
            }
            case ESCAPE -> {
                selected = -1;
                return true;
            }
            case TAB -> {
                panelsVisible = !panelsVisible;
                return true;
            }
            case HOME -> {
                resetView();
                return true;
            }
            case LEFT -> {
                nudge(-1, 0, shift);
                return true;
            }
            case RIGHT -> {
                nudge(1, 0, shift);
                return true;
            }
            case UP -> {
                nudge(0, -1, shift);
                return true;
            }
            case DOWN -> {
                nudge(0, 1, shift);
                return true;
            }
            default -> { return false; }
        }
    }

    private boolean editKeyPressed(EditorKey key) {
        switch (key) {
            case ENTER -> commitField();
            case ESCAPE -> cancelField();
            case BACKSPACE -> {
                if (editCursor > 0) editBuffer.deleteCharAt(--editCursor);
            }
            case DELETE -> {
                if (editCursor < editBuffer.length()) editBuffer.deleteCharAt(editCursor);
            }
            case LEFT -> editCursor = Math.max(0, editCursor - 1);
            case RIGHT -> editCursor = Math.min(editBuffer.length(), editCursor + 1);
            case HOME -> editCursor = 0;
            case END -> editCursor = editBuffer.length();
            default -> { }
        }
        return true;
    }

    // ================= 명령 =================

    public void undo() {
        if (session.undo()) {
            reload();
            setStatus("실행 취소", false);
        }
    }

    public void redo() {
        if (session.redo()) {
            reload();
            setStatus("다시 실행", false);
        }
    }

    public boolean save() {
        commitFieldQuietly();
        try {
            host.save(session.text());
            session.markSaved();
            setStatus("저장됨: " + host.title(), false);
            return true;
        } catch (IOException e) {
            setStatus("저장 실패: " + e.getMessage(), true);
            return false;
        }
    }

    /** 선택한 컨테이너(리프면 그 부모) 맨 뒤에 요소를 추가한다. 팔레트 블록을 끌지 않고 클릭했을 때도 이 경로. */
    public void add(String tag) {
        LayoutBox sel = selectedBox();
        LayoutBox target = sel == null ? doc.layout() : CONTAINERS.contains(sel.tag()) ? sel : displayParent(sel);
        int parentIdx = target == null || target.node.sourceIndex() < 0 ? 0 : target.node.sourceIndex();
        EditorOps.Added r = EditorOps.addChild(session.text(), parentIdx, Integer.MAX_VALUE, tag);
        if (applyEdit(src -> r.text())) selected = r.index();
    }

    public void duplicate() {
        if (selected <= 0) return;
        EditorOps.Added r = EditorOps.duplicate(session.text(), selected);
        if (applyEdit(src -> r.text())) selected = r.index();
    }

    public void deleteSelected() { deleteIndex(selected); }

    private void deleteIndex(int index) {
        if (index <= 0) return;
        EditorOps.Added r = EditorOps.delete(session.text(), index);
        if (applyEdit(src -> r.text())) {
            selected = r.index();
            setStatus("삭제됨 (Ctrl+Z 로 되돌릴 수 있어요)", false);
        }
    }

    /** 화살표: stack 계열 부모에서는 x/y 오프셋을 1px(Shift 8px), row/column 에서는 순서를 한 칸 이동. */
    private void nudge(int dx, int dy, boolean shift) {
        LayoutBox b = selectedBox();
        if (b == null || b.parent == null) return;
        int step = shift ? 8 : 1;
        if (isFlow(b.parent)) {
            boolean horizontal = b.parent.tag().equals("row");
            int dir = horizontal ? dx : dy;
            if (dir == 0) return;
            List<LayoutBox> siblings = new ArrayList<>(displayChildren(b.parent));
            int p = siblings.indexOf(b);
            siblings.remove(b);
            int k = Math.max(0, Math.min(siblings.size(), p + dir));
            if (k == p) return;
            int parentIdx = b.parent.node.sourceIndex();
            int idx = selected;
            applyEdit(src -> {
                Element parentEl = src.element(parentIdx);
                int pos = k < siblings.size()
                        ? parentEl.children().indexOf(src.element(siblings.get(k).node.sourceIndex()))
                        : parentEl.children().size();
                return EditorOps.move(src.text(), idx, parentIdx, pos).text();
            });
            // 이동 후 선택 번호는 다시 계산한다(요소 번호는 순서에 따라 바뀐다).
            List<LayoutBox> after = displayChildren(boxByIndex(doc.layout(), parentIdx));
            if (k < after.size()) selected = after.get(k).node.sourceIndex();
            return;
        }
        int curX = b.style.integer("x", 0) + dx * step, curY = b.style.integer("y", 0) + dy * step;
        int idx = selected;
        LayoutBox box = b;
        Map<String, String> props = new LinkedHashMap<>();
        if (dx != 0) props.put("x", curX == 0 ? null : Integer.toString(curX));
        if (dy != 0) props.put("y", curY == 0 ? null : Integer.toString(curY));
        applyEdit(src -> EditorOps.setProps(src.text(), idx, props,
                p -> box.style.get(p) != null && box.node.attr(p) == null));
    }

    // ---- 속성 필드 편집 ----

    private void startEdit(String key, String value, Rect fieldRect) {
        commitFieldQuietly();
        editKey = key;
        editBuffer.setLength(0);
        editBuffer.append(value);
        editCursor = editBuffer.length();
        editRect = fieldRect;
        // 게임이 SDL 이면 글자 입력(IME 포함)을 따로 켜 줘야 한다. 켜지 않으면 키 이벤트만 오고 글자는 들어오지 않는다.
        host.textInput(true, fieldRect.x(), fieldRect.y(), fieldRect.w(), fieldRect.h());
    }

    private void cancelField() {
        boolean wasEditing = editKey != null;
        editKey = null;
        editRect = null;
        if (wasEditing) host.textInput(false, 0, 0, 0, 0);
    }

    private void commitFieldQuietly() {
        if (editKey != null) commitField();
    }

    private void commitField() {
        String key = editKey;
        String raw = editBuffer.toString().trim();
        cancelField();
        LayoutBox box = selectedBox();
        if (key == null || box == null) return;
        String value = raw.isEmpty() ? null : raw;
        int idx = selected;
        applyEdit(src -> {
            Element el = src.element(idx);
            if (key.equals("text")) return EditorOps.setText(src, el, raw);
            if (ATTR_KEYS.contains(key)) return XmlPatcher.setAttr(src, el, key, value);
            return EditorOps.setProp(src, el, key, value, box.style.get(key) != null && box.node.attr(key) == null);
        });
    }

    private List<PropRow> rowsFor(LayoutBox b) {
        List<PropRow> rows = new ArrayList<>();
        String tag = b.tag();
        boolean root = b.parent == null;
        rows.add(new PropRow(label("tag"), "", tag + "  #" + b.node.sourceIndex(), false));
        rows.add(attr(b, "id"));
        rows.add(attr(b, "class"));
        if (tag.equals("label") || tag.equals("button")) rows.add(new PropRow(label("text"), "text", LayoutEngine.rawText(b), true));
        if (tag.equals("button")) rows.add(attr(b, "onClick"));
        if (tag.equals("image")) rows.add(attr(b, "src"));
        if (!root) {
            rows.add(style(b, "width"));
            rows.add(style(b, "height"));
            rows.add(style(b, "margin"));
            if (!isFlow(b.parent)) {
                rows.add(style(b, "anchor"));
                rows.add(style(b, "x"));
                rows.add(style(b, "y"));
            }
        }
        if (!tag.equals("label") && !tag.equals("image") && !tag.equals("spacer")) rows.add(style(b, "padding"));
        if (tag.equals("row") || tag.equals("column")) {
            rows.add(style(b, "gap"));
            rows.add(style(b, "justify"));
            rows.add(style(b, "align"));
        }
        rows.add(style(b, "background"));
        if (tag.equals("label") || tag.equals("button")) rows.add(style(b, "color"));
        rows.add(style(b, "border"));
        rows.add(style(b, "border-color"));
        rows.add(style(b, "opacity"));
        if (root) rows.add(style(b, "use"));
        return rows;
    }

    private static String label(String key) { return PROP_LABELS.getOrDefault(key, key); }

    private static PropRow attr(LayoutBox b, String key) {
        String v = b.node.attr(key);
        return new PropRow(label(key), key, v == null ? "" : v, true);
    }

    private static PropRow style(LayoutBox b, String key) {
        String v = b.style.get(key);
        return new PropRow(label(key), key, v == null ? "" : v, true);
    }

    // ================= 구조 트리(접기/펼치기) =================

    /** 접힘 상태를 편집으로 요소 번호가 바뀌어도 유지하려고 id 가 있으면 id, 없으면 루트부터의 자식 순번 경로를 키로 쓴다. */
    private static String keyOf(LayoutBox b) {
        if (b.id() != null) return "#" + b.id();
        StringBuilder path = new StringBuilder();
        for (LayoutBox x = b; displayParent(x) != null; x = displayParent(x)) {
            path.insert(0, "/" + displayChildren(displayParent(x)).indexOf(x));
        }
        return "r" + path;
    }

    private List<TreeRow> treeRows() {
        List<TreeRow> out = new ArrayList<>();
        buildRows(doc.layout(), 0, out);
        return out;
    }

    private void buildRows(LayoutBox b, int depth, List<TreeRow> out) {
        List<LayoutBox> kids = displayChildren(b);
        String key = keyOf(b);
        boolean isCollapsed = !kids.isEmpty() && collapsed.contains(key);
        out.add(new TreeRow(b, depth, !kids.isEmpty(), isCollapsed, key));
        if (!isCollapsed) for (LayoutBox k : kids) buildRows(k, depth + 1, out);
    }

    private void toggleCollapsed(String key) {
        if (!collapsed.remove(key)) collapsed.add(key);
    }

    private void collapseAll() {
        for (TreeRow r : treeRows()) {
            if (r.hasKids() && r.box().parent != null) collapsed.add(r.key());
        }
        outlinerScroll = 0;
    }

    private void expandAll() { collapsed.clear(); }

    /** 선택이 바뀌면(캔버스 클릭 등) 그 요소가 보이도록 조상 행을 펼치고 스크롤한다. */
    private void revealSelected(List<TreeRow> rows) {
        LayoutBox sb = selectedBox();
        if (sb == null) return;
        boolean changed = false;
        for (LayoutBox a = displayParent(sb); a != null; a = displayParent(a)) changed |= collapsed.remove(keyOf(a));
        List<TreeRow> now = changed ? treeRows() : rows;
        for (int i = 0; i < now.size(); i++) {
            if (now.get(i).box().node.sourceIndex() == selected) {
                if (i < outlinerScroll) outlinerScroll = i;
                else if (i >= outlinerScroll + visibleRows()) outlinerScroll = i - visibleRows() + 1;
                break;
            }
        }
    }

    // ================= 화면 이동(팬) =================

    /**
     * 선택이 바뀌었을 때, 선택한 요소가 양옆 패널에 가려지거나 화면 밖이면 가운데 작업 영역 중앙으로 보이게 옮긴다.
     * 작업 영역보다 큰 요소(배경 패널, 루트)는 옮기지 않는다. 끄는 중에는 화면이 움직이지 않는다.
     */
    private void autoPan() {
        if (lastPanned == selected) return;
        lastPanned = selected;
        if (drag != null || dnd != null) return;
        LayoutBox sb = selectedBox();
        if (sb == null) return;
        Rect v = centerView();
        Rect r = rectOf(sb).translate(panX, panY);
        if (r.w() > v.w() || r.h() > v.h()) return;
        boolean inside = r.x() >= v.x() && r.right() <= v.right() && r.y() >= v.y() && r.bottom() <= v.bottom();
        if (inside) return;
        panX += v.centerX() - r.centerX();
        panY += v.centerY() - r.centerY();
    }

    // ================= 그리기 =================

    private void refreshRegions() { paint(new DrawList()); }

    /** 편집 중인 문서를 현재 화면 이동 위치에 맞춰 그린다. (호버/클릭 효과는 끈다.) */
    public void paintDocument(UiGfx g) {
        g.pushTransform(panX, panY, 1f, 0f, 0f, 0f);
        doc.paint(g, -1, -1);
        g.popTransform();
    }

    public void paint(UiGfx g) {
        regions.clear();
        autoPan();
        g.pushTransform(panX, panY, 1f, 0f, 0f, 0f);
        paintCanvasOverlay(g);
        g.popTransform();
        if (leftVisible()) paintLeftPanel(g);
        if (rightVisible()) paintProperties(g);
        if (panelsVisible) paintToolbar(g);
        if (panelsVisible && !dragStarted()) paintTabs(g);
        paintDndFeedback(g);
        paintStatus(g);
    }

    private static int tagColor(String tag) {
        return switch (tag) {
            case "screen", "panel", "row", "column", "stack", "fragment" -> 0xFFD08A2E;
            case "label" -> 0xFF4C8DDB;
            case "button" -> 0xFF8A5CD6;
            case "image" -> 0xFF45A86B;
            case "spacer" -> 0xFF7A7F8F;
            default -> 0xFF666B7A;
        };
    }

    /** 문서 좌표계(팬 변환 안)에서 그리는 캔버스 위 표시: 호버/선택 윤곽, 손잡이, 정렬선. */
    private void paintCanvasOverlay(UiGfx g) {
        boolean dragging = drag != null || dnd != null || panning;
        LayoutBox hover = !dragging && !overPanel(mouseX, mouseY) ? hitSource(mouseX - panX, mouseY - panY) : null;
        hoverIndex = hover == null ? -1 : hover.node.sourceIndex();
        if (hover != null && hoverIndex != selected) outline(g, rectOf(hover), C_HOVER);

        LayoutBox sel = selectedBox();
        if (sel != null) {
            Rect r = rectOf(sel);
            outline(g, r, C_SEL);
            if (sel.parent != null) {
                for (Handle h : Handle.values()) {
                    if (h == Handle.BODY) continue;
                    int[] c = Geometry.handleCenter(r, h);
                    g.fillRect(c[0] - HANDLE_R - 1, c[1] - HANDLE_R - 1, HANDLE_R * 2 + 2, HANDLE_R * 2 + 2, 0xFF000000);
                    g.fillRect(c[0] - HANDLE_R, c[1] - HANDLE_R, HANDLE_R * 2, HANDLE_R * 2, 0xFFFFFFFF);
                }
            }
            // 크기/위치 표시는 요소 위쪽(자리가 없으면 아래쪽)의 불투명한 띠에 그려 뒤의 글자와 섞이지 않게 한다.
            String label = r.w() + "x" + r.h() + "  @" + r.x() + "," + r.y();
            int ly = r.y() >= tm.lineHeight() + 8 ? r.y() - tm.lineHeight() - 6 : r.bottom() + 6;
            g.fillRect(r.x(), ly - 2, tm.width(label) + 6, tm.lineHeight() + 4, 0xFF000000);
            g.drawText(label, r.x() + 3, ly, C_SEL, false);
        }
        for (Geometry.Guide gd : guides) {
            if (gd.vertical()) g.fillRect(gd.pos(), -4000, 1, 8000 + viewH, C_GUIDE);
            else g.fillRect(-4000, gd.pos(), 8000 + viewW, 1, C_GUIDE);
        }
    }

    private static void outline(UiGfx g, Rect r, int color) {
        g.fillRect(r.x(), r.y(), r.w(), 1, color);
        g.fillRect(r.x(), r.bottom() - 1, r.w(), 1, color);
        g.fillRect(r.x(), r.y(), 1, r.h(), color);
        g.fillRect(r.right() - 1, r.y(), 1, r.h(), color);
    }

    private int button(UiGfx g, int x, int y, String label, boolean enabled, Runnable action) {
        int w = tm.width(label) + 8, h = TOOLBAR_H - 4;
        g.fillRect(x, y, w, h, enabled ? C_BTN : C_BTN_OFF);
        g.drawText(label, x + 4, y + (h - tm.lineHeight()) / 2 + 1, enabled ? C_TEXT : C_DIM, false);
        regions.add(new Region(new Rect(x, y, w, h), enabled ? action : null));
        return w + 2;
    }

    private void paintToolbar(UiGfx g) {
        g.fillRect(0, 0, viewW, TOOLBAR_H, C_PANEL);
        regions.add(new Region(new Rect(0, 0, viewW, TOOLBAR_H), null));
        int x = 2, y = 2;
        x += button(g, x, y, "Save" + (session.isDirty() ? "*" : ""), true, this::save);
        x += button(g, x, y, "Undo", session.canUndo(), this::undo);
        x += button(g, x, y, "Redo", session.canRedo(), this::redo);
        x += 4;
        x += button(g, x, y, "Dup", selected > 0, this::duplicate);
        x += button(g, x, y, "Del", selected > 0, this::deleteSelected);
        x += 4;
        x += button(g, x, y, grid ? "Grid:8" : "Grid:off", true, () -> grid = !grid);
        x += button(g, x, y, "Fit", panX != 0 || panY != 0, this::resetView);
        String title = host.title() + (session.isDirty() ? " *" : "");
        g.drawText(title, Math.max(x + 6, viewW - tm.width(title) - 4), 4, C_DIM, false);
    }

    /** 패널 가장자리의 접기/펼치기 탭. */
    private void paintTabs(UiGfx g) {
        Rect lt = leftTab(), rt = rightTab();
        g.fillRect(lt.x(), lt.y(), lt.w(), lt.h(), C_TAB);
        g.drawText(leftCollapsed ? ">" : "<", lt.x() + 2, lt.y() + (lt.h() - tm.lineHeight()) / 2, C_TEXT, false);
        regions.add(new Region(lt, () -> leftCollapsed = !leftCollapsed));
        g.fillRect(rt.x(), rt.y(), rt.w(), rt.h(), C_TAB);
        g.drawText(rightCollapsed ? "<" : ">", rt.x() + 2, rt.y() + (rt.h() - tm.lineHeight()) / 2, C_TEXT, false);
        regions.add(new Region(rt, () -> rightCollapsed = !rightCollapsed));
    }

    private void paintStatus(UiGfx g) {
        String text = status;
        boolean error = statusError;
        int x = (leftVisible() ? LEFT_W : 0) + 14, y = viewH - tm.lineHeight() - 5;
        if (text.isEmpty()) {
            if (dnd != null || drag != null || !panelsVisible || !leftVisible()) return;
            g.drawText("블록을 끌어다 놓으세요 · 요소를 왼쪽 블록 칸으로 끌면 삭제 · 휠/가운데 버튼: 화면 이동", x, y, C_DIM, false);
            return;
        }
        g.fillRect(x - 3, y - 2, tm.width(text) + 6, tm.lineHeight() + 4, 0xE0000000);
        g.drawText(text, x, y, error ? C_ERR : C_OK, false);
    }

    // ---- 왼쪽: 블록 팔레트 + 구조 트리 ----

    private void paintLeftPanel(UiGfx g) {
        g.fillRect(0, TOOLBAR_H, LEFT_W, viewH - TOOLBAR_H, C_PANEL);
        g.fillRect(LEFT_W - 1, TOOLBAR_H, 1, viewH - TOOLBAR_H, 0x40FFFFFF);
        regions.add(new Region(new Rect(0, TOOLBAR_H, LEFT_W, viewH - TOOLBAR_H), null));

        // 팔레트
        g.drawText("블록 (끌어다 놓기)", 4, TOOLBAR_H + 2, C_DIM, false);
        for (int i = 0; i < PALETTE_TAGS.size(); i++) {
            String tag = PALETTE_TAGS.get(i);
            Rect r = paletteBlock(i);
            g.fillRect(r.x(), r.y(), r.w(), r.h(), tagColor(tag));
            g.fillRect(r.x(), r.bottom() - 1, r.w(), 1, 0x60000000);
            String label = PALETTE_LABELS.get(tag);
            g.drawText(label, r.x() + (r.w() - tm.width(label)) / 2, r.y() + (r.h() - tm.lineHeight()) / 2 + 1, C_TEXT, false);
            regions.add(new Region(r, () -> dnd = new Dnd(true, tag, -1, mouseX, mouseY)));
        }

        // 구조 트리 머리말
        int headerY = TOOLBAR_H + PALETTE_H;
        g.fillRect(0, headerY, LEFT_W - 1, 1, 0x40FFFFFF);
        g.drawText("구조", 4, headerY + 3, C_DIM, false);
        Rect expand = new Rect(LEFT_W - 14, headerY + 2, 10, 9), collapse = new Rect(LEFT_W - 26, headerY + 2, 10, 9);
        g.fillRect(collapse.x(), collapse.y(), collapse.w(), collapse.h(), C_BTN);
        g.drawText("-", collapse.x() + 3, collapse.y() + 1, C_TEXT, false);
        g.fillRect(expand.x(), expand.y(), expand.w(), expand.h(), C_BTN);
        g.drawText("+", expand.x() + 3, expand.y() + 1, C_TEXT, false);
        regions.add(new Region(collapse, this::collapseAll));
        regions.add(new Region(expand, this::expandAll));

        // 트리
        List<TreeRow> rows = treeRows();
        if (lastRevealed != selected) {
            lastRevealed = selected;
            revealSelected(rows);
            rows = treeRows();
        }
        int visible = visibleRows();
        outlinerScroll = Math.max(0, Math.min(outlinerScroll, Math.max(0, rows.size() - visible)));
        int top = rowsTop();
        g.pushClip(0, top - 1, LEFT_W - 1, viewH - top + 1);
        for (int i = outlinerScroll; i < rows.size() && i < outlinerScroll + visible; i++) {
            TreeRow row = rows.get(i);
            LayoutBox b = row.box();
            int y = top + (i - outlinerScroll) * ROW_H;
            int idx = b.node.sourceIndex();
            boolean sel = idx == selected;
            boolean hot = mouseX >= 0 && mouseX < LEFT_W && mouseY >= y - 1 && mouseY < y - 1 + ROW_H && dnd == null;
            if (sel) g.fillRect(0, y - 1, LEFT_W - 1, ROW_H, C_ROW_SEL);
            else if (hot || idx == hoverIndex) g.fillRect(0, y - 1, LEFT_W - 1, ROW_H, C_ROW_HOVER);
            int x = 3 + row.depth() * 8;
            if (row.hasKids()) {
                g.fillRect(x, y + 1, 7, 7, 0xFF2A2D3E);
                g.drawText(row.collapsed() ? "+" : "-", x + 1, y, C_TEXT, false);
            }
            int chip = x + 9;
            g.fillRect(chip, y + 1, 3, 7, tagColor(b.tag()));
            g.drawText(fit(rowLabel(b), LEFT_W - chip - 10), chip + 6, y, sel ? C_SEL : C_TEXT, false);
            Rect rowRect = new Rect(0, y - 1, LEFT_W, ROW_H);
            regions.add(new Region(rowRect, () -> {
                select(idx);
                dnd = new Dnd(false, "", idx, mouseX, mouseY);
            }));
            if (row.hasKids()) {
                final String key = row.key();
                regions.add(new Region(new Rect(x - 1, y - 1, 10, ROW_H), () -> toggleCollapsed(key)));
            }
        }
        g.popClip();
    }

    private static String rowLabel(LayoutBox b) {
        StringBuilder sb = new StringBuilder(b.tag());
        String id = b.id();
        if (id != null) sb.append('#').append(id);
        else if (b.node.attr("class") != null) sb.append('.').append(b.node.attr("class").split("\\s+")[0]);
        String t = LayoutEngine.rawText(b);
        if (!t.isEmpty() && (b.tag().equals("label") || b.tag().equals("button"))) {
            sb.append(" \"").append(t.length() > 12 ? t.substring(0, 12) + "…" : t).append('"');
        }
        return sb.toString();
    }

    // ---- 끌어놓는 중의 표시(놓을 자리, 따라다니는 블록, 휴지통) ----

    private void paintDndFeedback(UiGfx g) {
        boolean canvasMove = dragStarted() && drag.handle == Handle.BODY && drag.index > 0;
        boolean treeMove = dnd != null && !dnd.fromPalette && dnd.started;
        boolean palette = dnd != null && dnd.fromPalette;
        if (!canvasMove && !treeMove && !palette) return;

        int moving = canvasMove ? drag.index : treeMove ? dnd.index : -1;
        Drop drop = canvasMove ? null : dropTarget(mouseX, mouseY, moving);
        Rect trash = trashRect();
        boolean overTrash = panelsVisible && trash.contains(mouseX, mouseY);

        // 캔버스에서 끄는 동안에는 패널이 숨겨지므로 왼쪽 아래에 삭제 칸이 나타난다.
        if (canvasMove) {
            g.fillRect(trash.x(), trash.y(), trash.w(), trash.h(), overTrash ? 0xD0C03030 : 0xA0402020);
            String msg = overTrash ? "놓으면 삭제" : "삭제 칸";
            g.drawText(msg, trash.x() + (trash.w() - tm.width(msg)) / 2, trash.y() + (trash.h() - tm.lineHeight()) / 2, C_TEXT, true);
        }

        if (drop != null) {
            if (drop.trash()) {
                if (drop.valid() && (canvasMove || treeMove)) {
                    Rect p = paletteRect();
                    g.fillRect(p.x(), p.y(), p.w(), p.h(), C_TRASH);
                    String msg = "여기에 놓으면 삭제";
                    g.drawText(msg, p.x() + (p.w() - tm.width(msg)) / 2, p.y() + p.h() / 2 - 4, C_TEXT, true);
                }
            } else {
                int color = drop.valid() ? C_DROP_OK : C_DROP_BAD;
                boolean doc = drop.onCanvas();            // 캔버스 위 표시는 문서 좌표 → 팬 변환 안에서 그린다
                if (doc) g.pushTransform(panX, panY, 1f, 0f, 0f, 0f);
                if (drop.highlight() != null) outline(g, drop.highlight(), color);
                if (drop.line() != null) g.fillRect(drop.line().x(), drop.line().y(), drop.line().w(), drop.line().h(), color);
                if (doc) g.popTransform();
            }
        }

        // 마우스를 따라다니는 블록
        String tag = palette ? dnd.tag : tagOfIndex(moving);
        String label = palette ? PALETTE_LABELS.getOrDefault(tag, tag) : tag;
        if (!canvasMove) {
            int w = tm.width(label) + 10, h = 13, x = mouseX + 6, y = mouseY + 6;
            g.fillRect(x, y, w, h, (tagColor(tag) & 0x00FFFFFF) | 0xD0000000);
            outline(g, new Rect(x, y, w, h), 0xFFFFFFFF);
            g.drawText(label, x + 5, y + (h - tm.lineHeight()) / 2 + 1, C_TEXT, false);
        }
    }

    private String tagOfIndex(int index) {
        LayoutBox b = index < 0 ? null : boxByIndex(doc.layout(), index);
        return b == null ? "" : b.tag();
    }

    // ---- 오른쪽: 속성 패널 ----

    private void paintProperties(UiGfx g) {
        int x0 = viewW - RIGHT_W, top = TOOLBAR_H;
        g.fillRect(x0, top, RIGHT_W, viewH - top, C_PANEL);
        g.fillRect(x0, top, 1, viewH - top, 0x40FFFFFF);
        regions.add(new Region(new Rect(x0, top, RIGHT_W, viewH - top), null));
        LayoutBox sel = selectedBox();
        if (sel == null) {
            g.drawText("요소를 선택하세요", x0 + 6, top + 6, C_DIM, false);
            return;
        }
        int y = top + 3;
        int fieldX = x0 + FIELD_OFFSET, fieldW = RIGHT_W - FIELD_OFFSET - 4;
        g.pushClip(x0, top, RIGHT_W, viewH - top);
        for (PropRow row : rowsFor(sel)) {
            if (y + ROW_H > viewH) break;
            g.drawText(row.label(), x0 + 5, y + 1, C_DIM, false);
            Rect field = new Rect(fieldX, y - 1, fieldW, ROW_H);
            boolean editing = row.key().equals(editKey) && !row.key().isEmpty();
            g.fillRect(field.x(), field.y(), field.w(), field.h(), C_FIELD);
            if (editing) {
                outline(g, field, C_SEL);
                editRect = field;
                paintEditText(g, field);
            } else {
                g.drawText(fit(row.value(), fieldW - 4), fieldX + 2, y + 1, row.editable() ? C_TEXT : C_DIM, false);
                if (row.editable()) {
                    final String key = row.key(), value = row.value();
                    regions.add(new Region(field, () -> startEdit(key, value, field)));
                }
            }
            y += ROW_H + 1;
        }
        g.popClip();
    }

    private void paintEditText(UiGfx g, Rect field) {
        String s = editBuffer.toString();
        int maxW = field.w() - 6;
        int start = 0;
        while (start < editCursor && tm.width(s.substring(start, editCursor)) > maxW) start++;
        int end = s.length();
        while (end > editCursor && tm.width(s.substring(start, end)) > maxW) end--;
        String shown = s.substring(start, end);
        g.drawText(shown, field.x() + 3, field.y() + 2, C_TEXT, false);
        int cx = field.x() + 3 + tm.width(s.substring(start, editCursor));
        g.fillRect(cx, field.y() + 1, 1, field.h() - 2, C_SEL);
    }

    private String fit(String s, int maxW) {
        if (tm.width(s) <= maxW) return s;
        int end = s.length();
        while (end > 0 && tm.width(s.substring(0, end) + "…") > maxW) end--;
        return s.substring(0, end) + "…";
    }
}
