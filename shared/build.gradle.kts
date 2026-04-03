plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("app.cash.sqldelight")
}

android {
    namespace = "com.stashapp.shared"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

sqldelight {
    databases {
        create("StashDatabase") {
            packageName.set("com.stashapp.shared.db")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    
    val sqlDelightVersion = "2.0.2"
    implementation("app.cash.sqldelight:android-driver:$sqlDelightVersion")
    implementation("app.cash.sqldelight:coroutines-extensions-jvm:$sqlDelightVersion")
}
