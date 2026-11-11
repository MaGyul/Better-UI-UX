package dev.magyul.betteruiux.mc26;

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
 * 블록/유체/블록 엔티티는 모두 비어 있는 것으로 답한다(틴트 소스가 바이옴만 읽는다).
 */
final class McViewerTintGetter implements BlockAndTintGetter {
    private final Function<BlockPos, Holder<Biome>> biomes;
    private final LevelLightEngine lightEngine;

    McViewerTintGetter(Function<BlockPos, Holder<Biome>> biomes, LevelLightEngine lightEngine) {
        this.biomes = biomes;
        this.lightEngine = lightEngine;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        Holder<Biome> biome = biomes.apply(pos);
        return biome == null ? -1 : resolver.getColor(biome.value(), pos.getX(), pos.getZ());   // -1 = 흰색(색조 없음)
    }

    @Override public CardinalLighting cardinalLighting() { return CardinalLighting.DEFAULT; }

    @Override public LevelLightEngine getLightEngine() { return lightEngine; }

    @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }

    @Override public BlockState getBlockState(BlockPos pos) { return Blocks.AIR.defaultBlockState(); }

    @Override public FluidState getFluidState(BlockPos pos) { return Fluids.EMPTY.defaultFluidState(); }

    @Override public int getHeight() { return 4096; }

    @Override public int getMinY() { return 0; }
}
