plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
}

group = "org.kozyrev"
version = "0.0.1"

application {
    mainClass = "org.kozyrev.ApplicationKt"
}

dependencies {
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.core)
//    implementation(libs.ktor.server.netty)
//    implementation(libs.ktor.server.config.yaml)
//    testImplementation(libs.ktor.server.test.host)

    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.serialization)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.logging)

    implementation(libs.logback.classic)

    // Compose for Desktop
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.material)
    implementation(compose.material3)
    implementation(compose.components.resources)
    implementation(compose.components.uiToolingPreview)
    implementation(compose.desktop.currentOs)

    testImplementation(libs.kotlin.test.junit)
}
