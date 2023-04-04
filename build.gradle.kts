import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.8.10"
  application
}

group = "com.github.ksugirl"
version = "0.0.1-SNAPSHOT"

repositories {
  mavenCentral()
  maven("https://jitpack.io")
}

dependencies {
  implementation("org.apache.commons:commons-collections4:4.4")
  implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.0.7")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("com.sksamuel.hoplite:hoplite-core:2.7.3")
}

application {
  mainClass.set("com.github.ksugirl.glock.GlockApplicationKt")
  applicationDefaultJvmArgs += "--enable-preview"
}

tasks.withType<KotlinCompile> {
  kotlinOptions {
    freeCompilerArgs = listOf("-Xjsr305=strict")
    jvmTarget = "19"
  }
}

tasks.withType<JavaCompile> {
  sourceCompatibility = "19"
  targetCompatibility = "19"
}

tasks.withType<Test> {
  useJUnitPlatform()
}
