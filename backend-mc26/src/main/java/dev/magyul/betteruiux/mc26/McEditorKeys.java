package dev.magyul.betteruiux.mc26;

import com.mojang.blaze3d.platform.InputConstants;
import dev.magyul.betteruiux.core.edit.EditorKey;
import net.minecraft.client.input.KeyEvent;

/**
 * 게임 키 → 에디터 키. 26.x 의 키 번호는 SDL 스캔코드 기반이라 값이 예전 GLFW 와 다르므로,
 * 숫자를 직접 쓰지 않고 {@link InputConstants} 상수와 비교한다.
 */
final class McEditorKeys {
    private McEditorKeys() {}

    static EditorKey map(KeyEvent e) {
        int k = e.key();
        if (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) return EditorKey.ENTER;
        if (k == InputConstants.KEY_ESCAPE) return EditorKey.ESCAPE;
        if (k == InputConstants.KEY_BACKSPACE) return EditorKey.BACKSPACE;
        if (k == InputConstants.KEY_DELETE) return EditorKey.DELETE;
        if (k == InputConstants.KEY_LEFT) return EditorKey.LEFT;
        if (k == InputConstants.KEY_RIGHT) return EditorKey.RIGHT;
        if (k == InputConstants.KEY_UP) return EditorKey.UP;
        if (k == InputConstants.KEY_DOWN) return EditorKey.DOWN;
        if (k == InputConstants.KEY_HOME) return EditorKey.HOME;
        if (k == InputConstants.KEY_END) return EditorKey.END;
        if (k == InputConstants.KEY_TAB) return EditorKey.TAB;
        if (k == InputConstants.KEY_A) return EditorKey.A;
        if (k == InputConstants.KEY_C) return EditorKey.C;
        if (k == InputConstants.KEY_V) return EditorKey.V;
        if (k == InputConstants.KEY_X) return EditorKey.X;
        if (k == InputConstants.KEY_Z) return EditorKey.Z;
        if (k == InputConstants.KEY_Y) return EditorKey.Y;
        if (k == InputConstants.KEY_S) return EditorKey.S;
        if (k == InputConstants.KEY_D) return EditorKey.D;
        if (k == InputConstants.KEY_G) return EditorKey.G;
        if (k == InputConstants.KEY_F1) return EditorKey.F1;
        return EditorKey.OTHER;
    }
}
