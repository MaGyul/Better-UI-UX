pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "better-uiux"

// core: 마인크래프트 비의존 순수 Java 엔진. 이후 api/backend-mc26/neoforge 모듈 추가 예정.
include("core")
include("fabric")
include("neoforge")
