package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.api.UiGfx;
import dev.magyul.betteruiux.core.doc.UiElement;
import dev.magyul.betteruiux.core.doc.UiWidget;
import dev.magyul.betteruiux.core.mcv.McvCamera;
import dev.magyul.betteruiux.core.mcv.McvHost;
import dev.magyul.betteruiux.core.mcv.McvWorld;

/**
 * {@code <mc-viewer world="1"/>}: 스크립트가 만든 가상 마인크래프트 월드를 보여 준다({@code world} 를 생략하면 가장 먼저 만든 월드).
 * 마우스 드래그로 카메라를 돌리고, 휠로 확대/축소한다. 그리기는 {@link McViewerPip}(GUI Picture-in-Picture)가 맡는다.
 */
final class McViewerWidget implements UiWidget {
    private static final int MAX_TICKS_PER_FRAME = 5;
    private final UiElement el;
    private int lastX, lastY;
    private boolean dragging;

    McViewerWidget(UiElement el) { this.el = el; }

    @Override public int intrinsicWidth() { return 160; }

    @Override public int intrinsicHeight() { return 120; }

    private McvWorld world() {
        McvHost host = McViewerRegistry.get(el.document());
        if (host == null) return null;
        String attr = el.attr("world");
        if (attr != null) {
            try {
                return host.world(Integer.parseInt(attr.trim()));
            } catch (RuntimeException e) {
                return null;
            }
        }
        return host.primaryWorld();
    }

    @Override
    public void paint(UiGfx gfx, int x, int y, int w, int h, Frame frame) {
        McvWorld world = world();
        if (world == null || w <= 0 || h <= 0) {
            gfx.fillRect(x, y, w, h, 0x55000000);                       // 아직 월드가 없다(스크립트가 만들기 전)
            return;
        }
        advanceTicks(world);
        if (!(gfx instanceof McGfx mc)) return;
        McvCamera cam = world.camera();
        int[] va = world.viewArea();
        double dx = va == null ? world.sizeX() : va[3] - va[0] + 1;
        double dy = va == null ? world.sizeY() : va[4] - va[1] + 1;
        double dz = va == null ? world.sizeZ() : va[5] - va[2] + 1;
        double diag = Math.sqrt(dx * dx + dy * dy + dz * dz);          // 월드가 어느 각도에서도 들어오도록 대각선에 맞춘다
        float scale = (float) (Math.min(w, h) / diag) * cam.zoom();
        mc.addPictureInPicture(new McViewerRenderState(world, cam.yaw(), cam.pitch(), x, y, x + w, y + h, scale, null));
    }

    /**
     * 이 위젯이 그려질 때마다 월드 시계를 돌린다(안 보이는 월드는 멈춘다). 진행된 틱마다 스크립트의 onTick 을 부른다.
     * 한 월드를 위젯 여러 개가 그려도 {@link McvWorld#advance} 가 시간 기준이라 두 번 진행되지 않는다.
     */
    private void advanceTicks(McvWorld world) {
        int n = world.advance(System.nanoTime(), MAX_TICKS_PER_FRAME);
        if (n == 0) return;
        var sink = McViewerRegistry.tickSink(el.document());
        McvHost host = McViewerRegistry.get(el.document());
        if (sink == null || host == null) return;
        int handle = host.handleOf(world);
        long last = world.tickCount();
        for (int i = n - 1; i >= 0; i--) sink.accept(handle, last - i);
    }

    // ---- 입력: 드래그로 돌리고 휠로 확대 ----

    @Override
    public boolean mousePressed(UiElement element, int x, int y, int button) {
        if (world() == null || button != 0) return false;
        dragging = true;
        lastX = x;
        lastY = y;
        return true;
    }

    @Override
    public void mouseDragged(UiElement element, int x, int y) {
        McvWorld w = world();
        if (!dragging || w == null) return;
        McvCamera c = w.camera();
        c.setYaw(c.yaw() + (x - lastX) * 0.6f);
        c.setPitch(c.pitch() + (y - lastY) * 0.6f);
        lastX = x;
        lastY = y;
    }

    @Override
    public void mouseReleased(UiElement element, int x, int y, int button) { dragging = false; }

    @Override
    public boolean mouseScrolled(UiElement element, int x, int y, double amount) {
        McvWorld w = world();
        if (w == null) return false;
        McvCamera c = w.camera();
        c.setZoom((float) (c.zoom() * Math.pow(1.1, amount)));
        return true;
    }
}
