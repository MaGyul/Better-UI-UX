package dev.magyul.betteruiux.core.edit;

import dev.magyul.betteruiux.core.edit.PropertyModel.Kind;
import dev.magyul.betteruiux.core.edit.PropertyModel.PropRow;
import dev.magyul.betteruiux.core.layout.LayoutBox;
import dev.magyul.betteruiux.core.style.Selector;
import java.util.ArrayList;
import java.util.List;

/**
 * 문서 안 {@code <style>} 블록의 규칙을 <b>원본 텍스트 위치와 함께</b> 읽고 고친다(주석·들여쓰기는 그대로 둔다).
 * 외부 {@code <link>} 스타일시트는 다루지 않는다. 파서는 {@link dev.magyul.betteruiux.core.style.Stylesheet} 와 같이 관대하다.
 */
final class StyleText {
    private StyleText() {}

    /** 선언 하나: {@code 속성: 값} 의 값 범위와 선언 전체({@code 속성: 값;}) 범위(절대 위치). */
    record Decl(String prop, int valueStart, int valueEnd, int declStart, int declEnd) {}

    /** 규칙 하나: 선택자 글자, 본문({@code { }} 안쪽) 범위, 선언들. */
    record Rule(String selector, int bodyStart, int bodyEnd, List<Decl> decls) {}

    /** 문서의 모든 {@code <style>} 규칙을 문서 순서로(@keyframes 등 @규칙 제외). */
    static List<Rule> rules(XmlSource src) {
        List<Rule> out = new ArrayList<>();
        for (XmlSource.Element el : src.all()) {
            if (!el.tag.equals("style") || el.selfClosing() || el.endTagStart < 0) continue;
            int from = el.startTagEnd, to = el.endTagStart;
            String content = src.text().substring(from, to);
            String lead = content.stripLeading();
            if (lead.startsWith("<![CDATA[")) {                       // CDATA 안쪽만
                int s = from + content.indexOf("<![CDATA[") + 9;
                int e = src.text().lastIndexOf("]]>", to);
                if (e < s) continue;
                from = s;
                to = e;
            }
            scan(src.text(), from, to, out);
        }
        return out;
    }

    private static void scan(String text, int from, int to, List<Rule> out) {
        char[] m = mask(text, from, to);
        int i = from;
        while (i < to) {
            int open = indexOf(m, '{', i, to);
            if (open < 0) break;
            int close = matching(m, open, to);
            if (close < 0) break;
            String selector = new String(m, i, open - i).trim();
            if (!selector.isEmpty() && !selector.startsWith("@")) {
                out.add(new Rule(selector, open + 1, close, decls(m, open + 1, close)));
            }
            i = close + 1;
        }
    }

    /** 주석({@code /* *}{@code /})을 같은 길이의 공백으로 바꾼 사본(위치를 그대로 쓰려고). */
    private static char[] mask(String text, int from, int to) {
        char[] m = text.toCharArray();
        int i = from;
        while (i < to - 1) {
            if (m[i] == '/' && m[i + 1] == '*') {
                int e = text.indexOf("*/", i + 2);
                int end = e < 0 || e + 2 > to ? to : e + 2;
                for (int k = i; k < end; k++) if (m[k] != '\n') m[k] = ' ';
                i = end;
            } else {
                i++;
            }
        }
        return m;
    }

    private static int indexOf(char[] m, char c, int from, int to) {
        for (int i = from; i < to; i++) if (m[i] == c) return i;
        return -1;
    }

    private static int matching(char[] m, int open, int to) {
        int depth = 0;
        for (int i = open; i < to; i++) {
            if (m[i] == '{') depth++;
            else if (m[i] == '}' && --depth == 0) return i;
        }
        return -1;
    }

