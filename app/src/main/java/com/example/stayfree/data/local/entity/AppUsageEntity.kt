package com.example.stayfree.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_usage",
    indices = [Index(value = ["packageName", "date"], unique = true)]
)
data class AppUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val date: String,           // "YYYY-MM-DD"
    val totalTimeMs: Long = 0,
    val unlockCount: Int = 0,
    val screenOnCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        /** Synthetic row that stores device-level counters (unlocks, screen-ons) per day. */
        const val DEVICE_ROW = "__device__"
    }
}
