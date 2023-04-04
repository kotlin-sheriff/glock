package com.github.ksugirl.glock

import java.lang.Thread.startVirtualThread
import java.time.Duration

fun main() {
  val applicationFactory = ApplicationFactory()
  val glockBot = applicationFactory.glockBot

  glockBot.startPollingAsync()

  startLoopWithFixedRate(Duration.ofSeconds(1)) {
    glockBot.processRestrictions()
  }

  startLoopWithFixedRate(Duration.ofSeconds(2)) {
    glockBot.cleanTempMessages()
  }
}

private fun startLoopWithFixedRate(every: Duration, action: () -> Unit) {
  startVirtualThread {
    while (!Thread.interrupted()) {
      Thread.sleep(every)
      action()
    }
  }
}