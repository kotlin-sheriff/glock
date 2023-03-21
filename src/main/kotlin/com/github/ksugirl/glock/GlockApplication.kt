package com.github.ksugirl.glock

import com.github.kotlintelegrambot.entities.ChatPermissions
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.time.Duration
import java.time.Duration.ofMinutes
import java.util.concurrent.TimeUnit.SECONDS

@EnableScheduling
@SpringBootApplication
class GlockApplication {

  @Value("\${telegram.api.token}")
  private lateinit var apiToken: String

  @Bean
  fun restrictions(): ChatPermissions {
    return ChatPermissions(
      canSendMessages = false,
      canSendMediaMessages = false,
      canSendPolls = false,
      canSendOtherMessages = false,
      canAddWebPagePreviews = false,
      canChangeInfo = false,
      canInviteUsers = false,
      canPinMessages = false
    )
  }

  @Bean
  fun duration(): Duration {
    return ofMinutes(5)
  }

  @Bean
  fun glockBot(): GlockBot {
    val bot = GlockBot(apiToken, restrictions(), duration())
    bot.startPollingAsync()
    return bot
  }

  @Scheduled(fixedDelay = 15, timeUnit = SECONDS)
  fun cleanTempReplies() {
    glockBot().cleanTempReplies()
  }
}

fun main(args: Array<String>) {
  runApplication<GlockApplication>(*args)
}