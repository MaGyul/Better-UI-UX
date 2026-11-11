package dev.magyul.betteruiux.mc26;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.magyul.betteruiux.core.mcv.McvBlock;
import dev.magyul.betteruiux.core.mcv.McvView;
import dev.magyul.betteruiux.core.mcv.McvWorld;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * {@code <mc-viewer>} 를 그리는 Picture-in-Picture 렌더러. 바닐라가 엔티티/책/배너를 GUI 에 그리는 것과 같은 경로라서
 * 오프스크린 텍스처, 투영, 깊이, 라이팅을 마인크래프트가 맡고, 우리는 {@link #renderToTexture} 에서 월드를 제출(submit)하기만 한다.
 *
 * <p>카메라는 PoseStack 변환으로 표현된다(바닐라 PiP 의 투영은 정사영). 그래서 지금은 등각(ortho) 시점만 지원한다.
 */
public final class McViewerPip extends PictureInPictureRenderer<McViewerRenderState> {
    /** 블록 하나(상태 문자열)를 그릴 때 필요한 것들. 리소스가 다시 불러와지면 버린다. */
    private record Resolved(List<BlockStateModelPart> parts, boolean translucent, boolean solid, int[] tints) { }

    private final McViewerEntities entities = new McViewerEntities();
    private final Map<String, Resolved> cache = new HashMap<>();
    private BlockStateModelSet cachedFor;

    @Override
    public Class<McViewerRenderState> getRenderStateClass() { return McViewerRenderState.class; }

    @Override
    protected String getTextureLabel() { return "better_uiux mc-viewer"; }

    @Override
    protected float getTranslateY(int height, int guiScale) { return height / 2f; }

    @Override
    protected void renderToTexture(McViewerRenderState s, PoseStack pose, SubmitNodeCollector collector) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
        BlockStateModelSet models = mc.getModelManager().getBlockStateModelSet();
        if (models != cachedFor) {
            cache.clear();
            cachedFor = models;
        }
        McvWorld w = s.world();
        // 화면 y 는 아래로 자라고 월드 y 는 위로 자란다: 바닐라 GUI 엔티티와 같게 Z 로 뒤집은 뒤 카메라 회전을 건다.
        pose.rotate(Axis.ZP.rotationDegrees(180f));
        pose.rotate(Axis.XP.rotationDegrees(-s.pitch()));          // pitch 양수 = 위에서 내려다봄
        pose.rotate(Axis.YP.rotationDegrees(s.yaw()));
        double[] c = McvView.center(w);                              // 투영/레이캐스트(McvView)와 같은 중심
        double cx = c[0], cy = c[1], cz = c[2];
        pose.translate((float) -cx, (float) -cy, (float) -cz);

        Map<Long, McvBlock> blocks = w.blockSnapshot();
        for (Map.Entry<Long, McvBlock> e : blocks.entrySet()) {
            int[] p = McvWorld.unkey(e.getKey());
            if (!w.inView(p[0] + 0.5, p[1] + 0.5, p[2] + 0.5)) continue;
            Resolved r = resolve(models, e.getValue());
            if (r == null || r.parts().isEmpty()) continue;
            if (r.solid() && enclosed(w, blocks, p[0], p[1], p[2], models)) continue;    // 사방이 막힌 블록은 보이지 않는다
            pose.pushPose();
            pose.translate(p[0], p[1], p[2]);
            collector.submitBlockModel(pose, r.translucent() ? Sheets.translucentBlockItemSheet() : Sheets.cutoutBlockItemSheet(),
                    r.parts(), r.tints(), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        entities.render(w, s.yaw(), s.pitch(), pose, collector);
    }

    private boolean enclosed(McvWorld w, Map<Long, McvBlock> blocks, int x, int y, int z, BlockStateModelSet models) {
        int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] o : d) {
            int nx = x + o[0], ny = y + o[1], nz = z + o[2];
            McvBlock n = nx < 0 || ny < 0 || nz < 0 || nx >= w.sizeX() || ny >= w.sizeY() || nz >= w.sizeZ() ? null : blocks.get(McvWorld.key(nx, ny, nz));
            if (n == null) return false;
            Resolved nr = resolve(models, n);
            if (nr == null || !nr.solid()) return false;
        }
        return true;
    }

    private Resolved resolve(BlockStateModelSet models, McvBlock b) {
        String key = b.stateString();
        Resolved cached = cache.get(key);
        if (cached != null || cache.containsKey(key)) return cached;
        Resolved r = null;
        BlockState state = toState(b);
        if (state != null && !state.isAir()) {
            BlockStateModel model = models.get(state);
            List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(RandomSource.create(42L), parts);
            r = new Resolved(parts, model.hasMaterialFlag(net.minecraft.client.resources.model.geometry.BakedQuad.FLAG_TRANSLUCENT),
                    state.isSolidRender(), tints(state));
        }
        cache.put(key, r);
        return r;
    }

    /** 잔디/잎/물 같은 블록의 색조(틴트). 월드의 바이옴이 없으므로 기본 색을 쓴다. */
    private static int[] tints(BlockState state) {
        List<net.minecraft.client.color.block.BlockTintSource> sources = Minecraft.getInstance().getBlockColors().getTintSources(state);
        int[] out = new int[sources.size()];
        for (int i = 0; i < out.length; i++) out[i] = 0xFF000000 | sources.get(i).color(state);
        return out;
    }

    /** "minecraft:oak_stairs[facing=east]" → 실제 블록 상태. 모르는 블록이면 null(그리지 않는다). */
    static BlockState toState(McvBlock b) {
        Identifier id;
        try {
            id = Identifier.parse(b.id());
        } catch (RuntimeException e) {
            return null;
        }
        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
        if (block.isEmpty()) return null;
        BlockState state = block.get().defaultBlockState();
        for (Map.Entry<String, String> e : b.state().entrySet()) {
            Property<?> prop = block.get().getStateDefinition().getProperty(e.getKey());
            if (prop != null) state = withValue(state, prop, e.getValue());
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> prop, String value) {
        return prop.getValue(value).map(v -> state.setValue(prop, v)).orElse(state);
    }
}
