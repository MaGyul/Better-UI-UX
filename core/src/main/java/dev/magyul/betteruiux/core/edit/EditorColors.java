package dev.magyul.betteruiux.core.edit;

/** 에디터 화면 색(ARGB). 컨트롤러와 오버레이들이 같이 쓴다. */
final class EditorColors {
    private EditorColors() {}

    static final int C_MULTI = 0xFF55C8FF;
    static final int C_PANEL = 0xF2161820, C_BTN = 0xFF3A3F55, C_BTN_OFF = 0xFF262838, C_SEL = 0xFFFFFF55,
            C_HOVER = 0x9055FFFF, C_GUIDE = 0xFFFF55FF, C_TEXT = 0xFFFFFFFF, C_DIM = 0xFF9090A0, C_ERR = 0xFFFF6060,
            C_OK = 0xFF80FF80, C_ROW_SEL = 0xFF3A4A80, C_ROW_HOVER = 0xFF2A3050, C_FIELD = 0xFF101018,
            C_DROP_OK = 0xFF55FF77, C_DROP_BAD = 0xFFFF5555, C_TRASH = 0x70FF3030, C_TAB = 0xFF2E3350,
            SCREEN_OUTSIDE = 0x80000000, SCREEN_FRAME = 0xFF55AAFF, C_BTN_HOT = 0xFF7A6A20, C_SELECTION = 0xFF3A62B0;
}
