plugins {
    application
}

group = "it.uniupo.macchinetta"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    mainClass = "it.uniupo.macchinetta.Main"
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    implementation(libs.postgresql)
    implementation(libs.mqtt)
}

tasks.test {
    useJUnitPlatform()
}