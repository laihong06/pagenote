plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.kapt")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "io.pagenote.android"
  compileSdk = 35
  defaultConfig {
    applicationId = "io.pagenote.android"
    minSdk = 34
    targetSdk = 35
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    versionCode = 2
    versionName = "1.0.0"
  }
  buildTypes {
    getByName("debug") {
      applicationIdSuffix = ".debug"
    }
    getByName("release") {
      isMinifyEnabled = false
    }
    create("beta") {
      initWith(getByName("release"))
      applicationIdSuffix = ".beta"
      versionNameSuffix = "-beta.2"
      signingConfig = signingConfigs.getByName("debug")
      isDebuggable = false
      matchingFallbacks += listOf("release")
    }
  }
  splits {
    abi {
      isEnable = true
      reset()
      include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
      isUniversalApk = false
    }
  }
  buildFeatures {
    compose = true
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
  implementation(project(":core-project"))
  implementation(project(":core-reader"))
  implementation(project(":feature-notes"))
  implementation(project(":feature-annotations"))
  implementation(project(":feature-pen"))
  implementation(project(":feature-ocr"))
  implementation(project(":agent"))
  implementation("androidx.core:core-ktx:1.15.0")
  implementation("androidx.activity:activity-compose:1.9.3")
  implementation("androidx.datastore:datastore-preferences:1.2.1")
  implementation("androidx.webkit:webkit:1.17.1")
  implementation("com.tom-roush:pdfbox-android:2.0.27.0")
  implementation("com.caverock:androidsvg-aar:1.4")
  implementation(platform("androidx.compose:compose-bom:2024.12.01"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.compose.material3:material3")
  implementation("io.noties.markwon:core:4.6.2")
  implementation("io.noties.markwon:editor:4.6.2")
  implementation("io.noties.markwon:ext-latex:4.6.2")
  implementation("io.noties.markwon:inline-parser:4.6.2")
  implementation("io.noties.markwon:ext-strikethrough:4.6.2")
  implementation("io.noties.markwon:syntax-highlight:4.6.2") {
    exclude(group = "org.jetbrains", module = "annotations-java5")
  }
  implementation("io.noties.markwon:ext-tables:4.6.2")
  implementation("io.noties.markwon:ext-tasklist:4.6.2")
  implementation("io.noties.markwon:html:4.6.2")
  implementation("io.noties.markwon:image:4.6.2")
  implementation("io.noties:prism4j:2.0.0") {
    exclude(group = "org.jetbrains", module = "annotations-java5")
  }
  kapt("io.noties:prism4j-bundler:2.0.0")
  testImplementation("junit:junit:4.13.2")
  debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.6")
  androidTestImplementation("androidx.test:runner:1.6.2")
  androidTestImplementation("androidx.test.ext:junit:1.2.1")
  androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.6")
}
