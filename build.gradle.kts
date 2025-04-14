plugins {
    kotlin("jvm") version "2.1.0"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
    implementation("org.xerial:sqlite-jdbc:3.43.2.0")
    implementation("org.jetbrains.exposed:exposed-core:0.45.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.45.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.45.0")
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
    implementation("com.opencsv:opencsv:5.9")
    implementation("io.ktor:ktor-client-core:2.3.5")
    implementation("io.ktor:ktor-client-cio:2.3.5")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.5")
    implementation("org.slf4j:slf4j-simple:2.0.17")
}

application {
    mainClass.set("com.liarstudio.maven_indexer.MainKt")
}
