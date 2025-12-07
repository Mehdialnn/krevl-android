package com.krevl.sdk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import com.krevl.sdk.internal.*
import com.krevl.sdk.models.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Krevl SDK - Review Infrastructure for Android Apps
 * 
 * One-line initialization:
 * ```kotlin
 * Krevl.start(application, "krevl_live_xxx")
 * ```
 * 
 * Or with options:
 * ```kotlin
 * Krevl.start(application, "krevl_live_xxx") {
 *     enableDebugLogging()
 *     setFrustrationThreshold(FrustrationLevel.MEDIUM)
 * }
 * ```
 */
object Krevl {
    private var isInitialized = false
    private var apiKey: String? = null
    private var options: KrevlOptions = KrevlOptions()
    
    private lateinit var context: Context
    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: ApiClient
    private lateinit var eventQueue: EventQueue
    private lateinit var frustrationDetector: FrustrationDetector
    private lateinit var reviewFlowManager: ReviewFlowManager
    private lateinit var interventionManager: InterventionManager
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private var decayRunnable: Runnable? = null
    private var currentActivity: Activity? = null
    
    private var onFrustrationChangeListener: ((FrustrationLevel, Int) -> Unit)? = null
    
    /**
     * SDK Version
     */
    const val VERSION = "1.0.0"
    
    // ============================================
    // INITIALIZATION
    // ============================================
    
    /**
     * One-line initialization - RECOMMENDED
     * 
     * ```kotlin
     * Krevl.start(application, "krevl_live_xxx")
     * ```
     */
    @JvmStatic
    @JvmOverloads
    fun start(
        application: Application,
        apiKey: String,
        configure: (KrevlOptions.Builder.() -> Unit)? = null
    ) {
        val builder = KrevlOptions.builder()
        configure?.invoke(builder)
        initialize(application, apiKey, builder.build())
    }
    
    /**
     * Initialize with explicit options
     */
    @JvmStatic
    fun initialize(application: Application, apiKey: String, options: KrevlOptions = KrevlOptions()) {
        if (isInitialized) {
            Logger.w("Krevl already initialized")
            return
        }
        
        this.context = application.applicationContext
        this.apiKey = apiKey
        this.options = options
        
        // Setup logging
        Logger.debugEnabled = options.debugLogging
        
        // Initialize components
        sessionManager = SessionManager(context)
        apiClient = ApiClient(apiKey)
        eventQueue = EventQueue(context, apiClient, options.eventBatchSize, options.eventFlushIntervalMs)
        
        frustrationDetector = FrustrationDetector(
            rageTapThreshold = options.rageTapThreshold,
            rageTapWindowMs = options.rageTapWindowMs,
            onFrustrationChanged = { level, score ->
                onFrustrationChangeListener?.invoke(level, score)
                trackFrustrationChange(level, score)
                
                // Auto intervention
                if (options.enableAutoIntervention && level >= options.frustrationThreshold) {
                    currentActivity?.let { showIntervention(it) }
                }
            },
            onRageTapDetected = {
                track("rage_tap")
            }
        )
        
        reviewFlowManager = ReviewFlowManager(
            sessionManager = sessionManager,
            minimumSessions = options.reviewPromptMinimumSessions,
            cooldownDays = options.reviewPromptCooldownDays,
            onReviewFlowShown = { track("review_flow_shown") },
            onReviewFlowResponse = { response ->
                val eventType = when (response) {
                    is ReviewFlowResponse.Positive -> "review_flow_positive"
                    is ReviewFlowResponse.Neutral -> "review_flow_neutral"
                    is ReviewFlowResponse.Negative -> "review_flow_negative"
                    is ReviewFlowResponse.Dismissed -> "review_flow_dismissed"
                }
                track(eventType)
                
                // Send feedback if negative with message
                if (response is ReviewFlowResponse.Negative && !response.feedback.isNullOrEmpty()) {
                    captureFeedback(FeedbackType.REVIEW_FLOW, response.feedback)
                }
            }
        )
        
        interventionManager = InterventionManager(
            interventionDelayMs = options.interventionDelayMs,
            onFeedbackSubmitted = { feedback ->
                captureFeedback(FeedbackType.FRUSTRATION, feedback)
                track("intervention_feedback_submitted")
            }
        )
        
        // Register activity lifecycle
        registerActivityLifecycle(application)
        
        // Start frustration score decay
        startFrustrationDecay()
        
        isInitialized = true
        
        // Track session start
        track("session_start", DeviceInfo.getDeviceInfo(context))
        
        Logger.i("Krevl initialized with API key: ${apiKey.take(12)}...")
        Logger.i("Session started: ${sessionManager.currentSessionId}")
    }
    
    // ============================================
    // PUBLIC API
    // ============================================
    
    /**
     * Current frustration level
     */
    @JvmStatic
    val frustrationLevel: FrustrationLevel
        get() {
            checkInitialized()
            return frustrationDetector.frustrationLevel
        }
    
    /**
     * Current frustration score (0-100)
     */
    @JvmStatic
    val frustrationScore: Int
        get() {
            checkInitialized()
            return frustrationDetector.frustrationScore
        }
    
    /**
     * Whether review flow can be shown
     */
    @JvmStatic
    val canShowReviewFlow: Boolean
        get() {
            checkInitialized()
            return reviewFlowManager.canShowReviewFlow && 
                   frustrationDetector.frustrationLevel == FrustrationLevel.NONE
        }
    
    /**
     * Listen for frustration changes
     */
    @JvmStatic
    fun onFrustrationChange(listener: (FrustrationLevel, Int) -> Unit) {
        onFrustrationChangeListener = listener
    }
    
