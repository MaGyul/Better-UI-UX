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

    public static UiNode parse(String xml) throws UiParseException {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_COALESCING, true); // CDATA(<style> 안의 CSS 등)를 일반 텍스트와 합친다
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try {
            XMLStreamReader r = f.createXMLStreamReader(new StringReader(xml));
            Deque<UiNode> stack = new ArrayDeque<>();
            UiNode root = null;
            while (r.hasNext()) {
                switch (r.next()) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        UiNode n = new UiNode(r.getLocalName());
                        for (int i = 0; i < r.getAttributeCount(); i++) {
                            n.attributes().put(r.getAttributeLocalName(i), r.getAttributeValue(i));
                        }
                        if (stack.isEmpty()) root = n; else stack.peek().children().add(n);
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
