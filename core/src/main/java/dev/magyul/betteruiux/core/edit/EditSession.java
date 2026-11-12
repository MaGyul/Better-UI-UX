package dev.magyul.betteruiux.core.edit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Function;

/**
 * 에디터가 편집 중인 XML 텍스트와 Undo/Redo. 편집은 "텍스트 → 새 텍스트" 변환이고, 이전 텍스트를 스냅샷으로 쌓는다.
 * 드래그처럼 연속 입력은 {@link #beginTransient}/{@link #setTransient}/{@link #commitTransient} 로 한 번의 Undo 단위로 묶는다.
 */
public final class EditSession {
    private static final int MAX_UNDO = 200;

    private String text;
    private String savedText;
    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();
    private String transientBase;

    public EditSession(String initialText) {
        this.text = initialText;
        this.savedText = initialText;
    }

    public String text() { return text; }

    public XmlSource source() { return XmlSource.parse(text); }

    public boolean isDirty() { return !text.equals(savedText); }

    public void markSaved() { savedText = text; }

    public boolean canUndo() { return !undo.isEmpty(); }

    public boolean canRedo() { return !redo.isEmpty(); }

    /** 다시 실행 목록을 비운다(편집기 밖의 다른 되돌리기 단위가 끼어들 때). */
    public void clearRedo() { redo.clear(); }

    /**
     * 편집을 적용한다. 변환이 실패({@link IllegalArgumentException})하거나 결과가 같으면 아무것도 바꾸지 않는다.
     *
     * @return 텍스트가 바뀌었으면 true
     */
    public boolean edit(Function<XmlSource, String> patch) {
        String next;
        try {
            next = patch.apply(XmlSource.parse(text));
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (next == null || next.equals(text)) return false;
        push(text);
        text = next;
        redo.clear();
        return true;
    }

    public boolean undo() {
        if (undo.isEmpty()) return false;
        redo.push(text);
        text = undo.pop();
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) return false;
        undo.push(text);
        text = redo.pop();
        return true;
    }

    // ---- 임시 편집(드래그 중) ----

    /** 지금 텍스트를 기준으로 임시 편집을 시작한다. */
    public void beginTransient() { transientBase = text; }

    public boolean inTransient() { return transientBase != null; }

    /** 기준 텍스트에 변환을 적용한 결과를 Undo 기록 없이 현재 텍스트로 쓴다. 실패하면 기준 텍스트로 되돌린다. */
    public boolean setTransient(Function<XmlSource, String> patch) {
        if (transientBase == null) throw new IllegalStateException("beginTransient() 가 필요합니다");
        try {
            String next = patch.apply(XmlSource.parse(transientBase));
            text = next == null ? transientBase : next;
            return true;
        } catch (IllegalArgumentException e) {
            text = transientBase;
            return false;
        }
    }

    /** 임시 편집을 확정한다(바뀌었다면 Undo 한 단계로 기록). */
    public void commitTransient() {
        if (transientBase == null) return;
        if (!text.equals(transientBase)) {
            push(transientBase);
            redo.clear();
        }
        transientBase = null;
    }

    /** 임시 편집을 버리고 기준 텍스트로 되돌린다. */
    public void cancelTransient() {
        if (transientBase == null) return;
        text = transientBase;
        transientBase = null;
    }

    private void push(String previous) {
        undo.push(previous);
        while (undo.size() > MAX_UNDO) undo.removeLast();
    }
}
