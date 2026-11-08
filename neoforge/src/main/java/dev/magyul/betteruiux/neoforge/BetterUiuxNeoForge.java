package dev.magyul.betteruiux.neoforge;

import dev.magyul.betteruiux.mc26.ServerCommands;
import dev.magyul.betteruiux.mc26.ServerSync;
import dev.magyul.betteruiux.mc26.SyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * NeoForge 공통 진입점(전용 서버/클라이언트 모두). 서버 동기화 채널을 등록한다.
 * 채널은 {@code optional()} 이라 이 모드가 없는 클라이언트도 이 서버에 접속할 수 있다(그 클라이언트에는 서버 기능만 꺼진다).
 */
@Mod(BetterUiuxNeoForgeClient.MOD_ID)
public final class BetterUiuxNeoForge {
    public BetterUiuxNeoForge(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent e) -> {
            var r = e.registrar("1").optional();
            // 같은 채널 id 를 양방향으로 한 번만 등록한다(방향은 flow 로 구분).
            r.playBidirectional(SyncPayload.TYPE, SyncPayload.CODEC,
                    // 이 버전의 인자 순서: (서버가 받을 때, 클라이언트가 받을 때). 바뀌면 서버에서 클라이언트 클래스가 불려 바로 드러난다.
                    (payload, ctx) -> ctx.enqueueWork(() -> ServerSync.onPayload((ServerPlayer) ctx.player(), payload)),
                    (payload, ctx) -> ctx.enqueueWork(() -> NeoClientHooks.onPayload(payload)));
        });
        ServerCommands.registerDemoActions();
        ServerSync.setSender((player, payload) -> {
            if (player.connection.hasChannel(SyncPayload.TYPE)) PacketDistributor.sendToPlayer(player, payload);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) ServerSync.onJoin(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) ServerSync.onDisconnect(p);
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> ServerSync.tick());
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> ServerCommands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> ServerSync.loadBundle(FMLPaths.CONFIGDIR.get().resolve("better-uiux")));
    }
}
