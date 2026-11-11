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

    @Test
    void shorthandValuesAreChecked() {
        for (String[] ok : new String[][] {{"margin", "4 8"}, {"margin", "auto"}, {"padding", "0"}, {"border", "1 solid #FFFFFF"}, {"border", "2px dashed red"},
                {"border", "none"}, {"border-radius", "4 8 4 8"}, {"border-radius", "50%"}, {"box-shadow", "0 2 4 #000000AA"},
                {"box-shadow", "inset 0 0 6 #000000AA, 0 1 2 red"}, {"box-shadow", "none"}, {"background", "#102030"}, {"background", "linear-gradient(#FF0000, #0000FF)"},
                {"background", "transparent"}, {"font-weight", "bold"}, {"font-weight", "700"}, {"outline", "1 solid #FFFFFF"}}) {
            assertNull(PropValues.check(ok[0], ok[1]), ok[0] + ": " + ok[1]);
        }
        assertNotNull(PropValues.check("margin", "1 2 3 4 5"));
        assertNotNull(PropValues.check("margin", "abc"));
        assertNotNull(PropValues.check("border", "1 sold #FFFFFF"));
        assertNotNull(PropValues.check("border-radius", "big"));
        assertNotNull(PropValues.check("box-shadow", "0 #000000"));
        assertNotNull(PropValues.check("box-shadow", "0 2 4 nocolor"));
        assertNotNull(PropValues.check("background", "12abc"));
        assertNotNull(PropValues.check("font-weight", "heavy"));
    }

    @Test
    void valueSuggestionsExistForShorthands() {
        assertTrue(PropValues.suggest("box-shadow").contains("none"));
        assertTrue(PropValues.suggest("background").stream().anyMatch(x -> x.startsWith("linear-gradient")));
        assertTrue(PropValues.suggest("border").contains("1 solid #FFFFFF"));
    }
}
