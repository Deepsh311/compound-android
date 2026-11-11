package app.compound.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
    isLenient = true
}

fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

private object AppDataSerializer : Serializer<AppData> {
    override val defaultValue: AppData = AppData()

    override suspend fun readFrom(input: InputStream): AppData = try {
        AppJson.decodeFromString(AppData.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Could not read saved data", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Could not read saved data", e)
    }

    override suspend fun writeTo(t: AppData, output: OutputStream) {
        output.write(AppJson.encodeToString(AppData.serializer(), t).encodeToByteArray())
    }
}

/** The single source of truth. Every change goes through [update], which DataStore writes atomically. */
class Repository(context: Context) {
    private val store: DataStore<AppData> = DataStoreFactory.create(
        serializer = AppDataSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { AppData() },
        produceFile = { context.applicationContext.dataStoreFile("compound.json") },
    )

    val data: Flow<AppData> = store.data

    suspend fun current(): AppData = store.data.first()

    suspend fun update(transform: (AppData) -> AppData): AppData = store.updateData { transform(it) }

    fun export(data: AppData): String = AppJson.encodeToString(AppData.serializer(), data)

    fun import(text: String): AppData = AppJson.decodeFromString(AppData.serializer(), text)
}
