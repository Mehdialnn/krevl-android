package com.krevl.sdk.internal

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.UUID

/**
 * Device information utilities
 */
internal object DeviceInfo {
    private var cachedDeviceId: String? = null
    
    fun getDeviceId(context: Context): String {
        cachedDeviceId?.let { return it }
        
        val prefs = context.getSharedPreferences("krevl_prefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)
        
        if (deviceId == null) {
            // Try to get Android ID first
            deviceId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (e: Exception) {
                null
            }
            
            // Fallback to UUID
            if (deviceId.isNullOrEmpty()) {
                deviceId = UUID.randomUUID().toString()
            }
            
            prefs.edit().putString("device_id", deviceId).apply()
        }
        
        cachedDeviceId = deviceId
        return deviceId!!
    }
    
    fun getDeviceModel(): String = Build.MODEL
    
    fun getManufacturer(): String = Build.MANUFACTURER
    
    fun getOsVersion(): String = Build.VERSION.RELEASE
    
    fun getSdkVersion(): Int = Build.VERSION.SDK_INT
    
    fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    fun getAppVersionCode(context: Context): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }
    
    fun getPackageName(context: Context): String = context.packageName
    
    fun getDeviceInfo(context: Context): Map<String, String> = mapOf(
        "device_id" to getDeviceId(context),
        "device_model" to getDeviceModel(),
        "manufacturer" to getManufacturer(),
        "os_version" to getOsVersion(),
        "sdk_version" to getSdkVersion().toString(),
        "app_version" to getAppVersion(context),
        "app_version_code" to getAppVersionCode(context).toString(),
        "package_name" to getPackageName(context),
        "platform" to "android"
    )
}

