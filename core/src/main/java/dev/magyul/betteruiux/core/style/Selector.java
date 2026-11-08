package dev.magyul.betteruiux.core.style;

import dev.magyul.betteruiux.core.layout.LayoutBox;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * CSS 선택자 하나. 지원: {@code type}, {@code *}, {@code #id}, {@code .class}, {@code [attr]}, {@code [attr=v]},
 * {@code [attr~=v] [attr^=v] [attr$=v] [attr*=v]}, 자손(공백)/자식({@code >}) 결합자,
 * 의사 클래스 {@code :hover :active}(동적, 대상 요소에만) 및 {@code :first-child :last-child :root :disabled :enabled}(정적).
 */
public final class Selector {
    public record AttrSel(String name, String op, String value) {}

    public record Compound(String tag, String id, List<String> classes, List<AttrSel> attrs, List<String> pseudos) {}

    /** {@code directChild}: 바로 왼쪽 단계와 자식({@code >}) 관계인지(false 면 자손). */
    public record Step(Compound compound, boolean directChild) {}

    private static final Set<String> DYNAMIC = Set.of("hover", "active");
    private static final Set<String> STATIC = Set.of("first-child", "last-child", "root", "disabled", "enabled");

    private final List<Step> steps;
    private final String source;

    private Selector(List<Step> steps, String source) {
        this.steps = steps;
        this.source = source;
    }

    @Override public String toString() { return source; }

    // ---- 파싱 ----

    public static Selector parse(String text) {
        String src = text.trim();
        if (src.isEmpty()) throw new IllegalArgumentException("빈 선택자");
        List<Step> steps = new ArrayList<>();
        boolean direct = false;
        int i = 0, n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '>') {
                if (steps.isEmpty()) throw new IllegalArgumentException("'>' 앞에 선택자가 없습니다: " + src);
                direct = true;
                i++;
            } else {
                int end = i;
                int bracket = 0;
                while (end < n) {
                    char d = src.charAt(end);
                    if (d == '[') bracket++;
                    else if (d == ']') bracket--;
                    else if (bracket == 0 && (Character.isWhitespace(d) || d == '>')) break;
                    end++;
                }
                steps.add(new Step(parseCompound(src.substring(i, end), src), direct));
                direct = false;
                i = end;
            }
        }
        if (steps.isEmpty() || direct) throw new IllegalArgumentException("불완전한 선택자: " + src);

        // 동적 의사 클래스는 대상(가장 오른쪽) 요소에만 허용한다.
        for (int k = 0; k < steps.size() - 1; k++) {
            for (String p : steps.get(k).compound().pseudos()) {
                if (DYNAMIC.contains(p)) {
                    throw new IllegalArgumentException("':" + p + "' 은(는) 선택자의 마지막 요소에만 쓸 수 있습니다: " + src);
                }
            }
        }
        return new Selector(steps, src);
    }

    private static Compound parseCompound(String s, String whole) {
        String tag = null, id = null;
        List<String> classes = new ArrayList<>();
        List<AttrSel> attrs = new ArrayList<>();
        List<String> pseudos = new ArrayList<>();
        int i = 0, n = s.length();
        // 타입 / *
        int j = i;
        while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '-' || s.charAt(j) == '_' || s.charAt(j) == '*')) j++;
        if (j > i) {
            tag = s.substring(i, j);
            if (tag.equals("*")) tag = null;
            i = j;
        }
        while (i < n) {
            char c = s.charAt(i);
            if (c == '#' || c == '.' || c == ':') {
                int e = i + 1;
                while (e < n && (Character.isLetterOrDigit(s.charAt(e)) || s.charAt(e) == '-' || s.charAt(e) == '_')) e++;
                String name = s.substring(i + 1, e);
                if (name.isEmpty()) throw new IllegalArgumentException("잘못된 선택자: " + whole);
                switch (c) {
                    case '#' -> id = name;
                    case '.' -> classes.add(name);
                    default -> {
                        String p = name.toLowerCase();
                        if (!DYNAMIC.contains(p) && !STATIC.contains(p)) {
                            throw new IllegalArgumentException("지원하지 않는 의사 클래스 ':" + name + "' (" + whole + ")");
                        }
                        pseudos.add(p);
                    }
                }
                i = e;
            } else if (c == '[') {
                int e = s.indexOf(']', i);
                if (e < 0) throw new IllegalArgumentException("']' 가 없습니다: " + whole);
                attrs.add(parseAttr(s.substring(i + 1, e).trim(), whole));
                i = e + 1;
            } else {
                throw new IllegalArgumentException("잘못된 선택자: " + whole);
            }
        }
        return new Compound(tag, id, classes, attrs, pseudos);
    }

    private static AttrSel parseAttr(String body, String whole) {
        for (String op : new String[] {"~=", "^=", "$=", "*=", "="}) {
            int k = body.indexOf(op);
            if (k > 0) {
                String v = body.substring(k + op.length()).trim();
                if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"))) {
                    v = v.substring(1, v.length() - 1);
                }
                return new AttrSel(body.substring(0, k).trim(), op, v);
            }
        }
        if (body.isEmpty()) throw new IllegalArgumentException("빈 속성 선택자: " + whole);
        return new AttrSel(body, "", "");
    }

    // ---- 속성 ----

    /** (id 수 × 100) + ((클래스+속성+의사클래스) × 10) + 타입 수. */
    public int specificity() {
        int ids = 0, mid = 0, types = 0;
        for (Step s : steps) {
            Compound c = s.compound();
            if (c.id() != null) ids++;
            mid += c.classes().size() + c.attrs().size() + c.pseudos().size();
            if (c.tag() != null) types++;
        }
        return ids * 100 + mid * 10 + types;
    }

    /** 대상 요소의 동적 의사 클래스(알파벳순, 중복 제거). 예: {@code ["active","hover"]} */
    public Set<String> dynamicPseudos() {
        Set<String> r = new TreeSet<>();
        for (String p : steps.get(steps.size() - 1).compound().pseudos()) if (DYNAMIC.contains(p)) r.add(p);
        return r;
    }

    // ---- 매칭 (동적 의사 클래스는 무시하고 구조/속성만 비교) ----

    public boolean matches(LayoutBox box) { return matchFrom(steps.size() - 1, box); }

    private boolean matchFrom(int idx, LayoutBox box) {
        if (!compoundMatches(steps.get(idx).compound(), box)) return false;
        if (idx == 0) return true;
        LayoutBox parent = box.parent;
        if (steps.get(idx).directChild()) return parent != null && matchFrom(idx - 1, parent);
        for (LayoutBox a = parent; a != null; a = a.parent) {
            if (matchFrom(idx - 1, a)) return true;
        }
        return false;
    }

    private static boolean compoundMatches(Compound c, LayoutBox b) {
        if (c.tag() != null && !c.tag().equals(b.tag())) return false;
        if (c.id() != null && !c.id().equals(b.node.attr("id"))) return false;
        if (!c.classes().isEmpty()) {
            String cls = b.node.attr("class");
            if (cls == null) return false;
            List<String> have = List.of(cls.trim().split("\\s+"));
            for (String want : c.classes()) if (!have.contains(want)) return false;
        }
        for (AttrSel a : c.attrs()) if (!attrMatches(a, b.node.attr(a.name()))) return false;
        for (String p : c.pseudos()) {
            boolean ok = switch (p) {
                case "first-child" -> b.index == 0;
                case "last-child" -> b.parent != null && b.index == b.parent.children.size() - 1;
                case "root" -> b.parent == null;
                case "disabled" -> !b.node.attributes().getOrDefault("enabled", "true").equalsIgnoreCase("true")
                        && !b.node.attributes().getOrDefault("enabled", "true").equals("1");
                case "enabled" -> b.node.attributes().getOrDefault("enabled", "true").equalsIgnoreCase("true")
                        || b.node.attributes().getOrDefault("enabled", "true").equals("1");
                default -> true; // hover/active 는 StyleResolver 가 상태별 오버레이로 처리
            };
            if (!ok) return false;
        }
        return true;
    }

    private static boolean attrMatches(AttrSel a, String actual) {
        if (actual == null) return false;
        return switch (a.op()) {
            case "" -> true;
            case "=" -> actual.equals(a.value());
            case "~=" -> List.of(actual.trim().split("\\s+")).contains(a.value());
            case "^=" -> actual.startsWith(a.value());
            case "$=" -> actual.endsWith(a.value());
            case "*=" -> actual.contains(a.value());
            default -> false;
        };
    }
}
