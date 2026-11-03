package com.hipka.app

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hipka.app.data.local.datastore.EncryptedAuthStorage
import io.github.jan.supabase.gotrue.user.UserSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real Android Keystore test. It neither authenticates nor uses live credentials. */
@RunWith(AndroidJUnit4::class)
class EncryptedAuthStorageTest {
    private fun context(): Context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        override fun getNoBackupFilesDir(): File = File(cacheDir, "auth-storage-instrumentation").apply { mkdirs() }
    }
    @Test fun encryptedSessionSurvivesStorageRecreationAndCanBeDeleted() = runBlocking {
        val context = context()
        val session = UserSession("test-access-token", "test-refresh-token", "", "", 3600L, "bearer", null)
        val storage = EncryptedAuthStorage(context)
        try {
            storage.saveSession(session)
            val ciphertext = File(context.noBackupFilesDir, "supabase-session.enc").readBytes()
            assertFalse(String(ciphertext, Charsets.ISO_8859_1).contains("test-access-token"))
            assertFalse(String(ciphertext, Charsets.ISO_8859_1).contains("test-refresh-token"))
            val restored = EncryptedAuthStorage(context).loadSession()
            assertEquals(session.accessToken, restored?.accessToken)
            assertEquals(session.refreshToken, restored?.refreshToken)
            storage.deleteSession()
            assertNull(EncryptedAuthStorage(context).loadSession())
        } finally { storage.deleteSession() }
    }
    @Test fun tamperedCiphertextCannotRestoreAuthentication() = runBlocking {
        val context = context()
        val storage = EncryptedAuthStorage(context)
        try {
            storage.saveSession(UserSession("test-access", "test-refresh", "", "", 3600L, "bearer", null))
            val file = File(context.noBackupFilesDir, "supabase-session.enc")
            val bytes = file.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            file.writeBytes(bytes)
            assertNull(storage.loadSession())
        } finally { storage.deleteSession() }
    }
}
