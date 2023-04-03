import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("org.springframework.boot") version "3.1.0-SNAPSHOT"
  id("io.spring.dependency-management") version "1.1.0"
  kotlin("jvm") version "1.8.10"
  kotlin("plugin.spring") version "1.8.10"
}

group = "com.github.ksugirl"
version = "0.0.1-SNAPSHOT"

repositories {
  mavenCentral()
  maven("https://repo.spring.io/milestone")
  maven("https://repo.spring.io/snapshot")
  maven("https://jitpack.io")
}

dependencies {
  implementation("org.apache.commons:commons-collections4:4.4")
  implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.0.7")
  implementation("org.springframework.boot:spring-boot-starter")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
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

tasks.bootJar {
  archiveVersion.set("boot")
}
