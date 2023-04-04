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
  private val latestMessages = synchronizedQueue(CircularFifoQueue<Message>(7))

  fun cleanTempMessages() {
    val tempMessagesCount = messagesToLifetimes.mappingCount()
    messagesToLifetimes.forEach(tempMessagesCount, ::tryRemoveMessage)
  }

  private fun tryRemoveMessage(messageId: Long, epochSecond: Long) {
    if (isLifetimeExceeded(epochSecond)) {
      bot.deleteMessage(chatId, messageId)
      messagesToLifetimes.remove(messageId)
    }
  }

  fun processRestrictions() {
    restrictionsExecutor.submit {
      val restrictedUsersCount = usersToRestrictions.mappingCount()
      usersToRestrictions.forEach(restrictedUsersCount, ::processRestriction)
    }.get()
  }

  private fun processRestriction(userId: Long, epochSecond: Long) {
    if (isLifetimeExceeded(epochSecond)) {
      usersToRestrictions.remove(userId)
    }
  }

  fun process(message: Message) {
    val userId = message.from?.id ?: return
    if (isRestricted(userId)) {
      val messageId = message.messageId
      bot.deleteMessage(chatId, messageId)
      return
    }
    latestMessages += message
    val text = message.text ?: return
    val maxCommandLength = 9
    when (text.take(maxCommandLength).lowercase()) {
      "/shoot" -> shoot(message)
      "/buckshot" -> buckshot(message)
      "/statuette" -> statuette(message)
    }
  }

  private fun shoot(gunfighterMessage: Message) {
    markAsTemp(gunfighterMessage.messageId)
    val target = gunfighterMessage.replyToMessage ?: return
    mute(target, restrictionsDurationSec, "💥")
  }

  private fun statuette(gunfighterMessage: Message) {
    markAsTemp(gunfighterMessage.messageId)
    val target = latestMessages.random()
    mute(target, restrictionsDurationSec, "🗿")
  }

  private fun buckshot(gunfighterMessage: Message) {
    markAsTemp(gunfighterMessage.messageId)
    val targetsCount = nextInt(1, latestMessages.size)
    for (t in 1..targetsCount) {
      val target = latestMessages.random()
      val restrictionsDurationSec = nextInt(45, restrictionsDurationSec + 1)
      val emoji = setOf("💥", "🗯️", "💨").random()
      mute(target, restrictionsDurationSec, emoji)
    }
  }

  private fun mute(target: Message, restrictionsDurationSec: Int, emoji: String) {
    val userId = target.from?.id ?: return
    val untilEpochSecond = now().epochSecond + restrictionsDurationSec
    bot.restrictChatMember(chatId, userId, restrictions, untilEpochSecond)
    restrictionsExecutor.execute {
      usersToRestrictions[userId] = untilEpochSecond
    }
    showAnimation(target.messageId, emoji)
  }

  private fun isRestricted(userId: Long): Boolean {
    val epochSecond = usersToRestrictions[userId]
    return epochSecond != null && !isLifetimeExceeded(epochSecond)
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