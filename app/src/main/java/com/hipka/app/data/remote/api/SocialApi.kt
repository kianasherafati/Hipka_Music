package com.hipka.app.data.remote.api

import com.hipka.app.data.remote.dto.MessageDto
import com.hipka.app.data.remote.dto.UserDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import retrofit2.http.*

@Serializable
data class ProfileEdit(val name: String, val bio: String, @SerialName("avatar_url") val avatarUrl: String?)
@Serializable
data class BlockUser(@SerialName("blocker_id") val blockerId: String, @SerialName("blocked_id") val blockedId: String)

interface SocialApi {
    @GET("rest/v1/hipka_messages")
    suspend fun inbox(@Query("order") order: String = "timestamp.desc", @Query("limit") limit: Int = 200): List<MessageDto>
    @PATCH("rest/v1/hipka_profiles")
    suspend fun edit(@Query("id") id: String, @Body profile: ProfileEdit)
    @POST("rest/v1/hipka_blocks")
    suspend fun block(@Body block: BlockUser)
    @DELETE("rest/v1/hipka_blocks")
    suspend fun unblock(@Query("blocker_id") me: String, @Query("blocked_id") peer: String)
    @GET("rest/v1/hipka_profiles")
    suspend fun search(@Query("or") query: String, @Query("limit") limit: Int = 50): List<UserDto>
}
