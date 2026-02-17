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

dependencyResolution {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Castor"

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
include(":agent:orchestrator")
