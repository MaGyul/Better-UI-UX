package dev.magyul.betteruiux.mc26;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * 유체 렌더러가 내보내는 정점을 받아 <b>포즈를 적용</b>하고 <b>조명을 최대 밝기로 고정</b>해 실제 소비자로 넘긴다.
 *
 * <ul>
 *   <li>포즈: {@code submitCustomGeometry} 의 콜백은 변환되지 않은 정점 버퍼와 현재 {@link PoseStack.Pose} 를 함께 받는다(변환은 호출자 몫).
 *       위치와 법선에 포즈를 안 걸면 정점이 뷰/투영 행렬 밖 좌표로 들어가 화면에 아무것도 안 나온다.</li>
 *   <li>조명: 유체 렌더러는 정점마다 레벨의 조명 엔진에서 밝기를 읽는데, 가상 월드에는 조명 엔진이 없어(항상 0) 그대로 두면 물이 새까맣게 나온다.
 *       뷰어의 블록/엔티티는 모두 최대 밝기로 그리므로 유체도 같게 맞춘다.</li>
 * </ul>
 */
final class McViewerFullBrightConsumer implements VertexConsumer {
    private static final int FULL = 240;                             // 블록 조명 15, 하늘 조명 15 (uv2 한 칸의 최대값)
    private final VertexConsumer delegate;
    private final PoseStack.Pose pose;

    McViewerFullBrightConsumer(VertexConsumer delegate, PoseStack.Pose pose) {
        this.delegate = delegate;
        this.pose = pose;
    }

    @Override public VertexConsumer addVertex(float x, float y, float z) { delegate.addVertex(pose, x, y, z); return this; }

    @Override public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r, g, b, a); return this; }

    @Override public VertexConsumer setColor(int argb) { delegate.setColor(argb); return this; }

    @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }

    @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }

    @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(FULL, FULL); return this; }

    @Override public VertexConsumer setUv3(float u, float v) { delegate.setUv3(u, v); return this; }

    @Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(pose, x, y, z); return this; }

    @Override public VertexConsumer setLineWidth(float width) { delegate.setLineWidth(width); return this; }
}
