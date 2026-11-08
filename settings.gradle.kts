pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "better-uiux"

// core: 마인크래프트 비의존 순수 Java 엔진. 이후 api/backend-mc26/neoforge 모듈 추가 예정.
include("api")
include("core")
// 선택 모듈: JavaScript(GraalJS) 스크립트 런타임. 없으면 <script> 는 무시되고 경고만 나온다.
include("script-graal")
include("fabric")
include("neoforge")
