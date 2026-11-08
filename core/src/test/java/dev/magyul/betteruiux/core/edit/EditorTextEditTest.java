package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 속성 칸을 채팅창처럼: 선택, Shift/Ctrl 조합, 클립보드, 마우스 커서/드래그/더블클릭. */
class EditorTextEditTest {
    private static final int W = 400, H = 240;
    private static final int FIELD_X = W - 142 + EditorController.FIELD_OFFSET;       // 칸의 왼쪽
    private static final int TEXT_X = FIELD_X + 3;                                    // 글자가 시작하는 x (글자당 6px)
    private static final int TEXT_ROW_Y = 16 + 3 + 3 * 12 + 1;                         // 라벨의 4번째 행("글자")

    private static final String XML = "<screen><label id=\"t\" x=\"140\" y=\"40\" text=\"ab cd ef\"/></screen>";

    private static final class Host implements EditorHost {
        String clipboard = "";

        @Override public UiDocument load(String text) throws UiParseException {
            return UiDocument.parse(text, new FixedMetrics());
        }

        @Override public void save(String text) throws IOException { }

        @Override public String title() { return "t.xml"; }

        @Override public String clipboard() { return clipboard; }

        @Override public void setClipboard(String text) { clipboard = text; }
    }

    private Host host;
    private EditorController ed;

    @BeforeEach
    void setUp() throws Exception {
        host = new Host();
        ed = new EditorController(host, new FixedMetrics(), XML, W, H);
        ed.select(1);
    }

    private void startEditing() {
        ed.mousePressed(FIELD_X + 6, TEXT_ROW_Y, 0);
        ed.mouseReleased(FIELD_X + 6, TEXT_ROW_Y, 0);
        assertTrue(ed.isEditingField());
    }

    private void key(EditorKey k) { ed.keyPressed(k, false, false); }

    private void ctrl(EditorKey k) { ed.keyPressed(k, true, false); }

    private void shift(EditorKey k) { ed.keyPressed(k, false, true); }

    private void ctrlShift(EditorKey k) { ed.keyPressed(k, true, true); }

    private void type(String s) { for (char c : s.toCharArray()) ed.charTyped(c); }

    private String commit() {
        key(EditorKey.ENTER);
        XmlSource s = XmlSource.parse(ed.text());
        return s.attrValue(s.element(1), "text");
    }

    // ---- 선택 / 입력 ----

    @Test
    void clickingAFieldSelectsEverythingSoTypingReplacesIt() {
        startEditing();
        type("hi");
        assertEquals("hi", commit());
    }

    @Test
    void arrowKeysCollapseSelectionAndHomeEndMoveTheCaret() {
        startEditing();
        key(EditorKey.LEFT);                   // 선택이 있으면 시작점으로 접힌다
        type("X");
        assertEquals("Xab cd ef", commit());

        startEditing();
        key(EditorKey.RIGHT);                  // 선택이 있으면 끝점으로 접힌다
        type("Y");
        assertEquals("Xab cd efY", commit());
    }

    @Test
    void shiftArrowsExtendTheSelectionAndTypingReplacesIt() {
        startEditing();
        key(EditorKey.END);
        shift(EditorKey.LEFT);
        shift(EditorKey.LEFT);                 // "ef"
        type("Z");
        assertEquals("ab cd Z", commit());
    }

    @Test
    void shiftHomeSelectsToTheStartAndBackspaceDeletesOnlyTheSelection() {
        startEditing();
        key(EditorKey.END);
        key(EditorKey.LEFT);
        key(EditorKey.LEFT);
        shift(EditorKey.HOME);                 // "ab cd" 선택
        key(EditorKey.BACKSPACE);
        assertEquals("ef", commit());
    }

    // ---- Ctrl 조합: 단어 이동/삭제 ----

    @Test
    void ctrlArrowsJumpByWordAndCtrlShiftSelectsWords() {
        startEditing();
        key(EditorKey.END);
        ctrl(EditorKey.LEFT);                  // "ef" 앞
        type("X");
        assertEquals("ab cd Xef", commit());

        startEditing();
        key(EditorKey.HOME);
        ctrl(EditorKey.RIGHT);                 // "cd" 앞(인덱스 3)
        ctrlShift(EditorKey.RIGHT);            // "cd " 선택
        type("Z");
        assertEquals("ab ZXef", commit());
    }

    @Test
    void ctrlBackspaceAndCtrlDeleteRemoveWholeWords() {
        startEditing();
        key(EditorKey.END);
        ctrl(EditorKey.BACKSPACE);             // "ef" 삭제 → "ab cd "
        type("!");
        assertEquals("ab cd !", commit());

        startEditing();
        key(EditorKey.END);
        ctrl(EditorKey.BACKSPACE);             // "!" 삭제 → "ab cd "
        ctrl(EditorKey.BACKSPACE);             // "cd " 삭제 → "ab "
        type("?");
        assertEquals("ab ?", commit());

        startEditing();
        key(EditorKey.HOME);
        ctrl(EditorKey.DELETE);                // "ab " 삭제
        type("Q");
        assertEquals("Q?", commit());
    }

