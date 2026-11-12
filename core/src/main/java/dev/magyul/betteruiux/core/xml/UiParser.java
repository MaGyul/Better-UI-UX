package dev.magyul.betteruiux.core.xml;

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** JDK 내장 StAX 기반 UI XML 파서. 외부 엔티티/DTD는 비활성화한다(보안). */
public final class UiParser {
    private UiParser() {}

    private static final java.util.regex.Pattern STYLE_BLOCK = java.util.regex.Pattern.compile("(<style(?:[ \t\r\n][^>]*)?>)(.*?)(</style>)", java.util.regex.Pattern.DOTALL);
    private static final java.util.regex.Pattern LONE_AMP = java.util.regex.Pattern.compile("&(?!(?:amp|lt|gt|quot|apos|#[0-9]+|#x[0-9a-fA-F]+);)");

    /**
     * {@code <style>} 안의 CSS 중첩({@code &:hover { }})에 쓰는 {@code &} 가 XML 에서는 엔티티 시작이라 파싱이 깨진다. CDATA 로 감싸지 않은
     * style 블록 안의 단독 {@code &} 를 {@code &amp;} 로 바꿔 준다(이미 엔티티인 것과 CDATA 블록은 그대로).
     */
    static String escapeStyleAmpersands(String xml) {
        if (xml.indexOf('&') < 0) return xml;
        java.util.regex.Matcher m = STYLE_BLOCK.matcher(xml);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String body = m.group(2);
            if (!body.contains("<![CDATA[")) body = LONE_AMP.matcher(body).replaceAll("&amp;");
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(m.group(1) + body + m.group(3)));
        }
        m.appendTail(out);
        return out.toString();
    }

    public static UiNode parse(String xml) throws UiParseException {
        xml = escapeStyleAmpersands(xml);
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_COALESCING, true); // CDATA(<style> 안의 CSS 등)를 일반 텍스트와 합친다
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try {
            XMLStreamReader r = f.createXMLStreamReader(new StringReader(xml));
            Deque<UiNode> stack = new ArrayDeque<>();
            UiNode root = null;
            int counter = 0;
            while (r.hasNext()) {
                switch (r.next()) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        UiNode n = new UiNode(r.getLocalName());
                        n.setSourceIndex(counter++);
                        for (int i = 0; i < r.getAttributeCount(); i++) {
                            n.attributes().put(r.getAttributeLocalName(i), r.getAttributeValue(i));
                        }
                        if (stack.isEmpty()) root = n; else stack.peek().append(n);
                        stack.push(n);
                    }
                    case XMLStreamConstants.CHARACTERS -> {
                        if (!stack.isEmpty() && !r.isWhiteSpace()) {
                            UiNode n = stack.peek();
                            n.setText(n.text() + r.getText());
                        }
                    }
                    case XMLStreamConstants.END_ELEMENT -> stack.pop();
                    default -> { }
                }
            }
            if (root == null) throw new UiParseException("빈 문서입니다", -1);
            return root;
        } catch (XMLStreamException e) {
            int line = e.getLocation() != null ? e.getLocation().getLineNumber() : -1;
            throw new UiParseException(e.getMessage(), line);
        }
    }
}
