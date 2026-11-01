plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "dev.yggskins"
version = "1.0.0"
description = "Gestor de skins con soporte Yggdrasil/authlib-injector para Paper"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.12.3")

    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.4.1")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("org.bstats:bstats-bukkit:3.0.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    enabled = false
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
    mergeServiceFiles()
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    // org.sqlite no se reubica: su JNI se enlaza por nombre de clase.
    relocate("com.google.gson", "dev.yggskins.libs.gson")
    relocate("com.zaxxer.hikari", "dev.yggskins.libs.hikari")
    relocate("org.bstats", "dev.yggskins.libs.bstats")
    relocate("org.mariadb.jdbc", "dev.yggskins.libs.mariadb")
    relocate("org.postgresql", "dev.yggskins.libs.postgresql")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

// Copia el JAR al servidor local de pruebas (run/).
tasks.register<Copy>("deployLocal") {
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.flatMap { it.archiveFile })
    into(layout.projectDirectory.dir("run/plugins"))
}
