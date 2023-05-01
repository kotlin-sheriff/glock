package com.github.ksugirl.glock

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.entities.ChatId.Companion.fromId
import com.google.common.primitives.Longs.tryParse
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.System.getenv

/*
 * You need provide BOT_TOKEN and CHANNEL_NAME environment variables for IT test
 */
class KseniaStorageIT {

  @Serializable
  data class People(
    val name: String,
    val address: String,
    val bankIdToMoney: Map<Long, Long>
  )

  private lateinit var storage: KseniaStorage

  @BeforeEach
  fun openChannelStorage() {
    val bot = bot { token = getenv("TELEGRAM_API_TOKEN") }
    val channelId = fromId(tryParse(getenv("TEST_CHANNEL_ID"))!!)
    storage = KseniaStorage(bot, channelId)
  }

  @AfterEach
  fun closeChannelStorage() {
    storage.close()
  }

  @Test
  fun testSave() {
    storage["id"] = People("Elon Musk", "Texas", mapOf(1L to 100L))
  }

  @Test
  fun testDownload() {
    assertThat(storage.get<People>("id"))
      .isEqualTo(People("Elon Musk", "Texas", mapOf(1L to 100L)))
  }
}