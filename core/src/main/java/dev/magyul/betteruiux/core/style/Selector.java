package dev.magyul.betteruiux.core.style;

import dev.magyul.betteruiux.core.layout.LayoutBox;
import dev.magyul.betteruiux.core.xml.UiNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * CSS 선택자 하나. 지원:
 * <ul>
 *   <li>단순: {@code type}, {@code *}, {@code #id}, {@code .class}, {@code [attr]}, {@code [attr=v]},
 *       {@code [attr~=v] [attr^=v] [attr$=v] [attr*=v] [attr|=v]}(뒤에 {@code i} 플래그 허용)</li>
 *   <li>결합자: 자손(공백), 자식({@code >}), 인접 형제({@code +}), 일반 형제({@code ~})</li>
 *   <li>동적 의사 클래스(대상 요소에만): {@code :hover :active}</li>
 *   <li>정적 의사 클래스: {@code :first-child :last-child :only-child :root :empty :disabled :enabled :checked
 *       :first-of-type :last-of-type :only-of-type}</li>
 *   <li>함수형: {@code :nth-child() :nth-last-child() :nth-of-type() :nth-last-of-type()}({@code odd even an+b}),
 *       {@code :not() :is() :where() :matches()}(선택자 목록)</li>
 * </ul>
 */
public final class Selector {
    public record AttrSel(String name, String op, String value, boolean ignoreCase) {}

    /**
     * 의사 클래스 하나. {@code a},{@code b} 는 nth-* 의 {@code an+b}, {@code list} 는 :not/:is/:where 의 인자.
     */
    public record Pseudo(String name, int a, int b, List<Selector> list) {
        public boolean dynamic() { return DYNAMIC.contains(name); }
    }

    /** {@code element}: {@code ::before} / {@code ::after} (없으면 null). 마지막 단계에만 올 수 있다. */
    public record Compound(String tag, String id, List<String> classes, List<AttrSel> attrs, List<Pseudo> pseudos, String element) {}

    public enum Combinator { DESCENDANT, CHILD, ADJACENT, SIBLING }

    /** {@code combinator}: 바로 왼쪽 단계와의 관계(첫 단계에서는 의미 없음). */
    public record Step(Compound compound, Combinator combinator) {}

    private static final Set<String> DYNAMIC = Set.of("hover", "active", "focus", "focus-within");

    /** 동적 의사 클래스({@code :hover :active :focus :focus-within})를 판정하는 상태. 요소(UiNode) 단위로 묻는다. */
    public interface DynState {
        boolean hovered(UiNode n);

        boolean pressed(UiNode n);

        boolean focused(UiNode n);

        /** 자신이나 자손이 포커스를 가졌는지(기본: 자신만). */
        default boolean focusWithin(UiNode n) { return focused(n); }
    }

    private static boolean dynTest(String name, UiNode n, DynState st) {
        return switch (name) {
            case "hover" -> st.hovered(n);
            case "active" -> st.pressed(n);
            case "focus" -> st.focused(n);
            default -> st.focusWithin(n);
        };
    }
    private static final Set<String> STATIC = Set.of("first-child", "last-child", "only-child", "root", "empty",
            "disabled", "enabled", "checked", "first-of-type", "last-of-type", "only-of-type");
    private static final Set<String> NTH = Set.of("nth-child", "nth-last-child", "nth-of-type", "nth-last-of-type");
    private static final Set<String> LISTS = Set.of("not", "is", "where", "matches", "has");

    private final List<Step> steps;
    private final String source;

    private Selector(List<Step> steps, String source) {
        this.steps = steps;
        this.source = source;
    }

    @Override public String toString() { return source; }

    // ---- 목록 분리 ----

    /** 괄호/대괄호/따옴표 밖의 ',' 로 선택자 목록을 나눈다(공백 제거, 빈 항목은 유지해 호출자가 오류로 처리). */
    public static List<String> splitList(String text) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = 0;
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
                depth = Math.max(0, depth - 1);
            } else if (c == ',' && depth == 0) {
                out.add(text.substring(start, i).trim());
                start = i + 1;
            }
        }
        out.add(text.substring(start).trim());
        return out;
    }

    // ---- 파싱 ----

    public static Selector parse(String text) { return parse(text, false); }

    private static Selector parse(String text, boolean nested) {
        String src = text.trim();
        if (src.isEmpty()) throw new IllegalArgumentException("빈 선택자");
        List<Step> steps = new ArrayList<>();
        Combinator comb = Combinator.DESCENDANT;
        boolean pendingExplicit = false;
        int i = 0, n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '>' || c == '+' || c == '~') {
                if (steps.isEmpty()) throw new IllegalArgumentException("'" + c + "' 앞에 선택자가 없습니다: " + src);
                if (pendingExplicit) throw new IllegalArgumentException("결합자가 연속됩니다: " + src);
                comb = c == '>' ? Combinator.CHILD : c == '+' ? Combinator.ADJACENT : Combinator.SIBLING;
                pendingExplicit = true;
                i++;
            } else {
                int end = i, depth = 0;
                char quote = 0;
                while (end < n) {
                    char d = src.charAt(end);
                    if (quote != 0) {
                        if (d == quote) quote = 0;
                    } else if (d == '"' || d == '\'') {
                        quote = d;
                    } else if (d == '[' || d == '(') {
                        depth++;
                    } else if (d == ']' || d == ')') {
                        depth--;
                    } else if (depth == 0 && (Character.isWhitespace(d) || d == '>' || d == '+' || d == '~')) {
                        break;
                    }
                    end++;
                }
                steps.add(new Step(parseCompound(src.substring(i, end), src, nested), comb));
                comb = Combinator.DESCENDANT;
                pendingExplicit = false;
                i = end;
            }
        }
        if (steps.isEmpty() || pendingExplicit) throw new IllegalArgumentException("불완전한 선택자: " + src);

        for (int k = 0; k < steps.size() - 1; k++) {
            if (steps.get(k).compound().element() != null) {
                throw new IllegalArgumentException("가상 요소는 선택자의 마지막에만 쓸 수 있습니다: " + src);
            }
        }
        if (nested && steps.get(steps.size() - 1).compound().element() != null) {
            throw new IllegalArgumentException("가상 요소는 :not()/:is() 안에서 쓸 수 없습니다: " + src);
        }
        return new Selector(steps, src);
    }

    private static boolean nameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }

    private static Compound parseCompound(String s, String whole, boolean nested) {
        String tag = null, id = null;
        List<String> classes = new ArrayList<>();
        List<AttrSel> attrs = new ArrayList<>();
        List<Pseudo> pseudos = new ArrayList<>();
        String element = null;
        int i = 0, n = s.length();
        int j = i;
        while (j < n && (nameChar(s.charAt(j)) || s.charAt(j) == '*')) j++;
        if (j > i) {
            tag = s.substring(i, j);
            if (tag.equals("*")) tag = null;
            i = j;
        }
        while (i < n) {
            char c = s.charAt(i);
            if (c == '#' || c == '.') {
                int e = i + 1;
                while (e < n && nameChar(s.charAt(e))) e++;
                String name = s.substring(i + 1, e);
                if (name.isEmpty()) throw new IllegalArgumentException("잘못된 선택자: " + whole);
                if (c == '#') id = name;
                else classes.add(name);
                i = e;
            } else if (c == ':') {
                boolean doubleColon = i + 1 < n && s.charAt(i + 1) == ':';
                int e = i + (doubleColon ? 2 : 1);
                int nameStart = e;
                while (e < n && nameChar(s.charAt(e))) e++;
                String el = s.substring(nameStart, e).toLowerCase();
                if (el.equals("before") || el.equals("after")) {            // ::before / ::after (옛 문법 :before 도 허용)
                    element = el;
                    i = e;
                    continue;
                }
                if (doubleColon) {
                    throw new IllegalArgumentException("지원하지 않는 가상 요소 '" + s.substring(i) + "' (" + whole + ")");
                }
                e = i + 1;
                while (e < n && nameChar(s.charAt(e))) e++;
                String name = s.substring(i + 1, e).toLowerCase();
                if (name.isEmpty()) throw new IllegalArgumentException("잘못된 선택자: " + whole);
                if (e < n && s.charAt(e) == '(') {
                    int close = closingParen(s, e);
                    if (close < 0) throw new IllegalArgumentException("')' 가 없습니다: " + whole);
                    pseudos.add(parseFunctional(name, s.substring(e + 1, close), whole));
                    i = close + 1;
                } else {
                    if (!DYNAMIC.contains(name) && !STATIC.contains(name)) {
                        throw new IllegalArgumentException("지원하지 않는 의사 클래스 ':" + name + "' (" + whole + ")");
                    }
                    pseudos.add(new Pseudo(name, 0, 0, List.of()));
                    i = e;
                }
            } else if (c == '[') {
                int e = s.indexOf(']', i);
                if (e < 0) throw new IllegalArgumentException("']' 가 없습니다: " + whole);
                attrs.add(parseAttr(s.substring(i + 1, e).trim(), whole));
                i = e + 1;
            } else {
                throw new IllegalArgumentException("잘못된 선택자: " + whole);
            }
        }
        return new Compound(tag, id, classes, attrs, pseudos, element);
    }

    private static int closingParen(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            if (s.charAt(i) == '(') depth++;
            else if (s.charAt(i) == ')' && --depth == 0) return i;
        }
        return -1;
    }

    private static Pseudo parseFunctional(String name, String arg, String whole) {
        if (name.equals("has")) {                           // :has(> a, + b, ~ c, d): 상대 선택자. 항목마다 결합자를 a 에 2비트씩 담는다(0 후손 1 자식 2 인접 3 형제)
            List<Selector> list = new ArrayList<>();
            int packed = 0, i = 0;
            for (String one : splitList(arg)) {
                String t = one.trim();
                int comb = 0;
                if (t.startsWith(">")) comb = 1;
                else if (t.startsWith("+")) comb = 2;
                else if (t.startsWith("~")) comb = 3;
                if (comb != 0) t = t.substring(1).trim();
                if (t.isEmpty() || i >= 15) throw new IllegalArgumentException("잘못된 :has() 인자 (" + whole + ")");
                list.add(parse(t, true));
                packed |= comb << (2 * i++);
            }
            return new Pseudo("has", packed, 0, list);
        }
        if (LISTS.contains(name)) {
            List<Selector> list = new ArrayList<>();
            for (String one : splitList(arg)) list.add(parse(one, true));
            return new Pseudo(name.equals("matches") ? "is" : name, 0, 0, list);
        }
        if (NTH.contains(name)) {
            int[] ab = parseNth(arg.trim().toLowerCase().replaceAll("\\s+", ""), whole);
            return new Pseudo(name, ab[0], ab[1], List.of());
        }
        throw new IllegalArgumentException("지원하지 않는 의사 클래스 ':" + name + "()' (" + whole + ")");
    }

    /** {@code odd even 3 2n 2n+1 -n+3 n} → {a, b}. */
    static int[] parseNth(String t, String whole) {
        try {
            if (t.equals("odd")) return new int[] {2, 1};
            if (t.equals("even")) return new int[] {2, 0};
            int nPos = t.indexOf('n');
            if (nPos < 0) return new int[] {0, Integer.parseInt(t.startsWith("+") ? t.substring(1) : t)};
            String as = t.substring(0, nPos);
            int a = as.isEmpty() || as.equals("+") ? 1 : as.equals("-") ? -1 : Integer.parseInt(as.startsWith("+") ? as.substring(1) : as);
            String bs = t.substring(nPos + 1);
            int b = bs.isEmpty() ? 0 : Integer.parseInt(bs.startsWith("+") ? bs.substring(1) : bs);
            return new int[] {a, b};
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("잘못된 an+b 식 '" + t + "' (" + whole + ")");
        }
    }

    private static AttrSel parseAttr(String body, String whole) {
        for (String op : new String[] {"~=", "^=", "$=", "*=", "|=", "="}) {
            int k = body.indexOf(op);
            if (k > 0) {
                String v = body.substring(k + op.length()).trim();
                boolean ci = false;
                if (v.length() > 2 && (v.endsWith(" i") || v.endsWith(" I"))) {
                    ci = true;
                    v = v.substring(0, v.length() - 2).trim();
                }
                if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"))) {
                    v = v.substring(1, v.length() - 1);
                }
                return new AttrSel(body.substring(0, k).trim(), op, v, ci);
            }
        }
        if (body.isEmpty()) throw new IllegalArgumentException("빈 속성 선택자: " + whole);
        return new AttrSel(body, "", "", false);
    }

    // ---- 속성 ----

    private static final int BASE = 1000;

    /** (id 수 × 1000²) + ((클래스+속성+의사클래스) × 1000) + 타입 수. :where 는 0, :not/:is 는 인자 중 최대값. */
    public int specificity() {
        long ids = 0, mid = 0, types = 0;
        for (Step s : steps) {
            Compound c = s.compound();
            if (c.id() != null) ids++;
            mid += c.classes().size() + c.attrs().size();
            if (c.tag() != null) types++;
            if (c.element() != null) types++;
            for (Pseudo p : c.pseudos()) {
                switch (p.name()) {
                    case "where" -> { }
                    case "not", "is", "has" -> {
                        int best = 0;
                        for (Selector sel : p.list()) best = Math.max(best, sel.specificity());
                        ids += best / (BASE * BASE);
                        mid += best / BASE % BASE;
                        types += best % BASE;
                    }
                    default -> mid++;
                }
            }
        }
        return (int) (Math.min(ids, BASE - 1) * BASE * BASE + Math.min(mid, BASE - 1) * BASE + Math.min(types, BASE - 1));
    }

    /** {@code ::before} / {@code ::after} 면 그 이름, 아니면 null. */
    public String pseudoElement() { return steps.get(steps.size() - 1).compound().element(); }

    /** 대상 요소의 동적 의사 클래스(알파벳순, 중복 제거). 예: {@code ["active","hover"]} */
    public Set<String> dynamicPseudos() {
        Set<String> r = new TreeSet<>();
        for (Pseudo p : steps.get(steps.size() - 1).compound().pseudos()) if (p.dynamic()) r.add(p.name());
        return r;
    }

    // ---- 매칭 (동적 의사 클래스는 무시하고 구조/속성만 비교) ----
    // UiNode 기준이라 레이아웃 전에도(예: Java API 의 query) 사용할 수 있다.

    public boolean matches(LayoutBox box) { return matches(box.node); }

    /** 구조/속성만 본다(동적 의사 클래스는 항상 참). */
    public boolean matches(UiNode node) { return matchFrom(steps.size() - 1, node, null); }

    /** 동적 의사 클래스를 {@code st} 로 판정해서 맞는지. */
    public boolean matches(UiNode node, DynState st) { return matchFrom(steps.size() - 1, node, st); }

    /** 어느 단계에든 동적 의사 클래스({@code :not()} 안 포함)가 있는지. */
    public boolean hasDynamic() {
        for (Step s : steps) if (compoundHasDynamic(s.compound())) return true;
        return false;
    }

    private static boolean compoundHasDynamic(Compound c) {
        for (Pseudo p : c.pseudos()) {
            if (p.dynamic()) return true;
            for (Selector sub : p.list()) if (sub.hasDynamic()) return true;
        }
        return false;
    }

    /** 어떤 상태 조합에서든 맞을 수 있는지(규칙 분류용: hover/active/focus/focus-within 의 16 조합을 시험). */
    public boolean maybeMatches(UiNode node) {
        for (int mask = 0; mask < 16; mask++) {
            final int m = mask;
            DynState st = new DynState() {
                @Override public boolean hovered(UiNode n) { return (m & 1) != 0; }

                @Override public boolean pressed(UiNode n) { return (m & 2) != 0; }

                @Override public boolean focused(UiNode n) { return (m & 4) != 0; }

                @Override public boolean focusWithin(UiNode n) { return (m & 8) != 0; }
            };
            if (matchFrom(steps.size() - 1, node, st)) return true;
        }
        return false;
    }

    private boolean matchFrom(int idx, UiNode node, DynState dyn) {
        if (!compoundMatches(steps.get(idx).compound(), node, dyn)) return false;
        if (idx == 0) return true;
        UiNode parent = node.parent();
        switch (steps.get(idx).combinator()) {
            case CHILD:
                return parent != null && matchFrom(idx - 1, parent, dyn);
            case ADJACENT: {
                UiNode prev = previousSibling(node);
                return prev != null && matchFrom(idx - 1, prev, dyn);
            }
            case SIBLING: {
                for (UiNode p = previousSibling(node); p != null; p = previousSibling(p)) {
                    if (matchFrom(idx - 1, p, dyn)) return true;
                }
                return false;
            }
            default:
                for (UiNode a = parent; a != null; a = a.parent()) {
                    if (matchFrom(idx - 1, a, dyn)) return true;
                }
                return false;
        }
    }

    /** 레이아웃 대상이 아닌 메타 요소(형제 순서 계산에서 제외). */
    private static boolean isMeta(UiNode n) {
        return n.tag().equals("style") || n.tag().equals("link") || n.tag().equals("template");
    }

    /** 메타 요소를 뺀 형제 목록(자기 자신 포함). 부모가 없으면 자기 하나. */
    private static List<UiNode> siblings(UiNode n) {
        UiNode p = n.parent();
        if (p == null) return List.of(n);
        List<UiNode> out = new ArrayList<>(p.children().size());
        for (UiNode c : p.children()) if (!isMeta(c)) out.add(c);
        return out;
    }

    private static UiNode previousSibling(UiNode n) {
        UiNode p = n.parent();
        if (p == null) return null;
        UiNode prev = null;
        for (UiNode c : p.children()) {
            if (c == n) return prev;
            if (!isMeta(c)) prev = c;
        }
        return null;
    }

    private static boolean enabledAttr(UiNode n) {
        String v = n.attributes().getOrDefault("enabled", "true");
        return v.equalsIgnoreCase("true") || v.equals("1");
    }

    private static boolean checkedAttr(UiNode n) {
        String v = n.attributes().get("checked");
        return v != null && (v.isEmpty() || v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("checked"));
    }

    private static List<String> classList(String cls) {
        return cls == null || cls.isBlank() ? List.of() : List.of(cls.trim().split("\\s+"));
    }

    private static boolean compoundMatches(Compound c, UiNode n, DynState dyn) {
        if (c.tag() != null && !c.tag().equals(n.tag())) return false;
        if (c.id() != null && !c.id().equals(n.attr("id"))) return false;
        if (!c.classes().isEmpty()) {
            List<String> have = classList(n.attr("class"));
            for (String want : c.classes()) if (!have.contains(want)) return false;
        }
        for (AttrSel a : c.attrs()) if (!attrMatches(a, n.attr(a.name()))) return false;
        for (Pseudo p : c.pseudos()) if (!pseudoMatches(p, n, dyn)) return false;
        return true;
    }

    private static boolean pseudoMatches(Pseudo p, UiNode n, DynState dyn) {
        switch (p.name()) {
            case "first-child": return siblings(n).get(0) == n;
            case "last-child": { List<UiNode> s = siblings(n); return s.get(s.size() - 1) == n; }
            case "only-child": return siblings(n).size() == 1;
            case "root": return n.parent() == null;
            case "empty": return n.children().stream().allMatch(Selector::isMeta) && n.text().isBlank();
            case "disabled": return !enabledAttr(n);
            case "enabled": return enabledAttr(n);
            case "checked": return checkedAttr(n);
            case "first-of-type": return nth(n, true, false, 0, 1);
            case "last-of-type": return nth(n, true, true, 0, 1);
            case "only-of-type": return nth(n, true, false, 0, 1) && nth(n, true, true, 0, 1);
            case "nth-child": return nth(n, false, false, p.a(), p.b());
            case "nth-last-child": return nth(n, false, true, p.a(), p.b());
            case "nth-of-type": return nth(n, true, false, p.a(), p.b());
            case "nth-last-of-type": return nth(n, true, true, p.a(), p.b());
            case "not": {
                for (Selector s : p.list()) if (s.matches(n, dyn)) return false;
                return true;
            }
            case "is", "where": {
                for (Selector s : p.list()) if (s.matches(n, dyn)) return true;
                return false;
            }
            case "has": {
                for (int i = 0; i < p.list().size(); i++) {
                    if (hasMatch(n, (p.a() >> (2 * i)) & 3, p.list().get(i), dyn)) return true;
                }
                return false;
            }
            default: return dyn == null || dynTest(p.name(), n, dyn);   // 상태 판정자가 없으면 구조만 본다
        }
    }

    /** {@code :has()} 한 항목: n 의 (후손/자식/바로 다음 형제/뒤따르는 형제) 중 sel 에 맞는 것이 있는지. */
    private static boolean hasMatch(UiNode n, int comb, Selector sel, DynState dyn) {
        switch (comb) {
            case 1:
                for (UiNode c : n.children()) if (!isMeta(c) && sel.matches(c, dyn)) return true;
                return false;
            case 2: {
                UiNode next = nextSibling(n);
                return next != null && sel.matches(next, dyn);
            }
            case 3:
                for (UiNode x = nextSibling(n); x != null; x = nextSibling(x)) if (sel.matches(x, dyn)) return true;
                return false;
            default:
                for (UiNode c : n.children()) {
                    if (isMeta(c)) continue;
                    if (sel.matches(c, dyn) || hasMatch(c, 0, sel, dyn)) return true;
                }
                return false;
        }
    }

    private static UiNode nextSibling(UiNode n) {
        UiNode p = n.parent();
        if (p == null) return null;
        boolean seen = false;
        for (UiNode c : p.children()) {
            if (seen && !isMeta(c)) return c;
            if (c == n) seen = true;
        }
        return null;
    }

    /** 형제 중 (같은 타입만) 몇 번째인지(1부터, fromEnd 면 뒤에서)가 an+b (n>=0) 에 맞는지. */
    private static boolean nth(UiNode n, boolean sameType, boolean fromEnd, int a, int b) {
        List<UiNode> sibs = siblings(n);
        int pos = 0, count = 0;
        for (UiNode s : sibs) {
            if (sameType && !s.tag().equals(n.tag())) continue;
            count++;
            if (s == n) pos = count;
        }
        int index = fromEnd ? count - pos + 1 : pos;
        if (a == 0) return index == b;
        int diff = index - b;
        return diff % a == 0 && diff / a >= 0;
    }

    private static boolean attrMatches(AttrSel a, String actual) {
        if (actual == null) return false;
        String v = a.value();
        if (a.ignoreCase()) {
            actual = actual.toLowerCase();
            v = v.toLowerCase();
        }
        return switch (a.op()) {
            case "" -> true;
            case "=" -> actual.equals(v);
            case "~=" -> classList(actual).contains(v);
            case "|=" -> actual.equals(v) || actual.startsWith(v + "-");
            case "^=" -> !v.isEmpty() && actual.startsWith(v);
            case "$=" -> !v.isEmpty() && actual.endsWith(v);
            case "*=" -> !v.isEmpty() && actual.contains(v);
            default -> false;
        };
    }
}
