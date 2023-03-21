package com.github.ksugirl.glock

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.MessageHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromId
import com.github.kotlintelegrambot.entities.ChatPermissions
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.ParseMode.MARKDOWN_V2
import java.lang.Thread.startVirtualThread
import java.time.Duration
import java.time.Duration.ofSeconds
import java.time.Instant.now
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors.newSingleThreadExecutor

class GlockBot(apiKey: String, private val restrictions: ChatPermissions, restrictionsDuration: Duration) {

  private val restrictionMessage =
    "your post has been deleted because you were shot. Deleted post can be viewed in the admin panel"

  private val bot =
    bot {
      token = apiKey
      dispatch {
        command("shoot", ::shoot)
        message(::checkRestrictions)
      }
    }

  private val tempMessagesExecutor = newSingleThreadExecutor()

  private val restrictionsExecutor = newSingleThreadExecutor()

  private val tempMessageSec = ofSeconds(5).toSeconds()

  private val restrictionsDurationSec = restrictionsDuration.toSeconds()

  private val tempMessages = ConcurrentHashMap<Long, ConcurrentHashMap<Long, Long>>()

  private val restrictedUsers = ConcurrentHashMap<Long, ConcurrentHashMap<Long, Long>>()

  private fun shoot(env: CommandHandlerEnvironment) {
    val gunfighter = env.message.from?.id ?: return
    val gunfighterRequestId = env.message.messageId
    val chatId = env.message.chat.id
    val message = env.message.replyToMessage ?: return
    if (isRestricted(chatId, gunfighter)) {
      return
    }
    markAsTemp(chatId, gunfighterRequestId)
    val userId = message.from?.id ?: return
    val messageId = message.messageId
    restrictUser(chatId, userId)
    sendTempMessage(chatId, "💥", replyTo = messageId)
  }

  private fun restrictUser(chatId: Long, userId: Long) {
    val untilDate = now().epochSecond + restrictionsDurationSec
    bot.restrictChatMember(fromId(chatId), userId, restrictions, untilDate)
    restrictionsExecutor.execute {
      restrictedUsers.compute(chatId) { _, users ->
        val restrictedUsers = users ?: ConcurrentHashMap()
        restrictedUsers[userId] = untilDate
        return@compute restrictedUsers
      }
    }
  }

  private fun sendTempMessage(chatId: Long, text: String, replyTo: Long? = null, parseMode: ParseMode? = null) {
    val tempMessage = bot.sendMessage(fromId(chatId), text, replyToMessageId = replyTo)
    val tempMessageId = tempMessage.get().messageId
    markAsTemp(chatId, tempMessageId)
  }


  private fun markAsTemp(chatId: Long, messageId: Long) {
    tempMessagesExecutor.execute {
      tempMessages.compute(chatId) { _, replies ->
        val tempReplies = replies ?: ConcurrentHashMap()
        val untilDate = now().epochSecond + tempMessageSec
        tempReplies[messageId] = untilDate
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
          if (now().epochSecond >= expirationMillis) {
            it.remove()
          }
        }
      }
    }.get()
  }

  private fun checkRestrictions(env: MessageHandlerEnvironment) {
    val user = env.message.from ?: return
    val messageId = env.message.messageId
    val chatId = env.message.chat.id
    if (isTempMessage(chatId, messageId)) {
      return
    }
    val userId = user.id
    if (isRestricted(chatId, userId)) {
      val firstName = user.firstName
      val personalizedRestrictionMessage = "[$firstName](tg://user?id=${userId}), $restrictionMessage"
      sendTempMessage(chatId, personalizedRestrictionMessage, parseMode = MARKDOWN_V2)
      bot.deleteMessage(fromId(chatId), messageId)
    }
  }

  private fun isTempMessage(chatId: Long, messageId: Long): Boolean {
    val chatTempMessages = tempMessages[chatId] ?: return false
    val lifetimeUntil = chatTempMessages[messageId]
    return lifetimeUntil != null
  }

  private fun getRestrictionDateUntil(chatId: Long, userId: Long): Long? {
    val chatRestrictions = restrictedUsers[chatId] ?: return null
    return chatRestrictions[userId]
  }

  private fun isRestricted(chatId: Long, userId: Long): Boolean {
    val restrictedUntil = getRestrictionDateUntil(chatId, userId)
    return restrictedUntil != null && now().epochSecond < restrictedUntil
  }

  fun cleanTempMessages() {
    tempMessagesExecutor.submit {
      for ((chatId, repliesIds) in tempMessages) {
        val it = repliesIds.iterator()
        while (it.hasNext()) {
          val (replyId, expirationSec) = it.next()
          if (now().epochSecond >= expirationSec) {
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