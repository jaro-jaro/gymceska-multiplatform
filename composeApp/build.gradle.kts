import com.codingfeline.buildkonfig.compiler.FieldSpec
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.gms.google.services)
    alias(libs.plugins.firebase.crashlytics)
    id("com.codingfeline.buildkonfig") version "0.15.2" apply true
}

val appVersionCode = 102
val appVersionName = "2.5.0-alpha.5"

kotlin {
    js(IR) {
        moduleName = "composeApp"
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(rootDirPath)
                        add(projectDirPath)
                    }
                }
            }
        }
        binaries.executable()
    }

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.bundles.androidx.jetpack.glance)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.firebase.crashlytics)
        }
        jsMain {
            dependsOn(commonMain.get())
            dependencies {
                implementation(compose.html.core)
                implementation(libs.multiplatform.settings.make.observable)
            }
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlin.stdlib)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.firebase.database)
            implementation(libs.firebase.config)
            implementation(libs.bundles.ksoup)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.androidx.adaptive)
            implementation(libs.semver)
            implementation(libs.kotlinx.datetime)
            implementation(libs.filekit.core)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.firebase.analytics)
        }
    }
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}

buildkonfig {
    packageName = "cz.jaro.gymceska"

    defaultConfigs {
        buildConfigField(FieldSpec.Type.STRING, "versionName", appVersionName)
        buildConfigField(FieldSpec.Type.INT, "versionCode", "$appVersionCode")
    }
}

android {
    namespace = "cz.jaro.gymceska"
    compileSdk = 35

    defaultConfig {
        applicationId = "cz.jaro.gymceska"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

