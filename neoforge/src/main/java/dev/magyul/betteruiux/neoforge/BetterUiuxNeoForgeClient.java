package dev.magyul.betteruiux.neoforge;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.magyul.betteruiux.mc26.ClientSync;
import dev.magyul.betteruiux.mc26.HudManager;
import dev.magyul.betteruiux.mc26.SyncPayload;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import dev.magyul.betteruiux.mc26.UiApi;
import dev.magyul.betteruiux.mc26.UiOpener;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.Commands;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge 클라이언트 진입점. 현재: {@code /uiux open <id>} 로 XML 화면을 연다. */
@Mod(value = BetterUiuxNeoForgeClient.MOD_ID, dist = Dist.CLIENT)
public final class BetterUiuxNeoForgeClient {
    public static final String MOD_ID = "better_uiux";

    public BetterUiuxNeoForgeClient(IEventBus modBus) {
        Path baseDir = FMLPaths.CONFIGDIR.get().resolve("better-uiux");
        UiApi.init(baseDir);
        // <mc-viewer>: 가상 월드를 GUI Picture-in-Picture 로 그리는 렌더러를 등록한다.
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent e) ->
                e.register(dev.magyul.betteruiux.mc26.McViewerRenderState.class, dev.magyul.betteruiux.mc26.McViewerPip::new));
        // 서버 동기화(채널은 공통 진입점이 등록): 접속하면 인사를 보내고, 서버가 보낸 것은 렌더 스레드에서 처리한다.
        ClientSync.init(baseDir);
        ClientSync.setSender(p -> ClientPacketDistributor.sendToServer(p));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null && conn.hasChannel(SyncPayload.TYPE)) ClientSync.onJoin();
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientSync.onDisconnect());
        // XML HUD: 설정(config.toml 의 [hud] ids)에 적은 문서를 인게임 HUD 맨 위에 그린다.
        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerAboveAll(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), HudManager::render));
        // 개발용 자동 열기: 타이틀 화면이 뜬 뒤 한 번만 연다.
        boolean[] opened = {false};
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            if (!opened[0] && Minecraft.getInstance().gui.screen() instanceof TitleScreen) {
                opened[0] = true;
                UiOpener.autoOpenIfRequested(baseDir);
            }
        });
        NeoSmoke.install(baseDir);                                  // -Dbetteruiux.smoke=true 일 때만 동작하는 개발용 스모크
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ClientSync.tick());
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) ->
                e.getDispatcher().register(Commands.literal("uiux")
                        .then(Commands.literal("open")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            UiApi.open(StringArgumentType.getString(ctx, "id"));
                                            return 1;
                                        })))
                        .then(Commands.literal("publish")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> ClientSync.publishUi(StringArgumentType.getString(ctx, "id")) ? 1 : 0)))
                        .then(Commands.literal("editor")                       // 열린 화면 없는 빈 에디터: 안에서 편집할 화면을 고른다
                                .executes(ctx -> {
                                    UiApi.editor();
                                    return 1;
                                }))
                        .then(Commands.literal("edit")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            UiApi.edit(StringArgumentType.getString(ctx, "id"));
                                            return 1;
                                        })))));
    }
}
