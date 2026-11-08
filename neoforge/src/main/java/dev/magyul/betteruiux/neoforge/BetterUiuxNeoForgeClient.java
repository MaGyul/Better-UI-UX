package dev.magyul.betteruiux.neoforge;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.magyul.betteruiux.mc26.UiOpener;
import java.nio.file.Path;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge 클라이언트 진입점. 현재: {@code /uiux open <id>} 로 XML 화면을 연다. */
@Mod(value = BetterUiuxNeoForgeClient.MOD_ID, dist = Dist.CLIENT)
public final class BetterUiuxNeoForgeClient {
    public static final String MOD_ID = "better_uiux";

    public BetterUiuxNeoForgeClient() {
        Path uiDir = FMLPaths.CONFIGDIR.get().resolve("better-uiux").resolve("ui");
        net.neoforged.fml.ModLoadingContext.get().getActiveContainer().getEventBus()
                .addListener((FMLClientSetupEvent e) -> UiOpener.autoOpenIfRequested(uiDir));
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) ->
                e.getDispatcher().register(Commands.literal("uiux")
                        .then(Commands.literal("open")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            UiOpener.open(StringArgumentType.getString(ctx, "id"), uiDir);
                                            return 1;
                                        })))));
    }
}
