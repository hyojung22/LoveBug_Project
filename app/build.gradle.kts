plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

android {
    namespace = "com.example.lovebug_project"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.lovebug_project"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "SUPABASE_URL", "\"https://dvfidxoksmswsyjjcczy.supabase.co\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImR2ZmlkeG9rc21zd3N5ampjY3p5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTQ1MzAyOTEsImV4cCI6MjA3MDEwNjI5MX0.9VVwcUU9xiOk_qtYXibA6UZos4cs-WFh3dOvFexvgqw\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // For production, use environment variables or gradle.properties
            buildConfigField("String", "SUPABASE_URL", "\"${findProperty("SUPABASE_URL") ?: ""}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${findProperty("SUPABASE_ANON_KEY") ?: ""}\"")
        }
    }
    compileOptions {
        // Enable support for the new language APIs
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // Supabase
    val supabaseVersion = "3.2.2"
    val ktorVersion = "3.2.2"
    implementation(platform("io.github.jan-tennert.supabase:bom:$supabaseVersion"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    
    // Required for Supabase Auth session management
    implementation("com.russhwolf:multiplatform-settings:1.1.1")
    
    // Room Database removed - migrated to Supabase
    
    // Coroutines and Lifecycle
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    
    // Volley
    implementation("com.android.volley:volley:1.2.1")

    // Glide 이미지 생성 추가
    implementation("com.github.bumptech.glide:glide:4.11.0")

    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.fragment:fragment-ktx:1.8.1")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.recyclerview)
    
    // Kizitonwose Calendar
    implementation("com.kizitonwose.calendar:view:2.6.0")


    // Java 8+ API desugaring support
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

}