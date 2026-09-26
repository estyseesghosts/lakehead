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

rootProject.name = "ProtoLaunch"
include(":app", ":core:model", ":core:navigation", ":core:grid", ":core:designsystem", ":platform:launcherapps", ":platform:feed", ":feature:home", ":feature:allapps", ":fixtures:launchable", ":data:database", ":data:repository")
