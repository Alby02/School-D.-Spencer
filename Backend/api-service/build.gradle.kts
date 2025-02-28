plugins {
    application
}

group = "it.uniupo.restfullMachineManager"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application{
    mainClass = "it.uniupo.restfullMachineManager.Main"
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    implementation(libs.spark.core)
    implementation(libs.gson)
    implementation(libs.postgresql)
    implementation(libs.mqtt)
}

tasks.test {
    useJUnitPlatform()
}