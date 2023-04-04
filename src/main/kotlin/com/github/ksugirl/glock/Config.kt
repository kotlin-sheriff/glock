package com.github.ksugirl.glock

import java.time.Duration
import java.time.Duration.ofSeconds

data class Config(
  val telegramApiToken: String,
  val restrictionsDuration: Duration = ofSeconds(60),
  val tempMessagesLifetime: Duration = ofSeconds(3)
)