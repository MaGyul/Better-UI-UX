package dev.magyul.betteruiux.core.edit;

import static dev.magyul.betteruiux.core.edit.EditorColors.*;
import static dev.magyul.betteruiux.core.edit.LayoutQueries.*;
import static dev.magyul.betteruiux.core.edit.PropertyModel.*;

import dev.magyul.betteruiux.api.Cursor;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.api.UiGfx;
import dev.magyul.betteruiux.core.doc.BuiltinWidgets;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.edit.Geometry.Handle;
import dev.magyul.betteruiux.core.edit.Geometry.Rect;
import dev.magyul.betteruiux.core.edit.XmlSource.Element;
import dev.magyul.betteruiux.core.layout.Colors;
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
    static final int PALETTE_H = 74, OUTLINER_HEADER_H = 12;
    /** 속성 패널에서 값 칸이 시작하는 x 오프셋(패널 왼쪽 기준). */
    static final int FIELD_OFFSET = 60;
    static final int TAB_W = 10, TAB_H = 26;
    static final List<String> PALETTE_TAGS = List.of("label", "button", "panel", "column", "row", "image", "spacer", "separator", "item",
            "checkbox", "radio", "slider", "textfield", "select", "progress",
            "grid", "scroll", "tabs", "dialog", "video", "stack");
    /** 팔레트 탭: {이름, 시작 번호, 끝 번호(미포함)}. */
    static final Object[][] PALETTE_TABS = {{"기본", 0, 9}, {"입력", 9, 15}, {"구조", 15, 19}, {"기타", 19, 21}};
    static final Map<String, String> PALETTE_LABELS = Map.ofEntries(Map.entry("label", "라벨"), Map.entry("button", "버튼"),
            Map.entry("panel", "패널"), Map.entry("column", "세로"), Map.entry("row", "가로"), Map.entry("image", "이미지"),
            Map.entry("spacer", "여백"), Map.entry("separator", "구분선"), Map.entry("item", "아이템"), Map.entry("checkbox", "체크"),
            Map.entry("radio", "라디오"), Map.entry("slider", "슬라이더"), Map.entry("textfield", "입력칸"), Map.entry("select", "선택"),
            Map.entry("progress", "진행바"), Map.entry("grid", "격자"), Map.entry("scroll", "스크롤"), Map.entry("tabs", "탭"),
            Map.entry("dialog", "대화상자"), Map.entry("video", "영상"), Map.entry("stack", "겹침"));
    private static final int SUGGEST_MAX = 6;
    private static final int HANDLE_R = 3, MIN_SIZE = 4, SNAP_THRESHOLD = 4, DRAG_START = 3, GRID = 8, WHEEL_STEP = 16;
    /** 기준점 자동 추론 임계값: 끌어 놓았을 때 / 팔레트에서 놓았을 때(가운데는 놓을 때 추론하지 않는다). */
    private static final int ANCHOR_T = 8, DROP_ANCHOR_T = 6;
    /** 선택한 요소를 화면으로 불러올 때 작업 영역 가장자리와 띄울 여유. */
    private static final int REVEAL_MARGIN = 6;

    private static final Set<String> CONTAINERS = Set.of("screen", "panel", "row", "column", "stack", "fragment", "grid", "scroll", "tabs", "tab", "dialog");

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
        /** 크기를 줄일 수 있는 하한. row/column 은 내용(자식들)보다 작아지지 못한다. */
        int minW = MIN_SIZE, minH = MIN_SIZE;
        /** 함께 끌려가는 다른 선택 요소들(자유 배치 부모 안의 것만). */
        List<GroupItem> group = List.of();
    }

    private record GroupItem(int index, Rect startRect, String anchor, Insets margin, Rect parentContent, int curX, int curY,
                             Map<String, Boolean> definedElsewhere) {}

    private final EditorHost host;
    private final TextMetrics tm;
    private final HelpOverlay help;
    private final EditSession session;
    private UiDocument doc;
    private int viewW, viewH;

    private int selected = -1;
    /** 여러 개를 고른 경우 대표({@link #selected}) 외의 선택. 번호는 편집 구조가 바뀌면 의미가 없어지므로 그때 비운다. */
    private final java.util.LinkedHashSet<Integer> multi = new java.util.LinkedHashSet<>();
    private boolean ctrlDown, shiftDown;
    private int hoverIndex = -1;
    private int mouseX = -1, mouseY = -1;
    private boolean panelsVisible = true;
    private boolean leftCollapsed, rightCollapsed;
    private boolean grid;
    private int outlinerScroll;
    private final Set<String> collapsed = new HashSet<>();
    private int lastRevealed = -2;
    /** 현재 화면 이동 위치(그리기/히트 테스트에 쓴다)와 목표 위치(부드러운 이동 중이면 현재가 목표를 따라간다). */
    private int panX, panY, targetPanX, targetPanY;
    /** 선택한 요소를 가운데로 옮겨 달라는 요청(구조 트리에서 고른 경우 등). 캔버스에서 직접 누른 요소는 이미 보이므로 옮기지 않는다. */
    private boolean revealRequested;
    private final boolean animatePan;
    private boolean panning;
    private int panAnchorX, panAnchorY;
    /** 요소를 가장자리/가운데에 붙이면 기준점(anchor)을 자동으로 그쪽으로 바꾼다. */
    private boolean autoAnchor = true;
    private Drag drag;
    private Dnd dnd;
    private List<Geometry.Guide> guides = List.of();

    private String editKey;
    private final FieldText fieldText = new FieldText();

    // ---- 외부 스타일시트(.uicss) 편집: 파일에 바로 쓰고, 되돌리기는 문서 편집과 순서를 맞춘다 ----
    private record ExtEdit(String href, String before, String after) {}

    /** 되돌리기 순서: true = 외부 파일 편집, false = 문서(XML) 편집. */
    private final java.util.ArrayDeque<Boolean> hist = new java.util.ArrayDeque<>(), redoHist = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<ExtEdit> extUndo = new java.util.ArrayDeque<>(), extRedo = new java.util.ArrayDeque<>();
    private final Map<String, String> sheetCache = new HashMap<>();

    private String sheetText(String href) {
        if (!sheetCache.containsKey(href)) sheetCache.put(href, host.readFile(href));
        return sheetCache.get(href);
    }

    private void applyExternalEdit(String href, String key, String raw) {
        String before = sheetText(href);
        if (before == null) {
            setStatus("스타일시트 '" + href + "' 를 읽을 수 없어요", true);
            return;
        }
        String next;
        try {
            next = StyleText.applyExternal(before, key, raw);
        } catch (IllegalArgumentException e) {
            setStatus(e.getMessage(), true);
            return;
        }
        if (next.equals(before)) {
            setStatus("바뀐 것이 없어요", false);
            return;
        }
        try {
            host.writeFile(href, next);
        } catch (IOException e) {
            setStatus("스타일시트를 저장하지 못했어요: " + e.getMessage(), true);
            return;
        }
        hist.push(true);
        extUndo.push(new ExtEdit(href, before, next));
        redoHist.clear();
        extRedo.clear();
        session.clearRedo();
        reload();
        setStatus(href + " 에 바로 저장했어요", false);
    }

    private boolean writeExt(ExtEdit e, String text) {
        try {
            host.writeFile(e.href(), text);
            return true;
        } catch (IOException ex) {
            setStatus("스타일시트를 저장하지 못했어요: " + ex.getMessage(), true);
            return false;
        }
    }
    /** 칸 안에서 글자가 그려지기 시작하는 문자 번호와 x 위치(마우스로 커서를 찍을 때 쓴다). */
    private int editViewStart, editTextX;
    private boolean fieldDragging;
    private long lastFieldClickNanos;
    private int lastFieldClickIndex = -1;
    private Rect editRect;

    /** 마우스 왼쪽 버튼이 눌린 상태(버튼의 눌림 색 표시용). */
    private boolean mouseDown;
    private final ColorPicker picker = new ColorPicker();
    /** 색 칸을 편집하는 동안 요소에 실시간으로 반영 중인가(Esc 로 취소하면 되돌린다). */
    private boolean previewActive;
    /** 지금 펼쳐진 팝업(컬러피커 또는 자동 완성 목록)의 영역. 여기를 눌러도 칸 편집이 확정되지 않는다. */
    private Rect popupRect;
    private List<String> suggestions = List.of();
    private int suggestIndex = -1;
    private String suggestQuery = "";

    private String status = "";
    private boolean statusError;
    private final List<Region> regions = new ArrayList<>();

    public EditorController(EditorHost host, TextMetrics tm, String text, int viewW, int viewH) throws UiParseException {
        this.host = host;
        this.animatePan = host.panAnimation();
        this.tm = tm;
        this.help = new HelpOverlay(tm);
        this.session = new EditSession(text);
        this.viewW = viewW;
        this.viewH = viewH;
        this.doc = host.load(text);
        this.doc.setAnimationsEnabled(false);
        this.doc.layout(docW(), docH());
        java.util.Map<String, String> prefs = host.loadPrefs();
        leftCollapsed = "true".equalsIgnoreCase(prefs.get("leftCollapsed"));
        rightCollapsed = "true".equalsIgnoreCase(prefs.get("rightCollapsed"));
        toolbarHidden = "true".equalsIgnoreCase(prefs.get("toolbarHidden"));
        lastPrefs = currentPrefs();
        diskBase = host.diskText();
        String rec = host.readAutosave();
        if (rec != null && !rec.equals(text)) {                      // 지난번에 저장하지 못하고 끝난 편집이 있다
            recovery = rec;
            dialogRecover = true;
            exitDialog = true;
        }
    }

    // ---- 자동 저장 / 복구 ----

    private static final long AUTOSAVE_NANOS = 5_000_000_000L;
    private String recovery;
    private boolean dialogRecover;
    private String autosavedText;
    private long autosavedAt;

    /** 저장하지 않은 편집이 있으면 몇 초마다 임시 파일에 남기고, 없으면 지운다. 복구 여부를 묻는 동안에는 덮어쓰지 않는다. */
    private void autosaveTick() {
        if (dialogRecover) return;
        if (!session.isDirty()) {
            if (autosavedText != null) {
                host.clearAutosave();
                autosavedText = null;
            }
            return;
        }
        long now = clock.getAsLong();
        String t = session.text();
        if (session.inTransient() || t.equals(autosavedText) || now - autosavedAt < AUTOSAVE_NANOS) return;
        host.autosave(t);
        autosavedText = t;
        autosavedAt = now;
    }

    private void recoverNow() {
        String r = recovery;
        recovery = null;
        dialogRecover = false;
        exitDialog = false;
        if (r != null && applyEdit(src -> r)) setStatus("자동 저장본을 복구했습니다 — 저장하면 적용됩니다", false);
        else setStatus("복구할 변경이 없습니다", false);
    }

    // ---- 해상도(GUI 크기) 미리보기 ----

    /** 0 이면 창 크기 그대로, 아니면 이 크기(GUI 단위)로 화면을 배치해 본다. */
    private int previewW, previewH;

    private static final List<String> SIZE_ITEMS = List.of("크기: 창 그대로", "크기: 960x540 (1080p·2x)", "크기: 640x360 (1080p·3x)",
            "크기: 480x270 (1080p·4x)", "크기: 427x240 (480p·2x)", "크기: 320x180");

    int docW() { return previewW > 0 ? previewW : viewW; }

    int docH() { return previewW > 0 ? previewH : viewH; }

    /** "크기: 960x540 (…)" 형태의 항목을 적용한다(창 그대로는 해제). */
    private void applySizeItem(String item) {
        previewW = previewH = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9]+)x([0-9]+)").matcher(item);
        if (m.find()) {
            previewW = Integer.parseInt(m.group(1));
            previewH = Integer.parseInt(m.group(2));
        }
        reload();
        setStatus(previewW > 0 ? "배치 크기: " + previewW + "x" + previewH : "배치 크기: 창 그대로", false);
    }

    /** 외부(테스트/백엔드)에서 미리보기 크기를 정한다. 0,0 이면 해제. */
    public void setPreviewSize(int w, int h) {
        previewW = Math.max(0, w);
        previewH = previewW > 0 ? Math.max(1, h) : 0;
        reload();
    }

    private void discardRecovery() {
        recovery = null;
        dialogRecover = false;
        exitDialog = false;
        host.clearAutosave();
    }

    private java.util.Map<String, String> lastPrefs = java.util.Map.of();
    private String diskBase;
    private boolean overwriteArmed;

    private java.util.Map<String, String> currentPrefs() {
        return java.util.Map.of("leftCollapsed", Boolean.toString(leftCollapsed),
                "rightCollapsed", Boolean.toString(rightCollapsed), "toolbarHidden", Boolean.toString(toolbarHidden));
    }

    /** 패널/툴바 접힘이 바뀌었으면 설정 파일에 저장해 다음에도 유지한다. */
    private void persistPrefs() {
        java.util.Map<String, String> cur = currentPrefs();
        if (cur.equals(lastPrefs)) return;
        lastPrefs = cur;
        host.savePrefs(cur);
    }

    // ---- 상태 조회 ----

    /** 지금 화면에 보여줄 문서(편집할 때마다 새로 만들어진다). */
    public UiDocument document() { return doc; }

    public String text() { return session.text(); }

    public boolean isDirty() { return session.isDirty(); }

    public int selectedIndex() { return selected; }

    public String status() { return status; }

    public boolean isEditingField() { return editKey != null; }

    /** 편집 중인 칸의 글자 커서 화면 위치 {x, 칸 위쪽 y, 칸 높이}. 편집 중이 아니면 null. IME 조합창을 띄울 위치. */
    public int[] caretScreen() {
        if (editKey == null || editRect == null) return null;
        int start = Math.min(editViewStart, fieldText.buf.length()), cur = Math.min(fieldText.cursor, fieldText.buf.length());
        return new int[] {editTextX + tm.width(fieldText.buf.substring(Math.min(start, cur), cur)), editRect.y(), editRect.h()};
    }

    public boolean isGridSnap() { return grid; }

    public void setView(int w, int h) {
        this.viewW = w;
        this.viewH = h;
        doc.layout(docW(), docH());
    }

    /** 구조 트리 등에서 요소를 고른다. 요소가 가려져 있으면 가운데로 화면을 옮긴다. */
    public void select(int index) {
        selected = index;
        revealRequested = true;
        scrollRevealPending = true;
        commitFieldQuietly();
    }

    /** 에디터를 닫을 때 호출한다(편집 중인 칸을 취소하고 글자 입력을 끈다). */
    public void close() {
        closeItemPicker();
        cancelField();
        dnd = null;
        drag = null;
        panning = false;
    }

    private void setStatus(String s, boolean error) {
        status = s;
        statusError = error;
        statusSince = clock.getAsLong();
    }

    private long statusSince;
    private Rect hintRect = new Rect(0, 0, 0, 0);
    private static final long FADE_IN = 150_000_000L, FADE_OUT = 450_000_000L, SHOW_OK = 2_200_000_000L, SHOW_ERR = 4_500_000_000L;

    // ---- 패널 보이기 상태 ----

    private boolean dragStarted() { return drag != null && drag.started; }

    /** 왼쪽 패널(블록+구조): 접었거나, 캔버스에서 요소를 끄는 중이면 숨긴다. */
    private boolean leftVisible() { return panelsVisible && (!leftCollapsed || leftPeek) && !dragStarted(); }

    /** 오른쪽 패널(속성): 접었거나, 무엇이든 끄는 중이면 숨긴다. */
    private boolean rightVisible() { return panelsVisible && (rightShown() || rightPeek) && !dragStarted() && dnd == null; }

    /** 접어 두었어도 요소를 선택하고 있는 동안은 잠깐 펼쳐 보인다(선택이 풀리면 다시 접힘). */
    private boolean rightShown() { return !rightCollapsed || (selected >= 0 && !tempDismissed); }

    private boolean rightTempShown() { return rightCollapsed && selected >= 0 && !tempDismissed; }

    /** 임시로 펼쳐진 패널을 사용자가 탭으로 닫았다(선택이 바뀌거나 풀리면 다시 임시 표시 대상). */
    private boolean tempDismissed;
    private int lastSelected = -2;

    // ---- 툴바 숨기기 / 위쪽 가장자리에 마우스를 올려 두면 잠깐 표시 ----

    private static final long PEEK_NANOS = 1_200_000_000L;
    private static final int PEEK_SLIVER = 6;
    private boolean toolbarHidden, toolbarPeek;
    private long topHoverSince = -1;
    /** 시간 원본(테스트에서 바꿀 수 있다). */
    java.util.function.LongSupplier clock = System::nanoTime;

    /** 시간 원본을 바꾼다(스냅샷/애니메이션 테스트용). */
    public void setClockForTests(java.util.function.LongSupplier c) { this.clock = c; }

    private boolean toolbarVisible() {
        return panelsVisible && !dragStarted() && (!toolbarHidden || toolbarPeek);
    }

    // ---- 접어 둔 양옆 패널: 가장자리에 마우스를 올려 두면 튀어나오고 잠시 뒤 표시 ----

    private boolean leftPeek, rightPeek;
    private long leftHoverSince = -1, rightHoverSince = -1;

    private void updateSidePeek() {
        if (!panelsVisible || dragStarted() || dnd != null || panning) {
            leftPeek = rightPeek = false;
            leftHoverSince = rightHoverSince = -1;
            return;
        }
        long now = clock.getAsLong();
        if (leftCollapsed) {
            if (leftPeek) {
                if (mouseX >= LEFT_W + TAB_W || mouseX < 0) leftPeek = false;
            } else if (mouseX >= 0 && mouseX <= (leftHoverSince >= 0 ? PEEK_SLIVER : 2) && mouseY >= TOOLBAR_H) {
                if (leftHoverSince < 0) leftHoverSince = now;
                if (now - leftHoverSince >= PEEK_NANOS) leftPeek = true;
            } else {
                leftHoverSince = -1;
            }
        } else {
            leftPeek = false;
            leftHoverSince = -1;
        }
        boolean rightHidden = rightCollapsed && !rightTempShown();
        if (rightHidden) {
            if (rightPeek) {
                if (mouseX < viewW - RIGHT_W - TAB_W) rightPeek = false;
            } else if (mouseX >= viewW - (rightHoverSince >= 0 ? PEEK_SLIVER : 2) && mouseY >= TOOLBAR_H) {
                if (rightHoverSince < 0) rightHoverSince = now;
                if (now - rightHoverSince >= PEEK_NANOS) rightPeek = true;
            } else {
                rightHoverSince = -1;
            }
        } else {
            rightPeek = false;
            rightHoverSince = -1;
        }
    }

    private void paintSideSlivers(UiGfx g) {
        if (!panelsVisible || dragStarted()) return;
        int top = toolbarVisible() ? TOOLBAR_H : 0;
        if (leftCollapsed && !leftPeek && leftHoverSince >= 0) {
            g.fillRect(0, top, PEEK_SLIVER - 1, viewH - top, C_PANEL | 0xFF000000);
            g.fillRect(PEEK_SLIVER - 1, top, 1, viewH - top, C_SEL);
        }
        if (rightCollapsed && !rightPeek && rightHoverSince >= 0) {
            g.fillRect(viewW - PEEK_SLIVER + 1, top, PEEK_SLIVER - 1, viewH - top, C_PANEL | 0xFF000000);
            g.fillRect(viewW - PEEK_SLIVER, top, 1, viewH - top, C_SEL);
        }
    }

    private void updateToolbarPeek() {
        if (!toolbarHidden || dragStarted() || dnd != null) {
            toolbarPeek = false;
            topHoverSince = -1;
            return;
        }
        long now = clock.getAsLong();
        if (toolbarPeek) {
            if (mouseY >= TOOLBAR_H || mouseY < 0) toolbarPeek = false;      // 마우스가 막대를 벗어나면 다시 숨긴다
            return;
        }
        if (mouseY >= 0 && mouseY <= (topHoverSince >= 0 ? PEEK_SLIVER : 2)) {   // 튀어나온 조각 위에서도 계속 기다린다
            if (topHoverSince < 0) topHoverSince = now;
            if (now - topHoverSince >= PEEK_NANOS) toolbarPeek = true;
        } else {
            topHoverSince = -1;
        }
    }

    // ---- 저장하지 않은 변경이 있을 때 닫기 확인 ----

    private boolean exitDialog;
    private int realMouseX, realMouseY;

    /** 에디터를 닫는다. 저장하지 않은 변경이 있으면 먼저 물어본다. */
    public void requestExit() {
        if (!session.isDirty()) {
            host.clearAutosave();
            host.requestExit();
            return;
        }
        cancelField();
        exitDialog = true;
    }

    private void saveAndExit() {
        if (save()) {
            exitDialog = false;
            host.requestExit();
        } else {
            exitDialog = false;               // 저장 실패 메시지를 보여 주고 에디터는 계속 연다
        }
    }

    /** 숨긴 툴바 위에 마우스를 올려 두면 위쪽 가장자리에서 조금씩 튀어나온다(곧 나타난다는 표시). */
    private void paintPeekSliver(UiGfx g) {
        if (!toolbarHidden || toolbarPeek || topHoverSince < 0 || !panelsVisible || dragStarted()) return;
        // 길쭉하게(가로 전체) 살짝 튀어나온 채로 기다린다 — 크기는 변하지 않는다.
        g.fillRect(0, 0, viewW, PEEK_SLIVER - 1, C_PANEL | 0xFF000000);
        g.fillRect(0, PEEK_SLIVER - 1, viewW, 1, C_SEL);
    }

    private void paintExitDialog(UiGfx g) {
        if (!exitDialog) return;
        Rect full = new Rect(0, 0, viewW, viewH);
        g.fillRect(0, 0, viewW, viewH, 0xB0000000);
        regions.add(new Region(full, null));                  // 뒤의 모든 것을 막는다
        if (dialogRecover) {
            paintRecoverDialog(g);
            return;
        }
        String l1 = "저장하지 않은 변경이 있습니다.", l2 = "저장하고 닫을까요?";
        String b1 = "저장 후 닫기", b2 = "저장 안 함", b3 = "취소";
        int bw1 = tm.width(b1) + 8, bw2 = tm.width(b2) + 8, bw3 = tm.width(b3) + 8;
        int w = Math.max(Math.max(tm.width(l1), tm.width(l2)) + 24, bw1 + bw2 + bw3 + 6 + 24);
        int h = tm.lineHeight() * 2 + TOOLBAR_H + 26;
        int x = (viewW - w) / 2, y = (viewH - h) / 2;
        g.fillRect(x - 1, y - 1, w + 2, h + 2, C_SEL);
        g.fillRect(x, y, w, h, 0xFF161820);
        g.drawText(l1, x + (w - tm.width(l1)) / 2, y + 8, C_TEXT, false);
        g.drawText(l2, x + (w - tm.width(l2)) / 2, y + 10 + tm.lineHeight(), C_DIM, false);
        int by = y + h - TOOLBAR_H - 4;
        int bx = x + (w - (bw1 + bw2 + bw3 + 6)) / 2;
        bx += button(g, bx, by, b1, true, this::saveAndExit, 0xFF2A6A3A);
        bx += button(g, bx, by, b2, true, () -> {
            exitDialog = false;
            host.clearAutosave();
            host.requestExit();
        }, 0xFF7A2A2A);
        button(g, bx, by, b3, true, () -> exitDialog = false);
    }

    private void paintRecoverDialog(UiGfx g) {
        String l1 = "저장하지 못하고 끝난 편집 내용이 남아 있습니다.", l2 = "복구할까요?";
        String b1 = "복구", b2 = "버리기", b3 = "나중에";
        int bw1 = tm.width(b1) + 8, bw2 = tm.width(b2) + 8, bw3 = tm.width(b3) + 8;
        int w = Math.max(Math.max(tm.width(l1), tm.width(l2)) + 24, bw1 + bw2 + bw3 + 6 + 24);
        int h = tm.lineHeight() * 2 + TOOLBAR_H + 26;
        int x = (viewW - w) / 2, y = (viewH - h) / 2;
        g.fillRect(x - 1, y - 1, w + 2, h + 2, C_SEL);
        g.fillRect(x, y, w, h, 0xFF161820);
        g.drawText(l1, x + (w - tm.width(l1)) / 2, y + 8, C_TEXT, false);
        g.drawText(l2, x + (w - tm.width(l2)) / 2, y + 10 + tm.lineHeight(), C_DIM, false);
        int by = y + h - TOOLBAR_H - 4;
        int bx = x + (w - (bw1 + bw2 + bw3 + 6)) / 2;
        bx += button(g, bx, by, b1, true, this::recoverNow, 0xFF2A6A3A);
        bx += button(g, bx, by, b2, true, this::discardRecovery, 0xFF7A2A2A);
        button(g, bx, by, b3, true, () -> {              // 자동 저장본은 그대로 두고 지금 파일로 계속한다(다음에 다시 묻지 않도록 이번엔 덮어쓰지 않음)
            dialogRecover = false;
            exitDialog = false;
            recovery = null;
        });
    }

    /**
     * 아래쪽 안내: 작업 영역 가운데에 늘 맨 위로 그린다. 글자가 상자보다 길면 마인크래프트 버튼처럼 좌우로 천천히 스크롤한다.
     */
    private void paintHint(UiGfx g, String text, int color) {
        Rect view = centerView();
        int boxW = Math.min(tm.width(text) + 12, view.w() - 8), h = tm.lineHeight() + 6;
        int x = view.x() + (view.w() - boxW) / 2, y = view.bottom() - h - 4;
        hintRect = new Rect(x, y, boxW, h);
        g.fillRect(x, y, boxW, h, 0xD0000000);
        int textW = tm.width(text), inner = boxW - 12;
        g.pushClip(x + 6, y, inner, h);
        int offset = textW > inner ? marqueeOffset(textW - inner, clock.getAsLong()) : 0;
        g.drawText(text, x + 6 - offset, y + 3, color, false);
        g.popClip();
    }

    /** 처음에 잠깐 멈추고 → 끝까지 천천히 → 잠깐 멈추고 → 되돌아온다. */
    static int marqueeOffset(int overflow, long nanos) {
        double sec = nanos / 1e9, travel = Math.max(0.5, overflow / 28.0), pause = 1.0;
        double period = 2 * (travel + pause), t = sec % period;
        if (t < pause) return 0;
        if (t < pause + travel) return (int) Math.round(overflow * (t - pause) / travel);
        if (t < 2 * pause + travel) return overflow;
        return (int) Math.round(overflow * (1 - (t - 2 * pause - travel) / travel));
    }

    private static int withAlpha(int argb, float a) {
        int base = (argb >>> 24) & 0xFF;
        return (Math.round(base * Math.max(0f, Math.min(1f, a))) << 24) | (argb & 0xFFFFFF);
    }

    /** "저장됨" 같은 알림: 오른쪽 아래에 맨 위로 떠서 서서히 나타났다가(fade in) 시간이 지나면 서서히 사라진다(fade out). */
    private void paintToast(UiGfx g) {
        if (status.isEmpty()) return;
        long age = clock.getAsLong() - statusSince, show = statusError ? SHOW_ERR : SHOW_OK;
        if (age >= show + FADE_OUT) return;
        float a = age < FADE_IN ? Math.max(0f, age / (float) FADE_IN) : age <= show ? 1f : 1f - (age - show) / (float) FADE_OUT;
        int w = Math.min(tm.width(status) + 14, viewW - 12), h = tm.lineHeight() + 8;
        int x = viewW - w - 6, y = viewH - h - 6 + Math.round((1f - Math.min(1f, a * 1.5f)) * 5);   // 아래에서 살짝 올라오며 나타난다
        if (hintRect.w() > 0 && x < hintRect.right() + 4 && y + h > hintRect.y() - 4) y -= hintRect.h() + 6;   // 안내와 겹치면 위로
        int color = statusError ? C_ERR : C_OK;
        g.fillRect(x - 1, y - 1, w + 2, h + 2, withAlpha(color, a * 0.8f));
        g.fillRect(x, y, w, h, withAlpha(0xFF101018, a * 0.95f));
        g.pushClip(x + 1, y, w - 2, h);
        g.drawText(status, x + 7, y + 4, withAlpha(color, a), false);
        g.popClip();
    }

    /** 팬 계산용: 끄는 중이 아닐 때의 "가운데 작업 영역"(패널이 접혀 있으면 그만큼 넓다). */
    private Rect centerView() {
        int left = panelsVisible && !leftCollapsed ? LEFT_W : 0, right = panelsVisible && rightShown() ? RIGHT_W : 0;
        int top = panelsVisible ? TOOLBAR_H : 0;
        return new Rect(left, top, Math.max(1, viewW - left - right), Math.max(1, viewH - top));
    }

    // ---- 문서 다시 만들기 / 편집 적용 ----

    private boolean reload() {
        try {
            sheetCache.clear();
            UiDocument d = host.load(session.text());
            d.setAnimationsEnabled(false);
            d.layout(docW(), docH());
            if (applyTabs(d)) d.layout(docW(), docH());
            if (applyScrolls(d)) d.layout(docW(), docH());
            doc = d;
            int count = XmlSource.parse(session.text()).all().size();
            if (selected >= count) selected = -1;
            return true;
        } catch (UiParseException e) {
            setStatus("XML 오류 (줄 " + e.line() + "): " + e.getMessage() + " — 되돌렸어요", true);
            return false;
        }
    }

    // ---- 탭: 편집으로 문서가 다시 만들어져도 보고 있던 탭을 유지한다 ----

    private final Map<Integer, Integer> tabActive = new HashMap<>();

    private boolean applyTabs(UiDocument d) {
        boolean any = false;
        for (Map.Entry<Integer, Integer> e : tabActive.entrySet()) {
            LayoutBox b = boxByIndex(d.layout(), e.getKey());
            if (b != null && b.widget instanceof BuiltinWidgets.Tabs t) {
                t.setActive(e.getValue());
                any = true;
            }
        }
        return any;
    }

    // ---- 스크롤 요소: 편집으로 문서가 다시 만들어져도 위치를 유지하고, 고른 요소가 가려져 있으면 보이게 스크롤한다 ----

    private final Map<Integer, Integer> scrollPos = new HashMap<>();
    private boolean scrollRevealPending;

    private boolean applyScrolls(UiDocument d) {
        boolean any = false;
        for (Map.Entry<Integer, Integer> e : scrollPos.entrySet()) {
            LayoutBox b = boxByIndex(d.layout(), e.getKey());
            if (b != null && b.widget instanceof BuiltinWidgets.Scroll sw) {
                sw.setScrollY(e.getValue());
                any = true;
            }
        }
        return any;
    }

    private void revealSelectedInScroll() {
        if (!scrollRevealPending) return;
        scrollRevealPending = false;
        LayoutBox b = selectedBox();
        if (b == null) return;
        boolean changed = false;
        for (LayoutBox a = b.parent; a != null; a = a.parent) {
            if (!(a.widget instanceof BuiltinWidgets.Scroll sw) || !sw.canScroll()) continue;
            int dy = 0;
            if (b.y < a.y) dy = b.y - a.y;                                   // 위로 가려짐
            else if (b.y + b.h > a.y + a.h) dy = Math.min(b.y - a.y, b.y + b.h - (a.y + a.h));   // 아래로 가려짐
            if (dy != 0 && sw.scrollBy(dy)) {
                scrollPos.put(a.node.sourceIndex(), sw.scrollY());
                changed = true;
            }
        }
        if (changed) doc.layout(docW(), docH());
    }

    /** 마우스 아래의 스크롤 요소를 휠로 스크롤한다. 스크롤할 수 있는 요소가 없으면 false. */
    private boolean scrollUnderMouse(int x, int y, double amount) {
        for (LayoutBox b = doc.hitTest(x - panX, y - panY); b != null; b = b.parent) {
            if (b.widget instanceof BuiltinWidgets.Scroll sw && sw.canScroll()) {
                if (sw.scrollBy(-(int) Math.signum(amount) * 12) || true) {
                    scrollPos.put(b.node.sourceIndex(), sw.scrollY());
                    doc.layout(docW(), docH());
                }
                return true;
            }
        }
        return false;
    }

    private static int tabIndex(LayoutBox tabs, LayoutBox tab) {
        int i = 0;
        for (LayoutBox c : tabs.children) {
            if (c == tab) return i;
            if (c.tag().equals("tab")) i++;
        }
        return 0;
    }

    /** 선택한 요소가 보이지 않는 탭 안에 있으면 그 탭으로 전환한다(구조 트리에서 고른 경우). */
    private void revealSelectedTab() {
        LayoutBox b = selectedBox();
        for (LayoutBox x = b; x != null; x = x.parent) {
            if (x.inactive && x.parent != null && x.parent.widget instanceof BuiltinWidgets.Tabs t) {
                int i = tabIndex(x.parent, x);
                tabActive.put(x.parent.node.sourceIndex(), i);
                t.setActive(i);
                doc.layout(docW(), docH());
                return;
            }
        }
    }

    /** 텍스트 패치를 Undo 가능한 한 단계로 적용하고 화면을 갱신한다. 실패하면 되돌린다. */
    private boolean applyEdit(Function<XmlSource, String> patch) {
        int countBefore = XmlSource.parse(session.text()).all().size();
        if (!session.edit(patch)) return false;
        if (XmlSource.parse(session.text()).all().size() != countBefore) multi.clear();   // 요소가 늘거나 줄면 번호가 밀린다
        if (!reload()) {
            session.undo();
            reload();
            return false;
        }
        hist.push(false);
        redoHist.clear();
        extRedo.clear();
        setStatus("", false);
        return true;
    }

    // ---- 박스 조회 (문서 좌표) ----

    private LayoutBox selectedBox() { return selected < 0 ? null : boxByIndex(doc.layout(), selected); }

    /** 문서 좌표 (x,y) 아래의 요소 중 원본 XML 에 실제로 있는 가장 깊은 것(확장으로 생긴 요소는 조상으로 올라간다). */
    private LayoutBox hitSource(int x, int y) {
        LayoutBox hit = doc.hitTest(x, y);
        while (hit != null && hit.node.sourceIndex() < 0) hit = hit.parent;
        return hit;
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
        int pos = i - (int) PALETTE_TABS[paletteTabOf(i)][1];                  // 탭 안에서의 위치
        return new Rect(4 + (pos % 3) * (w + 2), TOOLBAR_H + 24 + (pos / 3) * (h + 2), w, h);
    }

    private static int paletteTabOf(int i) {
        for (int t = 0; t < PALETTE_TABS.length; t++) {
            if (i >= (int) PALETTE_TABS[t][1] && i < (int) PALETTE_TABS[t][2]) return t;
        }
        return 0;
    }

    private int paletteTab;

    static int rowsTop() { return TOOLBAR_H + PALETTE_H + OUTLINER_HEADER_H + 2; }

    private int visibleRows() { return Math.max(1, (viewH - rowsTop()) / ROW_H); }

    /** 패널 접기 탭은 가장자리의 세로 중앙에 둔다(위쪽/구석에 놓이는 요소와 겹치지 않도록). */
    static int tabY(int viewH) { return (TOOLBAR_H + viewH) / 2 - TAB_H / 2; }

    private Rect leftTab() { return new Rect(leftCollapsed ? 0 : LEFT_W, tabY(viewH), TAB_W, TAB_H); }

    private Rect rightTab() {
        return new Rect(rightShown() ? viewW - RIGHT_W - TAB_W : viewW - TAB_W, tabY(viewH), TAB_W, TAB_H);
    }

    private boolean overPanel(int x, int y) {
        if (!panelsVisible) return false;
        if (y < TOOLBAR_H && toolbarVisible()) return true;
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

    /** 다음 마우스 입력에 적용할 Ctrl/Shift 상태(여러 개 선택용). */
    public void setModifiers(boolean ctrl, boolean shift) {
        this.ctrlDown = ctrl;
        this.shiftDown = shift;
    }

    /** 선택된 요소 번호들(대표가 맨 앞). 루트(0)는 여러 선택에 포함되지 않는다. */
    public List<Integer> selection() {
        List<Integer> all = new ArrayList<>();
        if (selected >= 0) all.add(selected);
        for (int i : multi) if (i != selected) all.add(i);
        return all;
    }

    private List<Integer> editableSelection() {
        List<Integer> out = new ArrayList<>();
        for (int i : selection()) if (i > 0) out.add(i);
        return out;
    }

    private void toggleMulti(int idx) {
        if (idx <= 0) return;
        if (idx == selected) {
            if (multi.isEmpty()) return;
            selected = multi.iterator().next();
            multi.remove(selected);
        } else if (!multi.remove(idx)) {
            if (selected <= 0) selected = idx;
            else multi.add(idx);
        }
    }

    // ---- 사각형(마퀴) 선택 ----

    private int marqStartX, marqStartY, marqX, marqY;
    private boolean marqPending, marqActive;

    private Rect marqueeRect() {
        return new Rect(Math.min(marqStartX, marqX), Math.min(marqStartY, marqY), Math.abs(marqX - marqStartX), Math.abs(marqY - marqStartY));
    }

    private void finishMarquee() {
        Rect m = marqueeRect();
        List<LayoutBox> hits = new ArrayList<>();
        collectInside(doc.layout(), m, hits);
        List<LayoutBox> top = new ArrayList<>();                         // 안에 있는 요소의 자손은 빼서 이중 이동을 막는다
        for (LayoutBox b : hits) {
            boolean covered = false;
            for (LayoutBox o : hits) if (o != b && isInsideOrSelf(b, o.node.sourceIndex())) covered = true;
            if (!covered) top.add(b);
        }
        multi.clear();
        selected = -1;
        for (LayoutBox b : top) {
            if (selected < 0) selected = b.node.sourceIndex();
            else multi.add(b.node.sourceIndex());
        }
        if (!top.isEmpty()) setStatus(top.size() + "개 선택", false);
    }

    private void collectInside(LayoutBox b, Rect m, List<LayoutBox> out) {
        if (!b.visible() || b.inactive) return;
        if (b.parent != null && b.node.sourceIndex() > 0 && b.w > 0 && b.h > 0
                && b.x >= m.x() && b.y >= m.y() && b.x + b.w <= m.right() && b.y + b.h <= m.bottom()) out.add(b);
        for (LayoutBox c : b.children) collectInside(c, m, out);
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
        mouseDown = true;
        refreshRegions();
        boolean inPopup = popupRect != null && popupRect.contains(x, y);
        if (editKey != null && !inPopup && (editRect == null || !editRect.contains(x, y))) commitField();
        refreshRegions();
        if (editKey != null && pickerActive() && picker.contains(x, y)) {      // 컬러피커: 색 고르기/드래그
            String hex = picker.press(x, y);
            if (hex != null) setBufferFromPicker(hex);
            return true;
        }
        if (editKey != null && editRect != null && editRect.contains(x, y)) {   // 편집 중인 칸 안: 커서 찍기/드래그 선택/더블클릭
            onFieldPress(x);
            return true;
        }
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (r.passthrough() || !r.rect().contains(x, y)) continue;
            if (r.action() != null) r.action().run();
            return true;
        }
        int cx = x - panX, cy = y - panY;                   // 이후는 문서 좌표
        boolean underToolbar = toolbarVisible() && y < TOOLBAR_H;
        marqPending = false;
        if (ctrlDown || shiftDown) {                                                    // Ctrl/Shift+클릭: 선택에 넣고 빼기(끌기보다 우선)
            LayoutBox mh = hitSource(cx, cy);
            if (mh != null && mh.parent != null) {
                toggleMulti(mh.node.sourceIndex());
                return true;
            }
        }
        LayoutBox sel = selectedBox();
        if (sel != null && sel.parent != null) {
            Handle h = handleAt(sel, cx, cy);
            if (h != null) {
                prepareDrag(sel, h, cx, cy);
                return true;
            }
            if (sel.contains(cx, cy)) {
                LayoutBox inner = hitSource(cx, cy);
                if (tabHeaderClick(inner, cx, cy)) return true;          // 선택돼 있어도 탭 머리글은 탭 전환
                boolean childUnderMouse = inner != null && inner != sel && isInsideOrSelf(inner, selected);
                if (!childUnderMouse) {                      // 선택한 요소 안의 자식을 누르면 그 자식을 고른다(아래로 이어짐)
                    prepareDrag(sel, Handle.BODY, cx, cy);
                    return true;
                }
            }
        }
        LayoutBox hit = hitSource(cx, cy);
        if (tabHeaderClick(hit, cx, cy)) return true;
        if ((ctrlDown || shiftDown) && hit != null && hit.parent != null) {            // Ctrl/Shift+클릭: 선택에 넣고 빼기
            toggleMulti(hit.node.sourceIndex());
            return true;
        }
        if (underToolbar && (hit == null || hit.parent == null)) return true;   // 툴바의 빈 곳을 눌렀을 뿐이면 배경(루트)을 고르지 않는다
        if (hit != null && hit.parent != null && multi.contains(hit.node.sourceIndex())) {   // 선택 묶음 안의 요소를 누르면 묶음째 끈다
            multi.remove(hit.node.sourceIndex());
            if (selected > 0) multi.add(selected);
            selected = hit.node.sourceIndex();
            prepareDrag(hit, Handle.BODY, cx, cy);
            return true;
        }
        multi.clear();
        if (hit == null || hit.parent == null) {                                         // 빈 곳: 클릭이면 선택/해제, 끌면 사각형 선택
            marqPending = true;
            marqActive = false;
            marqStartX = marqX = cx;
            marqStartY = marqY = cy;
        }
        if (hit != null) {
            selected = hit.node.sourceIndex();
            if (hit.parent != null) prepareDrag(hit, Handle.BODY, cx, cy);
        } else {
            selected = -1;
        }
        return true;
    }

    /** 탭 머리글을 눌렀으면 그 탭으로 전환하고 탭 요소를 선택한다. */
    private boolean tabHeaderClick(LayoutBox hit, int cx, int cy) {
        if (hit == null || !(hit.widget instanceof BuiltinWidgets.Tabs t) || cy >= hit.y + t.headerHeight()) return false;
        int i = t.tabAt(cx - hit.x);
        if (i < 0) return false;
        tabActive.put(hit.node.sourceIndex(), i);
        t.setActive(i);
        doc.layout(docW(), docH());
        selected = hit.node.sourceIndex();
        return true;
    }

    public boolean mouseDragged(int x, int y) {
        mouseX = x;
        mouseY = y;
        if (panning) {
            setPanNow(x - panAnchorX, y - panAnchorY);
            return true;
        }
        if (picker.dragging()) {
            String hex = picker.drag(x, y);
            if (hex != null) setBufferFromPicker(hex);
            return true;
        }
        if (fieldDragging) {
            fieldText.cursor = indexAt(x);
            return true;
        }
        if (marqPending) {
            marqX = x - panX;
            marqY = y - panY;
            if (!marqActive && Math.max(Math.abs(marqX - marqStartX), Math.abs(marqY - marqStartY)) >= DRAG_START) marqActive = true;
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
        mouseDown = false;
        if (panning) {
            panning = false;
            return true;
        }
        if (picker.dragging()) {
            picker.release();
            return true;
        }
        if (fieldDragging) {
            fieldDragging = false;
            return true;
        }
        if (marqPending) {
            boolean was = marqActive;
            marqPending = false;
            marqActive = false;
            if (was) finishMarquee();
            return true;
        }
        if (dnd != null) {
            Dnd d = dnd;
            try {
                // 놓는 순간까지 끌기 상태를 유지한다: 끄는 동안 숨겨져 있던 오른쪽 패널 자리에 놓은 것도 캔버스에 놓은 것으로 처리해야 한다.
                finishDnd(d, x, y);
            } finally {
                dnd = null;
            }
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
            hist.push(false);
            redoHist.clear();
            extRedo.clear();
            selected = d.resultIndex;
            reload();
            setStatus("", false);
        } else {
            multi.clear();                                          // 끌지 않고 그냥 눌렀다 뗐으면 그 요소 하나만 선택한다
        }
        return true;
    }

    /** 휠: 왼쪽 구조 트리 위에서는 목록 스크롤, 캔버스에서는 화면 이동. */
    public boolean mouseScrolled(int x, int y, double amount) {
        if (itemPicker.isOpen()) {
            itemPicker.scroll(amount);
            return true;
        }
        if (help.open) {
            help.scrollBy(amount);
            return true;
        }
        if (xmlView.open) {
            xmlView.scrollBy(amount);
            return true;
        }
        if (leftVisible() && x < LEFT_W && y >= rowsTop() - 2) {
            outlinerScroll = Math.max(0, outlinerScroll - (int) Math.signum(amount) * 3);
            return true;
        }
        if (rightVisible() && x >= viewW - RIGHT_W && y >= TOOLBAR_H && propMaxScroll > 0) {
            propScroll = Math.max(0, Math.min(propMaxScroll, propScroll - (int) Math.signum(amount) * (ROW_H + 1) * 2));
            return true;
        }
        if (overPanel(x, y)) return false;
        if (scrollUnderMouse(x, y, amount)) return true;                     // scroll 요소 위의 휠은 그 안의 내용을 스크롤
        setPanNow(panX, panY + (int) Math.signum(amount) * WHEEL_STEP);
        return true;
    }

    /** 화면 이동을 처음 위치로 되돌린다. */
    public void resetView() { setPanNow(0, 0); }

    /** 화면 이동 위치를 즉시 정한다(휠, 가운데 버튼 드래그, 되돌리기). */
    private void setPanNow(int x, int y) {
        panX = targetPanX = x;
        panY = targetPanY = y;
    }

    /** 부드러운 이동 중이면 현재 위치를 목표 쪽으로 한 걸음 옮긴다. */
    private void stepPan() {
        if (!animatePan) {
            panX = targetPanX;
            panY = targetPanY;
            return;
        }
        panX = ease(panX, targetPanX);
        panY = ease(panY, targetPanY);
    }

    private static int ease(int current, int target) {
        int diff = target - current;
        if (Math.abs(diff) <= 1) return target;
        return current + (int) Math.round(diff * 0.3);
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
        d.parentContent = parent == null ? new Rect(0, 0, docW(), docH()) : contentRect(parent);
        d.curX = b.style.integer("x", 0);
        d.curY = b.style.integer("y", 0);
        if (isSizedFlow(b) || isScroll(b)) {                // row/column/grid: 내용보다 작게 줄일 수 없다(scroll 은 너비만)
            int[] mn = doc.minimumSize(b);
            d.minW = Math.max(MIN_SIZE, mn[0]);
            d.minH = isScroll(b) ? MIN_SIZE : Math.max(MIN_SIZE, mn[1]);
        }
        for (String k : List.of("width", "height", "x", "y", "anchor")) {
            d.definedElsewhere.put(k, b.style.get(k) != null && b.node.attr(k) == null);
        }
        if (h == Handle.BODY && !d.parentFlow && !multi.isEmpty()) {
            List<GroupItem> group = new ArrayList<>();
            for (int gi : multi) {
                LayoutBox gb = boxByIndex(doc.layout(), gi);
                if (gb == null || gb.parent == null || isFlow(gb.parent) || gi == d.index) continue;
                Map<String, Boolean> def = new HashMap<>();
                for (String k : List.of("x", "y", "anchor")) def.put(k, gb.style.get(k) != null && gb.node.attr(k) == null);
                group.add(new GroupItem(gi, rectOf(gb), gb.style.get("anchor"), Insets.parse(gb.style.get("margin"), Insets.ZERO),
                        contentRect(gb.parent), gb.style.integer("x", 0), gb.style.integer("y", 0), def));
            }
            d.group = group;
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
            Rect target = snapped(d, Geometry.drag(d.startRect, d.handle, dx, dy, d.minW, d.minH));
            // 가장자리/중심선에 붙인 요소는 그쪽을 기준점으로 삼는다(창 크기가 바뀌어도 그 끝에 붙어 있도록).
            String anchor = Geometry.normalizeAnchor(d.anchor);
            if (autoAnchor && !d.parentFlow) anchor = Geometry.inferAnchor(target, d.parentContent, d.anchor, ANCHOR_T, true);
            boolean anchorChanged = !anchor.equals(Geometry.normalizeAnchor(d.anchor));
            Map<String, String> props = Geometry.propsForRect(d.parentFlow, anchor, d.margin, d.parentContent,
                    d.startRect, target, anchorChanged ? Geometry.UNKNOWN : d.curX, anchorChanged ? Geometry.UNKNOWN : d.curY);
            if (anchorChanged) props.put("anchor", anchor.equals("top-left") ? null : anchor);
            int tx = target.x() - d.startRect.x(), ty = target.y() - d.startRect.y();
            ok = session.setTransient(src -> {
                String t = EditorOps.setProps(src.text(), d.index, props, p -> d.definedElsewhere.getOrDefault(p, false));
                if (d.handle == Handle.BODY) {
                    for (GroupItem gi : d.group) {                         // 함께 선택한 요소들도 같은 만큼 옮긴다(각자의 부모 안에서)
                        Rect moved = Geometry.clamp(gi.startRect().translate(tx, ty), gi.parentContent());
                        Map<String, String> gp = Geometry.propsForRect(false, Geometry.normalizeAnchor(gi.anchor()), gi.margin(),
                                gi.parentContent(), gi.startRect(), moved, gi.curX(), gi.curY());
                        t = EditorOps.setProps(t, gi.index(), gp, p -> gi.definedElsewhere().getOrDefault(p, false));
                    }
                }
                return t;
            });
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
            int w = Math.max(d.minW, rt - l), h = Math.max(d.minH, b - t);
            out = new Rect(mL ? rt - w : l, mT ? b - h : t, w, h);
        }
        // 부모(루트의 자식이면 화면) 밖으로는 나갈 수 없다.
        Rect bounded = d.handle == Handle.BODY
                ? Geometry.clamp(out, d.parentContent)
                : Geometry.clampEdges(out, d.parentContent, d.minW, d.minH, d.handle);
        guides = g;
        return bounded;
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
        try {
            EditorOps.Added r = d.fromPalette
                    ? EditorOps.addChild(session.text(), drop.parentIndex(), pos, d.tag)
                    : EditorOps.move(session.text(), d.index, drop.parentIndex(), pos);
            String text;
            if (drop.onCanvas()) {
                text = placeAt(r.text(), r.index(), drop.parentIndex(), x - panX, y - panY);      // 놓은 지점에
            } else if (d.fromPalette) {
                int[] c = visibleCenterDoc();                                                    // 트리에 놓은 새 요소는 지금 보는 화면 한가운데에
                text = placeAt(r.text(), r.index(), drop.parentIndex(), c[0], c[1]);
            } else {
                text = r.text();
                revealRequested = true;                                                          // 옮겨진 기존 요소는 가려졌다면 화면으로 불러온다
            }
            String grown = growToFit(text, r.index());
            if (applyEdit(s -> grown)) selected = r.index();
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
            tmp.layout(docW(), docH());
            LayoutBox box = boxByIndex(tmp.layout(), index), parent = boxByIndex(tmp.layout(), parentIndex);
            if (box == null || parent == null || isFlow(parent)) return text;
            Rect before = rectOf(box);
            Rect content = contentRect(parent);
            // 부모 밖으로는 놓을 수 없다: 놓은 지점이 가장자리 근처면 안쪽으로 당긴다.
            Rect desired = Geometry.clamp(new Rect(x - before.w() / 2, y - before.h() / 2, before.w(), before.h()), content);
            // 오른쪽/아래쪽 끝에 붙여 놓았다면 그 끝을 기준점으로 삼는다(놓을 때는 가운데는 추론하지 않는다).
            String anchor = Geometry.normalizeAnchor(box.style.get("anchor"));
            if (autoAnchor) anchor = Geometry.inferAnchor(desired, content, box.style.get("anchor"), DROP_ANCHOR_T, false);
            boolean anchorChanged = !anchor.equals(Geometry.normalizeAnchor(box.style.get("anchor")));
            Map<String, String> props = Geometry.propsForRect(false, anchor,
                    Insets.parse(box.style.get("margin"), Insets.ZERO), content, before, desired,
                    anchorChanged ? Geometry.UNKNOWN : box.style.integer("x", 0),
                    anchorChanged ? Geometry.UNKNOWN : box.style.integer("y", 0));
            if (anchorChanged) props.put("anchor", anchor.equals("top-left") ? null : anchor);
            return EditorOps.setProps(text, index, props, p -> false);
        } catch (UiParseException | IllegalArgumentException e) {
            return text;
        }
    }

    /**
     * 기준점을 바꾼 편집의 결과 텍스트에서, 요소가 원래 있던 자리({@code desired})에 그대로 있도록 x/y 오프셋을 다시 계산한다.
     * (기준점만 바꾸면 이전 오프셋이 새 기준점에 적용돼 요소가 엉뚱한 곳으로 가 버리므로.)
     */
    private String keepRect(String text, int index, int parentIndex, Rect desired) {
        try {
            UiDocument tmp = host.load(text);
            tmp.layout(docW(), docH());
            LayoutBox box = boxByIndex(tmp.layout(), index), parent = boxByIndex(tmp.layout(), parentIndex);
            if (box == null || parent == null || isFlow(parent)) return text;
            Map<String, String> props = Geometry.propsForRect(false, box.style.get("anchor"),
                    Insets.parse(box.style.get("margin"), Insets.ZERO), contentRect(parent), rectOf(box), desired,
                    Geometry.UNKNOWN, Geometry.UNKNOWN);
            return EditorOps.setProps(text, index, props, p -> box.style.get(p) != null && box.node.attr(p) == null);
        } catch (UiParseException | IllegalArgumentException e) {
            return text;
        }
    }

    // ================= 입력: 키보드 =================

    public boolean charTyped(char c) {
        if (itemPicker.isOpen()) {
            itemPicker.type(c);
            return true;
        }
        if (editKey == null && c == '?' && exitDialog == false) {
            help.open = !help.open;
            return true;
        }
        if (editKey == null && !exitDialog && !help.open && !xmlView.open && (c == '[' || c == ']')) {
            reorder(c == ']' ? 1 : -1);                         // [ ] : 같은 부모 안에서 순서 이동(뒤로/앞으로)
            return true;
        }
        if (editKey == null || c < 32 || c == 127) return false;
        fieldText.insertText(String.valueOf(c));
        if (COLOR_KEYS.contains(editKey)) previewField();
        return true;
    }

    public boolean keyPressed(EditorKey key, boolean ctrl, boolean shift) {
        if (exitDialog) {
            if (dialogRecover) {
                if (key == EditorKey.ESCAPE) {
                    dialogRecover = false;
                    exitDialog = false;
                    recovery = null;
                } else if (key == EditorKey.ENTER) recoverNow();
                return true;
            }
            if (key == EditorKey.ESCAPE) exitDialog = false;
            else if (key == EditorKey.ENTER) saveAndExit();
            return true;
        }
        if (itemPicker.isOpen()) {                                 // 아이템 선택 상자: Esc 닫기, Backspace 검색어 지우기
            if (key == EditorKey.ESCAPE) closeItemPicker();
            else if (key == EditorKey.BACKSPACE) itemPicker.backspace();
            return true;
        }
        if (help.open) {
            if (key == EditorKey.ESCAPE || key == EditorKey.F1) help.open = false;
            return true;
        }
        if (xmlView.open) {
            if (key == EditorKey.ESCAPE) xmlView.open = false;
            return true;
        }
        if (menuNear != null) {
            if (key == EditorKey.ESCAPE) closeMenu();
            return true;
        }
        if (key == EditorKey.F1 && editKey == null) {
            help.open = true;
            return true;
        }
        if (editKey != null) return editKeyPressed(key, ctrl, shift);
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
                case C -> {
                    copyElement(false);
                    return true;
                }
                case X -> {
                    copyElement(true);
                    return true;
                }
                case V -> {
                    pasteElement();
                    return true;
                }
                case G -> {
                    if (shift) unwrapSelected();
                    else wrapSelected("column");
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
                if (!multi.isEmpty()) {                       // 여러 개 선택 중이면 먼저 대표만 남긴다
                    multi.clear();
                    return true;
                }
                if (selected < 0) requestExit();          // 아무것도 선택하지 않았으면 에디터를 닫는다
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

    /**
     * 칸 편집 중의 키. 채팅창과 같은 조합을 지원한다:
     * Shift+방향/Home/End = 선택 확장, Ctrl+방향 = 단어 이동, Ctrl+Backspace/Delete = 단어 삭제,
     * Ctrl+A/C/X/V = 전체 선택/복사/잘라내기/붙여넣기.
     */
    private boolean editKeyPressed(EditorKey key, boolean ctrl, boolean shift) {
        int len = fieldText.buf.length();
        switch (key) {
            case ENTER -> {
                if (suggestIndex >= 0 && suggestIndex < suggestions.size()) acceptSuggestion(suggestions.get(suggestIndex));
                else commitField();
            }
            case ESCAPE -> cancelField();
            case TAB -> {                                   // 채팅처럼 Tab 으로 자동 완성
                if (!suggestions.isEmpty()) acceptSuggestion(suggestions.get(Math.max(0, suggestIndex)));
            }
            case UP -> {
                if (!suggestions.isEmpty()) suggestIndex = suggestIndex <= 0 ? suggestions.size() - 1 : suggestIndex - 1;
                else if (NUMBERS.contains(editKey)) stepEditingNumber(1, shift);
            }
            case DOWN -> {
                if (!suggestions.isEmpty()) suggestIndex = (suggestIndex + 1) % suggestions.size();
                else if (NUMBERS.contains(editKey)) stepEditingNumber(-1, shift);
            }
            case BACKSPACE -> {
                if (fieldText.hasSelection()) fieldText.deleteSelection();
                else if (fieldText.cursor > 0) fieldText.deleteRange(ctrl ? fieldText.wordLeft(fieldText.cursor) : fieldText.cursor - 1, fieldText.cursor);
            }
            case DELETE -> {
                if (fieldText.hasSelection()) fieldText.deleteSelection();
                else if (fieldText.cursor < len) fieldText.deleteRange(fieldText.cursor, ctrl ? fieldText.wordRight(fieldText.cursor) : fieldText.cursor + 1);
            }
            case LEFT -> {
                if (!shift && fieldText.hasSelection() && !ctrl) fieldText.cursor = fieldText.selectionStart();
                else fieldText.cursor = Math.max(0, ctrl ? fieldText.wordLeft(fieldText.cursor) : fieldText.cursor - 1);
                if (!shift) fieldText.anchor = fieldText.cursor;
            }
            case RIGHT -> {
                if (!shift && fieldText.hasSelection() && !ctrl) fieldText.cursor = fieldText.selectionEnd();
                else fieldText.cursor = Math.min(len, ctrl ? fieldText.wordRight(fieldText.cursor) : fieldText.cursor + 1);
                if (!shift) fieldText.anchor = fieldText.cursor;
            }
            case HOME -> {
                fieldText.cursor = 0;
                if (!shift) fieldText.anchor = fieldText.cursor;
            }
            case END -> {
                fieldText.cursor = len;
                if (!shift) fieldText.anchor = fieldText.cursor;
            }
            case A -> {
                if (ctrl) {
                    fieldText.anchor = 0;
                    fieldText.cursor = len;
                }
            }
            case C -> {
                if (ctrl && fieldText.hasSelection()) host.setClipboard(fieldText.selectedText());
            }
            case X -> {
                if (ctrl && fieldText.hasSelection()) {
                    host.setClipboard(fieldText.selectedText());
                    fieldText.deleteSelection();
                }
            }
            case V -> {
                if (ctrl) fieldText.insertText(host.clipboard());
            }
            default -> { }
        }
        if (editKey != null && COLOR_KEYS.contains(editKey)) previewField();
        return true;
    }

    // ---- 칸 안의 텍스트 선택/편집 도우미 ----

    /** 화면 x 좌표에 가장 가까운 글자 사이 위치(칸 안에서 현재 보이는 구간 기준). */
    private int indexAt(int x) {
        int rel = x - editTextX;
        String s = fieldText.buf.toString();
        int best = editViewStart, prev = 0;
        for (int i = editViewStart + 1; i <= s.length(); i++) {
            int w = tm.width(s.substring(editViewStart, i));
            if (rel < w) return (rel - prev < w - rel) ? i - 1 : i;
            prev = w;
            best = i;
        }
        return best;
    }

    /** 편집 중인 칸을 눌렀을 때: 커서 찍기, 더블클릭이면 그 단어 선택. 누른 채 끌면 선택 범위가 늘어난다. */
    private void onFieldPress(int x) {
        int idx = indexAt(x);
        long now = System.nanoTime();
        boolean doubleClick = now - lastFieldClickNanos < 350_000_000L && Math.abs(idx - lastFieldClickIndex) <= 1;
        lastFieldClickNanos = now;
        lastFieldClickIndex = idx;
        if (doubleClick) {
            fieldText.selectWordAt(idx);
            return;
        }
        fieldText.anchor = fieldText.cursor = idx;
        fieldDragging = true;
    }

    // ================= 명령 =================

    private boolean canUndoAny() { return Boolean.TRUE.equals(hist.peek()) || session.canUndo(); }

    private boolean canRedoAny() { return Boolean.TRUE.equals(redoHist.peek()) || session.canRedo(); }

    public void undo() {
        multi.clear();
        if (Boolean.TRUE.equals(hist.peek())) {                    // 가장 최근이 외부 스타일시트 편집이면 그 파일을 되돌린다
            ExtEdit e = extUndo.peek();
            if (!writeExt(e, e.before())) return;
            extUndo.pop();
            hist.pop();
            extRedo.push(e);
            redoHist.push(true);
            reload();
            setStatus("실행 취소 (" + e.href() + ")", false);
            return;
        }
        if (session.undo()) {
            if (!hist.isEmpty()) hist.pop();
            redoHist.push(false);
            reload();
            setStatus("실행 취소", false);
        }
    }

    public void redo() {
        multi.clear();
        if (Boolean.TRUE.equals(redoHist.peek())) {
            ExtEdit e = extRedo.peek();
            if (!writeExt(e, e.after())) return;
            extRedo.pop();
            redoHist.pop();
            extUndo.push(e);
            hist.push(true);
            reload();
            setStatus("다시 실행 (" + e.href() + ")", false);
            return;
        }
        if (session.redo()) {
            if (!redoHist.isEmpty()) redoHist.pop();
            hist.push(false);
            reload();
            setStatus("다시 실행", false);
        }
    }

    public boolean save() {
        commitFieldQuietly();
        String disk = host.diskText();
        if (!overwriteArmed && !java.util.Objects.equals(disk, diskBase)) {       // 에디터 밖에서 파일이 바뀌었다
            overwriteArmed = true;
            setStatus("파일이 에디터 밖에서 바뀌었어요 — 한 번 더 저장하면 덮어씁니다", true);
            return false;
        }
        try {
            host.save(session.text());
            diskBase = host.diskText();
            overwriteArmed = false;
            session.markSaved();
            host.clearAutosave();
            autosavedText = null;
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
        int[] c = visibleCenterDoc();                      // 새 요소는 지금 보고 있는 화면 한가운데에 놓는다(패널에 가려진 구석이 아니라)
        String text = growToFit(placeAt(r.text(), r.index(), parentIdx, c[0], c[1]), r.index());
        if (applyEdit(src -> text)) selected = r.index();
    }

    /**
     * 요소를 넣은 뒤, 그 요소를 품은 row/column 이 <b>고정 크기(px)</b>로 지정돼 있고 내용보다 작아졌다면 내용이 들어갈 만큼 키운다.
     * (자동 크기인 컨테이너는 이미 내용에 맞으므로 건드리지 않는다.) 바깥쪽 컨테이너도 이어서 확인한다.
     */
    private String growToFit(String text, int index) {
        String t = text;
        for (int guard = 0; guard < 8; guard++) {
            UiDocument tmp;
            try {
                tmp = host.load(t);
                tmp.layout(docW(), docH());
            } catch (UiParseException e) {
                return t;
            }
            LayoutBox b = boxByIndex(tmp.layout(), index);
            if (b == null) return t;
            boolean changed = false;
            for (LayoutBox a = displayParent(b); a != null && !changed; a = displayParent(a)) {
                if (!isSizedFlow(a) && !isScroll(a)) continue;
                int[] mn = tmp.minimumSize(a);
                Map<String, String> props = new LinkedHashMap<>();
                if (explicitPx(a.style.get("width")) != null && a.w < mn[0]) props.put("width", Integer.toString(mn[0]));
                if (!isScroll(a) && explicitPx(a.style.get("height")) != null && a.h < mn[1]) props.put("height", Integer.toString(mn[1]));
                if (props.isEmpty()) continue;
                final LayoutBox box = a;
                t = EditorOps.setProps(t, a.node.sourceIndex(), props, p -> box.style.get(p) != null && box.node.attr(p) == null);
                changed = true;
            }
            if (!changed) return t;
        }
        return t;
    }

    /** "120" / "120px" 이면 120, 그 밖(fill, %, auto, 없음)이면 null. */
    private static Integer explicitPx(String v) {
        if (v == null) return null;
        String s = v.trim().toLowerCase();
        if (s.endsWith("px")) s = s.substring(0, s.length() - 2);
        try {
            return Math.round(Float.parseFloat(s));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 지금 보이는 작업 영역의 한가운데(문서 좌표). */
    private int[] visibleCenterDoc() {
        Rect v = centerView();
        return new int[] {v.centerX() - panX, v.centerY() - panY};
    }

    public void duplicate() {
        List<Integer> sel = editableSelection();
        if (sel.size() > 1) {                                               // 여러 개: 큰 번호부터 복제해야 앞쪽 번호가 밀리지 않는다
            List<Integer> desc = new ArrayList<>(sel);
            desc.sort(java.util.Comparator.reverseOrder());
            String text = session.text();
            for (int i : desc) text = EditorOps.duplicate(text, i).text();
            String result = text;
            if (applyEdit(src -> result)) {
                multi.clear();
                selected = -1;
                setStatus(sel.size() + "개 복제됨", false);
            }
            return;
        }
        if (selected <= 0) return;
        EditorOps.Added r = EditorOps.duplicate(session.text(), selected);
        String text = growToFit(r.text(), r.index());
        if (applyEdit(src -> text)) selected = r.index();
    }

    public void deleteSelected() {
        List<Integer> sel = editableSelection();
        if (sel.size() > 1) {                                               // 여러 개: 큰 번호(자손)부터 지운다
            List<Integer> desc = new ArrayList<>(sel);
            desc.sort(java.util.Comparator.reverseOrder());
            String text = session.text();
            for (int i : desc) {
                if (i < XmlSource.parse(text).all().size()) text = EditorOps.delete(text, i).text();
            }
            String result = text;
            if (applyEdit(src -> result)) {
                multi.clear();
                selected = -1;
                setStatus(sel.size() + "개 삭제됨 (Ctrl+Z 로 되돌릴 수 있어요)", false);
            }
            return;
        }
        deleteIndex(selected);
    }

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
        // 기준점에 맞는 오프셋으로 한 칸 옮기되, 부모 밖으로는 나가지 않는다. 여러 개를 골랐다면 모두 같이 옮긴다.
        List<Integer> targets = new ArrayList<>();
        targets.add(selected);
        for (int i : multi) if (i > 0 && i != selected) targets.add(i);
        String text = session.text();
        boolean moved = false;
        for (int idx : targets) {
            LayoutBox box = boxByIndex(doc.layout(), idx);
            if (box == null || box.parent == null || isFlow(box.parent)) continue;
            Rect before = rectOf(box);
            Rect content = contentRect(box.parent);
            Rect desired = Geometry.clamp(before.translate(dx * step, dy * step), content);
            if (desired.equals(before)) continue;
            Map<String, String> props = Geometry.propsForRect(false, box.style.get("anchor"),
                    Insets.parse(box.style.get("margin"), Insets.ZERO), content, before, desired,
                    box.style.integer("x", 0), box.style.integer("y", 0));
            final LayoutBox fb = box;
            text = EditorOps.setProps(text, idx, props, p -> fb.style.get(p) != null && fb.node.attr(p) == null);
            moved = true;
        }
        if (!moved) return;
        String result = text;
        java.util.Set<Integer> keep = new java.util.LinkedHashSet<>(multi);
        if (applyEdit(src -> result)) multi.addAll(keep);               // 속성만 바뀌므로 번호는 그대로다
    }

    // ---- 속성 필드 편집 ----

    private void startEdit(String key, String value, Rect fieldRect) {
        commitFieldQuietly();
        editKey = key;
        fieldText.setAllSelected(value);              // 칸을 누르면 전체가 선택된다
        editRect = fieldRect;
        suggestIndex = -1;
        suggestQuery = null;
        previewBox = null;
        if (COLOR_KEYS.contains(key)) picker.open(value);
        // 게임이 SDL 이면 글자 입력(IME 포함)을 따로 켜 줘야 한다. 켜지 않으면 키 이벤트만 오고 글자는 들어오지 않는다.
        host.textInput(true, fieldRect.x(), fieldRect.y(), fieldRect.w(), fieldRect.h());
    }

    private void cancelField() {
        boolean wasEditing = editKey != null;
        if (previewActive) {                    // 색 고르기 미리보기를 되돌린다(확정할 때는 이어서 진짜 편집이 적용된다)
            previewActive = false;
            session.cancelTransient();
            editKey = null;
            reload();
        }
        editKey = null;
        editRect = null;
        popupRect = null;
        suggestions = List.of();
        suggestIndex = -1;
        picker.release();
        fieldDragging = false;
        if (wasEditing) host.textInput(false, 0, 0, 0, 0);
    }

    private void commitFieldQuietly() {
        if (editKey != null) commitField();
    }

    private void commitField() {
        String key = editKey;
        String raw = fieldText.buf.toString().trim();
        cancelField();
        if (key != null) applyValue(key, raw);
    }

    /** 속성 하나를 값으로 바꾼다(빈 값이면 속성 제거). */
    private void applyValue(String key, String raw) {
        if (key.equals(FILTER_KEY)) {                                      // 구조 트리 검색어
            treeFilter = raw;
            outlinerScroll = 0;
            return;
        }
        LayoutBox box = selectedBox();
        if (key == null || box == null) return;
        String sheetHref = StyleText.externalHref(key);
        if (sheetHref != null) {                                                  // 외부 스타일시트 규칙
            applyExternalEdit(sheetHref, key, raw);
            return;
        }
        if (key.startsWith(StyleText.KEY) || key.startsWith(StyleText.ADD_KEY) || key.startsWith(StyleText.NEW_KEY) || StyleText.isKeyframeKey(key)) {       // <style> 규칙의 선언 편집
            try {
                String next = StyleText.apply(session.source(), key, raw);
                int before = session.source().all().size();
                if (!applyEdit(src -> next)) setStatus("바뀐 것이 없어요", false);
                else if (selected > 0 && session.source().all().size() > before) selected += session.source().all().size() - before;   // <style> 블록이 새로 생기면 뒤 요소 번호가 밀린다
            } catch (IllegalArgumentException e) {
                setStatus(e.getMessage(), true);
            }
            return;
        }
        String value0 = raw.isEmpty() ? null : raw;
        final String value = clampFlowSize(box, key, value0);
        int idx = selected;
        LayoutBox parentBox = box.parent;
        if (key.equals("anchor") && parentBox != null && !isFlow(parentBox)) {
            // 기준점만 바꾸면 요소가 엉뚱한 곳으로 가 버리므로, 같은 자리에 그대로 있도록 x/y 를 새 기준점에 맞게 다시 계산한다.
            Rect keep = rectOf(box);
            int parentIdx = parentBox.node.sourceIndex();
            applyEdit(src -> {
                String t1 = EditorOps.setProp(src, src.element(idx), "anchor", value,
                        box.style.get("anchor") != null && box.node.attr("anchor") == null);
                return keepRect(t1, idx, parentIdx, keep);
            });
            return;
        }
        applyEdit(fieldPatch(box, idx, key, raw, value));
    }

    private static Function<XmlSource, String> fieldPatch(LayoutBox box, int idx, String key, String raw, String value) {
        return src -> {
            Element el = src.element(idx);
            if (el.tag.equals("use")) return XmlPatcher.setAttr(src, el, key, value);
            if (key.equals("text")) return EditorOps.setText(src, el, raw);
            if (ATTR_KEYS.contains(key)) return XmlPatcher.setAttr(src, el, key, value);
            return EditorOps.setProp(src, el, key, value, box.style.get(key) != null && box.node.attr(key) == null);
        };
    }

    /** row/column 의 너비/높이를 내용(자식들)보다 작게 지정하면 내용 크기로 맞춘다. */
    private String clampFlowSize(LayoutBox box, String key, String value) {
        if (value == null || !(isSizedFlow(box) || isScroll(box)) || !(key.equals("width") || key.equals("height"))) return value;
        if (isScroll(box) && key.equals("height")) return value;                  // scroll 은 높이를 줄여도 된다(스크롤되므로)
        Integer px = explicitPx(value);
        if (px == null) return value;
        int[] mn = doc.minimumSize(box);
        int min = key.equals("width") ? mn[0] : mn[1];
        if (px >= min) return value;
        setStatus("안의 요소보다 작게는 줄일 수 없어서 " + min + " 으로 맞췄어요", true);
        return Integer.toString(min);
    }

    // ---- 색 고르기 / 자동 완성 ----

    private boolean pickerActive() { return editKey != null && COLOR_KEYS.contains(editKey); }

    /** 피커를 만졌을 때: 칸의 글자를 바꾸고 화면에 바로 미리 보여 준다(확정 전에는 되돌릴 수 있는 임시 편집). */
    private void setBufferFromPicker(String hex) {
        fieldText.setCaretAtEnd(hex);
        previewField();
    }

    private LayoutBox previewBox;

    private void previewField() {
        LayoutBox box = selectedBox();
        if (box == null || editKey == null || !COLOR_KEYS.contains(editKey)) return;
        String raw = fieldText.buf.toString().trim();
        if (Colors.tryParse(raw) == null) return;
        if (!previewActive) {
            session.beginTransient();
            previewActive = true;
            previewBox = box;                    // 인라인/속성 중 어디에 쓸지는 편집을 시작할 때의 상태로 정한다
        }
        String key = editKey;
        if (session.setTransient(fieldPatch(previewBox, selected, key, raw, raw))) {
            reload();
            editKey = key;
        }
    }

    /** 편집 중인 칸의 자동 완성 후보(onClick 등). */
    private List<String> currentSuggestions() {
        if (editKey == null) return List.of();
        List<String> all = host.suggestions(editKey);
        if (all.isEmpty()) return List.of();
        String q = fieldText.buf.toString().trim().toLowerCase();
        List<String> out = new ArrayList<>();
        for (String c : all) {
            if (c.equalsIgnoreCase(q)) return List.of();     // 이미 완성된 값이면 목록을 닫는다
            if (q.isEmpty() || c.toLowerCase().contains(q)) out.add(c);
        }
        out.sort((a, b) -> Boolean.compare(!a.toLowerCase().startsWith(q), !b.toLowerCase().startsWith(q)));
        return out.size() > SUGGEST_MAX ? new ArrayList<>(out.subList(0, SUGGEST_MAX)) : out;
    }

    /** 숫자 칸을 편집하는 중 ↑/↓ 로 값을 올리고 내린다(Shift 면 8). */
    private void stepEditingNumber(int dir, boolean big) {
        String key = editKey, cur = fieldText.buf.toString().trim();
        cancelField();
        adjustNumber(key, cur, dir, big);
    }

    private void acceptSuggestion(String value) {
        fieldText.setCaretAtEnd(value);
        suggestIndex = -1;
    }

    private void paintSuggestions(UiGfx g) {
        if (editKey == null || editRect == null || COLOR_KEYS.contains(editKey)) return;
        String q = fieldText.buf.toString();
        if (!q.equals(suggestQuery)) {                     // 글자가 바뀌면 선택을 처음(없음)으로
            suggestQuery = q;
            suggestIndex = -1;
        }
        suggestions = currentSuggestions();
        if (suggestions.isEmpty()) return;
        int w = 0;
        for (String c : suggestions) w = Math.max(w, tm.width(c));
        w = Math.max(editRect.w(), Math.min(RIGHT_W - 8, w + 8));
        int x = Math.min(editRect.x(), viewW - w - 2);
        int h = suggestions.size() * ROW_H + 2;
        int y = editRect.bottom() + 1;
        if (y + h > viewH) y = Math.max(TOOLBAR_H, editRect.y() - h - 1);
        popupRect = new Rect(x, y, w, h);
        g.fillRect(x - 1, y - 1, w + 2, h + 2, C_SEL);
        g.fillRect(x, y, w, h, 0xFF161820);
        regions.add(new Region(popupRect, null));
        for (int i = 0; i < suggestions.size(); i++) {
            Rect row = new Rect(x, y + 1 + i * ROW_H, w, ROW_H);
            boolean hot = i == suggestIndex || row.contains(mouseX, mouseY);
            if (hot) g.fillRect(row.x(), row.y(), row.w(), row.h(), i == suggestIndex ? C_ROW_SEL : C_ROW_HOVER);
            g.drawText(fit(suggestions.get(i), w - 6), x + 3, row.y() + 1, C_TEXT, false);
            final String value = suggestions.get(i);
            regions.add(new Region(row, () -> acceptSuggestion(value)));
        }
    }

    private void paintPicker(UiGfx g) {
        if (!pickerActive() || editRect == null) return;
        picker.syncFrom(fieldText.buf.toString().trim());
        picker.layout(editRect, viewW - RIGHT_W, RIGHT_W, TOOLBAR_H, viewH);
        popupRect = picker.area();
        picker.paint(g);
        regions.add(new Region(picker.area(), null));
    }

    private String srcText;
    private XmlSource srcSource;

    private Element sourceElement(int index) {
        if (index < 0) return null;
        String t = session.text();
        if (!t.equals(srcText)) {
            srcText = t;
            srcSource = XmlSource.parse(t);
        }
        return srcSource.element(index);
    }

    private List<PropRow> rowsFor(LayoutBox b) {
        List<PropRow> rows = PropertyModel.rows(b, sourceElement(b.node.sourceIndex()), srcSource);
        if (b.node.sourceIndex() >= 0 && srcSource != null) {                    // 이 요소에 적용되는 <style> 규칙(고칠 수 있다)
            List<PropRow> extra = StyleText.propRows(b, srcSource);
            List<StyleText.Sheet> sheets = new ArrayList<>();
            for (String href : StyleText.linkedSheets(srcSource)) {
                String css = sheetText(href);
                if (css != null) sheets.add(new StyleText.Sheet(href, css));
            }
            extra.addAll(StyleText.externalRows(b, sheets));
            var spec = dev.magyul.betteruiux.core.anim.AnimationSpec.parse(b.style.get("animation"));   // 이 요소가 쓰는 @keyframes
            if (spec != null) {
                extra.addAll(StyleText.keyframeRows(spec.name(), StyleText.INLINE_SHEET, srcSource.text(), StyleText.keyframes(srcSource)));
                for (StyleText.Sheet sh : sheets) extra.addAll(StyleText.keyframeRows(spec.name(), sh.href(), sh.text(), StyleText.cssKeyframes(sh.text())));
            }
            if (!extra.isEmpty()) {
                rows = new ArrayList<>(rows);
                rows.addAll(extra);
            }
        }
        return rows;
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

    private static final String FILTER_KEY = "§filter";
    private String treeFilter = "";

    /** 지금 적용할 검색어(편집 중이면 입력 중인 글자로 바로 걸러진다). */
    private String activeFilter() {
        return (FILTER_KEY.equals(editKey) ? fieldText.buf.toString() : treeFilter).trim().toLowerCase();
    }

    private boolean subtreeMatches(LayoutBox b, String f) {
        if (rowLabel(b).toLowerCase().contains(f)) return true;
        for (LayoutBox k : displayChildren(b)) if (subtreeMatches(k, f)) return true;
        return false;
    }

    private void buildFiltered(LayoutBox b, int depth, List<TreeRow> out, String f) {
        if (!subtreeMatches(b, f)) return;
        List<LayoutBox> kids = displayChildren(b);
        out.add(new TreeRow(b, depth, !kids.isEmpty(), false, keyOf(b)));
        for (LayoutBox k : kids) buildFiltered(k, depth + 1, out, f);
    }

    private void buildRows(LayoutBox b, int depth, List<TreeRow> out) {
        String f = activeFilter();
        if (!f.isEmpty()) {
            buildFiltered(b, depth, out, f);
            return;
        }
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
     * 구조 트리에서 고르는 등 "찾아 달라"는 요청이 있을 때만, 선택한 요소가 양옆 패널에 가려지거나 화면 밖이면
     * <b>보이게 되는 데 필요한 만큼만</b> 화면을 옮긴다(작업 영역 가장자리에 여유를 두고). 가운데로 정렬하지 않는 이유:
     * 화면 구석의 요소를 가운데로 데려오면 게임 화면 전체가 크게 밀려 "screen 위치가 어긋난" 것처럼 보이기 때문이다.
     * 축마다 따로 판단하고, 작업 영역보다 큰 쪽의 축은 옮기지 않는다. 끄는 중에는 요청을 보류했다가 놓은 뒤에 처리한다.
     */
    private void autoPan() {
        if (!revealRequested || drag != null || dnd != null) return;
        revealRequested = false;
        LayoutBox sb = selectedBox();
        if (sb == null) return;
        Rect v = centerView();
        Rect r = rectOf(sb).translate(targetPanX, targetPanY);
        int m = REVEAL_MARGIN;
        // 이미 완전히 보이는 축은 건드리지 않는다(가장자리에 딱 붙어 있어도 여유를 맞추려고 밀지 않는다).
        boolean xVisible = r.x() >= v.x() && r.right() <= v.right();
        boolean yVisible = r.y() >= v.y() && r.bottom() <= v.bottom();
        if (!xVisible && r.w() <= v.w() - 2 * m) {
            if (r.x() < v.x()) targetPanX += v.x() + m - r.x();
            else targetPanX += v.right() - m - r.right();
        }
        if (!yVisible && r.h() <= v.h() - 2 * m) {
            if (r.y() < v.y()) targetPanY += v.y() + m - r.y();
            else targetPanY += v.bottom() - m - r.bottom();
        }
    }

    // ================= 그리기 =================

    private void refreshRegions() { paint(new DrawList()); }

    /** 편집 중인 문서를 현재 화면 이동 위치에 맞춰 그린다. (호버/클릭 효과는 끈다.) */
    public void paintDocument(UiGfx g) {
        doc.forcePseudo(selected, previewState == 1, previewState == 2);   // 상태 미리보기(선택한 요소만)
        g.pushTransform(panX, panY, 1f, 0f, 0f, 0f);
        doc.paint(g, -1, -1);
        g.popTransform();
    }

    public void paint(UiGfx g) {
        regions.clear();
        revealSelectedTab();
        revealSelectedInScroll();
        realMouseX = mouseX;
        realMouseY = mouseY;
        if (exitDialog) {                                    // 메시지 박스 뒤의 모든 것(요소 윤곽, 버튼, 패널)에 호버가 전해지지 않게 한다
            mouseX = -9999;
            mouseY = -9999;
        }
        persistPrefs();
        autosaveTick();
        updateToolbarPeek();
        updateSidePeek();
        autoPan();
        stepPan();
        g.pushTransform(panX, panY, 1f, 0f, 0f, 0f);
        paintCanvasOverlay(g);
        g.popTransform();
        if (leftVisible()) paintLeftPanel(g);
        if (rightVisible()) paintProperties(g);
        if (selected != lastSelected) {
            lastSelected = selected;
            tempDismissed = false;
            propScroll = 0;
        }
        if (toolbarVisible()) paintToolbar(g);
        else paintPeekSliver(g);
        paintSideSlivers(g);
        if (panelsVisible && !dragStarted()) paintTabs(g);
        popupRect = null;
        paintPicker(g);
        paintSuggestions(g);
        paintDndFeedback(g);
        if (exitDialog) {                                    // 메시지 박스 뒤의 것들에는 호버가 전해지지 않게 한다
            mouseX = realMouseX;
            mouseY = realMouseY;
        }
        paintToolTip(g);
        tipNow = null;
        paintMenu(g);
        paintItemPicker(g);
        paintXmlView(g);
        help.paint(g, viewW, viewH, regions);
        paintExitDialog(g);
        paintStatus(g);
        g.requestCursor(cursorNow());
    }

    /** 마우스 아래에 있는 것에 맞는 포인터 모양. */
    private Cursor cursorNow() {
        if (panning || (drag != null && drag.started) || (dnd != null && dnd.started)) return Cursor.MOVE;
        if (editKey != null && editRect != null && editRect.contains(mouseX, mouseY)) return Cursor.TEXT;
        if (pickerActive() && picker.contains(mouseX, mouseY)) return Cursor.CROSSHAIR;
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (r.passthrough() || !r.rect().contains(mouseX, mouseY)) continue;
            return r.cursor() == null ? Cursor.DEFAULT : r.cursor();
        }
        LayoutBox sel = selectedBox();
        int cx = mouseX - panX, cy = mouseY - panY;
        if (sel != null && sel.parent != null) {
            Handle h = handleAt(sel, cx, cy);
            if (h != null) {
                return switch (h) {
                    case E, W -> Cursor.RESIZE_H;
                    case N, S -> Cursor.RESIZE_V;
                    default -> Cursor.MOVE;
                };
            }
            if (sel.contains(cx, cy)) return Cursor.MOVE;
        }
        return hoverIndex > 0 ? Cursor.POINTER : Cursor.DEFAULT;
    }

    private static int tagColor(String tag) {
        return switch (tag) {
            case "screen", "panel", "row", "column", "stack", "fragment" -> 0xFFD08A2E;
            case "label" -> 0xFF4C8DDB;
            case "button" -> 0xFF8A5CD6;
            case "image", "video" -> 0xFF45A86B;
            case "spacer", "separator" -> 0xFF7A7F8F;
            case "item" -> 0xFF45A86B;
            case "checkbox", "radio", "slider", "textfield", "select", "progress" -> 0xFFC2547A;
            case "grid", "scroll", "tabs", "tab", "dialog" -> 0xFFD08A2E;
            default -> 0xFF666B7A;
        };
    }

    /** 문서 좌표계(팬 변환 안)에서 그리는 캔버스 위 표시: 호버/선택 윤곽, 손잡이, 정렬선. */
    private void paintCanvasOverlay(UiGfx g) {
        paintScreenFrame(g);
        boolean dragging = drag != null || dnd != null || panning;
        LayoutBox hover = !dragging && !overPanel(mouseX, mouseY) ? hitSource(mouseX - panX, mouseY - panY) : null;
        hoverIndex = hover == null ? -1 : hover.node.sourceIndex();
        if (hover != null && hoverIndex != selected) outline(g, rectOf(hover), C_HOVER);

        for (int mi : multi) {
            LayoutBox mb = boxByIndex(doc.layout(), mi);
            if (mb != null) outline(g, rectOf(mb), C_MULTI);
        }
        if (marqActive) {
            Rect mr = marqueeRect();
            g.fillRect(mr.x(), mr.y(), mr.w(), mr.h(), 0x3055C8FF);
            outline(g, mr, C_MULTI);
        }
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
            String label = r.w() + "x" + r.h() + "  @" + r.x() + "," + r.y() + (multi.isEmpty() ? "" : "  외 " + multi.size() + "개");
            int ly = r.y() >= tm.lineHeight() + 8 ? r.y() - tm.lineHeight() - 6 : r.bottom() + 6;
            g.fillRect(r.x(), ly - 2, tm.width(label) + 6, tm.lineHeight() + 4, 0xFF000000);
            g.drawText(label, r.x() + 3, ly, C_SEL, false);
        }
        for (Geometry.Guide gd : guides) {
            if (gd.vertical()) g.fillRect(gd.pos(), -4000, 1, 8000 + viewH, C_GUIDE);
            else g.fillRect(-4000, gd.pos(), 8000 + viewW, 1, C_GUIDE);
        }
    }

    /**
     * 화면을 옮겨 둔 상태에서는 "실제 게임 화면의 경계"가 어디인지 알 수 있게, 경계 밖을 어둡게 하고 파란 테두리를 그린다.
     * (옮기지 않았을 때는 경계가 창 가장자리와 같으므로 아무것도 그리지 않는다.)
     */
    private void paintScreenFrame(UiGfx g) {
        if (panX == 0 && panY == 0 && previewW == 0) return;
        int far = 4000, dw = docW(), dh = docH();
        g.fillRect(-far, -far, 2 * far + dw, far, SCREEN_OUTSIDE);                       // 위
        g.fillRect(-far, dh, 2 * far + dw, far, SCREEN_OUTSIDE);                         // 아래
        g.fillRect(-far, 0, far, dh, SCREEN_OUTSIDE);                                    // 왼쪽
        g.fillRect(dw, 0, far, dh, SCREEN_OUTSIDE);                                      // 오른쪽
        outline(g, new Rect(0, 0, dw, dh), SCREEN_FRAME);
        if (previewW > 0) g.drawText(dw + "x" + dh, 3, dh - tm.lineHeight() - 2, C_DIM, false);   // 미리보기 크기 표시
    }

    /** 색의 밝기를 delta 만큼 올리거나 내린다(알파 유지). */
    private static int shade(int argb, int delta) {
        int r = Math.max(0, Math.min(255, ((argb >> 16) & 0xFF) + delta));
        int gr = Math.max(0, Math.min(255, ((argb >> 8) & 0xFF) + delta));
        int b = Math.max(0, Math.min(255, (argb & 0xFF) + delta));
        return (argb & 0xFF000000) | (r << 16) | (gr << 8) | b;
    }

    private static void outline(UiGfx g, Rect r, int color) {
        g.fillRect(r.x(), r.y(), r.w(), 1, color);
        g.fillRect(r.x(), r.bottom() - 1, r.w(), 1, color);
        g.fillRect(r.x(), r.y(), 1, r.h(), color);
        g.fillRect(r.right() - 1, r.y(), 1, r.h(), color);
    }

    private int button(UiGfx g, int x, int y, String label, boolean enabled, Runnable action) {
        return button(g, x, y, label, enabled, action, C_BTN);
    }

    private int button(UiGfx g, int x, int y, String label, boolean enabled, Runnable action, int bg) {
        int w = tm.width(label) + 8, h = TOOLBAR_H - 4;
        boolean over = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        if (over) tipNow = tipFor(label);
        boolean pressed = over && mouseDown;
        g.fillRect(x, y, w, h, !enabled ? C_BTN_OFF : pressed ? shade(bg, -28) : over ? shade(bg, 32) : bg);
        if (over) outline(g, new Rect(x, y, w, h), pressed ? C_SEL : 0x80FFFFFF);
        g.drawText(label, x + 4, y + (h - tm.lineHeight()) / 2 + 1, enabled ? C_TEXT : C_DIM, false);
        regions.add(new Region(new Rect(x, y, w, h), enabled ? action : null));
        return w + 2;
    }

    private static final Map<String, String> TIPS = Map.ofEntries(
            Map.entry("File", "다른 UI 열기 / 새 UI 만들기"), Map.entry("Save", "저장 (Ctrl+S)"), Map.entry("Pub", "서버로 발행 (저장 후, 서버 권한 레벨 4 필요)"), Map.entry("Undo", "실행 취소 (Ctrl+Z)"), Map.entry("Redo", "다시 실행 (Ctrl+Y)"),
            Map.entry("Dup", "선택한 요소 복제 (Ctrl+D)"), Map.entry("Del", "선택한 요소 삭제 (Delete)"),
            Map.entry("Wrap", "선택을 컨테이너로 감싸기 / 풀기 (Ctrl+G)"), Map.entry("Align", "부모 안에서 정렬(기준점 지정)"),
            Map.entry("View", "XML 보기, 상태 미리보기, 도움말"), Map.entry("Grid", "8px 격자에 맞추기 켜고 끄기"),
            Map.entry("Anchor", "끌어 놓을 때 기준점(anchor) 자동 지정 켜고 끄기"), Map.entry("Fit", "화면 이동을 처음 위치로"),
            Map.entry("Hide", "툴바 숨기기 (맨 위에 마우스를 올리면 나타남)"), Map.entry("Pin", "툴바를 항상 보이게"),
            Map.entry("Exit", "에디터 닫기 (F8)"), Map.entry("?", "도움말 (F1)"), Map.entry("...", "창이 좁아 숨겨진 버튼 더 보기"));

    private String tipNow, tipPrev;
    private long tipSince;

    private static String tipFor(String label) {
        for (Map.Entry<String, String> e : TIPS.entrySet()) if (label.startsWith(e.getKey())) return e.getValue();
        return null;
    }

    private void paintToolTip(UiGfx g) {
        if (!java.util.Objects.equals(tipNow, tipPrev)) {
            tipPrev = tipNow;
            tipSince = clock.getAsLong();
        }
        if (tipNow == null || clock.getAsLong() - tipSince < 450_000_000L || exitDialog) return;
        int w = tm.width(tipNow) + 8, h = tm.lineHeight() + 6;
        int x = Math.max(2, Math.min(mouseX, viewW - w - 2)), y = TOOLBAR_H + 4;
        g.fillRect(x - 1, y - 1, w + 2, h + 2, 0xF0300070);
        g.fillRect(x, y, w, h, 0xF0100010);
        g.drawText(tipNow, x + 4, y + 3, C_TEXT, false);
    }

    private record ToolItem(String label, boolean enabled, java.util.function.Consumer<Rect> action, int bg, int gap) {}

    private void paintToolbar(UiGfx g) {
        g.fillRect(0, 0, viewW, TOOLBAR_H, C_PANEL);
        regions.add(new Region(new Rect(0, 0, viewW, TOOLBAR_H), null, null, true));   // 버튼이 아닌 빈 곳은 아래 캔버스로 통과
        int y = 2, h = TOOLBAR_H - 4;
        boolean panned = panX != 0 || panY != 0 || targetPanX != 0 || targetPanY != 0;
        List<ToolItem> items = List.of(
                new ToolItem("File", true, r -> openFileMenu(r), C_BTN, 0),
                new ToolItem("Save" + (session.isDirty() ? "*" : ""), true, r -> save(), C_BTN, 0),
                new ToolItem("Undo", canUndoAny(), r -> undo(), C_BTN, 0),
                new ToolItem("Redo", canRedoAny(), r -> redo(), C_BTN, 0),
                new ToolItem("Dup", selected > 0, r -> duplicate(), C_BTN, 4),
                new ToolItem("Del", selected > 0, r -> deleteSelected(), C_BTN, 0),
                new ToolItem("Wrap", selected > 0, r -> openMenu(r, List.of("row", "column", "panel", "stack", "풀기(Unwrap)"), item -> {
                    if (item.startsWith("풀기")) unwrapSelected();
                    else wrapSelected(item);
                }), C_BTN, 0),
                new ToolItem("Align", selected > 0, r -> openMenu(r, ALIGN_ITEMS, this::alignSelected), C_BTN, 0),
                new ToolItem(grid ? "Grid:8" : "Grid:off", true, r -> grid = !grid, C_BTN, 4),
                new ToolItem(autoAnchor ? "Anchor:auto" : "Anchor:keep", true, r -> autoAnchor = !autoAnchor, C_BTN, 0),
                new ToolItem("View", true, r -> openMenu(r, viewItems(), item -> {
                    if (item.startsWith("크기:")) applySizeItem(item);
                    else if (item.startsWith("XML")) xmlView.open = true;
                    else if (item.contains("없음")) previewState = 0;
                    else if (item.contains("hover")) previewState = 1;
                    else if (item.contains("active")) previewState = 2;
                    else help.open = true;
                }), C_BTN, 0),
                new ToolItem("Pub", host.canPublish(), r -> { if (!session.isDirty() || save()) host.publish(); }, C_BTN, 4),
                new ToolItem(panned ? "Fit!" : "Fit", panned, r -> resetView(), C_BTN_HOT, 0));
        int exitW = tm.width("Exit") + 8;
        int exitX = viewW - exitW - 2, limit = exitX - 2;
        int total = 2;
        for (ToolItem it : items) total += it.gap() + tm.width(it.label()) + 8 + 2;
        boolean overflow = total - 2 > limit;
        int ovW = tm.width("...") + 8;
        int x = 2;
        List<ToolItem> hidden = new ArrayList<>();
        for (ToolItem it : items) {
            int w = tm.width(it.label()) + 8;
            if (!hidden.isEmpty() || (overflow && x + it.gap() + w + 2 + ovW > limit)) {   // 안 들어가는 버튼은 "..." 메뉴로
                hidden.add(it);
                continue;
            }
            x += it.gap();
            Rect r = new Rect(x, y, w, h);
            x += button(g, x, y, it.label(), it.enabled(), () -> it.action().accept(r), it.bg());
        }
        if (!hidden.isEmpty()) {
            Rect r = new Rect(x, y, ovW, h);
            List<String> labels = new ArrayList<>();
            for (ToolItem it : hidden) labels.add(it.label());
            x += button(g, x, y, "...", true, () -> openMenu(r, labels, label -> {
                for (ToolItem it : hidden) if (it.label().equals(label) && it.enabled()) it.action().accept(r);
            }));
        }
        int rx = exitX;
        button(g, rx, y, "Exit", true, this::requestExit, 0xFF7A2A2A);                 // 에디터 닫기(F8 과 같다)
        String hideLabel = toolbarHidden ? "Pin" : "Hide";
        int hideW = tm.width(hideLabel) + 8;
        if (rx - hideW - 2 > x + 2) {                                                  // 좁은 창에서는 겹치는 보조 버튼을 그리지 않는다
            rx -= hideW + 2;
            button(g, rx, y, hideLabel, true, () -> {                                  // 툴바 숨기기: 숨기면 위쪽에 마우스를 올려 두어야 보인다
                toolbarHidden = !toolbarHidden;
                toolbarPeek = false;
                topHoverSince = -1;
            });
            int helpW = tm.width("?") + 8;
            if (rx - helpW - 2 > x + 2) {
                rx -= helpW + 2;
                button(g, rx, y, "?", true, () -> help.open = true);
            }
        }
        String title = host.title() + (session.isDirty() ? " *" : "");
        if (x + 6 + tm.width(title) <= rx - 4) g.drawText(title, Math.max(x + 6, rx - tm.width(title) - 8), 4, C_DIM, false);   // 자리가 없으면 생략
    }

    /** 패널 가장자리의 접기/펼치기 탭. */
    private void paintTabs(UiGfx g) {
        Rect lt = leftTab(), rt = rightTab();
        g.fillRect(lt.x(), lt.y(), lt.w(), lt.h(), lt.contains(mouseX, mouseY) ? shade(C_TAB, mouseDown ? -20 : 30) : C_TAB);
        g.drawText(leftCollapsed ? ">" : "<", lt.x() + 2, lt.y() + (lt.h() - tm.lineHeight()) / 2, C_TEXT, false);
        regions.add(new Region(lt, () -> leftCollapsed = !leftCollapsed));
        g.fillRect(rt.x(), rt.y(), rt.w(), rt.h(), rt.contains(mouseX, mouseY) ? shade(C_TAB, mouseDown ? -20 : 30) : C_TAB);
        g.drawText(rightShown() ? ">" : "<", rt.x() + 2, rt.y() + (rt.h() - tm.lineHeight()) / 2, C_TEXT, false);
        regions.add(new Region(rt, () -> {
            if (rightTempShown()) tempDismissed = true;      // 임시로 펼쳐진 것은 이번 선택 동안만 닫는다
            else rightCollapsed = !rightCollapsed;
        }));
    }

    private void paintStatus(UiGfx g) {
        hintRect = new Rect(0, 0, 0, 0);
        if (exitDialog) return;
        paintHint(g, "블록을 끌어다 놓기 · 끌어서 왼쪽 블록 칸에 놓으면 삭제 · 휠/가운데 버튼으로 화면 이동 · F8: 에디터 끄기", 0xFFE0E0EC);
        paintToast(g);
    }

    // ---- 왼쪽: 블록 팔레트 + 구조 트리 ----

    private void paintLeftPanel(UiGfx g) {
        g.fillRect(0, TOOLBAR_H, LEFT_W, viewH - TOOLBAR_H, C_PANEL);
        g.fillRect(LEFT_W - 1, TOOLBAR_H, 1, viewH - TOOLBAR_H, 0x40FFFFFF);
        regions.add(new Region(new Rect(0, TOOLBAR_H, LEFT_W, viewH - TOOLBAR_H), null));

        // 팔레트
        g.drawText("블록 (끌어다 놓기)", 4, TOOLBAR_H + 2, C_DIM, false);
        int tabW = (LEFT_W - 8) / PALETTE_TABS.length;
        for (int t = 0; t < PALETTE_TABS.length; t++) {                       // 팔레트 탭
            Rect tr = new Rect(4 + t * tabW, TOOLBAR_H + 11, tabW - 1, 11);
            boolean on = t == paletteTab, over = tr.contains(mouseX, mouseY);
            g.fillRect(tr.x(), tr.y(), tr.w(), tr.h(), on ? 0xFF3A3F55 : over ? 0xFF2A2D3E : 0xFF1A1C28);
            if (on) g.fillRect(tr.x(), tr.y(), tr.w(), 1, C_SEL);
            String name = (String) PALETTE_TABS[t][0];
            g.drawText(name, tr.x() + (tr.w() - tm.width(name)) / 2, tr.y() + 2, on ? C_TEXT : C_DIM, false);
            final int idx = t;
            regions.add(new Region(tr, () -> paletteTab = idx));
        }
        for (int i = (int) PALETTE_TABS[paletteTab][1]; i < (int) PALETTE_TABS[paletteTab][2]; i++) {
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
        Rect filterField = new Rect(26, headerY + 2, LEFT_W - 26 - 30, 9);                 // 검색 칸
        g.fillRect(filterField.x(), filterField.y(), filterField.w(), filterField.h(), C_FIELD);
        if (FILTER_KEY.equals(editKey)) {
            outline(g, filterField, C_SEL);
            editRect = filterField;
            paintEditText(g, filterField);
        } else {
            boolean has = !treeFilter.isEmpty();
            g.drawText(has ? fit(treeFilter, filterField.w() - 4) : "검색", filterField.x() + 3, filterField.y() + 1, has ? C_SEL : C_DIM, false);
            regions.add(new Region(filterField, () -> startEdit(FILTER_KEY, treeFilter, filterField)));
        }
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
        if (rows.isEmpty()) g.drawText("일치하는 요소가 없어요", 6, rowsTop() + 4, C_DIM, false);
        outlinerScroll = Math.max(0, Math.min(outlinerScroll, Math.max(0, rows.size() - visible)));
        int top = rowsTop();
        g.pushClip(0, top - 1, LEFT_W - 1, viewH - top + 1);
        for (int i = outlinerScroll; i < rows.size() && i < outlinerScroll + visible; i++) {
            TreeRow row = rows.get(i);
            LayoutBox b = row.box();
            int y = top + (i - outlinerScroll) * ROW_H;
            int idx = b.node.sourceIndex();
            boolean sel = idx == selected || multi.contains(idx);
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
                if (ctrlDown || shiftDown) {                                  // Ctrl/Shift+클릭: 여러 개 선택
                    toggleMulti(idx);
                    return;
                }
                if (!multi.contains(idx) || idx == selected) multi.clear();
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

    private int propScroll, propMaxScroll;
    private final Set<String> collapsedSections = new HashSet<>();

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
        int fieldX = x0 + FIELD_OFFSET, fieldW = RIGHT_W - FIELD_OFFSET - 7;
        List<PropRow> vis = new ArrayList<>();
        boolean skip = false;
        for (PropRow row : rowsFor(sel)) {
            if (row.kind() == Kind.HEADER) {
                skip = collapsedSections.contains(row.key());
                vis.add(row);
            } else if (!skip) {
                vis.add(row);
            }
        }
        int rowH = ROW_H + 1;
        propMaxScroll = Math.max(0, vis.size() * rowH + 6 - (viewH - top));
        propScroll = Math.max(0, Math.min(propScroll, propMaxScroll));
        int y = top + 3 - propScroll;
        Rect panel = new Rect(x0, top, RIGHT_W, viewH - top);
        g.pushClip(x0, top, RIGHT_W, viewH - top);
        for (PropRow row : vis) {
            if (y + ROW_H > top && y < viewH) paintPropRow(g, row, panel, x0, y, fieldX, fieldW);
            y += rowH;
        }
        g.popClip();
        if (propMaxScroll > 0) {                                   // 스크롤 막대
            int trackH = viewH - top - 4, thumbH = Math.max(8, trackH * (viewH - top) / (vis.size() * rowH + 6));
            int thumbY = top + 2 + (trackH - thumbH) * propScroll / propMaxScroll;
            g.fillRect(viewW - 3, top + 2, 2, trackH, 0x30FFFFFF);
            g.fillRect(viewW - 3, thumbY, 2, thumbH, 0xA0FFFFFF);
        }
    }

    private static boolean sees(Rect panel, Rect r) {
        return r.y() >= panel.y() && r.bottom() <= panel.bottom();
    }

    private void paintPropRow(UiGfx g, PropRow row, Rect panel, int x0, int y, int fieldX, int fieldW) {
        if (row.kind() == Kind.HEADER) {
            boolean closed = collapsedSections.contains(row.key());
            Rect r = new Rect(x0 + 1, y - 1, RIGHT_W - 2, ROW_H);
            g.fillRect(r.x(), r.y(), r.w(), r.h(), r.contains(mouseX, mouseY) ? 0xFF2A2F48 : 0xFF1E2130);
            g.drawText((closed ? "+ " : "- ") + row.label(), x0 + 5, y + 1, C_TEXT, false);
            if (sees(panel, r)) {
                final String key = row.key();
                regions.add(new Region(r, () -> {
                    if (!collapsedSections.remove(key)) collapsedSections.add(key);
                }));
            }
            return;
        }
        g.drawText(row.label(), x0 + 5, y + 1, C_DIM, false);
        Rect field = new Rect(fieldX, y - 1, fieldW, ROW_H);
        boolean editing = row.key().equals(editKey) && !row.key().isEmpty();
        boolean fieldHover = !editing && row.editable() && field.contains(mouseX, mouseY) && panel.contains(mouseX, mouseY);
        g.fillRect(field.x(), field.y(), field.w(), field.h(), fieldHover ? (mouseDown ? shade(C_FIELD, -6) : shade(C_FIELD, 22)) : C_FIELD);
        if (fieldHover) outline(g, field, mouseDown ? C_SEL : 0x80FFFFFF);
        int reserve = 4;
        if (row.kind() == Kind.COLOR) {                            // 색 칸 오른쪽에 현재 색 견본
            reserve = 16;
            Integer swatch = Colors.tryParse(editing ? fieldText.buf.toString().trim() : row.value());
            if (swatch != null) {
                g.fillRect(field.right() - 11, field.y() + 2, 9, field.h() - 4, 0xFF2A2D3E);
                g.fillRect(field.right() - 11, field.y() + 2, 9, field.h() - 4, swatch);
            }
        }
        final String key = row.key(), value = row.value();
        if (row.kind() == Kind.NUMBER && !editing) reserve = 19;
        if (editing) {
            outline(g, field, C_SEL);
            editRect = field;
            paintEditText(g, field);
            return;
        }
        switch (row.kind()) {
            case BOOL -> {
                boolean def = BOOLS.getOrDefault(key, false);
                boolean on = value.isBlank() ? def : Boolean.parseBoolean(value.trim());
                Rect box = new Rect(field.x() + 2, field.y() + 2, ROW_H - 4, ROW_H - 4);
                g.fillRect(box.x(), box.y(), box.w(), box.h(), 0xFF8A8FA8);
                g.fillRect(box.x() + 1, box.y() + 1, box.w() - 2, box.h() - 2, 0xFF0A0B10);
                if (on) g.fillRect(box.x() + 2, box.y() + 2, box.w() - 4, box.h() - 4, C_OK);
                g.drawText(on ? "예" : "아니오", field.x() + ROW_H + 2, y + 1, C_TEXT, false);
                if (sees(panel, field)) regions.add(new Region(field, () -> toggleBool(key, value)));
            }
            case ENUM -> {
                g.drawText(fit(value.isBlank() ? "(기본)" : value, fieldW - 14), fieldX + 2, y + 1, value.isBlank() ? C_DIM : C_TEXT, false);
                g.drawText("v", field.right() - 8, y + 1, C_DIM, false);
                if (sees(panel, field)) regions.add(new Region(field, () -> openMenu(field, row.options(), v -> applyValue(key, v))));
            }
            case NUMBER -> {
                g.drawText(fit(value, fieldW - reserve), fieldX + 2, y + 1, C_TEXT, false);
                Rect minus = new Rect(field.right() - 18, field.y() + 1, 8, ROW_H - 2), plus = new Rect(field.right() - 9, field.y() + 1, 8, ROW_H - 2);
                stepper(g, minus, "-");
                stepper(g, plus, "+");
                if (sees(panel, field)) {
                    regions.add(new Region(field, () -> startEdit(key, value, field)));
                    regions.add(new Region(minus, () -> adjustNumber(key, value, -1, false)));
                    regions.add(new Region(plus, () -> adjustNumber(key, value, 1, false)));
                }
            }
            default -> {
                g.drawText(fit(row.value(), fieldW - reserve), fieldX + 2, y + 1, row.editable() ? C_TEXT : C_DIM, false);
                if (key.equals("item") || (key.equals("src") && isMediaTag())) {   // 아이템/이미지·영상 칸: 목록에서 고르기
                    g.drawText("▦", field.right() - 9, y + 1, C_DIM, false);
                    if (sees(panel, field)) regions.add(new Region(field, () -> openItemPicker(field, key)));
                } else if (row.editable() && sees(panel, field)) {
                    regions.add(new Region(field, () -> startEdit(key, value, field)));
                }
            }
        }
    }

    private void stepper(UiGfx g, Rect r, String label) {
        boolean over = r.contains(mouseX, mouseY);
        g.fillRect(r.x(), r.y(), r.w(), r.h(), over ? (mouseDown ? shade(C_BTN, -20) : shade(C_BTN, 30)) : C_BTN);
        g.drawText(label, r.x() + (r.w() - tm.width(label)) / 2, r.y() + (r.h() - tm.lineHeight()) / 2 + 1, C_TEXT, false);
    }

    private void toggleBool(String key, String current) {
        boolean def = BOOLS.getOrDefault(key, false);
        boolean now = current.isBlank() ? def : Boolean.parseBoolean(current.trim());
        boolean next = !now;
        applyValue(key, next == def ? "" : Boolean.toString(next));
    }

    private void adjustNumber(String key, String current, int dir, boolean big) {
        LayoutBox box = selectedBox();
        if (box == null) return;
        String next;
        if (key.equals("opacity")) {
            float v = 1f;
            try {
                if (!current.isBlank()) v = Float.parseFloat(current.trim());
            } catch (NumberFormatException ignored) {
                // 잘못된 값이면 1 에서 시작
            }
            v = Math.max(0f, Math.min(1f, Math.round((v + dir * 0.1f) * 10f) / 10f));
            next = v >= 1f ? "" : String.format(java.util.Locale.ROOT, "%.1f", v);
        } else {
            Integer px = explicitPx(current);
            int base = px != null ? px : switch (key) {
                case "width" -> box.w;
                case "height" -> box.h;
                default -> 0;
            };
            int v = base + dir * (big ? 8 : 1);
            if (key.equals("width") || key.equals("height")) v = Math.max(1, v);
            else if (key.equals("gap") || key.equals("border")) v = Math.max(0, v);
            next = Integer.toString(v);
        }
        applyValue(key, next);
    }

    // ---- 간단한 선택 메뉴(드롭다운) ----

    private Rect menuNear;
    private List<String> menuItems = List.of();
    private java.util.function.Consumer<String> menuPick;

    private static List<String> viewItems() {
        List<String> l = new ArrayList<>(List.of("XML 보기", "상태 미리보기: 없음", "상태 미리보기: hover", "상태 미리보기: active", "도움말"));
        l.addAll(SIZE_ITEMS);
        return l;
    }

    private static final String OPEN_PREFIX = "열기: ", NEW_PREFIX = "새 화면: ";
    private static final List<String> NEW_KINDS = List.of("빈 화면", "대화상자");

    /** File 메뉴: 다른 UI 열기, 새 UI 만들기. 저장하지 않은 변경이 있으면 전환하지 않고 먼저 저장하라고 알린다. */
    private void openFileMenu(Rect near) {
        List<String> items = new ArrayList<>();
        List<String> screens = host.screens();
        for (int i = 0; i < screens.size() && i < 24; i++) items.add(OPEN_PREFIX + screens.get(i));
        for (String k : NEW_KINDS) items.add(NEW_PREFIX + k);
        openMenu(near, items, item -> {
            if (session.isDirty()) {
                setStatus("저장하지 않은 변경이 있어요 — 저장(Ctrl+S)한 뒤 다른 화면으로 전환하세요", true);
                return;
            }
            if (item.startsWith(OPEN_PREFIX)) {
                host.switchTo(item.substring(OPEN_PREFIX.length()));
            } else if (item.startsWith(NEW_PREFIX)) {
                try {
                    String id = host.createScreen(NEW_KINDS.indexOf(item.substring(NEW_PREFIX.length())));
                    if (id != null) host.switchTo(id);
                } catch (IOException e) {
                    setStatus("새 화면을 만들지 못했습니다: " + e.getMessage(), true);
                }
            }
        });
    }

    private void openMenu(Rect near, List<String> items, java.util.function.Consumer<String> pick) {
        menuNear = near;
        menuItems = items;
        menuPick = pick;
    }

    private void closeMenu() {
        menuNear = null;
        menuItems = List.of();
        menuPick = null;
    }

    private void paintMenu(UiGfx g) {
        if (menuNear == null || menuItems.isEmpty()) return;
        regions.add(new Region(new Rect(0, 0, viewW, viewH), this::closeMenu));   // 바깥을 누르면 닫는다
        int w = menuNear.w();
        for (String s : menuItems) w = Math.max(w, tm.width(s) + 10);
        int h = menuItems.size() * ROW_H + 2;
        int x = Math.max(2, Math.min(menuNear.x(), viewW - w - 2));
        int y = menuNear.bottom() + 1;
        if (y + h > viewH) y = Math.max(2, menuNear.y() - h - 1);
        g.fillRect(x - 1, y - 1, w + 2, h + 2, C_SEL);
        g.fillRect(x, y, w, h, 0xFF161820);
        regions.add(new Region(new Rect(x, y, w, h), null));
        for (int i = 0; i < menuItems.size(); i++) {
            String item = menuItems.get(i);
            Rect row = new Rect(x, y + 1 + i * ROW_H, w, ROW_H);
            if (row.contains(mouseX, mouseY)) g.fillRect(row.x(), row.y(), row.w(), row.h(), C_ROW_SEL);
            g.drawText(item, x + 4, row.y() + 1, C_TEXT, false);
            regions.add(new Region(row, () -> {
                var pick = menuPick;
                closeMenu();
                if (pick != null) pick.accept(item);
            }));
        }
    }

    // ---- 복사 / 붙여넣기 / 감싸기 ----

    // ---- 아이템 선택 상자(격자 + 스크롤 + 검색: ItemPickerOverlay) ----

    private final ItemPickerOverlay itemPicker = new ItemPickerOverlay();

    /** 지금 열린 선택 상자가 채우는 속성("item" 또는 "src"). */
    private String pickerKey = "item";

    private boolean isMediaTag() {
        LayoutBox b = selectedBox();
        return b != null && (b.tag().equals("image") || b.tag().equals("video"));
    }

    private void openItemPicker(Rect near, String key) {
        commitFieldQuietly();
        pickerKey = key;
        itemPicker.open(near, host.suggestions(key), key.equals("src"));
        Rect search = itemPicker.searchArea(viewW, TOOLBAR_H);                  // 게임(SDL)은 글자 입력을 켜 줘야 검색어가 들어온다
        host.textInput(true, search.x(), search.y(), search.w(), search.h());
    }

    private void closeItemPicker() {
        if (!itemPicker.isOpen()) return;
        itemPicker.close();
        host.textInput(false, 0, 0, 0, 0);
    }

    private void paintItemPicker(UiGfx g) {
        if (!itemPicker.isOpen()) return;
        LayoutBox sel = selectedBox();
        itemPicker.paint(g, tm, viewW, viewH, TOOLBAR_H, mouseX, mouseY, regions, sel == null ? null : sel.node.attr(pickerKey),
                id -> {
                    closeItemPicker();
                    applyValue(pickerKey, id);
                }, this::closeItemPicker);
    }

    private String elementClip = "";
    /** 선택한 요소의 상태 미리보기: 0 없음, 1 :hover, 2 :active. */
    private int previewState;

    private void copyElement(boolean cut) {
        if (selected <= 0) return;
        elementClip = EditorOps.elementXml(session.text(), selected);
        host.setClipboard(elementClip);
        if (cut) deleteSelected();
        else setStatus("복사했어요", false);
    }

    private void pasteElement() {
        String clip = host.clipboard();
        String xml = clip != null && clip.strip().startsWith("<") ? clip.strip() : elementClip;
        if (xml.isBlank()) {
            setStatus("붙여넣을 요소가 없어요", true);
            return;
        }
        try {
            XmlSource.parse(xml);
        } catch (RuntimeException e) {
            setStatus("붙여넣을 수 없는 내용이에요", true);
            return;
        }
        LayoutBox sel = selectedBox();
        LayoutBox target = sel == null ? doc.layout() : CONTAINERS.contains(sel.tag()) ? sel : displayParent(sel);
        int parentIdx = target == null || target.node.sourceIndex() < 0 ? 0 : target.node.sourceIndex();
        String safe = EditorOps.uniqueIds(session.text(), xml);
        EditorOps.Added r;
        try {
            r = EditorOps.insertXml(session.text(), parentIdx, Integer.MAX_VALUE, safe);
        } catch (RuntimeException e) {
            setStatus("여기에는 붙여넣을 수 없어요", true);
            return;
        }
        int[] c = visibleCenterDoc();
        String text = growToFit(placeAt(r.text(), r.index(), parentIdx, c[0], c[1]), r.index());
        if (applyEdit(src -> text)) selected = r.index();
    }

    private static final List<String> ALIGN_ITEMS = List.of("top-left", "top", "top-right", "left", "center", "right", "bottom-left", "bottom", "bottom-right");

    /** 선택한 요소(여러 개면 모두)를 부모 안에서 정렬한다: 자유 배치 부모에서는 기준점을 지정하고 오프셋을 지우고, row/column 안에서는 교차축 정렬(align-self)을 쓴다. */
    public void alignSelected(String anchor) {
        String text = session.text();
        boolean any = false;
        java.util.Set<Integer> keep = new java.util.LinkedHashSet<>(multi);
        for (int idx : editableSelection()) {
            LayoutBox b = boxByIndex(doc.layout(), idx);
            if (b == null || b.parent == null) continue;
            LayoutBox parent = displayParent(b);
            if (parent == null) continue;
            Map<String, String> props = new LinkedHashMap<>();
            if (isFlow(parent)) {
                boolean horizontal = parent.tag().equals("row");            // row 는 위/아래, column 은 왼쪽/오른쪽이 교차축
                String start = horizontal ? "top" : "left", end = horizontal ? "bottom" : "right";
                props.put("align-self", anchor.contains(start) ? "start" : anchor.contains(end) ? "end" : "center");
            } else {
                props.put("anchor", anchor.equals("top-left") ? null : anchor);
                props.put("x", null);
                props.put("y", null);
            }
            final LayoutBox box = b;
            text = EditorOps.setProps(text, idx, props, p -> box.style.get(p) != null && box.node.attr(p) == null);
            any = true;
        }
        if (!any) return;
        String result = text;
        if (applyEdit(src -> result)) multi.addAll(keep);
    }

    /** 같은 부모 안에서 순서를 한 칸 옮긴다(dir -1 = 앞/뒤로 보내기: 먼저 그려지는 쪽, +1 = 위로). */
    public void reorder(int dir) {
        multi.clear();
        LayoutBox b = selectedBox();
        if (b == null || selected <= 0) return;
        LayoutBox parent = displayParent(b);
        if (parent == null) return;
        List<LayoutBox> siblings = displayChildren(parent);
        int p = siblings.indexOf(b), k = p + dir;
        if (p < 0 || k < 0 || k >= siblings.size()) return;
        int parentIdx = parent.node.sourceIndex(), idx = selected, otherIdx = siblings.get(k).node.sourceIndex();
        String text = session.text();
        XmlSource src = XmlSource.parse(text);
        Element parentEl = src.element(parentIdx);
        int otherPos = parentEl.children().indexOf(src.element(otherIdx));
        EditorOps.Added r = EditorOps.move(text, idx, parentIdx, dir > 0 ? otherPos + 1 : otherPos);
        if (applyEdit(s -> r.text())) selected = r.index();
    }

    // ---- XML 보기(읽기 전용) ----

    private final XmlViewOverlay xmlView = new XmlViewOverlay();

    private void paintXmlView(UiGfx g) {
        if (!xmlView.open) return;
        int selStart = -1, selEnd = -1;
        if (selected >= 0) {
            Element el = session.source().element(selected);
            if (el != null) {
                String t = session.text();
                selStart = t.substring(0, el.start()).split("\n", -1).length - 1;
                selEnd = t.substring(0, el.end()).split("\n", -1).length - 1;
            }
        }
        xmlView.paint(g, tm, viewW, viewH, TOOLBAR_H, regions, session.text(), selStart, selEnd);
    }

    /** 선택한 요소를 컨테이너(row/column/panel/stack)로 감싼다. */
    public void wrapSelected(String tag) {
        if (selected <= 0) return;
        try {
            EditorOps.Added r = EditorOps.wrap(session.text(), selected, tag);
            if (applyEdit(src -> r.text())) selected = r.index();
        } catch (IllegalArgumentException e) {
            setStatus(e.getMessage(), true);
        }
    }

    /** 선택한 컨테이너를 풀어 자식들을 그 자리로 올린다. */
    public void unwrapSelected() {
        LayoutBox b = selectedBox();
        if (b == null || selected <= 0 || !CONTAINERS.contains(b.tag()) || displayChildren(b).isEmpty()) {
            setStatus("풀 수 있는 컨테이너를 선택하세요", true);
            return;
        }
        try {
            EditorOps.Added r = EditorOps.unwrap(session.text(), selected);
            if (applyEdit(src -> r.text())) selected = r.index();
        } catch (IllegalArgumentException e) {
            setStatus(e.getMessage(), true);
        }
    }

    private void paintEditText(UiGfx g, Rect field) {
        String s = fieldText.buf.toString();
        int maxW = field.w() - 6;
        int start = 0;
        while (start < fieldText.cursor && tm.width(s.substring(start, fieldText.cursor)) > maxW) start++;
        int end = s.length();
        while (end > fieldText.cursor && tm.width(s.substring(start, end)) > maxW) end--;
        String shown = s.substring(start, end);
        editViewStart = start;                    // 마우스로 커서를 찍을 때 같은 기준을 쓴다
        editTextX = field.x() + 3;
        if (fieldText.hasSelection()) {                     // 선택 범위 배경
            int a = Math.max(start, fieldText.selectionStart()), b = Math.min(end, fieldText.selectionEnd());
            if (b > a) {
                int x1 = editTextX + tm.width(s.substring(start, a)), x2 = editTextX + tm.width(s.substring(start, b));
                g.fillRect(x1, field.y() + 1, x2 - x1, field.h() - 2, C_SELECTION);
            }
        }
        g.drawText(shown, editTextX, field.y() + 2, C_TEXT, false);
        int cx = editTextX + tm.width(s.substring(start, fieldText.cursor));
        g.fillRect(cx, field.y() + 1, 1, field.h() - 2, C_SEL);
    }

    private String fit(String s, int maxW) {
        if (tm.width(s) <= maxW) return s;
        int end = s.length();
        while (end > 0 && tm.width(s.substring(0, end) + "…") > maxW) end--;
        return s.substring(0, end) + "…";
    }
}
