package dev.magyul.betteruiux.neoforge;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.magyul.betteruiux.mc26.UiApi;
import dev.magyul.betteruiux.mc26.UiOpener;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge 클라이언트 진입점. 현재: {@code /uiux open <id>} 로 XML 화면을 연다. */
@Mod(value = BetterUiuxNeoForgeClient.MOD_ID, dist = Dist.CLIENT)
public final class BetterUiuxNeoForgeClient {
    public static final String MOD_ID = "better_uiux";

    public BetterUiuxNeoForgeClient() {
        Path baseDir = FMLPaths.CONFIGDIR.get().resolve("better-uiux");
        UiApi.init(baseDir);
        // 개발용 자동 열기: 타이틀 화면이 뜬 뒤 한 번만 연다.
        boolean[] opened = {false};
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            if (!opened[0] && Minecraft.getInstance().gui.screen() instanceof TitleScreen) {
                opened[0] = true;
                UiOpener.autoOpenIfRequested(baseDir);
            }
        });
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) ->
                e.getDispatcher().register(Commands.literal("uiux")
                        .then(Commands.literal("open")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            UiApi.open(StringArgumentType.getString(ctx, "id"));
                                            return 1;
                                        })))));
    }
}
