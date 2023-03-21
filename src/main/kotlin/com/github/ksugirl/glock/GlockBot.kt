package com.github.ksugirl.glock

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.MessageHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromId
import com.github.kotlintelegrambot.entities.ChatPermissions
import java.lang.System.currentTimeMillis
import java.lang.Thread.startVirtualThread
import java.time.Duration
import java.time.Duration.ofSeconds
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors.newSingleThreadExecutor

class GlockBot(apiKey: String, private val restrictions: ChatPermissions, restrictionsDuration: Duration) {

  private val restrictionMessage =
    "Your post has been deleted because you were shot. Deleted post can be viewed in the admin panel"

  private val bot =
    bot {
      token = apiKey
      dispatch {
        command("shoot", ::shoot)
        message(::checkoutRestrictions)
      }
    }

  private val repliesExecutor = newSingleThreadExecutor()

  private val restrictionsExecutor = newSingleThreadExecutor()

  private val tempReplyMillis = ofSeconds(15).toMillis()

  private val restrictionsDurationMillis = restrictionsDuration.toMillis()

  private val tempReplies = ConcurrentHashMap<Long, ConcurrentHashMap<Long, Long>>()

  private val restrictedUsers = ConcurrentHashMap<Long, ConcurrentHashMap<Long, Long>>()

  private fun shoot(env: CommandHandlerEnvironment) {
    val message = env.message.replyToMessage ?: return
    val userId = message.from?.id ?: return
    val messageId = message.messageId
    val chatId = message.chat.id
    restrictUser(chatId, userId)
    sendTempMessage(chatId, "💥", messageId)
  }

  private fun restrictUser(chatId: Long, userId: Long) {
    val untilDate = currentTimeMillis() + restrictionsDurationMillis
    bot.restrictChatMember(fromId(chatId), userId, restrictions, untilDate)
    restrictionsExecutor.execute {
      restrictedUsers.compute(chatId) { _, users ->
        val restrictedUsers = users ?: ConcurrentHashMap()
        restrictedUsers[userId] = untilDate
        return@compute restrictedUsers
      }
    }
  }

  private fun sendTempMessage(chatId: Long, text: String, originalMessageId: Long) {
    val tempMessage = bot.sendMessage(fromId(chatId), text, replyToMessageId = originalMessageId)
    val tempMessageId = tempMessage.get().messageId
    repliesExecutor.execute {
      tempReplies.compute(chatId) { _, replies ->
        val tempReplies = replies ?: ConcurrentHashMap()
        val untilDate = currentTimeMillis() + tempReplyMillis
        tempReplies[tempMessageId] = untilDate
        return@compute tempReplies
      }
    }
  }

  fun checkRestrictions() {
    restrictionsExecutor.submit {
      for ((_, users) in restrictedUsers) {
        val it = users.iterator()
        while (it.hasNext()) {
          val (_, expirationMillis) = it.next()
          if (currentTimeMillis() >= expirationMillis) {
            it.remove()
          }
        }
      }
    }.get()
  }

  private fun checkoutRestrictions(env: MessageHandlerEnvironment) {
    val sender = env.message.from ?: return
    val messageId = env.message.messageId
    val senderId = sender.id
    val chatId = env.message.chat.id
    val chatRestrictions = restrictedUsers[chatId] ?: return
    val untilDate = chatRestrictions[senderId] ?: return
    if (currentTimeMillis() < untilDate) {
      bot.deleteMessage(fromId(chatId), messageId)
      sendTempMessage(chatId, restrictionMessage, messageId)
    }
  }

  fun cleanTempReplies() {
    repliesExecutor.submit {
      for ((chatId, repliesIds) in tempReplies) {
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