package dev.magyul.betteruiux.core.edit;

import dev.magyul.betteruiux.api.Cursor;
import dev.magyul.betteruiux.core.edit.Geometry.Rect;

/**
 * 화면의 누를 수 있는 영역. {@code cursor} 는 그 위에서의 포인터 모양(없으면 기본), {@code passthrough} 이면 단순 배경이라
 * 누른 것이 아래의 캔버스로 통과한다(툴바의 빈 곳 아래에 걸친 요소를 잡을 수 있도록).
 */
record Region(Rect rect, Runnable action, Cursor cursor, boolean passthrough) {
    Region(Rect rect, Runnable action) { this(rect, action, action == null ? null : Cursor.POINTER, false); }
}
