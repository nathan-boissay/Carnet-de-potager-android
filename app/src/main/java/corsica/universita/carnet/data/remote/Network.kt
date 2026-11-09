package corsica.universita.carnet.data.remote

import android.content.Context
import android.util.Log
import corsica.universita.carnet.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "Carnet"

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Un seul OkHttpClient par process, avec un cache HTTP de 10 Mo dans `cacheDir/http`
 * et un intercepteur de logs vers Logcat (Chapitre 3, Slide 43 du Jour 5).
 */
fun okHttp(context: Context): OkHttpClient =
    OkHttpClient.Builder()
        .cache(Cache(File(context.applicationContext.cacheDir, "http"), 10L * 1024 * 1024))
        .addInterceptor(
            HttpLoggingInterceptor { message -> Log.d(TAG, message) }
                .apply { level = HttpLoggingInterceptor.Level.BASIC },
        )
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

fun syncApi(client: OkHttpClient): SyncApi =
    Retrofit.Builder()
        .baseUrl(BuildConfig.SYNC_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SyncApi::class.java)

