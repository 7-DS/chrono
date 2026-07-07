pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

includeBuild("third_party/ssp-transport") {
    dependencySubstitution {
        substitute(module("sh.haven:ssp-transport")).using(project(":"))
    }
}

includeBuild("third_party/et-transport") {
    dependencySubstitution {
        substitute(module("sh.haven:et-transport")).using(project(":"))
    }
}

rootProject.name = "ChronoSSH"
include(":app")
