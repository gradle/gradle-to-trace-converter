plugins {
    id("build.kotlin-application")
    id("com.google.protobuf") version ("0.9.1")
}

dependencies {
    api("com.google.protobuf:protobuf-java:3.21.12")
    implementation("org.apache.commons:commons-text:1.10.0")
    implementation("com.google.code.gson:gson:2.10")
    implementation("com.github.ajalt.clikt:clikt:3.5.1")
}

application {
    mainClass = "org.gradle.tools.trace.app.AppKt"
    applicationName = "gtc"
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.21.12"
    }
}

val installDistTask = tasks.named<Sync>("installDist")

tasks.register<Sync>("install") {
    val installDirName = "gtc.install.dir"
    val installDir = providers.gradleProperty(installDirName)
        .orElse(providers.systemProperty(installDirName))
        .orElse("$rootDir/distribution")

    from(installDistTask.map { it.destinationDir })
    into(installDir)
}
