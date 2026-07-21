package com.example.stayfree.data.repository

import com.example.stayfree.data.local.entity.WebsiteBlockEntity
import kotlinx.coroutines.flow.Flow

interface WebsiteBlockRepository {
    fun getAll(): Flow<List<WebsiteBlockEntity>>
    suspend fun getActiveOnce(): List<WebsiteBlockEntity>
    suspend fun insert(entity: WebsiteBlockEntity): Long
    suspend fun update(entity: WebsiteBlockEntity)
    suspend fun deleteById(id: Long)
    suspend fun updateTimeUsed(id: Long, timeMs: Long, date: String)
    suspend fun resetDailyTimers(date: String)
}
