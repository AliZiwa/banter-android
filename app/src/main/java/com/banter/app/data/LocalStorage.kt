package com.banter.app.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.banter.app.domain.ChatMessage
import com.banter.app.domain.Presets
import com.banter.app.domain.Scenario
import com.banter.app.domain.ScenarioRepository
import com.banter.app.domain.TranscriptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/*
 * Everything that lives on the phone's disk: the transcript in Room, the scenario in DataStore.
 * A chat app this size needs neither a schema migration story nor a second database, so both
 * fit in one file.
 */

// --- Transcript: Room ---------------------------------------------------------------------

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val speakerId: String?,
    val speakerName: String,
    val text: String,
    val at: Long,
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY at ASC")
    fun observe(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    /** Keeps the newest [keep] rows; the rest is not worth the disk. */
    @Query(
        "DELETE FROM messages WHERE id NOT IN " +
            "(SELECT id FROM messages ORDER BY at DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int)

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Database(entities = [MessageEntity::class], version = 1, exportSchema = false)
abstract class BanterDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
}

class TranscriptRepositoryImpl @Inject constructor(
    private val dao: MessageDao,
) : TranscriptRepository {

    override val messages: Flow<List<ChatMessage>> =
        dao.observe().map { rows -> rows.map { it.toDomain() } }

    override suspend fun append(message: ChatMessage) {
        dao.insert(message.toEntity())
        dao.trim(MAX_PERSISTED)
    }

    override suspend fun clear() = dao.clear()

    private fun MessageEntity.toDomain() = ChatMessage(id, speakerId, speakerName, text, at)
    private fun ChatMessage.toEntity() = MessageEntity(id, speakerId, speakerName, text, at)

    private companion object {
        const val MAX_PERSISTED = 200
    }
}

// --- Scenario: DataStore ------------------------------------------------------------------

/** One JSON document, read and written whole. A cast edit is rare and small. */
object ScenarioSerializer : Serializer<Scenario> {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    override val defaultValue: Scenario = Presets.stella

    override suspend fun readFrom(input: InputStream): Scenario =
        try {
            json.decodeFromString<Scenario>(input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("scenario.json is not a Scenario", e)
        }

    override suspend fun writeTo(t: Scenario, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}

class ScenarioRepositoryImpl @Inject constructor(
    private val store: DataStore<Scenario>,
) : ScenarioRepository {

    override val scenario: Flow<Scenario> = store.data.catch { error ->
        // An unreadable file should not take the chat down with it.
        if (error is IOException) emit(Presets.stella) else throw error
    }

    override suspend fun save(scenario: Scenario) {
        store.updateData { scenario }
    }
}