    /**
     * Identify the current user
     */
    @JvmStatic
    @JvmOverloads
    fun identify(userId: String, traits: Map<String, Any> = emptyMap()) {
        checkInitialized()
        sessionManager.identify(userId, traits)
        track("user_identified", mapOf("user_id" to userId))
    }
    
    /**
     * Reset user state (on logout)
     */
    @JvmStatic
    fun reset() {
        checkInitialized()
        sessionManager.reset()
        frustrationDetector.reset()
        track("session_reset")
    }
    
    /**
     * Track a custom event
     */
    @JvmStatic
    @JvmOverloads
    fun track(eventType: String, properties: Map<String, String> = emptyMap()) {
        checkInitialized()
        
        val event = KrevlEvent(
            eventType = eventType,
            sessionId = sessionManager.currentSessionId,
            deviceId = DeviceInfo.getDeviceId(context),
            clientTimestamp = getCurrentTimestamp(),
            payload = properties,
            userId = sessionManager.userId
        )
        
        eventQueue.enqueue(event)
    }
    
    /**
     * Track screen view
     */
    @JvmStatic
    @JvmOverloads
    fun trackScreen(screenName: String, properties: Map<String, String> = emptyMap()) {
        track("screen_view", properties + ("screen" to screenName))
    }
    
    /**
     * Track a failure (contributes to frustration score)
     */
    @JvmStatic
    @JvmOverloads
    fun trackFailure(reason: String, context: Map<String, String> = emptyMap()) {
        checkInitialized()
        frustrationDetector.trackFailure(reason)
        track("failure", context + ("reason" to reason))
    }
    
    /**
     * Track a success (reduces frustration)
     */
    @JvmStatic
    fun trackSuccess() {
        checkInitialized()
        frustrationDetector.trackSuccess()
    }
    
    /**
     * Show the smart review flow
     */
    @JvmStatic
    @JvmOverloads
    fun showReviewFlow(activity: Activity? = currentActivity, onResponse: ((ReviewFlowResponse) -> Unit)? = null): Boolean {
        checkInitialized()
        
        val targetActivity = activity ?: currentActivity
        if (targetActivity == null) {
            Logger.w("No activity available for review flow")
            return false
        }
        
        if (frustrationDetector.frustrationLevel != FrustrationLevel.NONE) {
            Logger.d("Review flow blocked: user is frustrated")
            return false
        }
        
        return reviewFlowManager.showReviewFlow(targetActivity, onResponse)
    }
    
    /**
     * Show intervention manually
     */
    @JvmStatic
    @JvmOverloads
    fun showIntervention(activity: Activity? = currentActivity) {
        checkInitialized()
        
        val targetActivity = activity ?: currentActivity
        if (targetActivity == null) {
            Logger.w("No activity available for intervention")
            return
        }
        
        track("intervention_shown")
        interventionManager.showIntervention(targetActivity, frustrationDetector.frustrationLevel, withDelay = false)
    }
    
    /**
     * Capture user feedback
     */
    @JvmStatic
    @JvmOverloads
    fun captureFeedback(
        type: FeedbackType,
        message: String,
        context: Map<String, String> = emptyMap()
    ) {
        checkInitialized()
        
        val feedback = FeedbackRequest(
            type = type.name.lowercase(),
            message = message,
            sessionId = sessionManager.currentSessionId,
            deviceId = DeviceInfo.getDeviceId(this.context),
            userId = sessionManager.userId,
            context = context + mapOf(
                "frustration_level" to frustrationDetector.frustrationLevel.name,
                "frustration_score" to frustrationDetector.frustrationScore.toString()
            )
        )
        
        scope.launch {
            apiClient.sendFeedback(feedback)
        }
        
        track("feedback_submitted", mapOf("type" to type.name.lowercase()))
    }
    
    /**
     * Manually flush events
     */
    @JvmStatic
    fun flush() {
        checkInitialized()
        eventQueue.flush()
    }
    
    /**
     * Process a touch event for rage tap detection
     * Call this from your base Activity's dispatchTouchEvent
     */
    @JvmStatic
    fun processTouchEvent(event: MotionEvent) {
        if (!isInitialized) return
        if (!options.enableAutoFrustrationDetection) return
        frustrationDetector.processTouchEvent(event)
    }
    
    // ============================================
    // INTERNAL
    // ============================================
    
    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("Krevl not initialized. Call Krevl.start() first.")
        }
    }
    
    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
    
    private fun trackFrustrationChange(level: FrustrationLevel, score: Int) {
        track("frustration_detected", mapOf(
            "level" to level.name.lowercase(),
            "score" to score.toString()
        ))
    }
    
    private fun startFrustrationDecay() {
        decayRunnable = object : Runnable {
            override fun run() {
                if (isInitialized) {
                    frustrationDetector.applyDecay()
                    handler.postDelayed(this, 30000) // Every 30 seconds
                }
            }
        }
        handler.postDelayed(decayRunnable!!, 30000)
    }
    
    private fun registerActivityLifecycle(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                // Inject touch listener for rage tap detection
                if (options.enableAutoFrustrationDetection) {
                    injectTouchListener(activity)
                }
            }
            
            override fun onActivityStarted(activity: Activity) {}
            
            override fun onActivityResumed(activity: Activity) {
                currentActivity = activity
            }
            
            override fun onActivityPaused(activity: Activity) {
                if (currentActivity == activity) {
                    currentActivity = null
                }
            }
            
            override fun onActivityStopped(activity: Activity) {}
            
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
    
    private fun injectTouchListener(activity: Activity) {
        activity.window?.callback = object : Window.Callback by activity.window.callback {
            override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
                event?.let { processTouchEvent(it) }
                return activity.window.superDispatchTouchEvent(event)
            }
        }
    }
}

