plugins {
    id("com.android.application")
}

val signingStoreFile = providers.environmentVariable("CM5_KEYSTORE_FILE").orNull
val signingStorePassword = providers.environmentVariable("CM5_KEYSTORE_PASSWORD").orNull
val signingKeyAlias = providers.environmentVariable("CM5_KEY_ALIAS").orNull
val signingKeyPassword = providers.environmentVariable("CM5_KEY_PASSWORD").orNull

android {
    namespace = "com.mhkprog.cm5"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mhkprog.cm5"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "5.0.2"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            if (!signingStoreFile.isNullOrBlank()) storeFile = file(signingStoreFile)
            if (!signingStorePassword.isNullOrBlank()) storePassword = signingStorePassword
            if (!signingKeyAlias.isNullOrBlank()) keyAlias = signingKeyAlias
            if (!signingKeyPassword.isNullOrBlank()) keyPassword = signingKeyPassword
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (!signingStoreFile.isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
        }
    }

}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            rootProject.tasks.named<Sync>("syncWebAssets")
        ) { task -> task.destinationDirectory }
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.18.0")
}
