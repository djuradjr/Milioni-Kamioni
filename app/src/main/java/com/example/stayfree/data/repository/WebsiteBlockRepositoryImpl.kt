package com.example.stayfree.data.repository

import com.example.stayfree.data.local.db.dao.WebsiteBlockDao
import com.example.stayfree.data.local.entity.WebsiteBlockEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebsiteBlockRepositoryImpl @Inject constructor(
    private val dao: WebsiteBlockDao
) : WebsiteBlockRepository {
    override fun getAll(): Flow<List<WebsiteBlockEntity>> = dao.getAll()
    override suspend fun getActiveOnce(): List<WebsiteBlockEntity> = dao.getActiveOnce()
    override suspend fun insert(entity: WebsiteBlockEntity): Long = dao.insert(entity)
    override suspend fun update(entity: WebsiteBlockEntity) = dao.update(entity)
    override suspend fun deleteById(id: Long) = dao.deleteById(id)
    override suspend fun updateTimeUsed(id: Long, timeMs: Long, date: String) = dao.updateTimeUsed(id, timeMs, date)
    override suspend fun resetDailyTimers(date: String) = dao.resetDailyTimers(date)
}
