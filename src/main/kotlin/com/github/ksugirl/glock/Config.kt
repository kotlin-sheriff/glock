package com.github.ksugirl.glock

import java.time.Duration


data class Config(
  val telegramApiToken: String,
  val restrictionsDuration: Duration = Duration.ofSeconds(60),
  val tempMessagesLifetime: Duration = Duration.ofSeconds(3)
)