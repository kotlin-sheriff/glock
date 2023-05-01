import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.8.20"
  kotlin("plugin.serialization") version "1.8.0"
  application
}

group = "com.github.ksugirl"
version = "0.0.1-SNAPSHOT"

repositories {
  mavenCentral()
  maven("https://jitpack.io")
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.5.0")
  implementation("org.apache.commons:commons-collections4:4.4")
  implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.0.7")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("com.sksamuel.hoplite:hoplite-core:2.7.3")
  testImplementation("com.google.truth:truth:1.1.3")
  testImplementation("io.mockk:mockk:1.13.4")
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")
}

application {
  mainClass.set("com.github.ksugirl.glock.GlockApplicationKt")
  applicationDefaultJvmArgs += "--enable-preview"
}

tasks.withType<KotlinCompile> {
  kotlinOptions {
    freeCompilerArgs = listOf("-Xjsr305=strict", "-Xopt-in=kotlinx.serialization.ExperimentalSerializationApi")
    jvmTarget = "19"
  }
}

tasks.withType<JavaCompile> {
  sourceCompatibility = "19"
  targetCompatibility = "19"
}

tasks.withType<Test> {
  useJUnitPlatform()
  jvmArgs("--enable-preview")
}

tasks.installDist {
  dependsOn(tasks.test)
}
