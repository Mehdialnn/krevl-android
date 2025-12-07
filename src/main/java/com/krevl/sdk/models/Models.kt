package com.krevl.sdk.models

import kotlinx.serialization.Serializable

/**
 * SDK Configuration options
 */
data class KrevlOptions(
    val enableAutoFrustrationDetection: Boolean = true,
    val frustrationThreshold: FrustrationLevel = FrustrationLevel.MEDIUM,
    val rageTapThreshold: Int = 6,
    val rageTapWindowMs: Long = 2000L,
    val enableAutoIntervention: Boolean = true,
    val interventionDelayMs: Long = 500L,
    val reviewPromptMinimumSessions: Int = 3,
    val reviewPromptCooldownDays: Int = 30,
    val eventBatchSize: Int = 10,
    val eventFlushIntervalMs: Long = 30000L,
    val debugLogging: Boolean = false,
    val environment: KrevlEnvironment = KrevlEnvironment.PRODUCTION
) {
    class Builder {
        private var options = KrevlOptions()
        
        fun enableAutoFrustrationDetection(enabled: Boolean = true) = apply {
            options = options.copy(enableAutoFrustrationDetection = enabled)
        }
        
        fun setFrustrationThreshold(level: FrustrationLevel) = apply {
            options = options.copy(frustrationThreshold = level)
        }
        
        fun setRageTapThreshold(taps: Int, windowMs: Long = 2000L) = apply {
            options = options.copy(rageTapThreshold = taps, rageTapWindowMs = windowMs)
        }
        
        fun enableAutoIntervention(enabled: Boolean = true) = apply {
            options = options.copy(enableAutoIntervention = enabled)
        }
        
        fun setInterventionDelay(delayMs: Long) = apply {
            options = options.copy(interventionDelayMs = delayMs)
        }
        
        fun setReviewPromptMinimumSessions(sessions: Int) = apply {
            options = options.copy(reviewPromptMinimumSessions = sessions)
        }
        
        fun setReviewPromptCooldown(days: Int) = apply {
            options = options.copy(reviewPromptCooldownDays = days)
        }
        
        fun setEventBatchSize(size: Int) = apply {
            options = options.copy(eventBatchSize = size)
        }
        
        fun setEventFlushInterval(intervalMs: Long) = apply {
            options = options.copy(eventFlushIntervalMs = intervalMs)
        }
        
        fun enableDebugLogging(enabled: Boolean = true) = apply {
            options = options.copy(debugLogging = enabled)
        }
        
        fun setEnvironment(env: KrevlEnvironment) = apply {
            options = options.copy(environment = env)
        }
        
        fun build(): KrevlOptions = options
    }
    
    companion object {
        fun builder() = Builder()
    }
}

/**
 * Environment type
 */
enum class KrevlEnvironment {
    DEVELOPMENT,
    PRODUCTION
}

/**
 * Frustration level
 */
enum class FrustrationLevel(val threshold: Int) {
    NONE(0),
    LOW(20),
    MEDIUM(40),
    HIGH(70)
}

/**
 * Feedback type
 */
enum class FeedbackType {
    FRUSTRATION,
    REVIEW_FLOW,
    GENERAL,
    BUG_REPORT
}

/**
 * Review flow response
 */
sealed class ReviewFlowResponse {
    object Positive : ReviewFlowResponse()
    object Neutral : ReviewFlowResponse()
    data class Negative(val feedback: String?) : ReviewFlowResponse()
    object Dismissed : ReviewFlowResponse()
}

/**
 * SDK Event for tracking
 */
@Serializable
data class KrevlEvent(
    val eventType: String,
    val sessionId: String,
    val deviceId: String,
    val clientTimestamp: String,
    val payload: Map<String, String> = emptyMap(),
    val userId: String? = null
)

/**
 * Events batch request
 */
@Serializable
data class EventsBatchRequest(
    val events: List<KrevlEvent>
)

/**
 * Feedback request
 */
@Serializable
data class FeedbackRequest(
    val type: String,
    val message: String,
    val sessionId: String,
    val deviceId: String,
    val userId: String? = null,
    val context: Map<String, String> = emptyMap()
)

/**
 * User traits for identification
 */
typealias UserTraits = Map<String, Any>

