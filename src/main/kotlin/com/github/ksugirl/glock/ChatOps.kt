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
import java.time.LocalTime
import java.time.ZoneId
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
  private val tempMessagesLifetime: Duration,
  private val healingConstant: Long,
  private val healingTimeZone: ZoneId
) : Closeable {

  private val restrictionsExecutor = newSingleThreadExecutor()
  private val usersToRestrictions = ConcurrentHashMap<Long, Long>()
  private val messagesToLifetimes = ConcurrentHashMap<Long, Long>()
  private val recentMessages = synchronizedQueue(CircularFifoQueue<Message>(10))
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
  
  fun heal(healerMessage: Message, args: List<String>) {
    if(isRestricted(healerMessage)) {
      return
    }
    markAsTemp(healerMessage)
    val target = healerMessage.replyToMessage ?: return
    val targetId = target.from?.id ?: return
    val magicCode = extractMagicCode(args) ?: return
    if(!isHealingCode(magicCode)) {
      return
    }
    bot.restrictChatMember(chatId, targetId, ChatPermissions(
      canSendMessages = true,
      canSendMediaMessages = true,
      canSendPolls = true,
      canSendOtherMessages = true,
      canAddWebPagePreviews = true,
      canChangeInfo = true,
      canInviteUsers = true,
      canPinMessages = true
    ))
    restrictionsExecutor.execute {
      usersToRestrictions.remove(targetId)
    }
    val emoji = setOf("💊", "💉", "🚑")
    reply(target, emoji.random(), true)
  }

  private fun extractMagicCode(args: List<String>): Long? {
    return args.singleOrNull()?.toLong()
  }

  private fun isHealingCode(code: Long): Boolean {
    val time = LocalTime.now(healingTimeZone)
    val hour = time.hour
    val minute = time.minute
    val verification = "${hour}${minute}".toLong() * healingConstant
    return code == verification
  }

  fun statuette(gunfighterMessage: Message) {
    if (isRestricted(gunfighterMessage)) {
      return
    }
    statuettes += reply(gunfighterMessage, "🗿")
    markAsTemp(gunfighterMessage)
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
    markAsTemp(gunfighterMessage)
    val targetsCount = nextInt(1, recentMessages.size)
    val emoji = setOf("💥", "🗯️", "⚡️")
    for (t in 1..targetsCount) {
      val target = recentMessages.random()
      val restrictionsDurationSec = nextLong(45, restrictionsDuration.seconds * 2 + 1)
      mute(target, restrictionsDurationSec, emoji.random())
    }
  }

  fun shoot(gunfighterMessage: Message) {
    if (isRestricted(gunfighterMessage)) {
      return
    }
    markAsTemp(gunfighterMessage)
    val target = gunfighterMessage.replyToMessage ?: return
    if(isTopic(target)) {
      return
    }
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

  private fun isTopic(message: Message): Boolean {
    return message.authorSignature != null
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
    markAsTemp(target)
  }

  private fun isLifetimeExceeded(epochSecond: Long): Boolean {
    return epochSecond < now().epochSecond
  }

  private fun reply(to: Message, emoji: String, isTemp: Boolean = false): Long {
    val message = bot.sendMessage(chatId, emoji, replyToMessageId = to.messageId).get()
    if(isTemp) {
      markAsTemp(message)
    }
    return message.messageId
  }

  private fun markAsTemp(message: Message) {
    messagesToLifetimes[message.messageId] = now().epochSecond + tempMessagesLifetime.seconds
  }

  override fun close() {
    restrictionsExecutor.close()
  }
}