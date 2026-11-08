package dev.magyul.betteruiux.core.doc;

/** 한 줄 글자 편집 상태(커서, 선택, 단어 단위 이동). 채팅창과 같은 조작을 지원한다. */
final class TextEditModel {
    private final StringBuilder buf = new StringBuilder();
    private int cursor, anchor;

    String text() { return buf.toString(); }

    int cursor() { return cursor; }

    int anchor() { return anchor; }

    int length() { return buf.length(); }

    /** 글자를 통째로 바꾼다(커서는 끝). */
    void set(String s) {
        buf.setLength(0);
        buf.append(s);
        cursor = anchor = buf.length();
    }

    boolean hasSelection() { return cursor != anchor; }

    int selStart() { return Math.min(cursor, anchor); }

    int selEnd() { return Math.max(cursor, anchor); }

    String selected() { return buf.substring(selStart(), selEnd()); }

    void selectAll() {
        anchor = 0;
        cursor = buf.length();
    }

    void placeCursor(int index, boolean extend) {
        cursor = Math.max(0, Math.min(buf.length(), index));
        if (!extend) anchor = cursor;
    }

    void selectWordAt(int index) {
        int s = Math.min(index, buf.length()), e = s;
        while (s > 0 && !Character.isWhitespace(buf.charAt(s - 1))) s--;
        while (e < buf.length() && !Character.isWhitespace(buf.charAt(e))) e++;
        anchor = s;
        cursor = e;
    }

    /** 선택 범위가 있으면 바꾸고 없으면 커서에 넣는다. 제어 문자/줄바꿈은 공백 하나로. 길이 제한을 넘으면 잘라 넣는다. */
    boolean insert(String s, int maxLen) {
        String clean = s.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\p{Cntrl}", "");
        if (clean.isEmpty()) return false;
        if (hasSelection()) deleteSelection();
        int room = maxLen - buf.length();
        if (room <= 0) return false;
        if (clean.length() > room) clean = clean.substring(0, room);
        buf.insert(cursor, clean);
        cursor += clean.length();
        anchor = cursor;
        return true;
    }

    void deleteSelection() {
        int a = selStart(), b = selEnd();
        buf.delete(a, b);
        cursor = anchor = a;
    }

    boolean backspace(boolean word) {
        if (hasSelection()) {
            deleteSelection();
            return true;
        }
        if (cursor == 0) return false;
        int from = word ? wordLeft(cursor) : cursor - 1;
        buf.delete(from, cursor);
        cursor = anchor = from;
        return true;
    }

    boolean delete(boolean word) {
        if (hasSelection()) {
            deleteSelection();
            return true;
        }
        if (cursor >= buf.length()) return false;
        int to = word ? wordRight(cursor) : cursor + 1;
        buf.delete(cursor, to);
        anchor = cursor;
        return true;
    }

    void left(boolean shift, boolean ctrl) {
        if (!shift && hasSelection() && !ctrl) cursor = selStart();
        else cursor = Math.max(0, ctrl ? wordLeft(cursor) : cursor - 1);
        if (!shift) anchor = cursor;
    }

    void right(boolean shift, boolean ctrl) {
        if (!shift && hasSelection() && !ctrl) cursor = selEnd();
        else cursor = Math.min(buf.length(), ctrl ? wordRight(cursor) : cursor + 1);
        if (!shift) anchor = cursor;
    }

    void home(boolean shift) {
        cursor = 0;
        if (!shift) anchor = 0;
    }

    void end(boolean shift) {
        cursor = buf.length();
        if (!shift) anchor = cursor;
    }

    private int wordLeft(int pos) {
        int i = Math.min(pos, buf.length());
        while (i > 0 && Character.isWhitespace(buf.charAt(i - 1))) i--;
        while (i > 0 && !Character.isWhitespace(buf.charAt(i - 1))) i--;
        return i;
    }

    private int wordRight(int pos) {
        int n = buf.length(), i = Math.max(0, pos);
        while (i < n && !Character.isWhitespace(buf.charAt(i))) i++;
        while (i < n && Character.isWhitespace(buf.charAt(i))) i++;
        return i;
    }
}
