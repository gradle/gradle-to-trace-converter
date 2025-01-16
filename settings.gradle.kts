pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("com.gradle.develocity") version "3.19"
}

rootProject.name = "trace-command-line-tool"

develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/terms-of-service"
        termsOfUseAgree = "yes"
        // TODO: workaround for https://github.com/gradle/gradle/issues/22879.
        val isCI = providers.environmentVariable("CI").isPresent
        publishing.onlyIf { isCI }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include("app")
