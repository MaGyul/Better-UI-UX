package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.mcv.McvWorld;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

/**
 * {@code <mc-viewer>} 한 프레임의 그리기 정보(GUI 의 Picture-in-Picture 상태). 좌표는 GUI 좌표(스케일 적용 전)이고,
 * {@code scale} 은 월드 한 칸이 GUI 좌표로 몇 픽셀인지, {@code light} 는 조명 방식("3d" 기본, "flat", "entity")이고, {@code perspective} 가 있으면 원근 투영이다. {@code scissorArea}, {@code bounds} 는 null 일 수 있다.
 */
public record McViewerRenderState(McvWorld world, float yaw, float pitch, String light, Perspective perspective, int x0, int y0, int x1, int y1, float scale,
                                  ScreenRectangle scissorArea, ScreenRectangle bounds)
        implements PictureInPictureRenderState {
    /** 원근 카메라 정보: 세로 시야각(도)과 눈 위치(월드 좌표). 정사영이면 null. 방향은 yaw/pitch 에서 나온다. */
    public record Perspective(float fov, double eyeX, double eyeY, double eyeZ) { }

    public McViewerRenderState(McvWorld world, float yaw, float pitch, String light, Perspective perspective, int x0, int y0, int x1, int y1, float scale,
                               ScreenRectangle scissorArea) {
        this(world, yaw, pitch, light, perspective, x0, y0, x1, y1, scale, scissorArea, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }
}
