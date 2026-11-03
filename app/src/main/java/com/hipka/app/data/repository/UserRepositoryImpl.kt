package com.hipka.app.data.repository

import com.hipka.app.data.local.datastore.SessionManager
import com.hipka.app.data.remote.api.UserApi
import com.hipka.app.data.remote.dto.PremiumUpdateDto
import com.hipka.app.data.remote.dto.UserDto
import com.hipka.app.data.remote.dto.UserFollowDto
import com.hipka.app.data.remote.dto.toDomain
import com.hipka.app.domain.model.User
import com.hipka.app.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val userApi: UserApi,
    private val supabase: io.github.jan.supabase.SupabaseClient,
    private val sessionManager: SessionManager
) : UserRepository {

    override suspend fun getUserById(id: String): User? =
        userApi.getUserById(idFilter = "eq.$id").firstOrNull()?.toDomain()

    override suspend fun getAllUsers(): List<User> =
        userApi.getAllUsers().map { it.toDomain() }

    override suspend fun searchUsersByName(query: String): List<User> =
        userApi.searchUsersByName(nameFilter = "ilike.*$query*").map { it.toDomain() }

    override suspend fun getFollowingIds(userId: String): List<String> =
        userApi.getFollowings(followerFilter = "eq.$userId").map { it.followingId }

    override suspend fun getFollowerIds(userId: String): List<String> =
        userApi.getFollowers(followingFilter = "eq.$userId").map { it.followerId }

    override suspend fun followUser(followerId: String, followingId: String) {
        userApi.followUser(UserFollowDto(followerId = followerId, followingId = followingId))
    }

    override suspend fun unfollowUser(followerId: String, followingId: String) {
        userApi.unfollowUser(
            followerFilter = "eq.$followerId",
            followingFilter = "eq.$followingId"
        )
    }

    override suspend fun setPremiumStatus(userId: String, isPremium: Boolean) {
        check(com.hipka.app.BuildConfig.LEGACY_DEMO_AUTH) { "Premium changes require a trusted backend" }
        val response = userApi.updatePremiumStatus(
            idFilter = "eq.$userId",
            body = PremiumUpdateDto(isPremium = isPremium)
        )
        if (!response.isSuccessful) throw retrofit2.HttpException(response)
    }

    // ورود با ایمیل یا نام کاربری
    override suspend fun login(emailOrUsername: String, password: String): Result<User> = authResult {
        require(emailOrUsername.contains("@")) { "AUTH_EMAIL_REQUIRED" }
        supabase.auth.signInWith(Email) { email = emailOrUsername.trim(); this.password = password }
        val id = supabase.auth.currentUserOrNull()?.id ?: error("AUTH_INVALID_CREDENTIALS")
        getUserById(id) ?: User(id = id, name = emailOrUsername.substringBefore("@"), avatarUrl = null, isPremium = false, createdAt = "")
    }
    override suspend fun register(name: String, username: String, email: String, password: String, gender: String): Result<User> = authResult {
        require(username.matches(Regex("[a-zA-Z0-9_]{3,30}"))) { "AUTH_USERNAME_INVALID" }
        require(password.length >= 8) { "AUTH_PASSWORD_SHORT" }
        supabase.auth.signUpWith(Email) {
            this.email = email.trim(); this.password = password
            data = buildJsonObject { put("name", name.trim()); put("username", username.lowercase(java.util.Locale.ROOT)) }
        }
        val id = supabase.auth.currentUserOrNull()?.id ?: error("AUTH_VERIFY_EMAIL")
        getUserById(id) ?: User(id = id, name = name, avatarUrl = null, isPremium = false, createdAt = "")
    }
    private suspend fun authResult(block: suspend () -> User): Result<User> = try {
        check(!com.hipka.app.BuildConfig.SUPABASE_KEY.startsWith("unconfigured")) { "AUTH_NOT_CONFIGURED" }
        Result.success(block())
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Result.failure(e)
    }

    override suspend fun logout() {
        sessionManager.clearCurrentUser()
    }

    override fun isLoggedIn(): Flow<Boolean> = sessionManager.isLoggedIn

    override fun getCurrentUserId(): Flow<String?> = sessionManager.currentUserId
}
