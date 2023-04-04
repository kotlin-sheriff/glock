package com.github.ksugirl.glock

import java.lang.Thread.startVirtualThread
import java.time.Duration


fun main() {
  val applicationFactory = ApplicationFactory()
  val glockBot = applicationFactory.glockBot
  val config = applicationFactory.config

  glockBot.startPollingAsync()

  startLoopWithFixedRate(config.restrictionsDuration) {
    glockBot.processRestrictions()
  }

  startLoopWithFixedRate(config.tempMessagesLifetime) {
    glockBot.cleanTempMessages()
  }
}

private fun startLoopWithFixedRate(every: Duration, action: () -> Unit) {
  startVirtualThread {
    while (!Thread.interrupted()) {
      startVirtualThread {
        action()
      }
      Thread.sleep(every)
    }
  }
}