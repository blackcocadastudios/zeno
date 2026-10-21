package com.blackcocadastudios.zeno.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val url: String,
    val title: String,
    val visitCount: Int,
    val lastVisitedAt: Long,
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY lastVisitedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<HistoryEntity>>
}

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = true)
abstract class ZenoDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao
}
