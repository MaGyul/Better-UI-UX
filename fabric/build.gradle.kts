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
    include("org.jcodec:jcodec:0.2.5")          // 영상(mp4/H.264) 해독 라이브러리(순수 Java)
    // 개발 실행에서만 스크립트 런타임(GraalJS)을 함께 띄운다. 배포 jar 에는 넣지 않는다(선택 모듈).
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

// 개발 실행(runClient)에서만 스크립트 런타임(GraalJS)을 클래스패스에 더한다. 배포 jar 에는 넣지 않는 선택 모듈이고,
// 일반 의존성으로 걸면 Loom 이 GraalJS jar 를 리맵하려다 실패하므로 Loom 이 모르는 별도 설정으로 풀어서 실행 태스크에만 붙인다.
val scriptRuntimeDeps = configurations.dependencyScope("scriptRuntimeDeps")
val scriptRuntime = configurations.resolvable("scriptRuntime") {
    extendsFrom(scriptRuntimeDeps.get())
    attributes { attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME)) }
}
dependencies { add("scriptRuntimeDeps", project(":script-graal")) }
// -PscriptFromMods : 개발용 클래스패스를 붙이지 않고, run/mods 에 넣은 배포용 묶음(bundleJar)으로 스크립트 런타임을 확인한다.
if (!providers.gradleProperty("scriptFromMods").isPresent) tasks.matching { it.name == "runClient" }.configureEach {
    // GraalJS 의 일부 의존성은 .pom 파일이 같이 풀려 나오는데 jar 가 아니라서 로더가 읽다가 실패한다: jar 만 붙인다.
    (this as JavaExec).classpath(scriptRuntime.get().filter { it.name.endsWith(".jar") })
}

// 인게임 클라이언트 GameTest(./gradlew :fabric:runClientGameTest): 실제 창/월드/입력으로 에디터를 검증한다.
// EULA 동의(eula)는 켜지 않는다: 싱글플레이(내장 서버)만 쓰므로 필요 없고, 동의는 사용자가 직접 해야 한다.
fabricApi {
    configureTests {
        createSourceSet.set(true)
        modId.set("better-uiux-test")
        enableGameTests.set(false)
        enableClientGameTests.set(true)
    }
}

// 실제 권한 플러그인 확인용: fabric/build/gametest-mods 에 둔 모드 jar(예: LuckPerms)는 GameTest 실행 폴더의 mods 에만 복사한다
// (개발용 run 폴더와 배포물에는 들어가지 않는다). 폴더가 비어 있으면 아무것도 하지 않는다.
tasks.matching { it.name == "runClientGameTest" }.configureEach {
    doFirst {
        copy {
            from(layout.buildDirectory.dir("gametest-mods"))
            include("*.jar")
            into(layout.buildDirectory.dir("run/clientGameTest/mods"))
        }
    }
}
