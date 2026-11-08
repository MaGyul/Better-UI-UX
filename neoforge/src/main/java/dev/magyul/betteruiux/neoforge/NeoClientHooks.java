package dev.magyul.betteruiux.neoforge;

import dev.magyul.betteruiux.mc26.ClientSync;
import dev.magyul.betteruiux.mc26.SyncPayload;

/** 클라이언트에서만 불린다(전용 서버에서는 이 클래스가 로드되지 않는다). */
final class NeoClientHooks {
    private NeoClientHooks() {}

    static void onPayload(SyncPayload payload) { ClientSync.onPayload(payload); }
}
