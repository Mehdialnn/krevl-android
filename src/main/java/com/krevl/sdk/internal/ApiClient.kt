package com.krevl.sdk.internal

import com.krevl.sdk.models.EventsBatchRequest
import com.krevl.sdk.models.FeedbackRequest
import com.krevl.sdk.models.KrevlEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * API client for Krevl backend
 */
internal class ApiClient(private val apiKey: String) {
    
    private val baseUrl = "https://krevl-api.mehdial2219.workers.dev"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    
    /**
     * Send events batch to the server
     */
    suspend fun sendEvents(events: List<KrevlEvent>): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val request = EventsBatchRequest(events)
            val body = json.encodeToString(request).toRequestBody(jsonMediaType)
            
            val httpRequest = Request.Builder()
                .url("$baseUrl/v1/sdk/events")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()
            
            val response = client.newCall(httpRequest).execute()
            
            if (response.isSuccessful) {
                Logger.d("Events sent successfully: ${events.size}")
                Result.success(events.size)
            } else {
                Logger.e("Failed to send events: ${response.code} - ${response.body?.string()}")
                Result.failure(Exception("API error: ${response.code}"))
            }
        } catch (e: Exception) {
            Logger.e("Error sending events", e)
            Result.failure(e)
        }
    }
    
    /**
     * Send feedback to the server
     */
    suspend fun sendFeedback(feedback: FeedbackRequest): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(feedback).toRequestBody(jsonMediaType)
            
            val httpRequest = Request.Builder()
                .url("$baseUrl/v1/sdk/feedback")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()
            
            val response = client.newCall(httpRequest).execute()
            
            if (response.isSuccessful) {
                Logger.d("Feedback sent successfully")
                Result.success(Unit)
            } else {
                Logger.e("Failed to send feedback: ${response.code}")
                Result.failure(Exception("API error: ${response.code}"))
            }
        } catch (e: Exception) {
            Logger.e("Error sending feedback", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get remote config
     */
    suspend fun getConfig(): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val httpRequest = Request.Builder()
                .url("$baseUrl/v1/sdk/config")
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            
            val response = client.newCall(httpRequest).execute()
            
            if (response.isSuccessful) {
                // Parse config response
                Result.success(emptyMap())
            } else {
                Result.failure(Exception("API error: ${response.code}"))
            }
        } catch (e: Exception) {
            Logger.e("Error fetching config", e)
            Result.failure(e)
        }
    }
}

