package com.krevl.sdk.internal

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory
import com.krevl.sdk.models.ReviewFlowResponse

/**
 * Manages the smart review flow
 */
internal class ReviewFlowManager(
    private val sessionManager: SessionManager,
    private val minimumSessions: Int,
    private val cooldownDays: Int,
    private val onReviewFlowShown: () -> Unit,
    private val onReviewFlowResponse: (ReviewFlowResponse) -> Unit
) {
    /**
     * Check if review flow can be shown
     */
    val canShowReviewFlow: Boolean
        get() = sessionManager.canShowReviewPrompt(minimumSessions, cooldownDays)
    
    /**
     * Show the smart review flow pre-prompt
     * Returns true if shown, false if blocked
     */
    fun showReviewFlow(
        activity: Activity,
        onResponse: ((ReviewFlowResponse) -> Unit)? = null
    ): Boolean {
        if (!canShowReviewFlow) {
            Logger.d("Review flow blocked by session/cooldown rules")
            return false
        }
        
        // Show pre-prompt dialog
        showPrePromptDialog(activity) { response ->
            sessionManager.recordReviewPrompt()
            onReviewFlowShown()
            onResponse?.invoke(response)
            onReviewFlowResponse(response)
            
            when (response) {
                is ReviewFlowResponse.Positive -> {
                    // Show Google Play in-app review
                    showPlayStoreReview(activity)
                }
                is ReviewFlowResponse.Negative -> {
                    // Feedback already captured in the dialog
                    Logger.d("Negative response with feedback: ${response.feedback}")
                }
                else -> {
                    Logger.d("Review flow response: $response")
                }
            }
        }
        
        return true
    }
    
    /**
     * Show the pre-prompt dialog
     */
    private fun showPrePromptDialog(activity: Activity, onResponse: (ReviewFlowResponse) -> Unit) {
        val dialog = android.app.AlertDialog.Builder(activity)
            .setTitle("Enjoying the app?")
            .setMessage("Your feedback helps us improve!")
            .setPositiveButton("Love it! ❤️") { _, _ ->
                onResponse(ReviewFlowResponse.Positive)
            }
            .setNeutralButton("It's okay") { _, _ ->
                onResponse(ReviewFlowResponse.Neutral)
            }
            .setNegativeButton("Not great") { dialog, _ ->
                dialog.dismiss()
                showFeedbackDialog(activity, onResponse)
            }
            .setOnCancelListener {
                onResponse(ReviewFlowResponse.Dismissed)
            }
            .create()
        
        dialog.show()
    }
    
    /**
     * Show feedback dialog for negative responses
     */
    private fun showFeedbackDialog(activity: Activity, onResponse: (ReviewFlowResponse) -> Unit) {
        val input = android.widget.EditText(activity).apply {
            hint = "What could we do better?"
            setPadding(48, 32, 48, 32)
        }
        
        android.app.AlertDialog.Builder(activity)
            .setTitle("We'd love your feedback")
            .setMessage("Help us improve by sharing what's not working for you.")
            .setView(input)
            .setPositiveButton("Submit") { _, _ ->
                val feedback = input.text.toString()
                onResponse(ReviewFlowResponse.Negative(feedback.ifEmpty { null }))
            }
            .setNegativeButton("Skip") { _, _ ->
                onResponse(ReviewFlowResponse.Negative(null))
            }
            .setOnCancelListener {
                onResponse(ReviewFlowResponse.Dismissed)
            }
            .show()
    }
    
    /**
     * Show Google Play in-app review
     */
    private fun showPlayStoreReview(activity: Activity) {
        try {
            val reviewManager = ReviewManagerFactory.create(activity)
            val request = reviewManager.requestReviewFlow()
            
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo = task.result
                    val flow = reviewManager.launchReviewFlow(activity, reviewInfo)
                    flow.addOnCompleteListener {
                        Logger.d("In-app review flow completed")
                    }
                } else {
                    // Fallback to Play Store
                    openPlayStore(activity)
                }
            }
        } catch (e: Exception) {
            Logger.e("Failed to show in-app review", e)
            openPlayStore(activity)
        }
    }
    
    /**
     * Open the Play Store page for the app
     */
    private fun openPlayStore(activity: Activity) {
        try {
            val packageName = activity.packageName
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            activity.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to web URL
            val packageName = activity.packageName
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
            activity.startActivity(intent)
        }
    }
}

