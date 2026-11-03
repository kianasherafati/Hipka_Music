package com.hipka.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.hipka.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.http.*
import java.io.ByteArrayOutputStream
import javax.inject.Inject

interface AvatarStorageApi {
    @Headers("x-upsert: true")
    @POST("storage/v1/object/hipka-avatars/{path}")
    suspend fun upload(@Path(value = "path", encoded = true) path: String, @Body image: RequestBody)
}

class AvatarUploader @Inject constructor(@ApplicationContext private val context: Context, retrofit: Retrofit) {
    private val api = retrofit.create(AvatarStorageApi::class.java)
    suspend fun upload(owner: String, uri: Uri): String = withContext(Dispatchers.IO) {
        require(uri.scheme == "content")
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 10 * 1024 * 1024) { "Image too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("Cannot open image")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 40_000_000)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Unsupported image")
        // Re-encoding drops location/EXIF metadata and bounds the uploaded image.
        val image = try { ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it); it.toByteArray() } }
            finally { bitmap.recycle() }
        require(image.size <= 2 * 1024 * 1024)
        api.upload("$owner/avatar.jpg", image.toRequestBody("image/jpeg".toMediaType()))
        "${BuildConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/public/hipka-avatars/$owner/avatar.jpg?v=${System.currentTimeMillis()}"
    }
}
