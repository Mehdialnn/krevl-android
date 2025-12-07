package com.krevl.sdk.internal

import android.view.MotionEvent
import android.view.View
import com.krevl.sdk.models.FrustrationLevel
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Detects user frustration through behavioral signals
 */
internal class FrustrationDetector(
    private val rageTapThreshold: Int = 6,
    private val rageTapWindowMs: Long = 2000L,
    private val onFrustrationChanged: (FrustrationLevel, Int) -> Unit,
    private val onRageTapDetected: () -> Unit
) {
    private val tapTimestamps = ConcurrentLinkedQueue<Long>()
    private var failureCount = 0
    private var _frustrationScore = 0
    private var _frustrationLevel = FrustrationLevel.NONE
    
    val frustrationScore: Int
        get() = _frustrationScore
    
    val frustrationLevel: FrustrationLevel
        get() = _frustrationLevel
    
    /**
     * Process a touch event for rage tap detection
     */
    fun processTouchEvent(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN) return
        
        val now = System.currentTimeMillis()
        tapTimestamps.add(now)
        
        // Remove old taps outside window
        val cutoff = now - rageTapWindowMs
        while (tapTimestamps.isNotEmpty() && tapTimestamps.peek()!! < cutoff) {
            tapTimestamps.poll()
        }
        
        // Check for rage tap
        if (tapTimestamps.size >= rageTapThreshold) {
            Logger.d("Rage tap detected! ${tapTimestamps.size} taps in ${rageTapWindowMs}ms")
            tapTimestamps.clear()
            
            // Increase frustration
            increaseScore(30)
            onRageTapDetected()
        }
    }
    
    /**
     * Track a failure event
     */
    fun trackFailure(reason: String) {
        failureCount++
        Logger.d("Failure tracked: $reason (total: $failureCount)")
        
        // Increase frustration based on consecutive failures
        val scoreIncrease = when {
            failureCount >= 3 -> 25
            failureCount >= 2 -> 15
            else -> 10
        }
        increaseScore(scoreIncrease)
    }
    
    /**
     * Track a success event (decreases frustration)
     */
    fun trackSuccess() {
        failureCount = 0
        decreaseScore(15)
    }
    
    /**
     * Reset frustration state
     */
    fun reset() {
        tapTimestamps.clear()
        failureCount = 0
        _frustrationScore = 0
        updateLevel()
    }
    
    /**
     * Create a touch listener that can be attached to views
     */
    fun createTouchListener(): View.OnTouchListener {
        return View.OnTouchListener { _, event ->
            processTouchEvent(event)
            false // Don't consume the event
        }
    }
    
    private fun increaseScore(amount: Int) {
        val oldLevel = _frustrationLevel
        _frustrationScore = minOf(_frustrationScore + amount, 100)
        updateLevel()
        
        if (_frustrationLevel != oldLevel) {
            onFrustrationChanged(_frustrationLevel, _frustrationScore)
        }
    }
    
    private fun decreaseScore(amount: Int) {
        val oldLevel = _frustrationLevel
        _frustrationScore = maxOf(_frustrationScore - amount, 0)
        updateLevel()
        
        if (_frustrationLevel != oldLevel) {
            onFrustrationChanged(_frustrationLevel, _frustrationScore)
        }
    }
    
    private fun updateLevel() {
        _frustrationLevel = when {
            _frustrationScore >= FrustrationLevel.HIGH.threshold -> FrustrationLevel.HIGH
            _frustrationScore >= FrustrationLevel.MEDIUM.threshold -> FrustrationLevel.MEDIUM
            _frustrationScore >= FrustrationLevel.LOW.threshold -> FrustrationLevel.LOW
            else -> FrustrationLevel.NONE
        }
    }
    
    /**
     * Start decay timer (call this periodically)
     */
    fun applyDecay() {
        if (_frustrationScore > 0) {
            decreaseScore(5)
        }
    }
}

