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
import java.time.Duration.ofSeconds
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors.newSingleThreadExecutor

class GlockBot(apiKey: String, private val restrictions: ChatPermissions, restrictionsDuration: Duration) {

  private val bot =
    bot {
      token = apiKey
      dispatch {
        command("shoot", ::shoot)
      }
    }

  private val restrictionsDurationMillis = restrictionsDuration.toMillis()

  private val tempReplyMillis = ofSeconds(15).toMillis()

  private val tempRepliesIds = ConcurrentHashMap<Long, ConcurrentHashMap<Long, Long>>()

  private val atomicExecutor = newSingleThreadExecutor()

  private fun shoot(env: CommandHandlerEnvironment) {
    val message = env.message.replyToMessage ?: return
    val messageId = message.messageId
    val userId = message.from?.id ?: return
    val chatId = message.chat.id.let(::fromId)
    val untilDate = currentTimeMillis() + restrictionsDurationMillis
    bot.restrictChatMember(chatId, userId, restrictions, untilDate)
    sendTempReply(chatId, messageId)
  }

  private fun sendTempReply(chat: Id, originalMessageId: Long) {
    val tempMessage = bot.sendMessage(chat, "💥", replyToMessageId = originalMessageId)
    val tempMessageId = tempMessage.get().messageId
    atomicExecutor.execute {
      tempRepliesIds.compute(chat.id) { _, replies ->
        val tempReplies = replies ?: ConcurrentHashMap()
        val untilDate = currentTimeMillis() + tempReplyMillis
        tempReplies[tempMessageId] = untilDate
        return@compute tempReplies
      }
    }
  }

  fun cleanTempReplies() {
    atomicExecutor.submit {
      for ((chatId, repliesIds) in tempRepliesIds) {
        val it = repliesIds.iterator()
        while (it.hasNext()) {
          val (replyId, expirationMillis) = it.next()
          if (currentTimeMillis() >= expirationMillis) {
            bot.deleteMessage(fromId(chatId), replyId)
            it.remove()
          }
        }
      }
    }.get()
  }

  fun startPollingAsync() {
    startVirtualThread(bot::startPolling)
  }
}