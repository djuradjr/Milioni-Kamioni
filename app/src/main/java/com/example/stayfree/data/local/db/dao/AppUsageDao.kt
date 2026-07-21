package com.example.stayfree.data.local.db.dao

import androidx.room.*
import com.example.stayfree.data.local.entity.AppUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppUsageDao {

    @Query("SELECT * FROM app_usage WHERE date = :date AND packageName != '__device__' ORDER BY totalTimeMs DESC")
    fun getUsageForDate(date: String): Flow<List<AppUsageEntity>>

    @Query("SELECT * FROM app_usage WHERE date = :date AND packageName != '__device__' ORDER BY totalTimeMs DESC")
    suspend fun getUsageForDateOnce(date: String): List<AppUsageEntity>

    @Query("SELECT * FROM app_usage WHERE packageName = :pkg AND date = :date LIMIT 1")
    suspend fun getUsageForPackageAndDate(pkg: String, date: String): AppUsageEntity?

    @Query("SELECT * FROM app_usage WHERE packageName = :pkg AND date = :date LIMIT 1")
    fun getUsageForPackageOnDate(pkg: String, date: String): Flow<AppUsageEntity?>

    @Query("SELECT * FROM app_usage WHERE packageName = :pkg AND date >= :fromDate ORDER BY date DESC")
    fun getUsageForPackage(pkg: String, fromDate: String): Flow<List<AppUsageEntity>>

    @Query("SELECT * FROM app_usage WHERE date >= :fromDate AND packageName != '__device__' ORDER BY date DESC")
    fun getUsageFromDate(fromDate: String): Flow<List<AppUsageEntity>>

    @Query("SELECT SUM(totalTimeMs) FROM app_usage WHERE date = :date AND packageName != '__device__'")
    fun getTotalScreenTimeForDate(date: String): Flow<Long?>

    @Query("SELECT SUM(unlockCount) FROM app_usage WHERE date = :date")
    fun getTotalUnlocksForDate(date: String): Flow<Int?>

    @Query("SELECT SUM(totalTimeMs) FROM app_usage WHERE date >= :fromDate AND date <= :toDate AND packageName != '__device__'")
    fun getTotalScreenTimeBetween(fromDate: String, toDate: String): Flow<Long?>

    @Query("SELECT SUM(unlockCount) FROM app_usage WHERE date >= :fromDate AND date <= :toDate")
    fun getTotalUnlocksBetween(fromDate: String, toDate: String): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppUsageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AppUsageEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: AppUsageEntity): Long

    @Query("UPDATE app_usage SET totalTimeMs = :totalTimeMs, lastUpdated = :lastUpdated WHERE packageName = :pkg AND date = :date")
    suspend fun updateTotalTime(pkg: String, date: String, totalTimeMs: Long, lastUpdated: Long = System.currentTimeMillis())

    @Query("UPDATE app_usage SET unlockCount = unlockCount + 1 WHERE packageName = :pkg AND date = :date")
    suspend fun incrementUnlockCount(pkg: String, date: String)

    @Query("UPDATE app_usage SET screenOnCount = screenOnCount + 1 WHERE packageName = :pkg AND date = :date")
    suspend fun incrementScreenOnCount(pkg: String, date: String)

    @Query("DELETE FROM app_usage WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)

    @Query("DELETE FROM app_usage WHERE packageName = :pkg")
    suspend fun deleteForPackage(pkg: String)

    // Purge whole days whose app time sums past [maxDayMs]. Under the single-
    // foreground model a day can't exceed 24h total, so such rows are corruption
    // from the old double-counting fold — better to drop them than show 80h.
    @Query(
        "DELETE FROM app_usage WHERE date IN " +
            "(SELECT date FROM app_usage GROUP BY date HAVING SUM(totalTimeMs) > :maxDayMs)"
    )
    suspend fun deleteCorruptDays(maxDayMs: Long)
}
