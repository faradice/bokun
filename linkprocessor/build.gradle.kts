plugins {
    kotlin("jvm") version "2.1.10"
    id("application")
}

group = "com.bokun.email.processor"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("io.mockk:mockk:1.13.5")

    // Javalin for the web framework
    implementation("io.javalin:javalin:5.6.2")

    // SQLite for database storage
    implementation("org.xerial:sqlite-jdbc:3.42.0.0")

    // Exposed ORM for database interaction
    implementation("org.jetbrains.exposed:exposed-core:0.42.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.42.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.42.0")

    // Logging with SLF4J and Logback
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // Rate limiting
    implementation("com.github.vladimir-bukhtoyarov:bucket4j-core:7.6.0")

    // Monitoring with Prometheus
    implementation("io.prometheus:simpleclient:0.16.0")
    implementation("io.prometheus:simpleclient_hotspot:0.16.0")
    implementation("io.prometheus:simpleclient_httpserver:0.16.0")

    // Testing dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
    testImplementation("io.mockk:mockk:1.13.5")

    // Jackson for JSON parsing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")
}

tasks.register<Jar>("fatJar") {
    archiveBaseName.set("email-processor")
    archiveVersion.set("")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })

    manifest {
        attributes["Main-Class"] = "com.bokun.email.processor.app.AppKt"
    }
}

application {
    mainClass.set("com.bokun.email.processor.app.App")
}
tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(20)
}

