package dev.magyul.betteruiux.mc26;

import com.mojang.blaze3d.platform.InputConstants;
import dev.magyul.betteruiux.core.doc.UiKey;
import net.minecraft.client.input.KeyEvent;

/** 게임 키 → 문서(위젯) 키. 에디터 클래스를 불러오지 않도록 {@link McEditorKeys} 와 따로 둔다. */
final class McUiKeys {
    private McUiKeys() {}

    static UiKey map(KeyEvent e) {
        int k = e.key();
        if (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) return UiKey.ENTER;
        if (k == InputConstants.KEY_ESCAPE) return UiKey.ESCAPE;
        if (k == InputConstants.KEY_BACKSPACE) return UiKey.BACKSPACE;
        if (k == InputConstants.KEY_DELETE) return UiKey.DELETE;
        if (k == InputConstants.KEY_LEFT) return UiKey.LEFT;
        if (k == InputConstants.KEY_RIGHT) return UiKey.RIGHT;
        if (k == InputConstants.KEY_UP) return UiKey.UP;
        if (k == InputConstants.KEY_DOWN) return UiKey.DOWN;
        if (k == InputConstants.KEY_HOME) return UiKey.HOME;
        if (k == InputConstants.KEY_END) return UiKey.END;
        if (k == InputConstants.KEY_TAB) return UiKey.TAB;
        if (k == InputConstants.KEY_A) return UiKey.A;
        if (k == InputConstants.KEY_C) return UiKey.C;
        if (k == InputConstants.KEY_V) return UiKey.V;
        if (k == InputConstants.KEY_X) return UiKey.X;
        return UiKey.OTHER;
    }
}
