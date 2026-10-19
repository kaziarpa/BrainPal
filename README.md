# BrainPal: Quit Scrolling (`com.brainpal.quitscrolling`)

**BrainPal** is a native Android application built with Kotlin and Jetpack Compose that tracks every short-form reel, YouTube Short, and vertical video scrolled across **Instagram, YouTube, Facebook, and X** in real time.

## Core Features
- **4-Tier Multi-App Scroll Detection (`ReelAccessibilityService`):** Obfuscation-resilient detection pipeline with comment bottom-sheet exclusion, known View ID matching, geometric + media surface heuristics, in-feed video scroll tracking, and an 850ms page-snap debounce state machine.
- **Dynamic Island Floating Pill Overlay (`FloatingPillOverlayManager`):** Zero-permission `TYPE_ACCESSIBILITY_OVERLAY` Compose pill hugging the top camera cutout with live brain state and animated counter.
- **4-Stage Procedural Brain Rot Mascot (`BrainMascotCanvas`):** Custom Compose `Canvas` mascot that progressively transforms across 4 stages (`0–49 Fresh`, `50–99 Draining`, `100–199 Rotting`, `200+ Dead Rot`).
- **Fitness-Style Analytics Dashboard (`StatsScreen`):** Interactive 7-day weekly bar chart with daily mascot indicator and Top Apps breakdown (`Instagram`, `YouTube`, `Facebook`, `X`).
- **1v1 Friend Battle Mode (`BattleScreen`):** Split-screen VS arena with a golden lightning divider, pacemaker rivals, and Base64/JSON battle code sharing & importing.
- **Jetpack Glance Home Screen Widget (`BrainRotWidget`):** Offscreen Bitmap mascot renderer and live daily reel count updated immediately on every scroll event.

---

## Automated GitHub Actions APK Building & Releases Setup

Because GitHub blocks OAuth pushes that directly create or modify files inside `.github/workflows/`, the workflow configuration is stored at the repository root in `github-workflow-build-apk.yml` alongside `ci-build-apk.sh`.

### One-Time GitHub Setup Instructions:
1. After syncing/pushing this project to GitHub, open your repository on **GitHub.com**.
2. Copy the contents of **`github-workflow-build-apk.yml`** into a new file at **`.github/workflows/build-apk.yml`** on GitHub.com *(do **not** delete `github-workflow-build-apk.yml` in the root)*.
3. Pull the change back into **AI Studio** once.
4. All future syncs and pushes will automatically build `BrainPal-debug.apk` and `BrainPal-release.apk`, upload them as workflow artifacts, and publish them to your **GitHub Releases** page!
