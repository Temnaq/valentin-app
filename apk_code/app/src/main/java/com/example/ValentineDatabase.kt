package com.example

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "valentine_history")
data class ValentineHistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface ValentineHistoryDao {
    @Query("SELECT * FROM valentine_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ValentineHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItem(item: ValentineHistoryItem)

    @Query("DELETE FROM valentine_history")
    suspend fun clearHistory()
}

@Database(entities = [ValentineHistoryItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun valentineHistoryDao(): ValentineHistoryDao
}

object DatabaseProvider {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "valentinka_database"
            )
            .fallbackToDestructiveMigration()
            .build()
            INSTANCE = instance
            instance
        }
    }
}

class ValentineRepository(private val dao: ValentineHistoryDao) {
    val allHistory: Flow<List<ValentineHistoryItem>> = dao.getAllHistory()

    suspend fun insert(item: ValentineHistoryItem) {
        dao.insertHistoryItem(item)
    }

    suspend fun clearAll() {
        dao.clearHistory()
    }
}
