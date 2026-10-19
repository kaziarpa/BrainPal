package com.brainpal.quitscrolling.data.model

enum class SocialPlatform(
    val packageNames: Set<String>,
    val displayName: String,
    val brandColorHex: Long
) {
    INSTAGRAM(
        packageNames = setOf("com.instagram.android", "com.instagram.lite"),
        displayName = "Instagram",
        brandColorHex = 0xFFE1306C
    ),
    YOUTUBE(
        packageNames = setOf("com.google.android.youtube", "com.google.android.apps.youtube.mango"),
        displayName = "YouTube",
        brandColorHex = 0xFFFF0000
    ),
    FACEBOOK(
        packageNames = setOf("com.facebook.katana", "com.facebook.lite"),
        displayName = "Facebook",
        brandColorHex = 0xFF1877F2
    ),
    X_TWITTER(
        packageNames = setOf("com.twitter.android", "com.twitter.android.lite", "com.x.android"),
        displayName = "X (Twitter)",
        brandColorHex = 0xFFFFFFFF
    );

    companion object {
        fun fromPackageName(packageName: String): SocialPlatform? {
            val normalized = packageName.lowercase()
            return entries.firstOrNull { platform ->
                platform.packageNames.any { it == normalized } ||
                    (platform == X_TWITTER && (normalized.startsWith("com.twitter.") || normalized.startsWith("com.x.android"))) ||
                    (platform == INSTAGRAM && normalized.startsWith("com.instagram.")) ||
                    (platform == FACEBOOK && normalized.startsWith("com.facebook.")) ||
                    (platform == YOUTUBE && normalized.contains("youtube"))
            }
        }

        fun fromNameOrDefault(name: String): SocialPlatform {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: INSTAGRAM
        }
    }
}
