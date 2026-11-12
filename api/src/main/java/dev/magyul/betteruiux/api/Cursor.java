package dev.magyul.betteruiux.api;

/** 마우스 포인터 모양. 게임이 지원하는 모양으로 백엔드가 바꿔 준다(없는 모양은 가장 가까운 것으로). */
public enum Cursor {
    DEFAULT, POINTER, TEXT, CROSSHAIR, RESIZE_H, RESIZE_V, MOVE, NOT_ALLOWED;

    /** CSS 의 {@code cursor: ...} 값에서. 알 수 없으면 null. */
    public static Cursor parse(String css) {
        if (css == null) return null;
        return switch (css.trim().toLowerCase()) {
            case "default", "auto" -> DEFAULT;
            case "pointer" -> POINTER;
            case "text" -> TEXT;
            case "crosshair" -> CROSSHAIR;
            case "ew-resize", "col-resize" -> RESIZE_H;
            case "ns-resize", "row-resize" -> RESIZE_V;
            case "move", "grab", "grabbing", "all-scroll" -> MOVE;
            case "not-allowed", "no-drop" -> NOT_ALLOWED;
            default -> null;
        };
    }
}
