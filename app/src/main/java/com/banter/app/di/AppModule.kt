package com.banter.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.room.Room
import com.banter.app.data.BanterDatabase
import com.banter.app.data.CharacterAgents
import com.banter.app.data.EngineManager
import com.banter.app.data.HuggingFaceApi
import com.banter.app.data.MessageDao
import com.banter.app.data.ModelRepositoryImpl
import com.banter.app.data.ScenarioRepositoryImpl
import com.banter.app.data.ScenarioSerializer
import com.banter.app.data.TranscriptRepositoryImpl
import com.banter.app.domain.Director
import com.banter.app.domain.EngineRepository
import com.banter.app.domain.ModelRepository
import com.banter.app.domain.Presets
import com.banter.app.domain.Scenario
import com.banter.app.domain.ScenarioRepository
import com.banter.app.domain.TranscriptRepository
import com.banter.app.domain.Voices
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.create
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** Things that need building: the database, the DataStore file, the HTTP stack. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): BanterDatabase =
        Room.databaseBuilder(context, BanterDatabase::class.java, "banter.db").build()

    @Provides
    fun messages(db: BanterDatabase): MessageDao = db.messages()

    @Provides
    @Singleton
    fun scenarioStore(@ApplicationContext context: Context): DataStore<Scenario> =
        DataStoreFactory.create(
            serializer = ScenarioSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { Presets.stella },
        ) { context.dataStoreFile("scenario.json") }

    @Provides
    @Singleton
    fun httpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Provides
    @Singleton
    fun huggingFace(client: OkHttpClient): HuggingFaceApi = Retrofit.Builder()
        // Every call passes its own @Url; Retrofit still insists on a base.
        .baseUrl("https://huggingface.co/")
        .client(client)
        .build()
        .create()

    /** Unscoped on purpose: each chat ViewModel gets its own turn loop. */
    @Provides
    fun director(voices: Voices): Director = Director(voices)
}

/** Interfaces the domain declares, mapped to the classes in the data layer. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindsModule {
    @Binds abstract fun scenarios(impl: ScenarioRepositoryImpl): ScenarioRepository
    @Binds abstract fun transcripts(impl: TranscriptRepositoryImpl): TranscriptRepository
    @Binds abstract fun models(impl: ModelRepositoryImpl): ModelRepository
    @Binds abstract fun engines(impl: EngineManager): EngineRepository
    @Binds abstract fun voices(impl: CharacterAgents): Voices
}
