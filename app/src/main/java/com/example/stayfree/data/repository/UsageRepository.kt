package com.example.stayfree.data.repository

import com.example.stayfree.domain.model.AppUsage
import kotlinx.coroutines.flow.Flow

interface UsageRepository {
    fun getUsageForDate(date: String): Flow<List<AppUsage>>
    fun getTotalScreenTimeForDate(date: String): Flow<Long>
    fun getTotalUnlocksForDate(date: String): Flow<Int>
    fun getUsageForPackage(packageName: String, fromDate: String): Flow<List<AppUsage>>
    fun getScreenTimeForPackageOnDate(packageName: String, date: String): Flow<Long>
    fun getUnlocksForPackageOnDate(packageName: String, date: String): Flow<Int>
    suspend fun syncFromUsageStats(date: String, resetTimeMinutes: Int)
    suspend fun incrementUnlock(date: String)
    suspend fun incrementScreenOn(date: String)
}
