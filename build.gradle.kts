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
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("com.sparkjava:spark-core:2.9.4")
}

tasks.test {
    useJUnitPlatform()
}