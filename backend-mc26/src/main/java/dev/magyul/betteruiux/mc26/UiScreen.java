package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.doc.UiDocument;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** {@link UiDocument} 하나를 마인크래프트 Screen 으로 보여주는 어댑터. 레이아웃/그리기/클릭은 모두 코어가 처리한다. */
public final class UiScreen extends Screen {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("better_uiux");
    private final UiDocument doc;
    private boolean loggedFirstFrame;

    public UiScreen(Component title, UiDocument doc) {
        super(title);
        this.doc = doc;
        doc.setActionHandler((action, source) -> UiActions.dispatch(action, this));
    }

    @Override
    protected void init() {
        doc.layout(this.width, this.height);
    }

    /** 창 크기/GUI scale 변경 시 마인크래프트가 init() 대신 호출한다. */
    @Override
    protected void repositionElements() {
        doc.layout(this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        doc.paint(new McGfx(graphics, this.font), mouseX, mouseY);
        if (!loggedFirstFrame) {
            loggedFirstFrame = true;
            LOG.info("[better-uiux] first frame painted: {}x{}", this.width, this.height);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return doc.mousePressed((int) event.x(), (int) event.y(), event.button()) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return doc.mouseReleased((int) event.x(), (int) event.y(), event.button()) || super.mouseReleased(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
