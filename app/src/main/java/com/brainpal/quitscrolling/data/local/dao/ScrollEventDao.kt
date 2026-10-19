package com.brainpal.quitscrolling.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.brainpal.quitscrolling.data.local.entity.ScrollEventEntity
import com.brainpal.quitscrolling.data.model.AppCountBreakdown
import com.brainpal.quitscrolling.data.model.DayCountSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface ScrollEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: ScrollEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<ScrollEventEntity>)

    @Query("SELECT COUNT(*) FROM scroll_events WHERE dateString = :dateString")
    fun observeDailyCount(dateString: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM scroll_events WHERE dateString = :dateString")
    suspend fun getDailyCountOnce(dateString: String): Int

    @Query("""
        SELECT dateString, COUNT(*) as totalCount 
        FROM scroll_events 
        WHERE dateString BETWEEN :startDate AND :endDate 
        GROUP BY dateString
    """)
    fun observeCountsByDateRange(startDate: String, endDate: String): Flow<List<DayCountSummary>>

    @Query("""
        SELECT platform, COUNT(*) as count 
        FROM scroll_events 
        WHERE dateString BETWEEN :startDate AND :endDate 
        GROUP BY platform 
        ORDER BY count DESC
    """)
    fun observePlatformBreakdown(startDate: String, endDate: String): Flow<List<AppCountBreakdown>>

    @Query("DELETE FROM scroll_events WHERE dateString = :dateString")
    suspend fun clearDayEvents(dateString: String)

    @Query("SELECT COUNT(*) FROM scroll_events")
    suspend fun getTotalEventsCount(): Int
}
