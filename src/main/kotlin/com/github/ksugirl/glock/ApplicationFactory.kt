package com.github.ksugirl.glock

import com.github.kotlintelegrambot.entities.ChatPermissions
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addFileSource

open class ApplicationFactory {

  open val config by lazy {
    ConfigLoaderBuilder.default()
      .addEnvironmentSource()
      .addFileSource("application.yaml", optional = true)
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
      requireNotNull(config.telegramApiToken),
      restrictions,
      config.restrictionsDuration,
      config.tempMessagesLifetime
    )
  }
}