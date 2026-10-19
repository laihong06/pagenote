plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "io.pagenote.core.project"
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
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  testImplementation("junit:junit:4.13.2")
}
