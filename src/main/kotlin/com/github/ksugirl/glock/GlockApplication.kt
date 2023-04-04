package com.github.ksugirl.glock

import java.lang.Thread.startVirtualThread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit


fun main() {
  val applicationFactory = ApplicationFactory()
  val glockBot = applicationFactory.glockBot

  glockBot.startPollingAsync()

  startLoopWithFixedRate(1.seconds) {
    glockBot.processRestrictions()
  }

  startLoopWithFixedRate(2.seconds) {
    glockBot.cleanTempMessages()
  }
}

private fun startLoopWithFixedRate(every: Duration, action: () -> Unit) {
  startVirtualThread {
    while (!Thread.interrupted()) {
      Thread.sleep(every.toLong(DurationUnit.MILLISECONDS))
      action()
    }
  }
}