package com.github.ksugirl.glock

import com.github.kotlintelegrambot.entities.ChatPermissions
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.TimeUnit.SECONDS

@EnableScheduling
@SpringBootApplication
class GlockApplication {

  @Value("\${telegram.api.token}")
  private var apiToken = null as String?

  @Value("\${restrictions.duration.sec:300}")
  private var restrictionsDurationSec = null as Int?

  @Value("\${temp.messages.lifetime.sec:3}")
  private var tempMessagesLifetimeSec = null as Int?

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
  fun taskScheduler(): TaskScheduler {
    val scheduler = ThreadPoolTaskScheduler()
    scheduler.poolSize = 2
    return scheduler
  }

  @Bean
  fun glockBot(): GlockBot {
    val bot =
      GlockBot(
        requireNotNull(apiToken),
        restrictions(),
        requireNotNull(restrictionsDurationSec),
        requireNotNull(tempMessagesLifetimeSec),
        shootingEmoji()
      )
    bot.startPollingAsync()
    return bot
  }

  @Bean
  fun shootingEmoji(): Set<String> {
    return setOf("💥", "💨", "🗯", "🔫", "🔪")
  }

  @Scheduled(fixedDelay = 2, timeUnit = SECONDS)
  fun cleanTempMessages() {
    glockBot().cleanTempMessages()
  }

  @Scheduled(fixedDelay = 1, timeUnit = SECONDS)
  fun processRestrictions() {
    glockBot().processRestrictions()
  }
}

fun main(args: Array<String>) {
  runApplication<GlockApplication>(*args)
}