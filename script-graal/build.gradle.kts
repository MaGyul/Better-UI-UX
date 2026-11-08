plugins {
    `java-library`
}

repositories { mavenCentral() }

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

val graalVersion = "25.0.5"

dependencies {
    api(project(":core"))
    implementation("org.graalvm.polyglot:polyglot:$graalVersion")
    runtimeOnly("org.graalvm.polyglot:js:$graalVersion")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

// ---- 배포용 묶음 ----
// 선택 모듈을 "모드 jar 하나"로 묶는다: GraalJS 의존성 jar 들의 내용 + 우리 클래스 + 모드 메타데이터(Fabric/NeoForge 둘 다).
// 사용: ./gradlew :script-graal:bundleJar  →  build/libs/script-graal-<버전>-bundle.jar 를 mods 폴더에 넣는다(Better UI/UX 와 함께).
val truffleServices = "META-INF/services/com.oracle.truffle.api.provider.TruffleLanguageProvider"
val bundleExtra = layout.buildDirectory.dir("bundle-extra")

val mergeServices = tasks.register("mergeTruffleServices") {
    val jars = configurations.runtimeClasspath
    inputs.files(jars)
    outputs.dir(bundleExtra)
    doLast {
        // 이 서비스 파일만 두 jar(js-language, regex)에 있어서 합쳐야 한다.
        val lines = linkedSetOf<String>()
        jars.get().filter { it.name.endsWith(".jar") }.forEach { jar ->
            zipTree(jar).matching { include(truffleServices) }.files.forEach { f ->
                f.readLines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { lines += it }
            }
        }
        val out = bundleExtra.get().file(truffleServices).asFile
        out.parentFile.mkdirs()
        out.writeText(lines.joinToString("\n", postfix = "\n"))
    }
}

tasks.register<Jar>("bundleJar") {
    archiveClassifier = "bundle"
    dependsOn(mergeServices)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from(bundleExtra)
    from(layout.projectDirectory.dir("bundle-meta")) {
        filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")) { expand("version" to project.version.toString()) }
    }
    from(configurations.runtimeClasspath.map { cp -> cp.filter { it.name.endsWith(".jar") }.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "module-info.class", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", truffleServices)
    }
}
