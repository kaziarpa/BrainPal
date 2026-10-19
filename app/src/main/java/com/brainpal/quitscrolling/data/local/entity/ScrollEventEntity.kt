package com.brainpal.quitscrolling.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scroll_events",
    indices = [
        Index(value = ["dateString"]),
        Index(value = ["packageName", "dateString"])
    ]
)
data class ScrollEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val platform: String,       // INSTAGRAM, YOUTUBE, FACEBOOK, X_TWITTER
    val contentType: String,    // REEL_SHORT, FEED_VIDEO
    val timestamp: Long,        // System.currentTimeMillis()
    val dateString: String      // LocalDate.now().toString() -> "YYYY-MM-DD"
)
