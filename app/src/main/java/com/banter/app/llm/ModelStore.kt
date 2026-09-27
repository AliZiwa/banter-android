package com.banter.app.llm

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** Where the model file lives, how it gets there, and whether there is room for it. */
class ModelStore(private val context: Context) {

    private val dir = File(context.filesDir, "models").apply { mkdirs() }

    /** The installed model, if any. Banter keeps at most one. */
    fun installed(): File? =
        dir.listFiles { f -> f.isFile && f.extension == "litertlm" }
            ?.maxByOrNull { it.length() }

    fun freeBytes(): Long = context.filesDir.usableSpace

    /** True on Wi-Fi or any other connection the user is not paying per megabyte for. */
    fun isUnmetered(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    suspend fun delete(): Boolean = withContext(Dispatchers.IO) {
        installed()?.delete() ?: false
    }

    /**
     * Copies a model the user picked with the system file picker. This is the path that actually
     * works for most people: the Gemma files are licence-gated, so they are usually downloaded on
     * a laptop and moved onto the phone.
     */
    fun importFrom(uri: android.net.Uri, displayName: String): Flow<Transfer> = flow {
        val total = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()?.takeIf { it > 0 } ?: -1L

        val input = context.contentResolver.openInputStream(uri)
            ?: error("Could not open the selected file")
        input.use { emitAll(it, total, safeName(displayName)) }
    }.flowOn(Dispatchers.IO)

    /** Direct download. Gated repositories need [token] (a Hugging Face read token). */
    fun download(url: String, token: String?): Flow<Transfer> = flow {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer ${token.trim()}")
        }
        try {
            connection.connect()
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
                error("The model is licence-gated ($code). Accept the licence on Hugging Face and paste a read token, or import the file instead.")
            }
            if (code !in 200..299) error("Download failed with HTTP $code")

            val total = connection.contentLengthLong
            val name = safeName(url.substringAfterLast('/').substringBefore('?'))
            connection.inputStream.use { emitAll(it, total, name) }
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun kotlinx.coroutines.flow.FlowCollector<Transfer>.emitAll(
        input: InputStream,
        total: Long,
        name: String,
    ) {
        if (total > 0 && total + SAFETY_MARGIN > freeBytes()) {
            error("Not enough free space. ${format(total)} needed, ${format(freeBytes())} available.")
        }

        // Download beside the real name, then rename, so a cancelled transfer never looks installed.
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
            error("Transfer ended early: got ${format(copied)} of ${format(total)}")
        }

        installed()?.delete()
        if (!partial.renameTo(target)) {
            partial.delete()
            error("Could not save the model file")
        }
        emit(Transfer.Done(target))
    }

    private fun safeName(raw: String): String {
        val cleaned = raw.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (cleaned.endsWith(".litertlm")) cleaned else "$cleaned.litertlm"
    }

    sealed interface Transfer {
        data class Running(val bytes: Long, val total: Long) : Transfer
        data class Done(val file: File) : Transfer
    }

    companion object {
        /** Leave room for the OS; filling the data partition is a bad way to end a download. */
        private const val SAFETY_MARGIN = 256L * 1024 * 1024
        private const val EMIT_EVERY = 2L * 1024 * 1024

        /**
         * Google's own Gemma 4 E2B conversion: ungated, instruction-tuned, and the model that
         * turned this chat from surreal filler into people talking. 2.59 GB.
         */
        const val SUGGESTED_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/" +
                "gemma-4-E2B-it.litertlm"

        fun format(bytes: Long): String = when {
            bytes < 0 -> "unknown size"
            bytes >= 1L shl 30 -> String.format("%.2f GB", bytes.toDouble() / (1L shl 30))
            bytes >= 1L shl 20 -> String.format("%.0f MB", bytes.toDouble() / (1L shl 20))
            else -> "$bytes B"
        }
    }
}
