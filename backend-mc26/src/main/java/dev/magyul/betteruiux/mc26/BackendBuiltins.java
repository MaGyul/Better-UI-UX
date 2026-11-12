package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.doc.UiActions;

/**
 * 모드가 기본으로 제공하는 데모 동작. 같은 일을 다른 모드가 하려면 {@link UiApi} 의 공개 API 만으로 충분하다는 예시이기도 하다:
 * 액션 등록, 바인딩, 요소 애니메이션.
 */
final class BackendBuiltins {
    private static boolean registered;
    private static int clicks;

    private BackendBuiltins() {}

    static synchronized void register() {
        if (registered) return;
        registered = true;

        // XML: <button onClick="hello"/>
        UiActions.register("hello", ctx -> {
            clicks++;
            // 눌린 버튼이 통통 튀게 한다(Java 로 시작하는 애니메이션)
            ctx.source().animate().from("scale", 1.25).to("scale", 1).duration(350).easing("spring").play();
            if (clicks == 1 || clicks % 5 == 0) {
                UiOpener.say(net.minecraft.client.Minecraft.getInstance(), "Hello from XML! (" + clicks + ")");
            }
        });

        // 입력 위젯(checkbox/slider/textfield/select...)의 onChange 데모: 바뀐 값을 채팅에 알려 준다.
        UiActions.register("report", ctx -> {
            var el = ctx.source();
            String value = el.attr("value") != null ? el.attr("value") : el.attr("checked") != null ? "checked" : "unchecked";
            UiOpener.say(net.minecraft.client.Minecraft.getInstance(), "[" + (el.id() == null ? el.tag() : el.id()) + "] " + value);
        });

        // demo 화면이 열릴 때마다(핫리로드 포함) 텍스트의 {clicks} 에 값을 공급한다.
        UiApi.onDocumentOpened((id, doc) -> {
            if (id.equals("demo")) doc.bind("clicks", () -> clicks);
        });
    }
}
