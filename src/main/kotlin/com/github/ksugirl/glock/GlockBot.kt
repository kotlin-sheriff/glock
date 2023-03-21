package com.github.ksugirl.glock

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromId
import com.github.kotlintelegrambot.entities.ChatId.Id
import com.github.kotlintelegrambot.entities.ChatPermissions
import java.lang.System.currentTimeMillis
import java.lang.Thread.startVirtualThread
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors.newSingleThreadExecutor

class GlockBot(apiKey: String, private val restrictions: ChatPermissions, duration: Duration) {

  private val bot =
    bot {
      token = apiKey
      dispatch {
        command("shoot", ::shoot)
      }
    }

  private val durationMillis = duration.toMillis()

  private val tempRepliesIds = ConcurrentHashMap<Long, Set<Long>>()

  private val tempRepliesExecutor = newSingleThreadExecutor()

  private fun shoot(env: CommandHandlerEnvironment) {
    val message = env.message.replyToMessage ?: return
    val messageId = message.messageId
    val userId = message.from?.id ?: return
    val chatId = message.chat.id.let(::fromId)
    val untilDate = currentTimeMillis() + durationMillis
    bot.restrictChatMember(chatId, userId, restrictions, untilDate)
    sendTempReply(chatId, messageId)
  }

  private fun sendTempReply(chat: Id, originalMessageId: Long) {
    val tempMessage = bot.sendMessage(chat, "💥", replyToMessageId = originalMessageId)
    val tempMessageId = tempMessage.get().messageId
    tempRepliesExecutor.execute {
      tempRepliesIds.compute(chat.id) { _, ids ->
        (ids ?: emptySet()) + tempMessageId
      }
    }
  }

  fun cleanTempReplies() {
    tempRepliesExecutor.submit {
      for ((chatId, messageIds) in tempRepliesIds) {
        for (messageId in messageIds) {
          bot.deleteMessage(fromId(chatId), messageId)
        }
      }
    }.get()
  }

  fun startPollingAsync() {
    startVirtualThread(bot::startPolling)
  }
}