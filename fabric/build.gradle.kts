plugins {
    id("net.fabricmc.fabric-loom") version "1.19-SNAPSHOT" // gradle.properties의 loom_version과 맞출 것
}

repositories { mavenCentral() }

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

dependencies {
    val mc = providers.gradleProperty("minecraft_version").get()
    add("minecraft", "com.mojang:minecraft:$mc")
    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")

    implementation(project(":core"))
    include(project(":core"))
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
}

// 26.x 공통 백엔드 소스를 로더 모듈이 함께 컴파일한다(두 로더 모두 같은 공식 이름의 마인크래프트 클래스를 사용).
sourceSets.main {
    java.srcDir("../backend-mc26/src/main/java")
    resources.srcDir("../backend-mc26/src/main/resources")
}
