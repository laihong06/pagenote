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
    maven { url = uri("$rootDir/third-party/opencv-maven") }
    google()
    mavenCentral()
  }
}
rootProject.name = "pagenote-android"
include(":app")
include(":core-project")
include(":core-reader")
include(":feature-notes")
include(":feature-annotations")
include(":feature-pen")
include(":feature-yuketang")
include(":feature-ocr")
include(":agent")
include(":shared-tests")
