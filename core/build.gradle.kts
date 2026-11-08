plugins {
    `java-library`
}

repositories { mavenCentral() }

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

dependencies {
    api(project(":api"))
    // 영상(H.264/mp4) 해독: 순수 Java(JCodec), 네이티브 없음.
    implementation("org.jcodec:jcodec:0.2.5")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // 골든(스냅샷) 파일을 다시 만들 때: ./gradlew :core:test -Pgolden.update=true
    systemProperty("golden.update", (project.findProperty("golden.update") ?: "false").toString())
}
