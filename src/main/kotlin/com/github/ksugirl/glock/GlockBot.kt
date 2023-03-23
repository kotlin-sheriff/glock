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
import java.util.concurrent.ConcurrentHashMap

class GlockBot(
  apiKey: String,
  private val restrictions: ChatPermissions,
  private val restrictionsDurationSec: Int,
  private val tempMessagesLifetimeSec: Int
) : Closeable {

  private val bot =
    bot {
      token = apiKey
      dispatch {
        command("shoot", ::shoot)
        message(::filter)
      }
    }

  private val idToChatOps = ConcurrentHashMap<Long, ChatOps>()

  private fun shoot(env: CommandHandlerEnvironment) {
    env.message.forward(ChatOps::shoot)
  }

  private fun filter(env: MessageHandlerEnvironment) {
    env.message.forward(ChatOps::filter)
  }

  fun cleanTempMessages() {
    forEachChat(ChatOps::cleanTempMessages)
  }

  fun removeRestrictions() {
    forEachChat(ChatOps::removeRestrictions)
  }

  fun startPollingAsync() {
    startVirtualThread(bot::startPolling)
  }

  fun countChats(): Long {
    return idToChatOps.mappingCount()
  }

  override fun close() {
    forEachChat(ChatOps::close)
  }

  private fun Message.forward(process: ChatOps.(Message) -> Unit) {
    startVirtualThread {
      idToChatOps
        .computeIfAbsent(chat.id, ::newChatOps)
        .process(this)
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

  private fun forEachChat(f: (ChatOps) -> Unit) {
    idToChatOps.forEachValue(countChats(), f)
  }
}