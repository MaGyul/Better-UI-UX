package dev.magyul.betteruiux.core.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PropValuesTest {
    @Test
    void validValuesPass() {
        for (String[] ok : new String[][] {{"width", "12"}, {"width", "50%"}, {"width", "auto"}, {"margin", "1 2 3 4"}, {"height", "fill"},
                {"opacity", "0.5"}, {"z-index", "-1"}, {"color", "#FF0000"}, {"color", "red"}, {"background-color", "rgb(1,2,3)"},
                {"color", "transparent"}, {"position", "absolute"}, {"display", "flex"}, {"width", "calc(100% - 4px)"},
                {"width", "var(--w)"}, {"color", "inherit"}, {"font-size", "1.5em"}, {"opacity", "1 !important"}, {"gap", "4 8"}}) {
            assertNull(PropValues.check(ok[0], ok[1]), ok[0] + ": " + ok[1]);
        }
    }

    @Test
    void obviousMistakesAreReported() {
        assertNotNull(PropValues.check("width", "abc"));
        assertNotNull(PropValues.check("opacity", "red"));
        assertNotNull(PropValues.check("color", "notacolor"));
        assertNotNull(PropValues.check("position", "sticky2"));
        assertTrue(PropValues.check("display", "flexx").contains("flex"), "가능한 값을 알려 준다");
        assertNotNull(PropValues.check("z-index", "1.5.2"));
    }

    @Test
    void unknownPropertiesAreNotChecked() {
        assertNull(PropValues.check("made-up", "whatever"));
        assertNull(PropValues.check("border", "1px solid red"));
    }

    @Test
    void sheetWarnsOncePerBadValue() {
        Stylesheet s = Stylesheet.parse("a { width: abc; opacity: red } b { width: abc } c { width: 10 }");
        List<String> w = s.warnings();
        assertEquals(2, w.stream().filter(x -> x.contains("'width: abc'") || x.contains("'opacity: red'")).count(), w.toString());
        assertTrue(s.rules().size() >= 3, "값은 그대로 저장된다");
    }
}
