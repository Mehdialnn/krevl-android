package com.krevl.sdk.internal

import android.content.Context
import java.util.UUID

/**
 * Manages user sessions
 */
internal class SessionManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("krevl_session", Context.MODE_PRIVATE)
    
    var currentSessionId: String = generateSessionId()
        private set
    
    var userId: String? = null
        private set
    
    var userTraits: Map<String, Any> = emptyMap()
        private set
    
    val sessionCount: Int
        get() = prefs.getInt("session_count", 0)
    
    val lastReviewPromptTime: Long
        get() = prefs.getLong("last_review_prompt", 0L)
    
    init {
        incrementSessionCount()
        Logger.d("Session started: $currentSessionId (session #$sessionCount)")
    }
    
    private fun generateSessionId(): String = "sess_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    
    private fun incrementSessionCount() {
        val newCount = sessionCount + 1
        prefs.edit().putInt("session_count", newCount).apply()
    }
    
    fun identify(userId: String, traits: Map<String, Any> = emptyMap()) {
        this.userId = userId
        this.userTraits = traits
        Logger.d("User identified: $userId")
    }
    
    fun reset() {
        userId = null
        userTraits = emptyMap()
        currentSessionId = generateSessionId()
        Logger.d("Session reset, new session: $currentSessionId")
    }
    
    fun recordReviewPrompt() {
        prefs.edit().putLong("last_review_prompt", System.currentTimeMillis()).apply()
    }
    
    fun canShowReviewPrompt(minimumSessions: Int, cooldownDays: Int): Boolean {
        // Check minimum sessions
        if (sessionCount < minimumSessions) {
            Logger.d("Review prompt blocked: session count ($sessionCount) < minimum ($minimumSessions)")
            return false
        }
        
        // Check cooldown
        val lastPrompt = lastReviewPromptTime
        if (lastPrompt > 0) {
            val daysSinceLastPrompt = (System.currentTimeMillis() - lastPrompt) / (1000 * 60 * 60 * 24)
            if (daysSinceLastPrompt < cooldownDays) {
                Logger.d("Review prompt blocked: cooldown not met ($daysSinceLastPrompt days < $cooldownDays)")
                return false
            }
        }
        
        return true
    }
}

