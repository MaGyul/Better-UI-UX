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
