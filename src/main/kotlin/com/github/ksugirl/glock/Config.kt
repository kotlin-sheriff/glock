package com.github.ksugirl.glock

import com.sksamuel.hoplite.ConfigAlias
import java.time.Duration


data class Config(
  @ConfigAlias("telegram.api.token")
  val apiToken: String,
  @ConfigAlias("restrictions.duration.sec")
  val restrictionsDuration: Duration = Duration.ofSeconds(60),
  @ConfigAlias("temp.messages.lifetime.sec")
  val tempMessagesLifetime: Duration = Duration.ofSeconds(3)
)