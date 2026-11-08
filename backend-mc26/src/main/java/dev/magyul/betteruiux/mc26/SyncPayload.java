package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.sync.SyncLimits;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 채널 {@code better_uiux:sync} 의 패킷. 내용은 {@code core.sync.SyncCodec} 이 만든 바이트 하나다(양방향 공용). */
public record SyncPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<SyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("better_uiux", "sync"));
    public static final StreamCodec<ByteBuf, SyncPayload> CODEC =
            ByteBufCodecs.byteArray(SyncLimits.MAX_MESSAGE).map(SyncPayload::new, SyncPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
