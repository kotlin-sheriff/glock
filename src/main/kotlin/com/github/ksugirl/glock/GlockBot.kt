package com.github.ksugirl.glock

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.MessageHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromId
import com.github.kotlintelegrambot.entities.ChatPermissions
import com.github.kotlintelegrambot.entities.Message
import java.io.Closeable
import java.lang.Thread.startVirtualThread
import java.time.Duration.ofDays
import java.util.concurrent.ConcurrentHashMap

class GlockBot(
  apiKey: String,
  private val restrictions: ChatPermissions,
  private val restrictionsDurationSec: Int,
  private val tempMessagesLifetimeSec: Int
) : Closeable {

  init {
    require(restrictionsDurationSec in 30..ofDays(366).seconds) {
      "If user is restricted for more than 366 days or less than 30 seconds from the current time," +
        " they are considered to be restricted forever"
    }
  }

  private val bot =
    bot {
      token = apiKey
      dispatch {
        command("shoot", ::processShoot)
        command("buckshot", ::processBuckshot)
        message(::processMessage)
      }
    }

  private val idToChatOps = ConcurrentHashMap<Long, ChatOps>()

  private fun processShoot(env: CommandHandlerEnvironment) {
    apply(ChatOps::processShoot, env.message)
  }

  private fun processBuckshot(env: CommandHandlerEnvironment) {
    apply(ChatOps::processBuckshot, env.message)
  }

  private fun processMessage(env: MessageHandlerEnvironment) {
    apply(ChatOps::processMessage, env.message)
  }

  fun cleanTempMessages() {
    forEachChat(ChatOps::cleanTempMessages)
  }

  fun processRestrictions() {
    forEachChat(ChatOps::processRestrictions)
  }

  fun startPollingAsync() {
    startVirtualThread(bot::startPolling)
  }

  override fun close() {
    forEachChat(ChatOps::close)
  }

  private fun apply(process: ChatOps.(Message) -> Unit, message: Message) {
    startVirtualThread {
      val chatOps = idToChatOps.computeIfAbsent(message.chat.id, ::newChatOps)
      chatOps.process(message)
    }
  }

  private fun newChatOps(chatId: Long): ChatOps {
    return ChatOps(
      bot,
      fromId(chatId),
      restrictions,
      restrictionsDurationSec,
      tempMessagesLifetimeSec
    )
  }

  private fun forEachChat(process: (ChatOps) -> Unit) {
    val chatsCount = idToChatOps.mappingCount()
    idToChatOps.forEachValue(chatsCount, process)
  }
}