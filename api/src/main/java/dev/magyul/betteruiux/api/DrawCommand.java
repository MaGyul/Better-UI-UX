package dev.magyul.betteruiux.api;

/** {@link UiGfx} 호출 하나를 값으로 표현한 것. 골든 테스트/드로우 리스트용. */
public sealed interface DrawCommand {
    record FillRect(int x, int y, int w, int h, int argb) implements DrawCommand {}

    record FillGradient(int x, int y, int w, int h, int topArgb, int bottomArgb) implements DrawCommand {}

    record DrawTexture(String texture, int x, int y, int w, int h,
                       float u, float v, int regionW, int regionH, int texW, int texH, int argb) implements DrawCommand {}

    record DrawText(String text, int x, int y, int argb, boolean shadow) implements DrawCommand {}

    record PushClip(int x, int y, int w, int h) implements DrawCommand {}

    record PopClip() implements DrawCommand {}

    record PushTransform(float tx, float ty, float scale, float rotateDeg, float originX, float originY)
            implements DrawCommand {}

    record PopTransform() implements DrawCommand {}

    record BlurBackground() implements DrawCommand {}
}