    // ---- 클립보드 ----

    @Test
    void copyCutPasteUseTheHostClipboard() {
        startEditing();                                           // 전체 선택 상태
        ctrl(EditorKey.C);
        assertEquals("ab cd ef", host.clipboard);
        ctrl(EditorKey.X);
        assertEquals("ab cd ef", host.clipboard);
        ctrl(EditorKey.V);                                        // 잘라낸 것을 다시 붙여넣기
        assertEquals("ab cd ef", commit());

        startEditing();                                           // 전체 선택 상태에서 붙여넣으면 값이 통째로 바뀐다
        host.clipboard = "first\nsecond";                         // 줄바꿈은 공백으로
        ctrl(EditorKey.V);
        assertEquals("first second", commit());

        startEditing();
        key(EditorKey.END);                                       // 선택이 없으면 커서 자리에 이어 붙는다
        host.clipboard = "!";
        ctrl(EditorKey.V);
        assertEquals("first second!", commit());
    }

    @Test
    void copyWithoutSelectionAndLettersWithoutCtrlDoNothing() {
        startEditing();
        key(EditorKey.END);                                       // 선택 해제
        host.clipboard = "keep";
        ctrl(EditorKey.C);
        assertEquals("keep", host.clipboard, "선택이 없으면 클립보드를 건드리지 않는다");
        key(EditorKey.A);
        key(EditorKey.V);
        key(EditorKey.X);
        assertEquals("ab cd ef", commit(), "Ctrl 없이는 글자 키 이벤트로 아무 일도 하지 않는다(글자는 charTyped 로 온다)");
    }

    @Test
    void ctrlASelectsAllAfterMovingTheCaret() {
        startEditing();
        key(EditorKey.HOME);
        ctrl(EditorKey.A);
        type("all");
        assertEquals("all", commit());
    }

    // ---- 마우스 ----

    @Test
    void clickPlacesTheCaretAndDragSelectsARange() {
        startEditing();
        ed.mousePressed(TEXT_X + 12, TEXT_ROW_Y, 0);               // 글자 2개 뒤(인덱스 2)
        ed.mouseDragged(TEXT_X + 20, TEXT_ROW_Y);
        ed.mouseDragged(TEXT_X + 30, TEXT_ROW_Y);                  // 인덱스 5
        ed.mouseReleased(TEXT_X + 30, TEXT_ROW_Y, 0);
        assertTrue(ed.isEditingField(), "칸 안을 누르고 끌어도 편집이 끝나지 않는다");
        type("X");                                                 // " cd" 가 바뀐다
        assertEquals("abX ef", commit());
    }

    @Test
    void clickBetweenCharactersChoosesTheNearestBoundary() {
        startEditing();
        ed.mousePressed(TEXT_X + 14, TEXT_ROW_Y, 0);               // 12(인덱스 2)와 18(인덱스 3) 사이에서 12 쪽에 가깝다
        ed.mouseReleased(TEXT_X + 14, TEXT_ROW_Y, 0);
        type("|");
        assertEquals("ab| cd ef", commit());
    }

    @Test
    void doubleClickSelectsTheWordUnderTheCursor() {
        startEditing();
        ed.mousePressed(TEXT_X + 19, TEXT_ROW_Y, 0);               // "cd" 안
        ed.mouseReleased(TEXT_X + 19, TEXT_ROW_Y, 0);
        ed.mousePressed(TEXT_X + 19, TEXT_ROW_Y, 0);               // 바로 한 번 더
        ed.mouseReleased(TEXT_X + 19, TEXT_ROW_Y, 0);
        type("Z");
        assertEquals("ab Z ef", commit());
    }

    @Test
    void clickingOutsideTheFieldStillCommits() {
        startEditing();
        type("done");
        ed.mousePressed(230, 220, 0);
        ed.mouseReleased(230, 220, 0);
        assertEquals(false, ed.isEditingField());
        XmlSource s = XmlSource.parse(ed.text());
        assertEquals("done", s.attrValue(s.element(1), "text"));
    }

    // ---- 그리기 ----

    @Test
    void selectionIsHighlightedBehindTheText() {
        startEditing();
        DrawList l = new DrawList();
        ed.paint(l);
        // 8글자 × 6px = 48px 폭의 파란 선택 배경(칸 위쪽 + 1)
        assertTrue(l.commands().contains(new DrawCommand.FillRect(TEXT_X, TEXT_ROW_Y - 1 + 1 - 0, 48, 9, 0xFF3A62B0))
                        || l.commands().stream().anyMatch(c -> c instanceof DrawCommand.FillRect r && r.argb() == 0xFF3A62B0 && r.w() == 48),
                "전체 선택 배경");
        key(EditorKey.END);
        DrawList none = new DrawList();
        ed.paint(none);
        assertTrue(none.commands().stream().noneMatch(c -> c instanceof DrawCommand.FillRect r && r.argb() == 0xFF3A62B0),
                "선택이 없으면 배경도 없다");
    }
}
