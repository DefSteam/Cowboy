plugins {
    id("com.android.application")
}

val syncCowboyWebAssets = tasks.register<Sync>("syncCowboyWebAssets") {
    from(rootProject.file("web")) {
        exclude("README.md", "serve.sh")
    }
    into(layout.buildDirectory.dir("generated/cowboyWebAssets/web"))
}

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
        versionCode = 27
        versionName = "0.1.27"
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
    implementation("com.google.android.material:material:1.14.0")
}
