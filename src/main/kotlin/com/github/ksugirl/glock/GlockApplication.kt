package com.github.ksugirl.glock

import java.time.Duration
import java.util.concurrent.ExecutorService


fun main() {
  val applicationFactory = ApplicationFactory()
  val glockBot = applicationFactory.glockBot
  val schedulerExecutor = applicationFactory.schedulerExecutor
  val config = applicationFactory.config

  glockBot.startPollingAsync()

  schedulerExecutor.startLoopWithFixedRate(config.restrictionsDuration) {
    glockBot.processRestrictions()
  }

  schedulerExecutor.startLoopWithFixedRate(config.tempMessagesLifetime) {
    glockBot.cleanTempMessages()
  }
}

private fun ExecutorService.startLoopWithFixedRate(every: Duration, action: () -> Unit) {
  submit {
    while (!Thread.interrupted()) {
      Thread.sleep(every)
      action()
    }
  }
}