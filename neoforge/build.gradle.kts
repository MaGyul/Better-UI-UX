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
            systemProperty("forge.logging.markers", "REGISTRIES")
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
}

// 배포 jar에 core 클래스를 합친다(M0 단순화; 추후 JarJar로 전환 가능).
tasks.jar {
    from(project(":core").extensions.getByType<SourceSetContainer>()["main"].output)
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
