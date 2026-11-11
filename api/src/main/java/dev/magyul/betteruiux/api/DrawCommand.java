package dev.magyul.betteruiux.api;

/** {@link UiGfx} 호출 하나를 값으로 표현한 것. 골든 테스트/드로우 리스트용. */
public sealed interface DrawCommand {
    record FillRect(int x, int y, int w, int h, int argb) implements DrawCommand {}

    record DrawStyledText(String text, int x, int y, int argb, boolean shadow, boolean italic, String font) implements DrawCommand {}

    record FillRoundRect(int x, int y, int w, int h, int tl, int tr, int br, int bl, int argb) implements DrawCommand {
        /** 네 반지름이 같을 때의 반지름(아니면 왼쪽 위). */
        public int radius() { return tl; }
    }

    record StrokeRoundRect(int x, int y, int w, int h, int tl, int tr, int br, int bl, int thickness, int argb) implements DrawCommand {
        public int radius() { return tl; }
    }

    record ShadowRoundRect(int x, int y, int w, int h, int tl, int tr, int br, int bl, int blur, int argb) implements DrawCommand {
        public int radius() { return tl; }
    }

    record FillRoundGradient(int x, int y, int w, int h, int tl, int tr, int br, int bl, int mode, int[] colors, float[] stops) implements DrawCommand {}

    record DrawRoundTexture(String texture, int x, int y, int w, int h, int tl, int tr, int br, int bl, int argb) implements DrawCommand {}

    record InsetShadowRoundRect(int x, int y, int w, int h, int tl, int tr, int br, int bl, int ox, int oy, int blur, int spread, int argb) implements DrawCommand {}

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

    record RequestCursor(Cursor cursor) implements DrawCommand {}

    record DrawItem(String itemId, int x, int y, int count) implements DrawCommand {}
}
