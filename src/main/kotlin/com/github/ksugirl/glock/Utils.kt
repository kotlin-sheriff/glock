package com.github.ksugirl.glock

object Utils {

  private val animations = setOf("💥", "💨", "🗯")

  fun randomAnimation(): String {
    return animations.random()
  }
}