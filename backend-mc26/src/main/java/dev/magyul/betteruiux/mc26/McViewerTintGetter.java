package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.mcv.McvBlock;
import dev.magyul.betteruiux.core.mcv.McvWorld;
import java.util.function.Function;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * 잔디·잎·물 같은 블록의 <b>바이옴 색조</b>를 구하는 데 쓰는 최소한의 {@link BlockAndTintGetter}.
 * 바닐라 틴트 소스({@code BlockTintSource.colorInWorld})는 위치의 바이옴으로 색을 정하는데, 가상 월드에는 청크도 바이옴 맵도 없으므로
 * 스크립트가 정한 바이옴(월드 기본 + 영역)을 위치마다 돌려주는 함수로 대신한다. 이웃 바이옴과 섞는 블렌딩은 하지 않는다(경계가 또렷하다).
 * 블록과 유체 상태는 뷰어 월드의 것을 답한다(유체 렌더러가 이웃을 읽는다). 블록 엔티티는 없다.
 */
final class McViewerTintGetter implements BlockAndTintGetter {
    private final Function<BlockPos, Holder<Biome>> biomes;
    private final Holder<Biome> fallbackBiome;
    private final LevelLightEngine lightEngine;
    private final McvWorld world;
    private final Function<McvBlock, BlockState> states;

    /**
     * @param biomes        위치의 바이옴(스크립트가 정한 것). 정하지 않은 곳은 null
     * @param fallbackBiome 바이옴을 정하지 않은 곳의 색조에 쓰는 바이옴(평원: 물은 기본 파랑)
     * @param world         블록/유체 상태를 답할 뷰어 월드(유체 렌더러가 이웃 블록을 보고 면을 자르고 높이를 정한다). null 이면 모두 공기
     * @param states        뷰어 월드의 블록 → 실제 블록 상태 변환(캐시는 부르는 쪽이 가진다)
     */
    McViewerTintGetter(Function<BlockPos, Holder<Biome>> biomes, Holder<Biome> fallbackBiome, LevelLightEngine lightEngine,
                       McvWorld world, Function<McvBlock, BlockState> states) {
        this.biomes = biomes;
        this.fallbackBiome = fallbackBiome;
        this.lightEngine = lightEngine;
        this.world = world;
        this.states = states;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        Holder<Biome> biome = biomes.apply(pos);
        if (biome == null) biome = fallbackBiome;                    // 바이옴을 안 정한 곳(예: 물)은 평원 색
        return biome == null ? -1 : resolver.getColor(biome.value(), pos.getX(), pos.getZ());   // -1 = 흰색(색조 없음)
    }

    @Override public CardinalLighting cardinalLighting() { return CardinalLighting.DEFAULT; }

    @Override public LevelLightEngine getLightEngine() { return lightEngine; }

    @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (world == null || pos.getX() < 0 || pos.getY() < 0 || pos.getZ() < 0
                || pos.getX() >= world.sizeX() || pos.getY() >= world.sizeY() || pos.getZ() >= world.sizeZ()) return Blocks.AIR.defaultBlockState();
        McvBlock b = world.getBlock(pos.getX(), pos.getY(), pos.getZ());
        if (b.isAir()) return Blocks.AIR.defaultBlockState();
        BlockState s = states.apply(b);
        return s == null ? Blocks.AIR.defaultBlockState() : s;
    }

    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }

    @Override public int getHeight() { return 4096; }

    @Override public int getMinY() { return 0; }
}
