import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun getVersionName(tagName: String) = if(tagName.startsWith("v")) tagName.substring(1) else tagName
val gitTagName: String? get() = Regex("(?<=refs/tags/).*").find(System.getenv("GITHUB_REF") ?: "")?.value
val gitCommitSha: String? get() = System.getenv("GITHUB_SHA") ?: null
val debugVersion: String get() = System.getenv("DBG_VERSION") ?: "0.0.0"

group = "com.github.balloonupdate"
version = gitTagName?.run { getVersionName(this) } ?: debugVersion

plugins {
    kotlin("jvm") version "1.7.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.yaml:snakeyaml:1.33")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.nanohttpd:nanohttpd-webserver:2.3.1")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<ShadowJar> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    manifest {
        attributes("Version" to archiveVersion.get())
        attributes("Git-Commit" to (gitCommitSha ?: ""))
        attributes("Main-Class" to "MiniHttpServer")
    }

    // 打包源代码
    sourceSets.main.get().allSource.sourceDirectories.map {
        from(it) {into("sources/"+it.name) }
    }

    archiveClassifier.set("")
}