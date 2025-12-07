# Krevl Android SDK

Review infrastructure for Android apps. Detect user frustration, prevent bad reviews, and capture actionable feedback.

[![](https://jitpack.io/v/Mehdialnn/krevl-android.svg)](https://jitpack.io/#Mehdialnn/krevl-android)
[![Platform](https://img.shields.io/badge/platform-Android%207%2B-green?style=flat-square)](https://developer.android.com/)
[![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](LICENSE)

## Installation

### Step 1: Add JitPack repository

Add JitPack to your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Step 2: Add the dependency

Add Krevl to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.Mehdialnn:krevl-android:1.0.0")
}
```

## Quick Start

### One-Line Setup

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // That's it! 🎉
        Krevl.start(this, "krevl_live_xxx")
    }
}
```

### With Options

```kotlin
Krevl.start(this, "krevl_live_xxx") {
    enableDebugLogging()
    setFrustrationThreshold(FrustrationLevel.MEDIUM)
    setReviewPromptMinimumSessions(5)
    setReviewPromptCooldown(30)
}
```

## Features

### Frustration Detection

Krevl automatically detects:
- **Rage taps** - Rapid, repeated taps
- **Failures** - Track errors and failed actions
- **Behavioral patterns** - Navigation issues, short sessions

```kotlin
// Track a failure manually
Krevl.trackFailure("payment_declined")

// Track success (reduces frustration)
Krevl.trackSuccess()

// Check current frustration level
when (Krevl.frustrationLevel) {
    FrustrationLevel.HIGH -> showHelpButton()
    else -> { }
}
```

### Smart Review Flow

Pre-prompt users before showing the Play Store review:

```kotlin
// After a positive moment (purchase, achievement, etc.)
Krevl.showReviewFlow { response ->
    when (response) {
        is ReviewFlowResponse.Positive -> {
            // User loves it → Play Store review shown
        }
        is ReviewFlowResponse.Negative -> {
            // Feedback captured: ${response.feedback}
        }
        else -> { }
    }
}
```

### Event Tracking

```kotlin
// Track custom events
Krevl.track("button_clicked", mapOf("button" to "subscribe"))

// Track screen views
Krevl.trackScreen("HomeScreen")

// Identify users
Krevl.identify("user_123", mapOf(
    "email" to "user@example.com",
    "plan" to "premium"
))
```

### User Feedback

```kotlin
// Capture feedback programmatically
Krevl.captureFeedback(
    type = FeedbackType.BUG_REPORT,
    message = "App crashes when uploading photos",
    context = mapOf("screen" to "PhotoUpload")
)
```

## Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `enableAutoFrustrationDetection` | `true` | Auto-detect rage taps |
| `frustrationThreshold` | `MEDIUM` | Trigger level for intervention |
| `rageTapThreshold` | `6` | Taps needed for rage detection |
| `rageTapWindowMs` | `2000` | Time window for rage taps |
| `enableAutoIntervention` | `true` | Auto-show feedback sheet |
| `interventionDelayMs` | `500` | Delay before intervention |
| `reviewPromptMinimumSessions` | `3` | Sessions before review prompt |
| `reviewPromptCooldownDays` | `30` | Days between prompts |
| `eventBatchSize` | `10` | Events per batch |
| `eventFlushIntervalMs` | `30000` | Flush interval (ms) |
| `debugLogging` | `false` | Enable debug logs |

## Requirements

- Android API 24+ (Android 7.0)
- Kotlin 1.8+

## License

MIT

