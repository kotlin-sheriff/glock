package com.github.ksugirl.glock

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ChatPermissions
import com.github.kotlintelegrambot.entities.Message
import org.apache.commons.collections4.QueueUtils.synchronizedQueue
import org.apache.commons.collections4.queue.CircularFifoQueue
import java.io.Closeable
import java.time.Instant.now
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random.Default.nextInt

/**
 * Class for sequential, secure, atomic operations in a single chat
 */
class ChatOps(
  private val bot: Bot,
  private val chatId: ChatId,
  private val restrictions: ChatPermissions,
  private val restrictionsDurationSec: Int,
  private val tempMessagesLifetimeSec: Int
) : Closeable {

  private val restrictionsExecutor = newSingleThreadExecutor()
  private val usersToRestrictions = ConcurrentHashMap<Long, Long>()
  private val messagesToLifetimes = ConcurrentHashMap<Long, Long>()
  private val recentMessages = synchronizedQueue(CircularFifoQueue<Message>(7))
  private val statuettes = AtomicInteger()

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
    markAsTemp(gunfighterMessage.messageId)
    statuettes.incrementAndGet()
    showAnimation(gunfighterMessage.messageId, "🗿")
  }

  fun tryProcessStatuette(message: Message) {
    if (isRestricted(message)) {
      return
    }
    val statuettesCount = statuettes.get()
    if (statuettesCount > 0 && statuettes.compareAndSet(statuettesCount, statuettesCount - 1)) {
      mute(message, restrictionsDurationSec, "💥")
    }
  }

  fun buckshot(gunfighterMessage: Message) {
    if (isRestricted(gunfighterMessage)) {
      return
    }
    markAsTemp(gunfighterMessage.messageId)
    val targetsCount = nextInt(1, recentMessages.size)
    val emoji = setOf("💥", "🗯️", "💨")
    for (t in 1..targetsCount) {
      val target = recentMessages.random()
      val restrictionsDurationSec = nextInt(45, restrictionsDurationSec + 1)
      mute(target, restrictionsDurationSec, emoji.random())
    }
  }

  fun shoot(gunfighterMessage: Message) {
    if (isRestricted(gunfighterMessage)) {
      return
    }
    markAsTemp(gunfighterMessage.messageId)
    val target = gunfighterMessage.replyToMessage ?: return
    mute(target, restrictionsDurationSec, "💥")
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

  private fun mute(target: Message, restrictionsDurationSec: Int, emoji: String) {
    val userId = target.from?.id ?: return
    val untilEpochSecond = now().epochSecond + restrictionsDurationSec
    bot.restrictChatMember(chatId, userId, restrictions, untilEpochSecond)
    restrictionsExecutor.execute {
      usersToRestrictions.compute(userId) { _, previousRestriction ->
        when(previousRestriction == null || untilEpochSecond > previousRestriction) {
          true -> untilEpochSecond
          else -> previousRestriction
        }
      }
    }
    showAnimation(target.messageId, emoji)
  }

  private fun isLifetimeExceeded(epochSecond: Long): Boolean {
    return epochSecond < now().epochSecond
  }

  private fun showAnimation(replyToId: Long, emoji: String) {
    val message = bot.sendMessage(chatId, emoji, replyToMessageId = replyToId)
    val messageId = message.get().messageId
    markAsTemp(messageId)
  }

  private fun markAsTemp(messageId: Long) {
    messagesToLifetimes[messageId] = now().epochSecond + tempMessagesLifetimeSec
  }

  override fun close() {
    restrictionsExecutor.close()
  }
}