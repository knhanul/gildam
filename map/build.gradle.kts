plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "kr.co.nuni.gildam.map"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        // 지도 스타일 위치. asset://... (앱 내장) 또는 https://... (예: 나중에 OVH PMTiles style.json)
        // gradle.properties 또는 -PmapStyleLightUrl=... 로 바꿀 수 있음
        val light = (project.findProperty("mapStyleLightUrl") as String?) ?: "asset://styles/liberty.json"
        val dark = (project.findProperty("mapStyleDarkUrl") as String?) ?: "asset://styles/dark.json"
        buildConfigField("String", "MAP_STYLE_LIGHT_URL", "\"$light\"")
        buildConfigField("String", "MAP_STYLE_DARK_URL", "\"$dark\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":data"))
    implementation(project(":recording"))

    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.maplibre.android.sdk)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
