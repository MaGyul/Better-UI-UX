plugins {
    `java-library`
    id("net.neoforged.moddev") version "2.0.148"
}

repositories { mavenCentral() }

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

neoForge {
    version = providers.gradleProperty("neo_version").get()

    runs {
        register("client") {
            client()
            // 개발용 자동 접속: -PquickPlay=localhost:25565 (이름은 Dev)
            providers.gradleProperty("quickPlay").orNull?.let { programArguments.addAll("--username", "Dev", "--quickPlayMultiplayer", it) }
            systemProperty("forge.logging.markers", "REGISTRIES")
        }
        register("server") {
            server()
            programArgument("--nogui")
        }
    }

    mods {
        register("better_uiux") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation(project(":core"))
    // 개발 실행에서만 스크립트 런타임(GraalJS)을 함께 띄운다. 배포 jar 에는 넣지 않는다(선택 모듈).
    runtimeOnly(project(":script-graal"))
}

// 배포 jar에 core 클래스를 합친다(M0 단순화; 추후 JarJar로 전환 가능).
tasks.jar {
    from(project(":core").extensions.getByType<SourceSetContainer>()["main"].output)
    // 영상(mp4/H.264) 해독 라이브러리(JCodec, 순수 Java)도 같은 jar 에 합친다.
    from(configurations.runtimeClasspath.map { cp -> cp.filter { it.name.startsWith("jcodec") && it.name.endsWith(".jar") }.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.processResources {
    val props = mapOf(
        "mod_version" to project.version.toString(),
        "neo_version" to providers.gradleProperty("neo_version").get(),
        "minecraft_version_range" to "[26.3]",
    )
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
}

// 26.x 공통 백엔드 소스를 로더 모듈이 함께 컴파일한다(두 로더 모두 같은 공식 이름의 마인크래프트 클래스를 사용).
sourceSets.main {
    java.srcDir("../backend-mc26/src/main/java")
    resources.srcDir("../backend-mc26/src/main/resources")
}
