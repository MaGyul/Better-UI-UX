package dev.magyul.betteruiux.neoforge;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.mc26.UiOpener;
import dev.magyul.betteruiux.mc26.UiScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 개발용 스모크 테스트(NeoForge 에는 Fabric 의 클라이언트 GameTest 가 없다): {@code -Dbetteruiux.smoke=true} 로 띄우면
 * 타이틀 화면에서 CSS3 데모 화면을 열어 실제 렌더링 경로(McGfx)로 몇 프레임 그린 뒤, 그리기 명령을 확인하고 결과를 한 줄로 찍고 종료한다.
 * 실행: {@code ./gradlew :neoforge:runSmokeClient} — 로그의 {@code [SMOKE] PASS/FAIL} 를 본다. 일반 실행에는 아무 영향이 없다.
 */
final class NeoSmoke {
    private static int tick, openedAt = -1;
    private static boolean done;

    static void install(Path baseDir) {
        if (!Boolean.getBoolean("betteruiux.smoke")) return;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            if (done) return;
            Minecraft mc = Minecraft.getInstance();
            tick++;
            try {
                if (openedAt < 0 && mc.gui.screen() instanceof TitleScreen && tick > 20) {
                    write(baseDir);
                    UiOpener.open("smoke_css", baseDir, false);
                    openedAt = tick;
                } else if (openedAt >= 0 && tick - openedAt > 40) {
                    finish(mc);
                }
            } catch (Throwable t) {
                fail(mc, t.toString());
            }
        });
    }

    private static void write(Path baseDir) throws IOException {
        Files.createDirectories(baseDir.resolve("ui"));
        Files.writeString(baseDir.resolve("ui/smoke_css.xml"),
                "<screen><style>#d { width: 80; height: 30; background: linear-gradient(#2255AA, #112244); border-radius: 6;"
                        + " border: 1 solid #FFFFFF; box-shadow: 0 2 4 #000000AA; } #d::before { content: \"* \"; }"
                        + " .row { flex-wrap: wrap; width: 60; } .row > panel { width: 25; height: 8; background: #888888; }</style>"
                        + "<column anchor=\"top-left\" x=\"10\" y=\"10\" gap=\"4\"><label id=\"d\" text=\"smoke\"/>"
                        + "<row class=\"row\"><panel id=\"a\"/><panel id=\"b\"/><panel id=\"c\"/></row></column></screen>");
    }

    private static void finish(Minecraft mc) {
        if (!(mc.gui.screen() instanceof UiScreen s)) { fail(mc, "UiScreen 이 열리지 않았다: " + mc.gui.screen()); return; }
        DrawList l = new DrawList();
        s.document().paint(l, 0, 0);
        long rects = l.commands().stream().filter(c -> c instanceof DrawCommand.FillRect).count();
        boolean text = l.commands().stream().anyMatch(c -> c instanceof DrawCommand.DrawText t && t.text().startsWith("* "));
        var a = s.document().findById("a");
        var c = s.document().findById("c");
        boolean wrapped = a != null && c != null && c.y > a.y;
        if (rects < 10) fail(mc, "둥근 모서리/그림자가 합성되지 않았다: rect=" + rects);
        else if (!text) fail(mc, "::before 글자가 없다");
        else if (!wrapped) fail(mc, "flex-wrap 이 동작하지 않았다");
        else if (!s.document().warnings().isEmpty()) fail(mc, "경고: " + s.document().warnings());
        else {
            System.out.println("[SMOKE] PASS rects=" + rects);
            done = true;
            mc.stop();
        }
    }

    private static void fail(Minecraft mc, String why) {
        System.out.println("[SMOKE] FAIL " + why);
        done = true;
        mc.stop();
    }
}
