plugins {
    id("com.android.application")
}

val syncCowboyWebAssets = tasks.register<Sync>("syncCowboyWebAssets") {
    from(rootProject.file("web")) {
        exclude("README.md", "serve.sh")
    }
    into(layout.buildDirectory.dir("generated/cowboyWebAssets/web"))
}
val cowboyRuntimeVersion = rootProject.file("runtime/VERSION").readText().trim()

android {
    namespace = "com.cowboy.app"
    compileSdk = 37

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.cowboy.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 45
        versionName = "0.1.45"
        buildConfigField("String", "RUNTIME_VERSION", "\"$cowboyRuntimeVersion\"")
    }

}

androidComponents {
    onVariants { variant ->
        variant.sources.assets!!.addGeneratedSourceDirectory(syncCowboyWebAssets) {
            it.destinationDirectory
        }
    }
}

dependencies {
    implementation("androidx.activity:activity:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.webkit:webkit:1.14.0")
}
