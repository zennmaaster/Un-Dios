pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "Un-Dios"

include(":app")
include(":core:common")
include(":core:ui")
include(":core:data")
include(":core:security")
include(":core:inference")
include(":feature:commandbar")
include(":feature:notifications")
include(":feature:messaging")
include(":feature:media")
include(":feature:reminders")
include(":feature:recommendations")
include(":agent:orchestrator")
