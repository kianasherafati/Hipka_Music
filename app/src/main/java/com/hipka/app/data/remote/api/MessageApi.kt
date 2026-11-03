package com.hipka.app.data.remote.api

import com.hipka.app.data.remote.dto.MessageDto
import com.hipka.app.data.remote.dto.MessageStatusUpdateDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

interface MessageApi {

    @GET("rest/v1/hipka_messages")
    suspend fun getConversation(
        // pass as "(and(sender_id.eq.A,receiver_id.eq.B),and(sender_id.eq.B,receiver_id.eq.A))"
        @Query("or") orQuery: String,
        @Query("order") order: String = "timestamp.desc,id.desc",
        @Query("limit") limit: Int = 50,
        @Query("select") select: String = "*"
    ): List<MessageDto>

    @Headers("Prefer: resolution=ignore-duplicates,return=representation")
    @POST("rest/v1/hipka_messages?on_conflict=id")
    suspend fun sendMessage(@Body message: MessageDto): List<MessageDto>

    // Bulk "mark as read" for an entire conversation direction — everything
    // the peer sent me that isn't already READ.
    @PATCH("rest/v1/hipka_messages")
    suspend fun markConversationAsRead(
        @Query("sender_id") senderIdFilter: String, // "eq.<peerId>"
        @Query("receiver_id") receiverIdFilter: String, // "eq.<myId>"
        @Query("status") notYetReadFilter: String = "neq.READ",
        @Body body: MessageStatusUpdateDto = MessageStatusUpdateDto(status = "READ")
    ): Response<Unit>
}