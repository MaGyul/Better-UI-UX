package dev.magyul.betteruiux.core.doc;

/** 요소에 발생한 입력 이벤트. 대상(target)에서 부모 방향으로 버블링한다. */
public final class UiEvent {
    public enum Type { CLICK, PRESS, RELEASE, HOVER_ENTER, HOVER_LEAVE }

    /** 이벤트 핸들러. */
    @FunctionalInterface
    public interface Handler {
        void handle(UiEvent event);
    }

    private final Type type;
    private final UiElement target;
    private final int x, y, button;
    private UiElement current;
    private boolean stopped;

    UiEvent(Type type, UiElement target, int x, int y, int button) {
        this.type = type;
        this.target = target;
        this.current = target;
        this.x = x;
        this.y = y;
        this.button = button;
    }

    public Type type() { return type; }

    /** 이벤트가 처음 발생한 가장 깊은 요소. */
    public UiElement target() { return target; }

    /** 지금 핸들러가 등록된 요소(버블링 중 대상 또는 조상). 선택자 위임 핸들러에서는 일치한 요소. */
    public UiElement currentTarget() { return current; }

    /** GUI 픽셀 좌표. */
    public int x() { return x; }

    public int y() { return y; }

    /** 마우스 버튼(0=좌, 1=가운데, 2=우). 마우스 이벤트가 아니면 0. */
    public int button() { return button; }

    /** 이후 조상 요소로의 전달을 막는다. */
    public void stopPropagation() { stopped = true; }

    public boolean isPropagationStopped() { return stopped; }

    void setCurrent(UiElement e) { this.current = e; }
}
