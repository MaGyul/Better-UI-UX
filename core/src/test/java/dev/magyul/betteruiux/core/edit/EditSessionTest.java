package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EditSessionTest {
    private static final String DOC = "<screen>\n  <label id=\"l\" text=\"a\"/>\n</screen>";

    private static boolean setText(EditSession s, String v) {
        return s.edit(src -> XmlPatcher.setAttr(src, src.element(1), "text", v));
    }

    @Test
    void editUndoRedoAndDirtyTracking() {
        EditSession s = new EditSession(DOC);
        assertFalse(s.isDirty());
        assertFalse(s.canUndo());
        assertTrue(setText(s, "b"));
        assertTrue(s.isDirty());
        assertTrue(setText(s, "c"));
        assertTrue(s.text().contains("text=\"c\""));

        assertTrue(s.undo());
        assertTrue(s.text().contains("text=\"b\""));
        assertTrue(s.redo());
        assertTrue(s.text().contains("text=\"c\""));
        assertTrue(s.undo() && s.undo());
        assertEquals(DOC, s.text());
        assertFalse(s.isDirty(), "처음 상태로 돌아오면 변경 없음");
        assertFalse(s.undo());
    }

    @Test
    void newEditClearsRedoAndNoOpEditsAreIgnored() {
        EditSession s = new EditSession(DOC);
        setText(s, "b");
        s.undo();
        assertTrue(s.canRedo());
        assertFalse(setText(s, "a"), "같은 값은 변경 없음");
        assertTrue(s.canRedo(), "변경 없는 편집은 redo 를 지우지 않는다");
        assertTrue(setText(s, "z"));
        assertFalse(s.canRedo());
    }

    @Test
    void failedPatchLeavesSessionUntouched() {
        EditSession s = new EditSession(DOC);
        assertFalse(s.edit(src -> XmlPatcher.removeElement(src, src.root())));
        assertFalse(s.edit(src -> { throw new IllegalArgumentException("x"); }));
        assertEquals(DOC, s.text());
        assertFalse(s.canUndo());
    }

    @Test
    void saveMarksCleanAndLaterEditsAreDirtyAgain() {
        EditSession s = new EditSession(DOC);
        setText(s, "b");
        s.markSaved();
        assertFalse(s.isDirty());
        setText(s, "c");
        assertTrue(s.isDirty());
        s.undo();
        assertFalse(s.isDirty(), "저장된 상태로 undo 하면 깨끗");
    }

    @Test
    void transientEditsCollapseIntoOneUndoStep() {
        EditSession s = new EditSession(DOC);
        s.beginTransient();
        for (int i = 1; i <= 5; i++) {
            int v = i;
            s.setTransient(src -> XmlPatcher.setAttr(src, src.element(1), "width", String.valueOf(v * 10)));
        }
        assertTrue(s.text().contains("width=\"50\""));
        assertFalse(s.canUndo(), "확정 전에는 undo 기록이 없다");
        s.commitTransient();
        assertTrue(s.canUndo());
        s.undo();
        assertEquals(DOC, s.text(), "드래그 전체가 한 번에 취소된다");
        assertFalse(s.canUndo());
    }

    @Test
    void cancelledTransientRestoresBaseAndUnchangedTransientLeavesNoHistory() {
        EditSession s = new EditSession(DOC);
        s.beginTransient();
        s.setTransient(src -> XmlPatcher.setAttr(src, src.element(1), "width", "9"));
        s.cancelTransient();
        assertEquals(DOC, s.text());
        s.beginTransient();
        s.commitTransient();
        assertFalse(s.canUndo());
        s.beginTransient();
        assertFalse(s.setTransient(src -> { throw new IllegalArgumentException("bad"); }));
        assertEquals(DOC, s.text(), "실패한 임시 편집은 기준 텍스트로 복귀");
    }
}
