package com.hipka.app.presentation.features.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.OtpType
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PasswordResetViewModel @Inject constructor(private val client: SupabaseClient) : ViewModel() {
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    fun request(email: String) = action {
        client.auth.resetPasswordForEmail(email.trim())
        message.value = "If this account exists, a recovery email has been sent. Enter its code below."
    }
    fun reset(email: String, code: String, password: String) = action {
        require(password.length >= 8)
        client.auth.verifyEmailOtp(OtpType.Email.RECOVERY, email.trim(), code.trim())
        client.auth.updateUser { this.password = password }
        message.value = "Password updated. You can return to your profile."
    }
    private fun action(block: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try { block() } catch (e: Exception) {
                if (e is CancellationException) throw e
                message.value = "Recovery failed. Check the email and code, or request a new code."
            } finally { busy.value = false }
        }
    }
}

@Composable
fun PasswordResetScreen(vm: PasswordResetViewModel = hiltViewModel()) {
    var email by rememberSaveable { mutableStateOf("") }
    // Password and recovery code deliberately never enter saved instance state.
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Reset password", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(email, { email = it }, label = { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_49)) })
        Button(enabled = !busy && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches(), onClick = { vm.request(email) }) { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_50)) }
        OutlinedTextField(code, { code = it }, label = { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_51)) })
        OutlinedTextField(password, { password = it }, label = { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_52)) }, visualTransformation = PasswordVisualTransformation())
        Button(enabled = !busy && code.isNotBlank() && password.length >= 8, onClick = { vm.reset(email, code, password); password = ""; code = "" }) { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_53)) }
        message?.let { Text(it) }
    }
}
