package com.banter.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.net.toUri
import com.banter.app.domain.InstalledModel
import com.banter.app.domain.ModelRepository
import com.banter.app.domain.Transfer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** The one remote call this app makes: fetching a model file, optionally with a read token. */
interface HuggingFaceApi {
    @Streaming
    @GET
    suspend fun download(
        @Url url: String,
        @Header("Authorization") authorization: String?,
    ): Response<ResponseBody>
}

/** Where the model file lives, how it gets there, and whether there is room for it. */
@Singleton
class ModelRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: HuggingFaceApi,
) : ModelRepository {

    private val dir = File(context.filesDir, "models").apply { mkdirs() }

    /** Banter keeps at most one model. */
    override fun installed(): InstalledModel? =
        dir.listFiles { f -> f.isFile && f.extension == "litertlm" }
            ?.maxByOrNull { it.length() }
            ?.let { InstalledModel(name = it.name, bytes = it.length(), path = it.absolutePath) }

    override fun freeBytes(): Long = context.filesDir.usableSpace

    override fun isUnmetered(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    override suspend fun delete(): Boolean = withContext(Dispatchers.IO) {
        installed()?.let { File(it.path).delete() } ?: false
    }

    /**
     * The path that actually works for most people: the Gemma files are licence-gated, so they
     * are usually downloaded on a laptop and moved onto the phone.
     */
    override fun import(uri: String, displayName: String): Flow<Transfer> = flow {
        val parsed = uri.toUri()
        val total = runCatching {
            context.contentResolver.openAssetFileDescriptor(parsed, "r")?.use { it.length }
        }.getOrNull()?.takeIf { it > 0 } ?: -1L

        val input = context.contentResolver.openInputStream(parsed)
            ?: error("Could not open the selected file")
        input.use { copy(it, total, safeName(displayName)) }
    }.flowOn(Dispatchers.IO)

    override fun download(url: String, token: String?): Flow<Transfer> = flow {
        val auth = token?.trim()?.takeIf { it.isNotEmpty() }?.let { "Bearer $it" }
        val response = api.download(url, auth)
        val code = response.code()
        if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
            error(
                "The model is licence-gated ($code). Accept the licence on Hugging Face and " +
                    "paste a read token, or import the file instead.",
            )
        }
        val body = response.body()?.takeIf { response.isSuccessful }
            ?: error("Download failed with HTTP $code")

        val name = safeName(url.substringAfterLast('/').substringBefore('?'))
        body.use { copy(it.byteStream(), it.contentLength(), name) }
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<Transfer>.copy(input: InputStream, total: Long, name: String) {
        if (total > 0 && total + SAFETY_MARGIN > freeBytes()) {
            error("Not enough free space: ${total / MB} MB needed, ${freeBytes() / MB} MB available.")
        }

        // Write beside the real name, then rename, so a cancelled transfer never looks installed.
        val partial = File(dir, "$name.part")
        val target = File(dir, name)
        partial.delete()

        var copied = 0L
        var lastEmit = 0L
        partial.outputStream().use { output ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                copied += read
                // Emitting on every 64 KB chunk would swamp the UI; once per 2 MB is plenty.
                if (copied - lastEmit >= EMIT_EVERY) {
                    lastEmit = copied
                    emit(Transfer.Running(copied, total))
                }
            }
        }

        if (total > 0 && copied != total) {
            partial.delete()
            error("Transfer ended early: got ${copied / MB} MB of ${total / MB} MB")
        }

        installed()?.let { File(it.path).delete() }
        if (!partial.renameTo(target)) {
            partial.delete()
            error("Could not save the model file")
        }
        emit(Transfer.Done(InstalledModel(target.name, target.length(), target.absolutePath)))
    }

    private fun safeName(raw: String): String {
        val cleaned = raw.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (cleaned.endsWith(".litertlm")) cleaned else "$cleaned.litertlm"
    }

    private companion object {
        const val MB = 1L shl 20

        /** Leave room for the OS; filling the data partition is a bad way to end a download. */
        const val SAFETY_MARGIN = 256L * MB
        const val EMIT_EVERY = 2L * MB
    }
}
