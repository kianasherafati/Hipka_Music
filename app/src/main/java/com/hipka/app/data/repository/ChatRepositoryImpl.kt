package com.hipka.app.data.repository

import android.util.Log
import com.hipka.app.data.local.dao.MessageDao
import com.hipka.app.data.local.entity.OfflineMessageEntity
import com.hipka.app.data.remote.api.MessageApi
import com.hipka.app.data.remote.dto.MessageDto
import com.hipka.app.data.remote.dto.toDomain
import com.hipka.app.domain.model.Message
import com.hipka.app.domain.model.MessageStatus
import com.hipka.app.domain.repository.ChatRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.presenceChangeFlow
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.util.UUID
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val messageApi: MessageApi,
    private val messageDao: MessageDao,
    private val supabaseClient: SupabaseClient
) : ChatRepository {

    @Serializable
    private data class TypingPayload(val senderId: String, val isTyping: Boolean)

    private val localStatusUpdatesFlow = MutableSharedFlow<Message>(extraBufferCapacity = 64)

    private val jsonFormatter = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private var activeTypingChannel: RealtimeChannel? = null
    private var activeTypingChannelName: String? = null

    private companion object {
        const val TYPING_EVENT = "typing"
    }

    override suspend fun getConversationHistory(myUserId: String, peerUserId: String): List<Message> {
        val localBeforeSync = messageDao.getConversationOnce(myUserId, peerUserId).associateBy { it.id }

        try {
            val sentByMe = messageApi.getConversation(orQuery = "(and(sender_id.eq.$myUserId,receiver_id.eq.$peerUserId))")
            val sentByPeer = messageApi.getConversation(orQuery = "(and(sender_id.eq.$peerUserId,receiver_id.eq.$myUserId))")
            val remoteMessages = sentByMe + sentByPeer

            // ✨ محافظت قطعی از دیتابیس لوکال: جلوگیری از دان‌گرید شدن وضعیت (از تیک به ساعت) توسط دیتای قدیمی سرور
            val safeToInsert = remoteMessages.map { remote ->
                val local = localBeforeSync[remote.id]
                if (local != null) {
                    val rStatus = MessageStatus.fromRaw(remote.status)
                    val lStatus = MessageStatus.fromRaw(local.status)

                    val finalStatus = when {
                        lStatus == MessageStatus.READ || rStatus == MessageStatus.READ -> MessageStatus.READ.name
                        lStatus == MessageStatus.SENT || rStatus == MessageStatus.SENT -> MessageStatus.SENT.name
                        else -> MessageStatus.SENDING.name
                    }
                    remote.toEntity().copy(status = finalStatus)
                } else {
                    remote.toEntity()
                }
            }
            messageDao.insertMessages(safeToInsert)

            // ✨ موتور Auto-Resend: پیدا کردن پیام‌های گیرکرده در حالت آفلاین و پوش کردن به سرور
            val stuckMessages = localBeforeSync.values.filter { it.status == MessageStatus.SENDING.name && it.senderId == myUserId }
            stuckMessages.forEach { stuck ->
                try {
                    val dto = MessageDto(
                        id = stuck.id, senderId = stuck.senderId, receiverId = stuck.receiverId,
                        text = stuck.text, status = MessageStatus.SENT.name,
                        sharedSongId = stuck.sharedSongId, timestamp = stuck.timestamp
                    )
                    messageApi.sendMessage(dto)
                    messageDao.updateStatus(stuck.id, MessageStatus.SENT.name)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e("ChatRepo", "Auto-resend failed for ${stuck.id}")
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (localBeforeSync.isEmpty()) throw e
            Log.e("ChatRepo", "Offline mode active, returning local database data.")
        }

        // ✨ همیشه پیام‌ها را از دیتابیس لوکال می‌خوانیم تا هیچ پیام آفلاینی غیب نشود
        return messageDao.getConversationOnce(myUserId, peerUserId)
            .map { it.toDomain() }
            .sortedWith(compareBy<Message> { it.timestamp }.thenBy { it.id })
            .takeLast(50)
    }

    override suspend fun loadOlderMessages(myUserId: String, peerUserId: String, before: Message): List<Message> {
        val cursor = "or(timestamp.lt.${before.timestamp},and(timestamp.eq.${before.timestamp},id.lt.${before.id}))"
        val query = "(and(sender_id.eq.$myUserId,receiver_id.eq.$peerUserId,$cursor),and(sender_id.eq.$peerUserId,receiver_id.eq.$myUserId,$cursor))"
        val messages = messageApi.getConversation(orQuery = query)
        messageDao.insertMessages(messages.map { it.toEntity() })
        return messages.map { it.toDomain() }
    }

    override suspend fun sendMessage(
        id: String,
        myUserId: String,
        receiverId: String,
        text: String,
        sharedSongId: String?
    ): Message {
        // ۱. ابتدا پیام را با حالت قطعی SENDING در دیتابیس لوکال ذخیره می‌کنیم
        val localDto = MessageDto(
            id = id, senderId = myUserId, receiverId = receiverId,
            text = text.ifBlank { " " }, status = MessageStatus.SENDING.name,
            sharedSongId = sharedSongId?.ifBlank { null }, timestamp = System.currentTimeMillis()
        )
        messageDao.insertMessage(localDto.toEntity())

        // ۲. تلاش می‌کنیم نسخه SENT را به سرور بفرستیم
        val serverDto = localDto.copy(status = MessageStatus.SENT.name)
        try {
            val delivered = messageApi.sendMessage(serverDto).firstOrNull() ?: serverDto
            messageDao.insertMessage(delivered.toEntity())
            // ۳. اگر موفق بود، لوکال را یک تیک (SENT) می‌کنیم
            messageDao.updateStatus(id, MessageStatus.SENT.name)
            return delivered.toDomain()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // ✨ ۴. اگر نت قطع بود، ارور می‌دهد، پیام در دیتابیس همان SENDING می‌ماند
            Log.e("ChatRepo", "Network error on sendMessage: stays SENDING")
            throw e
        }
    }

    override fun observeIncomingMessages(myUserId: String): Flow<Message> = callbackFlow {
        val channel = supabaseClient.channel("messages-inbox-$myUserId")
        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "hipka_messages"
            filter = "receiver_id=eq.$myUserId"
        }
        val collectJob = launch {
            changes.collect { action ->
                com.hipka.app.core.util.coroutineResult {
                    val dto = action.decodeRecord<MessageDto>()
                    messageDao.insertMessage(dto.toEntity())
                    trySend(dto.toDomain())
                }.onFailure { Log.e("ChatRepo", "Error parsing incoming message.") }
            }
        }
        channel.subscribe()
        awaitClose {
            collectJob.cancel()
            CoroutineScope(Dispatchers.IO).launch { com.hipka.app.core.util.coroutineResult { channel.unsubscribe() } }
        }
    }

    override fun observeSentMessageStatusUpdates(myUserId: String): Flow<Message> = callbackFlow {
        val channel = supabaseClient.channel("messages-sent-status-$myUserId")
        val changes = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "hipka_messages"
            filter = "sender_id=eq.$myUserId"
        }
        val localJob = launch {
            localStatusUpdatesFlow.filter { it.senderId == myUserId }.collect { trySend(it) }
        }
        val collectJob = launch {
            changes.collect { action ->
                com.hipka.app.core.util.coroutineResult {
                    val recordJson = action.record
                    val msgId = recordJson["id"]?.toString()?.replace("\"", "")
                    val statusStr = recordJson["status"]?.toString()?.replace("\"", "") ?: "READ"
                    if (!msgId.isNullOrEmpty()) {
                        messageDao.updateStatus(msgId, statusStr)
                        val updatedEntity = messageDao.getMessageById(msgId)
                        if (updatedEntity != null) {
                            trySend(updatedEntity.toDomain())
                        }
                    }
                }.onFailure {
                    com.hipka.app.core.util.coroutineResult {
                        val fullDto = action.decodeRecord<MessageDto>()
                        messageDao.updateStatus(fullDto.id, fullDto.status)
                        trySend(fullDto.toDomain())
                    }
                }
            }
        }
        channel.subscribe()
        awaitClose {
            localJob.cancel()
            collectJob.cancel()
            CoroutineScope(Dispatchers.IO).launch { com.hipka.app.core.util.coroutineResult { channel.unsubscribe() } }
        }
    }

    override suspend fun markConversationAsRead(myUserId: String, peerUserId: String) {
        messageDao.updateStatusForConversation(senderId = peerUserId, receiverId = myUserId, status = MessageStatus.READ.name)
        com.hipka.app.core.util.coroutineResult { messageApi.markConversationAsRead(senderIdFilter = "eq.$peerUserId", receiverIdFilter = "eq.$myUserId") }
        val conversation = messageDao.getConversationOnce(myUserId, peerUserId)
        conversation.filter { it.senderId == peerUserId && it.status == MessageStatus.READ.name }
            .forEach { entity -> localStatusUpdatesFlow.emit(entity.toDomain()) }
    }

    override fun observePresence(myUserId: String, peerUserId: String): Flow<Boolean> = callbackFlow {
        val name = "hipka-presence:" + listOf(myUserId, peerUserId).sorted().joinToString(":")
        val channel = supabaseClient.channel(name) { isPrivate = true; presence { key = myUserId } }
        val presenceJob = launch {
            channel.presenceChangeFlow().collect { event ->
                if (peerUserId in event.joins) trySend(true)
                if (peerUserId in event.leaves) trySend(false)
            }
        }
        val statusJob = launch {
            channel.status.collect { status ->
                if (status == RealtimeChannel.Status.SUBSCRIBED) channel.track(buildJsonObject { put("active", true) })
                else trySend(false)
            }
        }
        channel.subscribe()
        awaitClose {
            presenceJob.cancel(); statusJob.cancel()
            CoroutineScope(Dispatchers.IO).launch { com.hipka.app.core.util.coroutineResult { channel.unsubscribe() } }
        }
    }

    override fun observeTypingStatus(myUserId: String, peerUserId: String): Flow<Boolean> = callbackFlow {
        val chName = typingChannelName(myUserId, peerUserId)
        val channel = getOrCreateTypingChannel(chName)
        val collectJob = launch {
            channel.broadcastFlow<TypingPayload>(event = TYPING_EVENT)
                .filter { it.senderId == peerUserId }
                .map { it.isTyping }
                .collectLatest { isTyping ->
                    trySend(isTyping)
                    if (isTyping) { delay(4000); trySend(false) }
                }
        }
        awaitClose {
            collectJob.cancel()
            activeTypingChannel = null
            activeTypingChannelName = null
            CoroutineScope(Dispatchers.IO).launch { com.hipka.app.core.util.coroutineResult { channel.unsubscribe() } }
        }
    }

    override suspend fun sendTypingStatus(myUserId: String, peerUserId: String, isTyping: Boolean) {
        com.hipka.app.core.util.coroutineResult {
            val chName = typingChannelName(myUserId, peerUserId)
            val channel = getOrCreateTypingChannel(chName)
            val payload = TypingPayload(senderId = myUserId, isTyping = isTyping)
            val jsonPayload = jsonFormatter.encodeToJsonElement(payload).jsonObject
            channel.broadcast(event = TYPING_EVENT, message = jsonPayload)
        }
    }

    private suspend fun getOrCreateTypingChannel(chName: String): RealtimeChannel {
        if (activeTypingChannelName == chName && activeTypingChannel != null) {
            return activeTypingChannel!!
        }
        activeTypingChannel?.unsubscribe()
        val channel = supabaseClient.channel(chName) { isPrivate = true }
        channel.subscribe()
        activeTypingChannel = channel
        activeTypingChannelName = chName
        return channel
    }

    private fun typingChannelName(userIdA: String, userIdB: String): String = "hipka:" + listOf(userIdA, userIdB).sorted().joinToString(":")

    private fun MessageDto.toEntity() = OfflineMessageEntity(id = id, senderId = senderId, receiverId = receiverId, text = text, timestamp = timestamp, status = status, sharedSongId = sharedSongId)
    private fun OfflineMessageEntity.toDomain() = Message(id = id, senderId = senderId, receiverId = receiverId, text = text, status = MessageStatus.fromRaw(status), sharedSongId = sharedSongId, timestamp = timestamp)
}
