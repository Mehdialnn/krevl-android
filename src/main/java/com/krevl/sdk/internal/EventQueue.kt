package com.krevl.sdk.internal

import android.content.Context
import com.krevl.sdk.models.KrevlEvent
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Event queue with persistence and batching
 */
internal class EventQueue(
    private val context: Context,
    private val apiClient: ApiClient,
    private val batchSize: Int = 10,
    private val flushIntervalMs: Long = 30000L
) {
    private val queue = ConcurrentLinkedQueue<KrevlEvent>()
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var flushJob: Job? = null
    private val persistFile: File
        get() = File(context.filesDir, "krevl_events.json")
    
    init {
        loadPersistedEvents()
        startFlushTimer()
    }
    
    /**
     * Add event to queue
     */
    fun enqueue(event: KrevlEvent) {
        queue.add(event)
        Logger.d("Event queued: ${event.eventType} (queue size: ${queue.size})")
        
        // Persist
        persistEvents()
        
        // Flush if batch size reached
        if (queue.size >= batchSize) {
            flush()
        }
    }
    
    /**
     * Manually flush events
     */
    fun flush() {
        scope.launch {
            flushInternal()
        }
    }
    
    /**
     * Stop the queue
     */
    fun stop() {
        flushJob?.cancel()
        scope.cancel()
    }
    
    private suspend fun flushInternal() {
        if (queue.isEmpty()) return
        
        // Take events from queue
        val events = mutableListOf<KrevlEvent>()
        repeat(minOf(batchSize, queue.size)) {
            queue.poll()?.let { events.add(it) }
        }
        
        if (events.isEmpty()) return
        
        Logger.d("Flushing ${events.size} events...")
        
        val result = apiClient.sendEvents(events)
        
        if (result.isFailure) {
            // Re-queue failed events
            events.forEach { queue.add(it) }
            Logger.w("Failed to send events, re-queued")
        } else {
            // Remove persisted events on success
            persistEvents()
            Logger.d("Events sent successfully")
        }
    }
    
    private fun startFlushTimer() {
        flushJob = scope.launch {
            while (isActive) {
                delay(flushIntervalMs)
                flushInternal()
            }
        }
    }
    
    private fun persistEvents() {
        try {
            val events = queue.toList()
            val jsonString = json.encodeToString(events)
            persistFile.writeText(jsonString)
        } catch (e: Exception) {
            Logger.e("Failed to persist events", e)
        }
    }
    
    private fun loadPersistedEvents() {
        try {
            if (persistFile.exists()) {
                val jsonString = persistFile.readText()
                if (jsonString.isNotEmpty()) {
                    val events = json.decodeFromString<List<KrevlEvent>>(jsonString)
                    events.forEach { queue.add(it) }
                    Logger.d("Loaded ${events.size} persisted events")
                }
            }
        } catch (e: Exception) {
            Logger.e("Failed to load persisted events", e)
        }
    }
}

