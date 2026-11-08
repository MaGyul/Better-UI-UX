package dev.magyul.betteruiux.neoforge;

import dev.magyul.betteruiux.core.xml.UiNode;
import dev.magyul.betteruiux.core.xml.UiParser;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** NeoForge 클라이언트 진입점. M0: core 엔진이 게임 안에서 로드/동작하는지만 확인한다. */
@Mod(value = BetterUiuxNeoForgeClient.MOD_ID, dist = Dist.CLIENT)
public final class BetterUiuxNeoForgeClient {
    public static final String MOD_ID = "better_uiux";
    private static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public BetterUiuxNeoForgeClient() {
        try {
            UiNode root = UiParser.parse("<screen id=\"smoke\"><label text=\"ok\"/></screen>");
            LOG.info("[better-uiux] core loaded, parsed <{}> with {} child(ren)", root.tag(), root.children().size());
        } catch (Exception e) {
            LOG.error("[better-uiux] core smoke test failed", e);
        }
    }
}
