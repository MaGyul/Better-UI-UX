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
tasks.matching { it.name == "runClient" }.configureEach {
    // GraalJS 의 일부 의존성은 .pom 파일이 같이 풀려 나오는데 jar 가 아니라서 로더가 읽다가 실패한다: jar 만 붙인다.
    (this as JavaExec).classpath(scriptRuntime.get().filter { it.name.endsWith(".jar") })
}
