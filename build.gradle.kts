plugins {
    id("maven-publish")
    id("java")
    id("java-library")
    id("xyz.jpenilla.run-paper") version "2.3.0"
    id("com.modrinth.minotaur") version "2.+"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.battleplugins.tracker"
version = "4.0.0-SNAPSHOT"

val supportedVersions = listOf("1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6", "1.21")

java.toolchain.languageVersion.set(JavaLanguageVersion.of(17))

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.battleplugins.org/releases/")
    maven("https://repo.battleplugins.org/snapshots/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    implementation(libs.bstats.bukkit)
    implementation(libs.commons.dbcp)
    implementation(libs.commons.pool)

    compileOnlyApi(libs.paper.api)
    compileOnlyApi(libs.battlearena)
    compileOnlyApi(libs.placeholderapi)
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks {
    runServer {
        minecraftVersion("1.20.6")

        // Set Java 21 (1.20.6 requires Java 21)
        javaLauncher = project.javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    jar {
        archiveClassifier.set("unshaded")
    }

    shadowJar {
        from("src/main/java/resources") {
            include("*")
        }

        relocate("org.bstats", "org.battleplugins.tracker.util.shaded.bstats")
        relocate("org.apache", "org.battleplugins.tracker.util.shaded.apache")

        archiveFileName.set("BattleTracker.jar")
        archiveClassifier.set("")
    }

    javadoc {
        (options as CoreJavadocOptions).addBooleanOption("Xdoclint:none", true)
    }

    processResources {
        filesMatching("plugin.yml") {
            expand("version" to rootProject.version)
        }
    }
}

publishing {
    val isSnapshot = "SNAPSHOT" in version.toString()

    repositories {
        maven {
            name = "battleplugins"
            url = uri("https://repo.battleplugins.org/${if (isSnapshot) "snapshots" else "releases"}")
            credentials(PasswordCredentials::class)
            authentication {
                create<BasicAuthentication>("basic")
            }
        }

        publications {
            create<MavenPublication>("mavenJava") {
                artifactId = "arena"

                from(components["java"])
                pom {
                    packaging = "jar"
                    url.set("https://github.com/BattlePlugins/BattleTracker")

                    scm {
                        connection.set("scm:git:git://github.com/BattlePlugins/BattleTracker.git")
                        developerConnection.set("scm:git:ssh://github.com/BattlePlugins/BattleTracker.git")
                        url.set("https://github.com/BattlePlugins/BattleTracker");
                    }

                    licenses {
                        license {
                            name.set("GNU General Public License v3.0")
                            url.set("https://www.gnu.org/licenses/gpl-3.0.html")
                        }
                    }

                    developers {
                        developer {
                            name.set("BattlePlugins Team")
                            organization.set("BattlePlugins")
                            organizationUrl.set("https://github.com/BattlePlugins")
                        }
                    }
                }
            }
        }
    }
}

modrinth {
    val snapshot = "SNAPSHOT" in rootProject.version.toString()

    token.set(System.getenv("MODRINTH_TOKEN") ?: "")
    projectId.set("battletracker")
    versionNumber.set(rootProject.version as String + if (snapshot) "-" + System.getenv("BUILD_NUMBER") else "")
    versionType.set(if (snapshot) "beta" else "release")
    changelog.set(System.getenv("CHANGELOG") ?: "")
    uploadFile.set(tasks.shadowJar)
    gameVersions.set(supportedVersions)

    dependencies {
        optional.project("battlearena")
    }
}
