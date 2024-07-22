plugins {
    application
    kotlin("jvm") version "2.0.0"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")
}

application {
    mainClass = "parallelZip.MainKotlin"
    applicationDefaultJvmArgs = listOf("--add-opens=java.base/java.util.zip=ALL-UNNAMED")
}

task<JavaExec>("runJava") {
    configure("MainJava")
}

task<JavaExec>("runKotlin") {
    configure("MainKotlin")
}

task<JavaExec>("runSequential") {
    configure("Sequential")
}

fun JavaExec.configure(name: String) {
    mainClass = "parallelZip.$name"
    classpath = sourceSets["main"].runtimeClasspath
    jvmArguments = listOf("--add-opens=java.base/java.util.zip=ALL-UNNAMED")
}
