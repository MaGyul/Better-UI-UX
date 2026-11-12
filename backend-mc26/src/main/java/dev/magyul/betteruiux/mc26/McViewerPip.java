package dev.magyul.betteruiux.mc26;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
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
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private record Resolved(List<BlockStateModelPart> parts, boolean translucent, boolean solid, int[] tints, BlockState state,
                            List<net.minecraft.client.color.block.BlockTintSource> tintSources) { }

    private static final Logger LOG = LoggerFactory.getLogger("better_uiux");
    private final McViewerEntities entities = new McViewerEntities();
    private final McViewerBlockEntities blockEntities = new McViewerBlockEntities();
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
        mc.gameRenderer.lighting().setupFor(lighting(s.light()));
        BlockStateModelSet models = mc.getModelManager().getBlockStateModelSet();
        if (models != cachedFor) {
            cache.clear();
            cachedFor = models;
        }
        McvWorld w = s.world();
        Quaternionf billboard;                                       // 이름표처럼 화면을 향하는 것들이 쓰는 방향
        PoseStack stack;
        if (s.perspective() != null) {
            stack = new PoseStack();                                 // 기본 클래스의 변환은 정사영용 픽셀 변환이라 쓰지 않고 직접 만든다
            billboard = setupPerspective(s, stack);
        } else {
            stack = pose;
            // 화면 y 는 아래로 자라고 월드 y 는 위로 자란다: 바닐라 GUI 엔티티와 같게 Z 로 뒤집은 뒤 카메라 회전을 건다.
            stack.rotate(Axis.ZP.rotationDegrees(180f));
            stack.rotate(Axis.XP.rotationDegrees(-s.pitch()));       // pitch 양수 = 위에서 내려다봄
            stack.rotate(Axis.YP.rotationDegrees(s.yaw()));
            double[] c = McvView.center(w);                          // 투영/레이캐스트(McvView)와 같은 중심
            stack.translate((float) -c[0], (float) -c[1], (float) -c[2]);
            billboard = new Quaternionf().rotateY((float) Math.toRadians(-s.yaw())).rotateX((float) Math.toRadians(s.pitch())).rotateY((float) Math.PI);
        }

        Map<Long, McvBlock> blocks = w.blockSnapshot();
        McViewerTintGetter tintGetter = tintGetter(w);                              // 바이옴 틴트와 유체(이웃 블록을 읽는다)가 쓴다. 가상 레벨이 준비 중이면 null
        boolean beReady = blockEntities.begin(w, billboard);
        for (Map.Entry<Long, McvBlock> e : blocks.entrySet()) {
            int[] p = McvWorld.unkey(e.getKey());
            if (!w.inView(p[0] + 0.5, p[1] + 0.5, p[2] + 0.5)) continue;
            Resolved r = resolve(models, e.getValue());
            if (r == null) continue;
            if (!r.parts().isEmpty() && !(r.solid() && enclosed(w, blocks, p[0], p[1], p[2], models))) {   // 사방이 막힌 블록은 보이지 않는다
                stack.pushPose();
                stack.translate(p[0], p[1], p[2]);
                collector.submitBlockModel(stack, r.translucent() ? Sheets.translucentBlockItemSheet() : Sheets.cutoutBlockItemSheet(),
                        r.parts(), biomeTints(r, tintGetter, w, p[0], p[1], p[2]), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                stack.popPose();
            }
            if (tintGetter != null && !r.state().getFluidState().isEmpty()) submitFluid(collector, stack, tintGetter, r.state(), p[0], p[1], p[2]);   // 물·용암(·물에 잠긴 블록의 물)
            if (beReady) blockEntities.render(w, r.state(), e.getValue(), p[0], p[1], p[2], stack, collector);   // 상자·표지판 같은 블록 엔티티
        }
        if (beReady) blockEntities.end();
        entities.render(w, billboard, stack, collector);
    }

    // 원근 투영용(정사영은 기본 클래스가 쓰는 것을 그대로 쓴다)
    private final Projection perspectiveProjection = new Projection();
    private final ProjectionMatrixBuffer perspectiveBuffer = new ProjectionMatrixBuffer("better_uiux mc-viewer perspective");

    @Override
    public void close() {
        super.close();
        perspectiveBuffer.close();
    }

    /**
     * 원근 카메라를 건다: 투영 행렬을 시야각/가로세로 비로 다시 설정하고(렌더 패스가 시작되기 전이라 이 값이 쓰인다),
     * 뷰 행렬은 {@code lookAt(눈, 눈 + 앞, 위)} 로 PoseStack 에 넣는다. 눈과 기저는 core 의 {@link McvView} 와 같은 식이다(그래야 project/raycast 가 맞는다).
     * 돌려주는 값은 이름표 같은 것이 화면을 향하도록 하는 방향(뷰 회전의 역)이다.
     */
    private Quaternionf setupPerspective(McViewerRenderState s, PoseStack stack) {
        McViewerRenderState.Perspective p = s.perspective();
        double[][] b = McvView.basis(s.yaw(), s.pitch());           // {오른쪽, 위, 앞}
        float ex = (float) p.eyeX(), ey = (float) p.eyeY(), ez = (float) p.eyeZ();
        Matrix4f view = new Matrix4f().lookAt(ex, ey, ez, ex + (float) b[2][0], ey + (float) b[2][1], ez + (float) b[2][2],
                (float) b[1][0], (float) b[1][1], (float) b[1][2]);
        // 바닐라 조명 방향은 GUI 의 "y 가 아래로 자라는" 뷰 공간 기준이라, 보통(y 위) 뷰 행렬을 그대로 쓰면 위아래 조명이 뒤집힌다.
        // 정사영 경로가 하는 것처럼 뷰에 Z 180° 회전을 넣고, 투영 행렬에 같은 회전을 곱해 화면은 그대로 두고 조명만 바로잡는다.
        stack.rotate(Axis.ZP.rotationDegrees(180f));
        stack.mulPose(view);
        perspectiveProjection.setupPerspective(0.05f, 4000f, p.fov(), Math.max(1, s.x1() - s.x0()), Math.max(1, s.y1() - s.y0()));
        Matrix4f projection = perspectiveProjection.getMatrix(new Matrix4f()).rotateZ((float) Math.PI);
        RenderSystem.setProjectionMatrix(perspectiveBuffer.getBuffer(projection), ProjectionType.PERSPECTIVE);
        return view.getUnnormalizedRotation(new Quaternionf()).conjugate();
    }

    /**
     * 조명 방식. 한 번의 렌더에는 조명 하나만 쓸 수 있어서 블록과 엔티티가 같은 조명을 나눠 쓴다.
     * "3d"(기본): 블록의 면마다 밝기 차이가 뚜렷하다. "flat": 전체가 더 밝고 평평하다(엔티티가 많은 장면에 적합, 블록 입체감은 줄어든다).
     * "entity": 바닐라가 인벤토리의 엔티티를 그릴 때 쓰는 방식.
     */
    static Lighting.Entry lighting(String name) {
        if (name == null) return Lighting.Entry.ITEMS_3D;
        return switch (name.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "flat" -> Lighting.Entry.ITEMS_FLAT;
            case "entity" -> Lighting.Entry.ENTITY_IN_UI;
            default -> Lighting.Entry.ITEMS_3D;
        };
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
            // 모델이 없는 블록(물·용암 같은 유체 블록은 렌더 모양이 INVISIBLE)은 "없는 모델(보라색 격자)"을 그리지 않는다. 유체는 따로 그린다.
            if (state.getRenderShape() != net.minecraft.world.level.block.RenderShape.INVISIBLE) model.collectParts(RandomSource.create(42L), parts);
            r = new Resolved(parts, model.hasMaterialFlag(net.minecraft.client.resources.model.geometry.BakedQuad.FLAG_TRANSLUCENT),
                    state.isSolidRender(), tints(state), state,
                    Minecraft.getInstance().getBlockColors().getTintSources(state));
        }
        cache.put(key, r);
        return r;
    }

    // ---- 유체(물·용암) ----

    private FluidRenderer fluidRenderer;
    private FluidStateModelSet fluidModelsFor;
    private final Map<McvBlock, BlockState> stateCache = new HashMap<>();

    /** 뷰어 월드의 블록 → 블록 상태(문자열 파싱이 비싸서 캐시한다). 레지스트리가 바뀌어 모델 목록이 새로 만들어지면 같이 버린다. */
    private BlockState cachedState(McvBlock b) {
        BlockState s = stateCache.get(b);
        if (s == null && !stateCache.containsKey(b)) {
            s = toState(b);
            stateCache.put(b, s);
        }
        return s;
    }

    /**
     * 유체 블록 하나를 그린다. 바닐라 {@link FluidRenderer}(청크 메시를 만들 때 쓰는 것)가 이웃 유체/블록을 보고 면을 자르고 윗면 높이와
     * 흐름 방향을 정해 사각형을 내보내면, 그것을 {@code submitCustomGeometry} 로 받아 그린다. 물은 반투명, 용암은 불투명 패스다.
     * 렌더러는 위치를 청크 구역 안 좌표({@code pos & 15})로 쓰므로 구역 원점으로 옮겨 놓고 그린다. 조명은 최대 밝기로 고정한다.
     */
    private void submitFluid(SubmitNodeCollector collector, PoseStack stack, McViewerTintGetter getter, BlockState state, int x, int y, int z) {
        Minecraft mc = Minecraft.getInstance();
        FluidStateModelSet models = mc.getModelManager().getFluidStateModelSet();
        if (models != fluidModelsFor) {
            fluidRenderer = new FluidRenderer(models);
            fluidModelsFor = models;
        }
        net.minecraft.world.level.material.FluidState fluid = state.getFluidState();
        net.minecraft.client.renderer.chunk.ChunkSectionLayer layer = models.get(fluid).layer();
        net.minecraft.client.renderer.rendertype.RenderType type = switch (layer) {
            case TRANSLUCENT -> net.minecraft.client.renderer.rendertype.RenderTypes.translucentMovingBlock();
            case CUTOUT -> net.minecraft.client.renderer.rendertype.RenderTypes.cutoutMovingBlock();
            default -> net.minecraft.client.renderer.rendertype.RenderTypes.solidMovingBlock();
        };
        BlockPos pos = new BlockPos(x, y, z);
        FluidRenderer renderer = fluidRenderer;
        stack.pushPose();
        stack.translate(x - (x & 15), y - (y & 15), z - (z & 15));
        collector.submitCustomGeometry(stack, type, (pose, vc) -> {
            McViewerFullBrightConsumer consumer = new McViewerFullBrightConsumer(vc, pose);
            renderer.tesselate(getter, pos, l -> consumer, state, fluid);
        });
        stack.popPose();
    }

    // ---- 바이옴 색조 ----

    private final Map<String, Holder<Biome>> biomeHolders = new HashMap<>();
    private Object biomeRegistryFor;                                  // biomeHolders 를 만든 레지스트리(레벨이 바뀌면 다시 만든다)
    private final java.util.Set<String> warnedBiomes = new java.util.HashSet<>();

    /**
     * 이 월드의 바이옴(월드 기본 + 영역)을 위치마다 돌려주는 틴트 getter. 바이옴 레지스트리가 아직 준비 중이면(가상 레벨을 불러오는 동안) null 이라
     * 그 프레임은 기본 색으로 그린다.
     */
    private McViewerTintGetter tintGetter(McvWorld w) {
        Level level = McViewerFakeWorld.current();
        if (level == null) return null;
        var lookup = level.registryAccess().lookup(Registries.BIOME);
        if (lookup.isEmpty()) return null;
        if (biomeRegistryFor != lookup.get()) {
            biomeHolders.clear();
            biomeRegistryFor = lookup.get();
        }
        var registry = lookup.get();
        Holder<Biome> plains = registry.get(net.minecraft.world.level.biome.Biomes.PLAINS).orElse(null);
        return new McViewerTintGetter(pos -> {
            String id = w.biomeAt(pos.getX(), pos.getY(), pos.getZ());
            if (id == null) return null;
            Holder<Biome> h = biomeHolders.get(id);
            if (h == null && !biomeHolders.containsKey(id)) {
                try {
                    h = registry.get(ResourceKey.create(Registries.BIOME, Identifier.parse(id))).orElse(null);
                } catch (RuntimeException e) {
                    h = null;
                }
                if (h == null && warnedBiomes.add(id)) LOG.warn("[better-uiux] <mc-viewer> 알 수 없는 바이옴: {}", id);
                biomeHolders.put(id, h);
            }
            return h;
        }, plains, level instanceof McViewerFakeLevel fl ? fl.getLightEngine() : level.getLightEngine(), w, this::cachedState);
    }

    /**
     * 블록의 색조. 바이옴이 정해진 위치의 틴트 블록(잔디, 잎 ...)은 그 바이옴의 색을 쓰고, 그 밖에는 기본 색을 쓴다.
     * 틴트 소스는 바닐라 것({@code BlockTintSource.colorInWorld})을 그대로 쓰므로 잔디 색 변형(늪, 어두운 숲)과 모드의 틴트도 따라간다.
     */
    private static int[] biomeTints(Resolved r, McViewerTintGetter getter, McvWorld w, int x, int y, int z) {
        if (getter == null || r.tintSources().isEmpty() || w.biomeAt(x, y, z) == null) return r.tints();
        BlockPos pos = new BlockPos(x, y, z);
        int[] out = new int[r.tintSources().size()];
        for (int i = 0; i < out.length; i++) out[i] = 0xFF000000 | r.tintSources().get(i).colorInWorld(r.state(), getter, pos);
        return out;
    }

    /** 잔디/잎/물 같은 블록의 색조(틴트). 바이옴이 없으면 이 기본 색을 쓴다. */
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
