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
  private val latestMessages = synchronizedQueue(CircularFifoQueue<Message>(10))

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
  }

  fun shoot(gunfighterMessage: Message) {
    val gunfighterId = gunfighterMessage.from?.id ?: return
    if (isRestricted(gunfighterId)) {
      return
    }
    markAsTemp(gunfighterMessage.messageId)
    val attackedMessage = gunfighterMessage.replyToMessage ?: return
    muteTarget(attackedMessage)
  }

  fun shot(gunfighterMessage: Message) {
    val gunfighterId = gunfighterMessage.from?.id ?: return
    if (isRestricted(gunfighterId)) {
      return
    }
    markAsTemp(gunfighterMessage.messageId)
    muteRandomsExclude(gunfighterId)
  }

  private fun muteRandomsExclude(gunfighterId: Long) {
    latestMessages
      .filter(exclude(gunfighterId))
      .take(nextInt(1, latestMessages.size))
      .forEach(::muteTarget)
  }

  private fun exclude(userId: Long): (Message) -> Boolean {
    return { it.from?.id != userId }
  }

  private fun muteTarget(message: Message) {
    val attackedId = message.from?.id ?: return
    restrictUser(attackedId)
    showAnimation(message.messageId)
  }

  private fun restrictUser(userId: Long) {
    val untilEpochSecond = now().epochSecond + restrictionsDurationSec
    bot.restrictChatMember(chatId, userId, restrictions, untilEpochSecond)
    restrictionsExecutor.execute {
      usersToRestrictions[userId] = untilEpochSecond
    }
  }

  private fun isRestricted(userId: Long): Boolean {
    val epochSecond = usersToRestrictions[userId]
    return epochSecond != null && !isLifetimeExceeded(epochSecond)
  }

  private fun isLifetimeExceeded(epochSecond: Long): Boolean {
    return epochSecond < now().epochSecond
  }

  private fun showAnimation(replyToId: Long) {
    val message = bot.sendMessage(chatId, "💥", replyToMessageId = replyToId)
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