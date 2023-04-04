package com.github.ksugirl.glock

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.handlers.HandleCommand
import com.github.kotlintelegrambot.dispatcher.handlers.HandleMessage
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromId
import com.github.kotlintelegrambot.entities.ChatPermissions
import com.github.kotlintelegrambot.entities.Message
import java.io.Closeable
import java.lang.Thread.startVirtualThread
import java.time.Duration
import java.time.Duration.ofDays
import java.time.Duration.ofSeconds
import java.util.concurrent.ConcurrentHashMap

class GlockBot(
  apiKey: String,
  private val restrictions: ChatPermissions,
  private val restrictionsDuration: Duration,
  private val tempMessagesLifetime: Duration
) : Closeable {

  init {
    require(restrictionsDuration in ofSeconds(30)..ofDays(366)) {
      "If user is restricted for more than 366 days or less than 30 seconds from the current time," +
        " they are considered to be restricted forever"
    }
  }

  private val bot =
    bot {
      token = apiKey
      dispatch {
        command("shoot", handleCommand(ChatOps::shoot))
        command("buckshot", handleCommand(ChatOps::buckshot))
        message(handleMessage(ChatOps::tryProcessStatuette))
        command("statuette", handleCommand(ChatOps::statuette))
        message(handleMessage(ChatOps::filterMessage))
      }
    }

  private val idToChatOps = ConcurrentHashMap<Long, ChatOps>()

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

  private fun getChatOps(chatId: Long): ChatOps {
    return idToChatOps.computeIfAbsent(chatId, ::newChatOps)
  }

  private fun newChatOps(chatId: Long): ChatOps {
    return ChatOps(
      bot,
      fromId(chatId),
      restrictions,
      restrictionsDuration,
      tempMessagesLifetime
    )
  }

  private fun forEachChat(process: (ChatOps) -> Unit) {
    val chatsCount = idToChatOps.mappingCount()
    idToChatOps.forEachValue(chatsCount, process)
  }

  private fun handleMessage(method: ChatOps.(Message) -> Unit): HandleMessage {
    return {
      startVirtualThread(method, message)
    }
  }

  private fun handleCommand(method: ChatOps.(Message) -> Unit): HandleCommand {
    return {
      startVirtualThread(method, message)
    }
  }

  private fun startVirtualThread(method: ChatOps.(Message) -> Unit, message: Message) {
    startVirtualThread {
      getChatOps(message.chat.id).method(message)
    }
  }
}