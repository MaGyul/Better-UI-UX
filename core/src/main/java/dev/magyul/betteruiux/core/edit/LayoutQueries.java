package dev.magyul.betteruiux.core.edit;

import dev.magyul.betteruiux.core.edit.Geometry.Rect;
import dev.magyul.betteruiux.core.layout.Insets;
import dev.magyul.betteruiux.core.layout.LayoutBox;
import java.util.ArrayList;
import java.util.List;

/** 편집기가 레이아웃 상자 트리를 조회할 때 쓰는 순수 함수들(상태 없음): 번호로 찾기, 컨테이너 종류 판별, 원본 기준 부모/자식. */
final class LayoutQueries {
    private LayoutQueries() {}

    static LayoutBox boxByIndex(LayoutBox b, int idx) {
        if (b.node.sourceIndex() == idx) return b;
        for (LayoutBox c : b.children) {
            LayoutBox r = boxByIndex(c, idx);
            if (r != null) return r;
        }
        return null;
    }

    static Rect rectOf(LayoutBox b) { return new Rect(b.x, b.y, b.w, b.h); }

    /** 자식이 x/y 로 자유롭게 놓이지 않고 순서대로 배치되는 컨테이너(row/column/scroll/grid). */
    static boolean isFlow(LayoutBox parent) {
        return parent != null && (parent.tag().equals("row") || parent.tag().equals("column")
                || parent.tag().equals("scroll") || parent.tag().equals("grid"));
    }

    static boolean isScroll(LayoutBox b) { return b != null && b.tag().equals("scroll"); }

    /** 내용보다 작게 줄일 수 없는 컨테이너(row/column/grid). scroll 은 너비만 제한하므로 {@link #isScroll} 로 따로 다룬다. */
    static boolean isSizedFlow(LayoutBox b) {
        return b != null && (b.tag().equals("row") || b.tag().equals("column") || b.tag().equals("grid"));
    }

    static Rect contentRect(LayoutBox parent) {
        Insets pad = Insets.parse(parent.style.get("padding"), Insets.ZERO);
        return new Rect(parent.x + pad.left(), parent.y + pad.top(),
                Math.max(0, parent.w - pad.horizontal()), Math.max(0, parent.h - pad.vertical()));
    }

    /** 원본에 실제로 있는 자식들(템플릿 확장으로 생긴 요소는 그 자식들을 대신 펼쳐서 포함하지 않는다). */
    static List<LayoutBox> displayChildren(LayoutBox parent) {
        List<LayoutBox> out = new ArrayList<>();
        for (LayoutBox c : parent.children) {
            if (c.node.sourceIndex() >= 0) {
                if (c.visible() || c.inactive) out.add(c);
            } else {
                out.addAll(displayChildren(c));
            }
        }
        return out;
    }

    /** 원본에 있는 가장 가까운 조상. */
    static LayoutBox displayParent(LayoutBox b) {
        LayoutBox p = b.parent;
        while (p != null && p.node.sourceIndex() < 0) p = p.parent;
        return p;
    }

    static boolean isInsideOrSelf(LayoutBox box, int movingIndex) {
        for (LayoutBox b = box; b != null; b = b.parent) if (b.node.sourceIndex() == movingIndex) return true;
        return false;
    }
}
