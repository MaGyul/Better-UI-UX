package dev.magyul.betteruiux.core.edit;

/** 속성 칸 하나의 편집 상태: 글자, 커서, 선택 범위의 반대쪽 끝(anchor). 화면 좌표와는 상관없는 순수한 글자 편집만 맡는다. */
final class FieldText {
    final StringBuilder buf = new StringBuilder();
    int cursor;
    /** 선택 범위의 반대쪽 끝(커서와 같으면 선택 없음). */
    int anchor;

    boolean hasSelection() { return anchor != cursor; }

    int selectionStart() { return Math.min(anchor, cursor); }

    int selectionEnd() { return Math.max(anchor, cursor); }

    String selectedText() { return buf.substring(selectionStart(), selectionEnd()); }

    void deleteSelection() { deleteRange(selectionStart(), selectionEnd()); }

    void deleteRange(int from, int to) {
        int a = Math.max(0, Math.min(from, to)), b = Math.min(buf.length(), Math.max(from, to));
        buf.delete(a, b);
        cursor = anchor = a;
    }

    /** 선택 범위가 있으면 바꾸고, 없으면 커서 자리에 넣는다. 줄바꿈/제어 문자는 공백 하나로 바꾼다(한 줄 칸이므로). */
    void insertText(String s) {
        String clean = s.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\p{Cntrl}", "");
        if (clean.isEmpty()) return;
        if (hasSelection()) deleteSelection();
        buf.insert(cursor, clean);
        cursor += clean.length();
        anchor = cursor;
    }

    int wordLeft(int pos) {
        int i = Math.min(pos, buf.length());
        while (i > 0 && Character.isWhitespace(buf.charAt(i - 1))) i--;
        while (i > 0 && !Character.isWhitespace(buf.charAt(i - 1))) i--;
        return i;
    }

    int wordRight(int pos) {
        int n = buf.length(), i = Math.max(0, pos);
        while (i < n && !Character.isWhitespace(buf.charAt(i))) i++;
        while (i < n && Character.isWhitespace(buf.charAt(i))) i++;
        return i;
    }

    /** 칸 전체를 새 글자로 바꾸고 전체를 선택한다(바로 입력하면 값이 바뀌고, 방향키/Home/End 로 이어 쓸 수 있다). */
    void setAllSelected(String value) {
        buf.setLength(0);
        buf.append(value);
        anchor = 0;
        cursor = buf.length();
    }

    /** 칸 전체를 새 글자로 바꾸고 커서를 끝에 둔다(선택 없음). */
    void setCaretAtEnd(String value) {
        buf.setLength(0);
        buf.append(value);
        cursor = anchor = buf.length();
    }

    /** 가까운 낱말(공백으로 끊기는 덩어리)을 선택한다. */
    void selectWordAt(int idx) {
        int s = Math.min(idx, buf.length()), e = s;
        while (s > 0 && !Character.isWhitespace(buf.charAt(s - 1))) s--;
        while (e < buf.length() && !Character.isWhitespace(buf.charAt(e))) e++;
        anchor = s;
        cursor = e;
    }
}