    private static List<Decl> decls(char[] m, int from, int to) {
        List<Decl> out = new ArrayList<>();
        int start = from, paren = 0;
        char quote = 0;
        for (int i = from; i <= to; i++) {
            char c = i < to ? m[i] : ';';
            if (quote != 0) {
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') quote = c;
            else if (c == '(') paren++;
            else if (c == ')' && paren > 0) paren--;
            else if (c == ';' && paren == 0) {
                addDecl(m, start, i, i < to ? i + 1 : i, out);
                start = i + 1;
            }
        }
        return out;
    }

    private static void addDecl(char[] m, int s, int e, int declEnd, List<Decl> out) {
        int colon = -1;
        for (int i = s; i < e; i++) if (m[i] == ':') { colon = i; break; }
        if (colon < 0) return;
        String prop = new String(m, s, colon - s).trim();
        if (prop.isEmpty()) return;
        int vs = colon + 1, ve = e;
        while (vs < ve && Character.isWhitespace(m[vs])) vs++;
        while (ve > vs && Character.isWhitespace(m[ve - 1])) ve--;
        int ds = s;
        while (ds < e && Character.isWhitespace(m[ds])) ds++;
        out.add(new Decl(prop, vs, ve, ds, declEnd));
    }

    // ---- 선택한 요소에 적용되는 규칙 ----

    /** 이 요소에 맞는 규칙들(문서 순서)과 그 번호(= {@link #rules} 목록의 위치). */
    static List<Integer> matching(XmlSource src, LayoutBox box) {
        List<Rule> rules = rules(src);
        List<Integer> hits = new ArrayList<>();
        for (int n = 0; n < rules.size(); n++) {
            for (String one : rules.get(n).selector().split(",")) {
                try {
                    if (Selector.parse(one.trim()).matches(box)) {
                        hits.add(n);
                        break;
                    }
                } catch (IllegalArgumentException e) {
                    // 잘못된 선택자는 건너뛴다
                }
            }
        }
        return hits;
    }

    static final String KEY = "css:", ADD_KEY = "css+:";

    /** 속성 패널에 붙일 "스타일 규칙" 구역의 행들(맞는 규칙이 없으면 빈 목록). */
    static List<PropRow> propRows(LayoutBox box, XmlSource src) {
        List<Rule> rules = rules(src);
        List<PropRow> rows = new ArrayList<>();
        List<Integer> hits = matching(src, box);
        rows.add(PropertyModel.header("스타일 규칙"));
        for (int n : hits) {
            Rule r = rules.get(n);
            rows.add(new PropRow(r.selector(), "§rule" + n, "", false, Kind.HEADER, List.of()));
            for (Decl d : r.decls()) {
                String v = src.text().substring(d.valueStart(), d.valueEnd());
                rows.add(new PropRow(d.prop(), KEY + n + ":" + d.prop(), v, true));
            }
            rows.add(new PropRow("+ 추가", ADD_KEY + n, "", true));
        }
        rows.add(new PropRow("+ 새 규칙",NEW_KEY + selectorFor(box), "", true));   // 맞는 규칙이 없어도 첫 규칙을 만들 수 있다
        return rows;
    }

    static final String NEW_KEY = "css*:";

    /** 새 규칙의 선택자: id 가 있으면 #id, 없으면 첫 class, 둘 다 없으면 태그 이름. */
    static String selectorFor(LayoutBox box) {
        String id = box.id();
        if (id != null && !id.isBlank()) return "#" + id.trim();
        String cls = box.node.attr("class");
        if (cls != null && !cls.isBlank()) return "." + cls.trim().split("\\s+")[0];
        return box.tag();
    }

    /** 새 규칙을 만든다: 기존 {@code <style>} 끝에 덧붙이고, 없으면 루트 요소 맨 앞에 {@code <style>} 블록을 만든다. */
    static String addRule(XmlSource src, String selector, String declaration) {
        String s = declaration == null ? "" : declaration.trim();
        int c = s.indexOf(':');
        if (c <= 0) c = s.indexOf(' ');
        if (c <= 0) throw new IllegalArgumentException("속성: 값 형태로 입력하세요");
        String prop = s.substring(0, c).trim(), value = s.substring(c + 1).trim();
        if (!prop.matches("[A-Za-z-][A-Za-z0-9-]*") || value.isEmpty()) throw new IllegalArgumentException("속성: 값 형태로 입력하세요");
        String t = src.text();
        String rule = selector + " { " + prop + ": " + value + "; }";
        for (XmlSource.Element el : src.all()) {
            if (!el.tag.equals("style") || el.selfClosing() || el.endTagStart < 0) continue;
            int at = el.endTagStart;
            int cdata = t.lastIndexOf("]]>", at);
            if (cdata >= el.startTagEnd) at = cdata;
            int ls = at;
            while (ls > el.startTagEnd && t.charAt(ls - 1) != '\n' && Character.isWhitespace(t.charAt(ls - 1))) ls--;   // 닫는 태그 앞 들여쓰기는 그대로 둔다
            String indent = t.substring(ls, at);
            if (ls > el.startTagEnd && t.charAt(ls - 1) == '\n') {
                return t.substring(0, ls) + indent + "  " + rule + "\n" + t.substring(ls);
            }
            return t.substring(0, at) + " " + rule + " " + t.substring(at);
        }
        XmlSource.Element root = src.root();
        if (root.selfClosing()) throw new IllegalArgumentException("루트 요소가 비어 있어 스타일을 넣을 수 없어요");
        String block = "\n  <style>\n    " + rule + "\n  </style>";
        return t.substring(0, root.startTagEnd) + block + t.substring(root.startTagEnd);
    }

    // ---- 고치기 ----

    /**
     * 규칙 {@code ruleNo} 의 선언 {@code prop} 값을 바꾼다. 값이 비었으면(null/빈 문자열) 선언을 지운다. 없는 속성이면 규칙 끝에 덧붙인다.
     *
     * @throws IllegalArgumentException 규칙이 없을 때
     */
    static String setDecl(XmlSource src, int ruleNo, String prop, String value) {
        List<Rule> rules = rules(src);
        if (ruleNo < 0 || ruleNo >= rules.size()) throw new IllegalArgumentException("규칙이 없습니다: " + ruleNo);
        Rule r = rules.get(ruleNo);
        String t = src.text();
        boolean remove = value == null || value.isBlank();
        for (Decl d : r.decls()) {
            if (!d.prop().equalsIgnoreCase(prop)) continue;
            if (!remove) return t.substring(0, d.valueStart()) + value.trim() + t.substring(d.valueEnd());
            int s = d.declStart(), e = d.declEnd();
            while (s > r.bodyStart() && t.charAt(s - 1) != '\n' && Character.isWhitespace(t.charAt(s - 1))) s--;   // 줄 들여쓰기까지
            if (s > r.bodyStart() && t.charAt(s - 1) == '\n') s--;                                                    // 줄 하나를 통째로
            else if (e < r.bodyEnd() && Character.isWhitespace(t.charAt(e))) e++;
            return t.substring(0, s) + t.substring(e);
        }
        if (remove) return t;                                                                                        // 지울 것이 없다
        String body = t.substring(r.bodyStart(), r.bodyEnd());
        int last = body.length();
        while (last > 0 && Character.isWhitespace(body.charAt(last - 1))) last--;
        int at = r.bodyStart() + last;
        boolean needSemi = last > 0 && body.charAt(last - 1) != ';';
        String line = prop + ": " + value.trim() + ";";
        String insert;
        if (body.contains("\n")) {
            String indent = r.decls().isEmpty() ? "      " : indentOf(t, r.decls().get(0).declStart());
            insert = (needSemi ? ";" : "") + "\n" + indent + line;
        } else {
            insert = (needSemi ? ";" : "") + (last > 0 ? " " : " ") + line;
            if (last == 0) at = r.bodyStart();
        }
        return t.substring(0, at) + insert + t.substring(at);
    }

    private static String indentOf(String t, int pos) {
        int s = pos;
        while (s > 0 && t.charAt(s - 1) != '\n') s--;
        StringBuilder sb = new StringBuilder();
        for (int i = s; i < pos && Character.isWhitespace(t.charAt(i)); i++) sb.append(t.charAt(i));
        return sb.toString();
    }

    /** 속성 패널 키({@code css:규칙:속성}, {@code css+:규칙})로 값을 반영한다. 잘못된 입력이면 IllegalArgumentException. */
    static String apply(XmlSource src, String key, String raw) {
        if (key.startsWith(NEW_KEY)) {
            if (raw == null || raw.isBlank()) return src.text();
            return addRule(src, key.substring(NEW_KEY.length()), raw);
        }
        if (key.startsWith(ADD_KEY)) {
            int n = Integer.parseInt(key.substring(ADD_KEY.length()));
            String s = raw == null ? "" : raw.trim();
            if (s.isEmpty()) return src.text();
            int c = s.indexOf(':');
            if (c < 0) c = s.indexOf(' ');
            if (c <= 0) throw new IllegalArgumentException("속성: 값 형태로 입력하세요");
            String prop = s.substring(0, c).trim(), value = s.substring(c + 1).trim();
            if (!prop.matches("[A-Za-z-][A-Za-z0-9-]*") || value.isEmpty()) throw new IllegalArgumentException("속성: 값 형태로 입력하세요");
            return setDecl(src, n, prop, value);
        }
        String rest = key.substring(KEY.length());
        int colon = rest.indexOf(':');
        return setDecl(src, Integer.parseInt(rest.substring(0, colon)), rest.substring(colon + 1), raw);
    }
}
