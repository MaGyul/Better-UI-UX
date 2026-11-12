package dev.magyul.betteruiux.core.xml;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UiParserTest {
    @Test
    void parsesTreeAndAttributes() throws Exception {
        UiNode root = UiParser.parse("""
            <screen id="main">
              <column gap="4">
                <label text="Hello"/>
                <button id="ok" onClick="mymod:ok">OK</button>
              </column>
            </screen>
            """);
        assertEquals("screen", root.tag());
        UiNode column = root.children().get(0);
        assertEquals("4", column.attr("gap"));
        UiNode button = column.children().get(1);
        assertEquals("ok", button.attr("id"));
        assertEquals("OK", button.text());
    }

    @Test
    void reportsLineOnBrokenXml() {
        UiParseException e = assertThrows(UiParseException.class,
            () -> UiParser.parse("<screen>\n<column>\n</screen>"));
        assertTrue(e.line() >= 1);
    }

    @Test
    void rejectsDtd() {
        assertThrows(UiParseException.class,
            () -> UiParser.parse("<!DOCTYPE a [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><a>&x;</a>"));
    }
}
