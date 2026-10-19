# Likhon: Quit Scrolling (`com.brainpal.quitscrolling`)

**Likhon** is a native Android application built with Kotlin and Jetpack Compose that tracks and blocks short-form reels, YouTube Shorts, and vertical videos scrolled across **Instagram, YouTube, Facebook, and X (Twitter)** in real time.

## Core Features
- **Multi-Signal 4-Tier Scroll Detection (`ReelAccessibilityService` & `VideoScrollDetector`):** Tracks every reel and vertical video across Instagram, YouTube, Facebook, and X (including `com.twitter.android`, `com.twitter.android.lite`, and `com.x.android`) using index transitions, low-latency flick thresholds, and viewport content signature tracking so fast swipes are never missed.
- **Smart Dynamic Island Floating Pill (`FloatingPillOverlayManager`):** Shows the live counter pill **only** when watching Reels/Videos inside monitored social apps, and automatically hides it the moment you leave those apps.
- **Real-Time Reel & Video Blocker System (`ReelBlockerOverlayManager`):** Supports both **Daily Limit Blocking** (auto-blocks after reaching your daily reel limit) and **Instant Zero-Tolerance Blocking** (0 reels allowed), with per-platform toggles (`Instagram`, `YouTube`, `Facebook`, `X`), automatic back-out (`GLOBAL_ACTION_BACK`), and a full-screen Blocker Shield.
- **Floating Accessibility Setup Guide (`AccessibilityPermissionHelper`):** Shows a visual preview and floating guide window over Android Accessibility Settings indicating **"Likhon Scroll Tracker & Blocker"**.
- **Soft, Gentle Haptic Vibrations (`LikhonHaptics`):** Subtle tactile pulses across navigation, sliders, buttons, counted reels, and blocker shield alerts.

---

## Automated GitHub Actions APK Building & Releases Setup

Because GitHub blocks OAuth pushes that directly create or modify files inside `.github/workflows/`, the workflow configuration is stored at the repository root in `github-workflow-build-apk.yml` alongside `ci-build-apk.sh`.

### One-Time GitHub Setup Instructions:
1. After syncing/pushing this project to GitHub, open your repository on **GitHub.com**.
2. Copy the contents of **`github-workflow-build-apk.yml`** into a new file at **`.github/workflows/build-apk.yml`** on GitHub.com *(do **not** delete `github-workflow-build-apk.yml` in the root)*.
3. Pull the change back into **AI Studio** once.
4. All future syncs and pushes will automatically build `Likhon-debug.apk` and `Likhon-release.apk`, upload them as workflow artifacts, and publish them to your **GitHub Releases** page!
