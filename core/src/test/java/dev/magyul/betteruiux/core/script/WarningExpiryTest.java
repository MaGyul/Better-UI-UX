package dev.magyul.betteruiux.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarningExpiryTest {
    @Test
    void runtimeWarningsFadeAfterAFewSecondsAndReturnWhenRepeated() throws Exception {
        UiDocument d = UiDocument.parse("<screen/>", new FixedMetrics());
        long[] now = {1000};
        d.setClock(() -> now[0]);
        d.warn("거부됨");
        assertEquals(List.of("거부됨"), d.warnings());
        now[0] += 5000;
        assertEquals(List.of("거부됨"), d.warnings(), "아직 남아 있다");
        now[0] += 2000;
        assertEquals(List.of(), d.warnings(), "시간이 지나면 사라진다");
        d.warn("거부됨");
        assertEquals(List.of("거부됨"), d.warnings(), "또 발생하면 다시 보인다");
    }
}
