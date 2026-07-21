package com.example.stayfree.data.local.db.dao

import androidx.room.*
import com.example.stayfree.data.local.entity.WebsiteBlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WebsiteBlockDao {

    @Query("SELECT * FROM website_blocks ORDER BY domain ASC")
    fun getAll(): Flow<List<WebsiteBlockEntity>>

    @Query("SELECT * FROM website_blocks WHERE isActive = 1")
    suspend fun getActiveOnce(): List<WebsiteBlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WebsiteBlockEntity): Long

    @Update
    suspend fun update(entity: WebsiteBlockEntity)

    @Query("DELETE FROM website_blocks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE website_blocks SET timeUsedTodayMs = :timeMs, date = :date WHERE id = :id")
    suspend fun updateTimeUsed(id: Long, timeMs: Long, date: String)

    @Query("UPDATE website_blocks SET timeUsedTodayMs = 0, date = :date WHERE date != :date")
    suspend fun resetDailyTimers(date: String)
}
