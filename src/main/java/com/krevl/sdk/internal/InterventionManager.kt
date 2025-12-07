package com.krevl.sdk.internal

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.krevl.sdk.models.FrustrationLevel

/**
 * Manages frustration intervention UI
 */
internal class InterventionManager(
    private val interventionDelayMs: Long,
    private val onFeedbackSubmitted: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var isShowingIntervention = false
    
    /**
     * Show intervention with optional delay
     */
    fun showIntervention(
        activity: Activity,
        frustrationLevel: FrustrationLevel,
        withDelay: Boolean = true
    ) {
        if (isShowingIntervention) return
        
        val show = {
            showInterventionDialog(activity, frustrationLevel)
        }
        
        if (withDelay && interventionDelayMs > 0) {
            handler.postDelayed(show, interventionDelayMs)
        } else {
            show()
        }
    }
    
    private fun showInterventionDialog(activity: Activity, level: FrustrationLevel) {
        if (isShowingIntervention) return
        isShowingIntervention = true
        
        val (title, message) = when (level) {
            FrustrationLevel.HIGH -> Pair(
                "We noticed something might not be working",
                "We're sorry you're having trouble. Would you like to share what's happening so we can help?"
            )
            FrustrationLevel.MEDIUM -> Pair(
                "Need some help?",
                "It looks like you might be having trouble. We'd love to hear your feedback."
            )
            else -> Pair(
                "How can we help?",
                "Share your feedback to help us improve."
            )
        }
        
        val input = android.widget.EditText(activity).apply {
            hint = "Tell us what's happening..."
            setPadding(48, 32, 48, 32)
            minLines = 3
        }
        
        android.app.AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setView(input)
            .setPositiveButton("Send Feedback") { _, _ ->
                val feedback = input.text.toString()
                if (feedback.isNotEmpty()) {
                    onFeedbackSubmitted(feedback)
                    showThankYouToast(activity)
                }
                isShowingIntervention = false
            }
            .setNegativeButton("Not now") { _, _ ->
                isShowingIntervention = false
            }
            .setOnCancelListener {
                isShowingIntervention = false
            }
            .show()
    }
    
    private fun showThankYouToast(activity: Activity) {
        android.widget.Toast.makeText(
            activity,
            "Thank you for your feedback! 🙏",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

