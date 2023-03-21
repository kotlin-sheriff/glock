package com.github.ksugirl.glock

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromId
import com.github.kotlintelegrambot.entities.ChatPermissions
import java.lang.System.currentTimeMillis
import java.lang.Thread.startVirtualThread
import java.time.Duration

class GlockBot(apiKey: String, private val restrictions: ChatPermissions, duration: Duration) {

  private val bot: Bot

  private val durationMillis: Long

  init {
    bot = bot {
      token = apiKey
      dispatch {
        command("shoot", ::shoot)
      }
    }
    durationMillis = duration.toMillis()
  }

  private fun shoot(env: CommandHandlerEnvironment) {
    val message = env.message.replyToMessage ?: return
    val userId = message.from?.id ?: return
    val chatId = message.chat.id.let(::fromId)
    val untilDate = currentTimeMillis() + durationMillis
    bot.restrictChatMember(chatId, userId, restrictions, untilDate)
  }

  fun startPollingAsync() {
    startVirtualThread(bot::startPolling)
  }
}