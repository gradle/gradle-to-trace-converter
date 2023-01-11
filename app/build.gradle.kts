plugins {
    id("build.kotlin-application")
    id("com.google.protobuf") version ("0.9.1")
}

dependencies {
    api("com.google.protobuf:protobuf-java:3.21.12")
    implementation("org.apache.commons:commons-text:1.10.0")
    implementation("com.google.code.gson:gson:2.10")
}
repositories {
    mavenCentral()
}

application {
    mainClass.set("org.gradle.tools.trace.app.AppKt")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.21.12"
    }
}
