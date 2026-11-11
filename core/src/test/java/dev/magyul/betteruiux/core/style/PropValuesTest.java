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

    @Test
    void flexTransitionAnimationAndTracksAreChecked() {
        for (String[] ok : new String[][] {{"flex", "1"}, {"flex", "1 1 0"}, {"flex", "0 0 auto"}, {"flex", "none"}, {"flex", "1 1 50%"},
                {"grid-template-columns", "1fr 2fr"}, {"grid-template-columns", "repeat(3, 1fr)"}, {"grid-template-columns", "100px minmax(50px, 1fr) auto"},
                {"grid-template-rows", "20% auto"}, {"grid-template-columns", "none"}, {"grid-template-columns", "repeat(2, 1fr 40px)"},
                {"transition", "all 0.2s"}, {"transition", "opacity 0.3s ease"}, {"transition", "background 150ms linear 50ms, width 0.2s"},
                {"transition", "none"}, {"transition", "opacity 0.3s cubic-bezier(0.1, 0.7, 1, 0.1)"}, {"transition", "margin 0.2s steps(4)"},
                {"animation", "pulse 1s"}, {"animation", "pulse 1s ease-in-out 200ms infinite alternate forwards"}, {"animation", "a 1s, b 2s linear"},
                {"animation", "none"}, {"animation", "spin 2s linear 3 paused"}}) {
            assertNull(PropValues.check(ok[0], ok[1]), ok[0] + ": " + ok[1]);
        }
        assertNotNull(PropValues.check("flex", "1 2 3 4"));
        assertNotNull(PropValues.check("flex", "fast"));
        assertNotNull(PropValues.check("grid-template-columns", "1fr abc"));
        assertNotNull(PropValues.check("grid-template-columns", "repeat(x, 1fr)"));
        assertNotNull(PropValues.check("grid-template-columns", "minmax(1fr)"));
        assertTrue(PropValues.check("transition", "opacity").contains("지속 시간"));
        assertTrue(PropValues.check("transition", "display 1s").contains("전환할 수 없는"));
        assertNotNull(PropValues.check("transition", "opacity 1s 2s 3s"));
        assertNotNull(PropValues.check("transition", "opacity foo 1s"));
        assertTrue(PropValues.check("animation", "1s").contains("이름"));
        assertTrue(PropValues.check("animation", "pulse").contains("지속 시간"));
        assertNotNull(PropValues.check("animation", "pulse other 1s"));
        assertNotNull(PropValues.check("animation", "pulse 1s ease linear"));
    }

    @Test
    void suggestionsExistForTheNewTypes() {
        assertTrue(PropValues.suggest("transition").stream().anyMatch(x -> x.startsWith("opacity")));
        assertTrue(PropValues.suggest("animation").contains("none"));
        assertTrue(PropValues.suggest("grid-template-columns").contains("repeat(3, 1fr)"));
        assertTrue(PropValues.suggest("flex").contains("1"));
    }

    @Test
    void contentCursorFamilyAndDecorationAreChecked() {
        for (String[] ok : new String[][] {{"content", "\"> \""}, {"content", "attr(title)"}, {"content", "\"a\" attr(x) \"b\""}, {"content", "none"},
                {"cursor", "pointer"}, {"cursor", "not-allowed"}, {"font-family", "uniform"}, {"font-family", "\"Arial\", sans-serif"},
                {"font-family", "mymod:fancy/title"}, {"font-family", "default"}, {"text-decoration", "underline line-through"}, {"text-decoration", "none"}}) {
            assertNull(PropValues.check(ok[0], ok[1]), ok[0] + ": " + ok[1]);
        }
        assertNotNull(PropValues.check("content", "hello"));
        assertNotNull(PropValues.check("content", "\"a\" b"));
        assertNotNull(PropValues.check("cursor", "hand"));
        assertTrue(PropValues.check("font-family", "Arial").contains("글꼴"));
        assertNotNull(PropValues.check("text-decoration", "underlined"));
        assertTrue(PropValues.suggest("cursor").contains("pointer"));
        assertTrue(PropValues.suggest("font-family").contains("uniform"));
    }
}
