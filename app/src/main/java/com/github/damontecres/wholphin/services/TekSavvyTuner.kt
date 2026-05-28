package com.github.damontecres.wholphin.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TekSavvyTuner @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "http://192.168.1.13:8765"

    suspend fun tune(channelNumber: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.i("TekSavvyTuner: tuning to channel $channelNumber")
            val request = Request.Builder()
                .url("$baseUrl/tune?channel=$channelNumber")
                .post("".toRequestBody())
                .build()
            val response = client.newCall(request).execute()
            val body = response.body.string()
            Timber.i("TekSavvyTuner: response ${response.code} - $body")
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Tuning failed: ${response.code} $body"))
            }
        } catch (e: Exception) {
            Timber.e(e, "TekSavvyTuner: error tuning to channel $channelNumber")
            Result.failure(e)
        }
    }

    suspend fun launch(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.i("TekSavvyTuner: launching TekSavvy app")
            val request = Request.Builder()
                .url("$baseUrl/launch")
                .post("".toRequestBody())
                .build()
            val response = client.newCall(request).execute()
            val body = response.body.string()
            Timber.i("TekSavvyTuner: launch response ${response.code} - $body")
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Launch failed: ${response.code} $body"))
            }
        } catch (e: Exception) {
            Timber.e(e, "TekSavvyTuner: error launching TekSavvy app")
            Result.failure(e)
        }
    }
}
