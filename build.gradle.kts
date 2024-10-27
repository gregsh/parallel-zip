plugins {
    kotlin("jvm") version "2.0.0"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

task("fatJar", Jar::class) {
    group = project.name
    archiveBaseName = project.name
    manifest.attributes["Manifest-Version"] = "1.0"
    manifest.attributes["Main-Class"] = "parallelZip.MainJava"
    manifest.attributes["Add-Opens"] = "java.base/java.util.zip java.base/java.io"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get())
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
    group = project.name
    mainClass = "parallelZip.$name"
    classpath = sourceSets["main"].runtimeClasspath
    jvmArguments = listOf(
        "--add-opens=java.base/java.util.zip=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED"
    )
}
