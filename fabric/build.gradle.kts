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
