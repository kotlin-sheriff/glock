package com.github.ksugirl.glock

import com.github.kotlintelegrambot.entities.ChatPermissions
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addEnvironmentSource
import java.time.Duration
import java.time.Duration.ofSeconds
import java.time.ZoneId
import java.time.ZoneOffset

open class ApplicationFactory {
  data class Config(
    val telegramApiToken: String,
    val healingConstant: Long,
    val healingTimeZone: ZoneOffset,
    val restrictionsDuration: Duration = ofSeconds(60),
    val tempMessagesLifetime: Duration = ofSeconds(3)
  )

  open val config by lazy {
    ConfigLoaderBuilder.default()
      .addEnvironmentSource()
      .build()
      .loadConfigOrThrow<Config>()
  }

  open val restrictions = ChatPermissions(
    canSendMessages = false,
    canSendMediaMessages = false,
    canSendPolls = false,
    canSendOtherMessages = false,
    canAddWebPagePreviews = false,
    canChangeInfo = false,
    canInviteUsers = false,
    canPinMessages = false
  )

  open val glockBot by lazy {
    GlockBot(
      config.telegramApiToken,
      restrictions,
      config.restrictionsDuration,
      config.tempMessagesLifetime,
      config.healingConstant,
      config.healingTimeZone
    )
  }
}