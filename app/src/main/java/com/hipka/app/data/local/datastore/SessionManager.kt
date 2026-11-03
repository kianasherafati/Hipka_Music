package com.hipka.app.data.local.datastore
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class SessionManager @Inject constructor(private val client: SupabaseClient) {
    val currentUserId: Flow<String?> = client.auth.sessionStatus
        .filter { it !is SessionStatus.LoadingFromStorage }
        .map { status -> when (status) {
            is SessionStatus.Authenticated -> status.session.user?.id
            is SessionStatus.NetworkError -> client.auth.sessionManager.loadSession()?.user?.id
            else -> null
        } }.distinctUntilChanged()
    val isLoggedIn: Flow<Boolean> = currentUserId.map { it != null }
    suspend fun clearCurrentUser() { client.auth.signOut() }
}
