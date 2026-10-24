plugins {
    id("com.android.application")
}

val syncCowboyWebAssets by tasks.registering(Sync::class) {
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
        versionCode = 8
        versionName = "0.1.8"
    }

    sourceSets.getByName("main").assets.srcDir(
        layout.buildDirectory.dir("generated/cowboyWebAssets")
    )
}

tasks.named("preBuild").configure {
    dependsOn(syncCowboyWebAssets)
}

dependencies {
    implementation("com.google.android.material:material:1.14.0")
}
