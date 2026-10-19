package com.brainpal.quitscrolling.data.model

enum class SocialPlatform(
    val packageNames: Set<String>,
    val displayName: String,
    val brandColorHex: Long
) {
    INSTAGRAM(setOf("com.instagram.android"), "Instagram", 0xFFE1306C),
    YOUTUBE(setOf("com.google.android.youtube"), "YouTube", 0xFFFF0000),
    FACEBOOK(setOf("com.facebook.katana", "com.facebook.lite"), "Facebook", 0xFF1877F2),
    X_TWITTER(setOf("com.twitter.android"), "X", 0xFFFFFFFF);

    companion object {
        fun fromPackageName(packageName: String): SocialPlatform? {
            return entries.firstOrNull { packageName in it.packageNames }
        }

        fun fromNameOrDefault(name: String): SocialPlatform {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: INSTAGRAM
        }
    }
}
