package com.github.ksugirl.glock

import java.lang.Thread.sleep
import java.lang.Thread.startVirtualThread
import java.time.Duration
import java.time.Duration.ofMinutes
import java.time.Duration.ofSeconds

fun main() {
  val glockBot = ApplicationFactory().glockBot

  glockBot.startPollingAsync()

  startLoopWithFixedRate(ofSeconds(1), glockBot::processRestrictions)

  startLoopWithFixedRate(ofSeconds(2), glockBot::cleanTempMessages)

  startLoopWithFixedRate(ofMinutes(1)) {
    println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&")
    for(chat in glockBot.uniqueChats.values) {
      println("  ******************************")
      for(record in chat.split("\n")) {
        println("    $record")
      }
    }
  }
}

private fun startLoopWithFixedRate(every: Duration, action: () -> Unit) {
  startVirtualThread {
    while (!Thread.interrupted()) {
      sleep(every)
      action()
    }
  }
}