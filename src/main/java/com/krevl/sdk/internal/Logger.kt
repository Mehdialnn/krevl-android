package com.krevl.sdk.internal

import android.util.Log

/**
 * Internal logger for Krevl SDK
 */
internal object Logger {
    private const val TAG = "Krevl"
    var debugEnabled = false
    
    fun d(message: String) {
        if (debugEnabled) {
            Log.d(TAG, message)
        }
    }
    
    fun i(message: String) {
        Log.i(TAG, message)
    }
    
    fun w(message: String) {
        Log.w(TAG, message)
    }
    
    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
    }
}

