package com.github.ksugirl.glock

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ChatPermissions
import com.github.kotlintelegrambot.entities.Message
import org.apache.commons.collections4.QueueUtils.synchronizedQueue
import org.apache.commons.collections4.queue.CircularFifoQueue
import java.io.Closeable
import java.time.Duration
import java.time.Instant.now
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors.newSingleThreadExecutor
import kotlin.random.Random.Default.nextInt
import kotlin.random.Random.Default.nextLong

/**
 * Class for sequential, secure, atomic operations in a single chat
 */
class ChatOps(
  private val bot: Bot,
  private val chatId: ChatId,
  private val restrictions: ChatPermissions,
  private val restrictionsDuration: Duration,
  private val tempMessagesLifetime: Duration
) : Closeable {

  private val restrictionsExecutor = newSingleThreadExecutor()
  private val usersToRestrictions = ConcurrentHashMap<Long, Long>()
  private val messagesToLifetimes = ConcurrentHashMap<Long, Long>()
  private val recentMessages = synchronizedQueue(CircularFifoQueue<Message>(7))
  private val statuettes = ConcurrentLinkedQueue<Long>()

  fun cleanTempMessages() {
    val tempMessagesCount = messagesToLifetimes.mappingCount()
    messagesToLifetimes.forEach(tempMessagesCount, ::tryRemoveMessage)
  }

  fun processRestrictions() {
    restrictionsExecutor.submit {
      val restrictedUsersCount = usersToRestrictions.mappingCount()
      usersToRestrictions.forEach(restrictedUsersCount, ::processRestriction)
    }.get()
  }

  fun filterMessage(message: Message) {
    if (isRestricted(message)) {
      val messageId = message.messageId
      bot.deleteMessage(chatId, messageId)
      return
    }
    recentMessages += message
  }

  fun statuette(gunfighterMessage: Message) {
    if (isRestricted(gunfighterMessage)) {
      return
    }
    statuettes += reply(gunfighterMessage, "🗿")
    markAsTemp(gunfighterMessage.messageId)
  }

  fun tryProcessStatuette(message: Message) {
    if (isRestricted(message)) {
      return
    }
    val statuette = statuettes.poll() ?: return
    bot.deleteMessage(chatId, statuette)
    mute(message, restrictionsDuration.seconds, "💥")
  }

  fun buckshot(gunfighterMessage: Message) {
    if (isRestricted(gunfighterMessage)) {
      return
    }
    markAsTemp(gunfighterMessage.messageId)
    val targetsCount = nextInt(1, recentMessages.size)
    val emoji = setOf("💥", "🗯️")
    for (t in 1..targetsCount) {
      val target = recentMessages.random()
      val restrictionsDurationSec = nextLong(45, restrictionsDuration.seconds + 1)
      mute(target, restrictionsDurationSec, emoji.random())
    }
  }

  fun shoot(gunfighterMessage: Message) {
    if (isRestricted(gunfighterMessage)) {
      return
    }
    markAsTemp(gunfighterMessage.messageId)
    val target = gunfighterMessage.replyToMessage ?: return
    mute(target, restrictionsDuration.seconds, "💥")
  }

  private fun processRestriction(userId: Long, epochSecond: Long) {
    if (isLifetimeExceeded(epochSecond)) {
      usersToRestrictions.remove(userId)
    }
  }

  private fun tryRemoveMessage(messageId: Long, epochSecond: Long) {
    if (isLifetimeExceeded(epochSecond)) {
      bot.deleteMessage(chatId, messageId)
      messagesToLifetimes.remove(messageId)
    }
  }

  private fun isRestricted(message: Message): Boolean {
    val userId = message.from?.id ?: return false
    val epochSecond = usersToRestrictions[userId]
    return epochSecond != null && !isLifetimeExceeded(epochSecond)
  }

  private fun mute(target: Message, restrictionsDurationSec: Long, emoji: String) {
    val userId = target.from?.id ?: return
    val untilEpochSecond = now().epochSecond + restrictionsDurationSec
    bot.restrictChatMember(chatId, userId, restrictions, untilEpochSecond)
    restrictionsExecutor.execute {
      usersToRestrictions.compute(userId) { _, previousRestriction ->
        when (previousRestriction == null || untilEpochSecond > previousRestriction) {
          true -> untilEpochSecond
          else -> previousRestriction
        }
      }
    }
    reply(target, emoji, true)
  }

  private fun isLifetimeExceeded(epochSecond: Long): Boolean {
    return epochSecond < now().epochSecond
  }

  private fun reply(to: Message, emoji: String, isTemp: Boolean = false): Long {
    val message = bot.sendMessage(chatId, emoji, replyToMessageId = to.messageId)
    val messageId = message.get().messageId
    if(isTemp) {
      markAsTemp(messageId)
    }
    return messageId
  }

  private fun markAsTemp(messageId: Long) {
    messagesToLifetimes[messageId] = now().epochSecond + tempMessagesLifetime.seconds
  }

  override fun close() {
    restrictionsExecutor.close()
  }
}