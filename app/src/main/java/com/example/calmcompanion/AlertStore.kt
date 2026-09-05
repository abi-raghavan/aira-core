package com.example.calmcompanion

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "alert_events",
    indices = [Index(value = ["fingerprint"], unique = true)]
)
data class AlertEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fingerprint: String,
    val severity: String,
    val status: String = AlertStatus.QUEUED.name,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0
)

@Dao
interface AlertDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(event: AlertEvent): Long

    @Query("SELECT * FROM alert_events ORDER BY createdAt DESC LIMIT 1")
    fun observeLatest(): Flow<AlertEvent?>

    @Query("SELECT * FROM alert_events WHERE status IN ('QUEUED', 'FAILED') ORDER BY createdAt ASC LIMIT 1")
    suspend fun nextPending(): AlertEvent?

    @Query("SELECT * FROM alert_events WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): AlertEvent?

    @Query(
        "UPDATE alert_events SET status = :status, updatedAt = :updatedAt, " +
            "attemptCount = attemptCount + :attemptIncrement WHERE id = :id"
    )
    suspend fun updateStatus(
        id: Long,
        status: String,
        updatedAt: Long = System.currentTimeMillis(),
        attemptIncrement: Int = 0
    )

    @Query("DELETE FROM alert_events WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

@Database(entities = [AlertEvent::class], version = 1, exportSchema = false)
abstract class AiraDatabase : RoomDatabase() {
    abstract fun alerts(): AlertDao

    companion object {
        @Volatile private var instance: AiraDatabase? = null

        fun get(context: Context): AiraDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AiraDatabase::class.java,
                "aira_events.db"
            ).build().also { instance = it }
        }
    }
}
