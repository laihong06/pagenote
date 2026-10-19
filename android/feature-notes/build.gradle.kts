plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "io.pagenote.feature.notes"
  compileSdk = 35
  defaultConfig {
    minSdk = 34
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
}

dependencies {
  testImplementation("junit:junit:4.13.2")
  implementation(project(":core-project"))
}
