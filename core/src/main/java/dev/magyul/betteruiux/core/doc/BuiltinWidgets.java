package dev.magyul.betteruiux.core.doc;

import dev.magyul.betteruiux.api.Cursor;
import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.api.UiGfx;
import dev.magyul.betteruiux.core.layout.LayoutBox;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 내장 입력 태그: {@code checkbox radio slider progress textfield scroll tabs select}.
 * 모두 {@link UiWidget} 이라 모드가 등록하는 커스텀 위젯과 같은 방식으로 그려지고 입력을 받는다.
 * 값은 XML 속성({@code checked value ...})에 그대로 저장되어 {@code query("#id").attr("value")} 로 읽을 수 있고,
 * 바뀌면 {@code onChange} 액션과 {@link UiEvent.Type#CHANGE} 이벤트가 발생한다.
 */
public final class BuiltinWidgets {
    private BuiltinWidgets() {}

    /** 위젯으로 구현된 내장 태그. */
    public static final Set<String> TAGS = Set.of("checkbox", "radio", "slider", "progress", "textfield", "scroll", "tabs", "select");

    static boolean has(String tag) { return TAGS.contains(tag); }

    static UiWidget create(UiElement el, UiDocument doc, TextMetrics tm) {
        return switch (el.tag()) {
            case "checkbox" -> new Checkbox(el, doc, tm, false);
            case "radio" -> new Checkbox(el, doc, tm, true);
            case "slider" -> new Slider(el, doc, tm);
            case "progress" -> new Progress(el, doc, tm);
            case "textfield" -> new TextField(el, doc, tm);
            case "scroll" -> new Scroll(el, doc, tm);
            case "tabs" -> new Tabs(el, doc, tm);
            case "select" -> new Select(el, doc, tm);
            default -> null;
        };
    }

    // ---- 공통 ----

    private static final int C_BORDER = 0xFF8A8FA8, C_BORDER_HOT = 0xFFFFFFFF, C_BG = 0xFF101018, C_TEXT = 0xFFFFFFFF, C_DIM = 0xFF888888,
            C_ACCENT = 0xFF55FF55, C_FILL = 0xFF2E5FA8, C_FOCUS = 0xFFFFFF55;

    private static void outline(UiGfx g, int x, int y, int w, int h, int c) {
        g.fillRect(x, y, w, 1, c);
        g.fillRect(x, y + h - 1, w, 1, c);
        g.fillRect(x, y, 1, h, c);
        g.fillRect(x + w - 1, y, 1, h, c);
    }

    abstract static class Base implements UiWidget {
        final UiElement el;
        final UiDocument doc;
        final TextMetrics tm;

        Base(UiElement el, UiDocument doc, TextMetrics tm) {
            this.el = el;
            this.doc = doc;
            this.tm = tm;
        }

        String attr(String name, String def) {
            String v = el.attr(name);
            return v == null ? def : doc.interpolate(v);
        }

        double num(String name, double def) {
            try {
                return Double.parseDouble(attr(name, "").trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }

        boolean flag(String name) { return "true".equalsIgnoreCase(attr(name, "").trim()); }

        boolean enabled() { return !"false".equalsIgnoreCase(attr("enabled", "true").trim()); }

        LayoutBox box() { return el.box(); }

        int style(String key, int def) { return box() == null ? def : box().style.color(key, def); }

        boolean focused() { return doc.isFocused(el); }

        void changed() { doc.fireChange(el); }

        static String fmt(double v) {
            if (v == Math.rint(v) && Math.abs(v) < 1e9) return Long.toString((long) v);
            String s = String.format(java.util.Locale.ROOT, "%.3f", v);
            s = s.replaceAll("0+$", "");
            return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
        }
    }

    // ---- checkbox / radio ----

    public static final class Checkbox extends Base {
        private static final int BOX = 10, GAP = 4;
        private final boolean radio;

        Checkbox(UiElement el, UiDocument doc, TextMetrics tm, boolean radio) {
            super(el, doc, tm);
            this.radio = radio;
        }

        private String label() { return attr("text", ""); }

        @Override public int intrinsicWidth() { return BOX + (label().isEmpty() ? 0 : GAP + tm.width(label())); }

        @Override public int intrinsicHeight() { return Math.max(BOX, tm.lineHeight()); }

        @Override public boolean focusable() { return enabled(); }

        @Override public Cursor cursor() { return enabled() ? Cursor.POINTER : null; }

        @Override
        public void paint(UiGfx g, int x, int y, int w, int h, Frame f) {
            boolean on = flag("checked"), en = enabled();
            int by = y + (h - BOX) / 2;
            int border = !en ? C_DIM : f.hovered() ? C_BORDER_HOT : style("border-color", C_BORDER);
            g.fillRect(x, by, BOX, BOX, border);
            g.fillRect(x + 1, by + 1, BOX - 2, BOX - 2, style("background", C_BG));
            if (on) {
                int c = en ? style("check-color", C_ACCENT) : C_DIM;
                if (radio) g.fillRect(x + 3, by + 3, BOX - 6, BOX - 6, c);
                else g.fillRect(x + 2, by + 2, BOX - 4, BOX - 4, c);
            }
            if (!label().isEmpty()) {
                g.drawText(label(), x + BOX + GAP, y + (h - tm.lineHeight()) / 2, en ? style("color", C_TEXT) : C_DIM, true);
            }
            if (focused()) outline(g, x - 2, by - 2, BOX + 4, BOX + 4, C_FOCUS);
        }

        @Override public boolean mousePressed(UiElement e, int x, int y, int button) { return button == 0 && enabled(); }

        @Override
        public void mouseReleased(UiElement e, int x, int y, int button) {
            LayoutBox b = box();
            if (button == 0 && enabled() && b != null && b.contains(x, y)) toggle();
        }

        @Override
        public boolean keyPressed(UiElement e, UiKey key, boolean ctrl, boolean shift) {
            if (key != UiKey.ENTER && key != UiKey.SPACE) return false;
            toggle();
            return true;
        }

        @Override
        public boolean charTyped(UiElement e, char c) {
            if (c != ' ') return false;
            toggle();
            return true;
        }

        private void toggle() {
            if (!enabled()) return;
            boolean on = flag("checked");
            if (radio) {
                if (on) return;
                String group = el.attr("group");
                for (UiElement other : doc.queryAll("radio")) {
                    if (other != el && group != null && group.equals(other.attr("group"))) other.setAttr("checked", null);
                }
                el.setAttr("checked", "true");
            } else {
                el.setAttr("checked", on ? null : "true");
            }
            changed();
        }
    }

    // ---- slider ----

    public static final class Slider extends Base {
        private boolean dragging;
        private double startValue;

        Slider(UiElement el, UiDocument doc, TextMetrics tm) { super(el, doc, tm); }

        private double min() { return num("min", 0); }

        private double max() { return Math.max(min(), num("max", 100)); }

        private double step() { return Math.max(0.0001, num("step", 1)); }

        private double value() { return Math.max(min(), Math.min(max(), num("value", min()))); }

        @Override public int intrinsicWidth() { return 100; }

        @Override public int intrinsicHeight() { return Math.max(12, tm.lineHeight() + 4); }

        @Override public boolean focusable() { return enabled(); }

        @Override public Cursor cursor() { return enabled() ? Cursor.POINTER : null; }

        @Override
        public void paint(UiGfx g, int x, int y, int w, int h, Frame f) {
            boolean en = enabled();
            double range = max() - min();
            double ratio = range <= 0 ? 0 : (value() - min()) / range;
            g.fillRect(x, y, w, h, !en ? C_DIM : f.hovered() || dragging ? C_BORDER_HOT : style("border-color", C_BORDER));
            g.fillRect(x + 1, y + 1, w - 2, h - 2, style("background", C_BG));
            int fillW = (int) Math.round((w - 2) * ratio);
            g.fillRect(x + 1, y + 1, fillW, h - 2, en ? style("fill-color", C_FILL) : 0xFF444455);
            int tx = x + 1 + Math.max(0, Math.min(w - 5, fillW - 2));
            g.fillRect(tx, y, 4, h, en ? 0xFFFFFFFF : C_DIM);
            String label = attr("text", "");
            String shown = (label.isEmpty() ? "" : label + ": ") + fmt(value());
            g.drawText(shown, x + (w - tm.width(shown)) / 2, y + (h - tm.lineHeight()) / 2 + 1, en ? C_TEXT : C_DIM, true);
            if (focused()) outline(g, x - 1, y - 1, w + 2, h + 2, C_FOCUS);
        }

        private void setFromX(int mx) {
            LayoutBox b = box();
            if (b == null || b.w <= 2) return;
            double ratio = Math.max(0, Math.min(1, (mx - b.x - 1) / (double) (b.w - 2)));
            double raw = min() + ratio * (max() - min());
            double snapped = min() + Math.round((raw - min()) / step()) * step();
            setValue(snapped);
        }

        private boolean setValue(double v) {
            double clamped = Math.max(min(), Math.min(max(), v));
            if (Math.abs(clamped - value()) < 1e-9 && el.attr("value") != null) return false;
            el.setAttr("value", fmt(clamped));
            return true;
        }

        @Override
        public boolean mousePressed(UiElement e, int x, int y, int button) {
            if (button != 0 || !enabled()) return false;
            dragging = true;
            startValue = value();
            setFromX(x);
            return true;
        }

        @Override public void mouseDragged(UiElement e, int x, int y) { if (dragging) setFromX(x); }

        @Override
        public void mouseReleased(UiElement e, int x, int y, int button) {
            if (!dragging) return;
            dragging = false;
            if (Math.abs(value() - startValue) > 1e-9) changed();
        }

        @Override
        public boolean keyPressed(UiElement e, UiKey key, boolean ctrl, boolean shift) {
            double d = switch (key) {
                case LEFT, DOWN -> -step() * (shift ? 10 : 1);
                case RIGHT, UP -> step() * (shift ? 10 : 1);
                case HOME -> min() - value();
                case END -> max() - value();
                default -> Double.NaN;
            };
            if (Double.isNaN(d)) return false;
            if (setValue(value() + d)) changed();
            return true;
        }

        @Override
        public boolean mouseScrolled(UiElement e, int x, int y, double amount) {
            if (!enabled() || !focused()) return false;
            if (setValue(value() + Math.signum(amount) * step())) changed();
            return true;
        }
    }

    // ---- progress ----

    public static final class Progress extends Base {
        Progress(UiElement el, UiDocument doc, TextMetrics tm) { super(el, doc, tm); }

        @Override public int intrinsicWidth() { return 100; }

        @Override public int intrinsicHeight() { return attr("text", "").isEmpty() ? 8 : Math.max(10, tm.lineHeight() + 2); }

        @Override
        public void paint(UiGfx g, int x, int y, int w, int h, Frame f) {
            double min = num("min", 0), max = Math.max(min + 1e-9, num("max", 100)), v = Math.max(min, Math.min(max, num("value", min)));
            double ratio = (v - min) / (max - min);
            g.fillRect(x, y, w, h, style("border-color", C_BORDER));
            g.fillRect(x + 1, y + 1, w - 2, h - 2, style("background", C_BG));
            g.fillRect(x + 1, y + 1, (int) Math.round((w - 2) * ratio), h - 2, style("bar-color", C_ACCENT));
            String label = attr("text", "");
            if (!label.isEmpty()) g.drawText(label, x + (w - tm.width(label)) / 2, y + (h - tm.lineHeight()) / 2 + 1, C_TEXT, true);
        }
    }

    // ---- textfield ----

    public static final class TextField extends Base {
        private final TextEditModel model = new TextEditModel();
        private boolean loaded, dragging;
        private int viewStart, textX;
        private long lastClick;
        private int lastClickIndex = -1;

        TextField(UiElement el, UiDocument doc, TextMetrics tm) { super(el, doc, tm); }

        private int maxLen() { return (int) Math.max(1, num("maxlength", 256)); }

        /** XML 의 value 가 바뀌었으면(Java API 등) 모델에 반영한다. */
        private void sync() {
            String v = el.attr("value");
            if (v == null) v = "";
            if (!loaded || (!focused() && !v.equals(model.text()))) {
                model.set(v);
                loaded = true;
            }
        }

        private void commit() {
            String v = model.text();
            if (!v.equals(el.attr("value"))) {
                el.setAttr("value", v);
                changed();
            }
        }

        @Override public int intrinsicWidth() { return 120; }

        @Override public int intrinsicHeight() { return tm.lineHeight() + 6; }

        @Override public boolean focusable() { return enabled(); }

        @Override public Cursor cursor() { return enabled() ? Cursor.TEXT : null; }

        @Override
        public void paint(UiGfx g, int x, int y, int w, int h, Frame f) {
            sync();
            boolean foc = focused(), en = enabled();
            g.fillRect(x, y, w, h, foc ? C_BORDER_HOT : f.hovered() && en ? 0xFFC0C4D8 : style("border-color", C_BORDER));
            g.fillRect(x + 1, y + 1, w - 2, h - 2, en ? style("background", 0xFF000000) : 0xFF1A1A22);
            String s = model.text();
            int maxW = w - 8, cur = model.cursor();
            viewStart = Math.min(viewStart, cur);
            while (viewStart < cur && tm.width(s.substring(viewStart, cur)) > maxW) viewStart++;
            int end = s.length();
            while (end > cur && tm.width(s.substring(viewStart, end)) > maxW) end--;
            textX = x + 4;
            int ty = y + (h - tm.lineHeight()) / 2 + 1;
            if (s.isEmpty() && !foc) {
                String ph = attr("placeholder", "");
                if (!ph.isEmpty()) g.drawText(ph, textX, ty, C_DIM, false);
                return;
            }
            if (foc && model.hasSelection()) {
                int a = Math.max(viewStart, model.selStart()), b = Math.min(end, model.selEnd());
                if (b > a) {
                    int x1 = textX + tm.width(s.substring(viewStart, a)), x2 = textX + tm.width(s.substring(viewStart, b));
                    g.fillRect(x1, y + 2, x2 - x1, h - 4, 0xFF3A62B0);
                }
            }
            g.drawText(s.substring(viewStart, end), textX, ty, en ? style("color", C_TEXT) : C_DIM, false);
            if (foc && (f.nowMillis() / 500) % 2 == 0) {
                g.fillRect(textX + tm.width(s.substring(viewStart, cur)), y + 2, 1, h - 4, C_TEXT);
            }
        }

        /** 화면 x 에 가장 가까운 글자 사이. */
        private int indexAt(int mx) {
            String s = model.text();
            int rel = mx - textX, best = viewStart, prev = 0;
            for (int i = viewStart + 1; i <= s.length(); i++) {
                int w = tm.width(s.substring(viewStart, i));
                if (rel < w) return (rel - prev < w - rel) ? i - 1 : i;
                prev = w;
                best = i;
            }
            return best;
        }

        @Override
        public boolean mousePressed(UiElement e, int x, int y, int button) {
            if (button != 0 || !enabled()) return false;
            sync();
            doc.focus(el);
            int idx = indexAt(x);
            long now = System.nanoTime();
            boolean dbl = now - lastClick < 350_000_000L && Math.abs(idx - lastClickIndex) <= 1;
            lastClick = now;
            lastClickIndex = idx;
            if (dbl) {
                model.selectWordAt(idx);
            } else {
                model.placeCursor(idx, false);
                dragging = true;
            }
            return true;
        }

        @Override public void mouseDragged(UiElement e, int x, int y) { if (dragging) model.placeCursor(indexAt(x), true); }

        @Override public void mouseReleased(UiElement e, int x, int y, int button) { dragging = false; }

        @Override
        public boolean keyPressed(UiElement e, UiKey key, boolean ctrl, boolean shift) {
            sync();
            boolean edited = false;
            switch (key) {
                case ENTER -> {
                    commit();
                    String action = el.attr("onSubmit");
                    if (action != null && !action.isBlank()) doc.fireAction(el, action.trim());
                    return true;
                }
                case BACKSPACE -> edited = model.backspace(ctrl);
                case DELETE -> edited = model.delete(ctrl);
                case LEFT -> model.left(shift, ctrl);
                case RIGHT -> model.right(shift, ctrl);
                case HOME -> model.home(shift);
                case END -> model.end(shift);
                case A -> {
                    if (!ctrl) return false;
                    model.selectAll();
                }
                case C -> {
                    if (!ctrl) return false;
                    if (model.hasSelection()) doc.setClipboard(model.selected());
                }
                case X -> {
                    if (!ctrl) return false;
                    if (model.hasSelection()) {
                        doc.setClipboard(model.selected());
                        model.deleteSelection();
                        edited = true;
                    }
                }
                case V -> {
                    if (!ctrl) return false;
                    edited = model.insert(doc.clipboard(), maxLen());
                }
                default -> { return false; }
            }
            if (edited) commit();
            return true;
        }

        @Override
        public boolean charTyped(UiElement e, char c) {
            if (c < 32 || c == 127) return false;
            sync();
            if (model.insert(String.valueOf(c), maxLen())) commit();
            return true;
        }

        @Override
        public void focusChanged(UiElement e, boolean focused) {
            if (focused) sync();
            else dragging = false;
            LayoutBox b = box();
            if (b != null) doc.textInput(focused, b.x, b.y, b.w, b.h);
            else doc.textInput(focused, 0, 0, 0, 0);
        }

        /** IME 조합창을 띄울 글자 커서 위치 {x, y, 높이}. */
        int[] caret() {
            LayoutBox b = box();
            if (b == null) return null;
            String s = model.text();
            int cur = Math.min(model.cursor(), s.length()), start = Math.min(viewStart, cur);
            return new int[] {textX + tm.width(s.substring(start, cur)), b.y, b.h};
        }
    }

    // ---- scroll (세로 스크롤 컨테이너) ----

    public static final class Scroll extends Base {
        private static final int BAR_W = 4, STEP = 12;
        private int scrollY, contentH, viewH;
        private boolean dragBar;
        private int dragGrab;

        Scroll(UiElement el, UiDocument doc, TextMetrics tm) { super(el, doc, tm); }

        @Override public boolean container() { return true; }

        public int scrollY() { return scrollY; }

        /** 레이아웃이 알려 주는 내용/보이는 높이. 스크롤 위치를 범위 안으로 되돌린다. */
        public void setContent(int contentH, int viewH) {
            this.contentH = contentH;
            this.viewH = viewH;
            scrollY = Math.max(0, Math.min(scrollY, Math.max(0, contentH - viewH)));
        }

        private boolean scrollable() { return contentH > viewH && viewH > 0; }

        private void setScroll(int v) {
            int clamped = Math.max(0, Math.min(v, Math.max(0, contentH - viewH)));
            if (clamped == scrollY) return;
            scrollY = clamped;
            doc.markDirty();
        }

        private int thumbH() { return Math.max(10, (int) ((long) viewH * viewH / Math.max(1, contentH))); }

        @Override public void paint(UiGfx g, int x, int y, int w, int h, Frame f) { }

        @Override
        public void paintOver(UiGfx g, int x, int y, int w, int h, Frame f) {
            if (!scrollable()) return;
            int th = thumbH(), range = Math.max(1, h - th), maxScroll = Math.max(1, contentH - viewH);
            int ty = y + range * scrollY / maxScroll;
            g.fillRect(x + w - BAR_W, y, BAR_W, h, 0x40000000);
            g.fillRect(x + w - BAR_W + 1, ty, BAR_W - 2, th, dragBar ? 0xFFFFFFFF : 0xC0C0C4D8);
        }

        @Override public Cursor cursor() { return null; }

        @Override
        public boolean mouseScrolled(UiElement e, int x, int y, double amount) {
            if (!scrollable()) return false;
            int before = scrollY;
            setScroll(scrollY - (int) Math.signum(amount) * STEP);
            return scrollY != before || true;
        }

        @Override
        public boolean mousePressed(UiElement e, int x, int y, int button) {
            LayoutBox b = box();
            if (button != 0 || b == null || !scrollable() || x < b.x + b.w - BAR_W - 1) return false;
            int th = thumbH(), range = Math.max(1, b.h - th), maxScroll = Math.max(1, contentH - viewH);
            int ty = b.y + range * scrollY / maxScroll;
            dragBar = true;
            dragGrab = y >= ty && y < ty + th ? y - ty : th / 2;
            drag(y);
            return true;
        }

        private void drag(int y) {
            LayoutBox b = box();
            if (b == null) return;
            int th = thumbH(), range = Math.max(1, b.h - th), maxScroll = Math.max(1, contentH - viewH);
            setScroll(Math.round((float) (y - dragGrab - b.y) * maxScroll / range));
        }

        @Override public void mouseDragged(UiElement e, int x, int y) { if (dragBar) drag(y); }

        @Override public void mouseReleased(UiElement e, int x, int y, int button) { dragBar = false; }
    }

    // ---- tabs ----

    public static final class Tabs extends Base {
        private int active;

        Tabs(UiElement el, UiDocument doc, TextMetrics tm) { super(el, doc, tm); }

        @Override public boolean container() { return true; }

        public int headerHeight() { return tm.lineHeight() + 7; }

        private List<UiElement> tabs() {
            List<UiElement> r = new ArrayList<>();
            for (UiElement c : el.children()) if (c.tag().equals("tab")) r.add(c);
            return r;
        }

        /** 머리글 안의 x(요소 왼쪽 기준)에 있는 탭 번호, 없으면 -1. */
        public int tabAt(int relX) {
            List<UiElement> tabs = tabs();
            for (int i = 0; i < tabs.size(); i++) {
                int[] s = slot(i);
                if (relX >= s[0] && relX < s[0] + s[1]) return i;
            }
            return -1;
        }

        /** 보이는 탭을 정한다(에디터가 선택한 요소가 있는 탭을 보여 줄 때). */
        public void setActive(int i) {
            active = Math.max(0, i);
            doc.markDirty();
        }

        /** 지금 보이는 탭 번호(범위 안으로 보정). */
        public int active() {
            int n = tabs().size();
            return n == 0 ? 0 : Math.max(0, Math.min(active, n - 1));
        }

        private String title(UiElement tab, int i) {
            String t = tab.attr("title");
            if (t == null) t = tab.id();
            return t == null ? "Tab " + (i + 1) : doc.interpolate(t);
        }

        private int[] slot(int i) {
            int x = 0;
            List<UiElement> tabs = tabs();
            for (int k = 0; k < i; k++) x += tm.width(title(tabs.get(k), k)) + 14;
            return new int[] {x, tm.width(title(tabs.get(i), i)) + 14};
        }

        @Override public boolean focusable() { return false; }

        @Override
        public void paint(UiGfx g, int x, int y, int w, int h, Frame f) {
            List<UiElement> tabs = tabs();
            int hh = headerHeight();
            g.fillRect(x, y + hh - 1, w, 1, C_BORDER);
            for (int i = 0; i < tabs.size(); i++) {
                int[] s = slot(i);
                boolean on = i == active(), hot = f.hovered() && f.mouseY() >= y && f.mouseY() < y + hh && f.mouseX() >= x + s[0] && f.mouseX() < x + s[0] + s[1];
                g.fillRect(x + s[0], y, s[1], hh, on ? 0xFF3A3F55 : hot ? 0xFF2A2D3E : 0xFF1A1C28);
                g.fillRect(x + s[0], y, s[1], 1, on ? C_ACCENT : C_BORDER);
                g.drawText(title(tabs.get(i), i), x + s[0] + 7, y + (hh - tm.lineHeight()) / 2 + 1, on ? C_TEXT : C_DIM, true);
            }
        }

        @Override
        public boolean mousePressed(UiElement e, int x, int y, int button) {
            LayoutBox b = box();
            if (button != 0 || b == null || y >= b.y + headerHeight()) return false;
            List<UiElement> tabs = tabs();
            for (int i = 0; i < tabs.size(); i++) {
                int[] s = slot(i);
                if (x >= b.x + s[0] && x < b.x + s[0] + s[1]) {
                    if (i != active()) {
                        active = i;
                        doc.markDirty();
                        changed();
                    }
                    return true;
                }
            }
            return false;
        }

        @Override public Cursor cursor() { return null; }

        @Override
        public boolean mouseScrolled(UiElement e, int x, int y, double amount) { return false; }
    }

    // ---- select (드롭다운) ----

    public static final class Select extends Base {
        private boolean open;
        private int hoverRow = -1, keyRow = -1;

        Select(UiElement el, UiDocument doc, TextMetrics tm) { super(el, doc, tm); }

        private List<String> options() {
            List<String> r = new ArrayList<>();
            for (String s : attr("options", "").split(",")) if (!s.isBlank()) r.add(s.trim());
            return r;
        }

        private int rowH() { return tm.lineHeight() + 4; }

        @Override
        public int intrinsicWidth() {
            int w = tm.width(attr("value", ""));
            for (String o : options()) w = Math.max(w, tm.width(o));
            return w + 22;
        }

        @Override public int intrinsicHeight() { return tm.lineHeight() + 6; }

        @Override public boolean focusable() { return enabled(); }

        @Override public Cursor cursor() { return enabled() ? Cursor.POINTER : null; }

        @Override
        public void paint(UiGfx g, int x, int y, int w, int h, Frame f) {
            boolean en = enabled();
            g.fillRect(x, y, w, h, !en ? C_DIM : f.hovered() || open ? C_BORDER_HOT : style("border-color", C_BORDER));
            g.fillRect(x + 1, y + 1, w - 2, h - 2, style("background", 0xFF202230));
            String v = attr("value", "");
            g.drawText(v.isEmpty() ? attr("placeholder", "") : v, x + 5, y + (h - tm.lineHeight()) / 2 + 1, v.isEmpty() || !en ? C_DIM : style("color", C_TEXT), false);
            g.drawText(open ? "^" : "v", x + w - 10, y + (h - tm.lineHeight()) / 2 + 1, C_DIM, false);
            if (focused()) outline(g, x - 1, y - 1, w + 2, h + 2, C_FOCUS);
        }

        private int[] popupRect() {
            LayoutBox b = box();
            if (b == null) return new int[] {0, 0, 0, 0};
            int h = options().size() * rowH() + 2, y = b.y + b.h;
            if (y + h > doc.viewHeight() && b.y - h >= 0) y = b.y - h;
            return new int[] {b.x, y, b.w, h};
        }

        @Override public boolean popupOpen() { return open; }

        @Override
        public void paintPopup(UiGfx g, Frame f) {
            if (!open) return;
            int[] r = popupRect();
            List<String> opts = options();
            g.fillRect(r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, C_BORDER_HOT);
            g.fillRect(r[0], r[1], r[2], r[3], 0xFF161820);
            hoverRow = -1;
            for (int i = 0; i < opts.size(); i++) {
                int ry = r[1] + 1 + i * rowH();
                boolean hot = f.mouseX() >= r[0] && f.mouseX() < r[0] + r[2] && f.mouseY() >= ry && f.mouseY() < ry + rowH();
                if (hot) hoverRow = i;
                if (hot || i == keyRow) g.fillRect(r[0], ry, r[2], rowH(), 0xFF3A4A80);
                else if (opts.get(i).equals(attr("value", ""))) g.fillRect(r[0], ry, r[2], rowH(), 0xFF262A40);
                g.drawText(opts.get(i), r[0] + 5, ry + 3, C_TEXT, false);
            }
        }

        @Override
        public boolean popupPressed(UiElement e, int x, int y) {
            int[] r = popupRect();
            if (x < r[0] || x >= r[0] + r[2] || y < r[1] || y >= r[1] + r[3]) return false;
            int row = (y - r[1] - 1) / rowH();
            List<String> opts = options();
            if (row >= 0 && row < opts.size()) choose(opts.get(row));
            return true;
        }

        @Override public void closePopup() { open = false; }

        @Override
        public boolean popupKey(UiElement e, UiKey key) {
            List<String> opts = options();
            if (opts.isEmpty()) {
                open = false;
                return true;
            }
            int cur = keyRow >= 0 ? keyRow : Math.max(0, opts.indexOf(attr("value", "")));
            switch (key) {
                case UP -> keyRow = Math.max(0, cur - 1);
                case DOWN -> keyRow = Math.min(opts.size() - 1, cur + 1);
                case ENTER, SPACE -> {
                    choose(opts.get(cur));
                    open = false;
                }
                case ESCAPE -> open = false;
                default -> { return true; }
            }
            return true;
        }

        private void choose(String v) {
            if (!v.equals(el.attr("value"))) {
                el.setAttr("value", v);
                changed();
            }
        }

        @Override
        public boolean mousePressed(UiElement e, int x, int y, int button) {
            if (button != 0 || !enabled()) return false;
            open = true;
            keyRow = -1;
            doc.openPopup(el);
            return true;
        }

        @Override
        public boolean keyPressed(UiElement e, UiKey key, boolean ctrl, boolean shift) {
            List<String> opts = options();
            if (opts.isEmpty()) return false;
            int cur = opts.indexOf(attr("value", ""));
            switch (key) {
                case UP -> choose(opts.get(Math.max(0, cur <= 0 ? 0 : cur - 1)));
                case DOWN -> choose(opts.get(Math.min(opts.size() - 1, cur + 1)));
                case ENTER, SPACE -> {
                    open = true;
                    keyRow = Math.max(0, cur);
                    doc.openPopup(el);
                }
                default -> { return false; }
            }
            return true;
        }
    }
}
