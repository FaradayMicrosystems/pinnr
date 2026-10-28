package com.pinnr.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    /** Shown on the Android Auto grid. */
    val showInCar: Boolean = true,
    val sortOrder: Int = 0,
    /** Phone grid tile size: 1 = small, 2 = medium, 3 = large (columns spanned). */
    @ColumnInfo(defaultValue = "1") val tileSize: Int = 1,
)

@Entity(
    tableName = "pins",
    foreignKeys = [ForeignKey(
        entity = Category::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("categoryId")],
)
data class Pin(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val lat: Double,
    val lng: Double,
    val accuracyM: Float?,
    val timestamp: Long,
    val address: String? = null,
    /** Shown as the description in exports (Google My Maps, GPX). */
    val note: String? = null,
    /** Custom title; null means "<category> – <date>". */
    val name: String? = null,
)

data class CategoryWithCount(
    @Embedded val category: Category,
    val pinCount: Int,
)

@Dao
interface PinDao {
    @Query(
        "SELECT c.*, (SELECT COUNT(*) FROM pins p WHERE p.categoryId = c.id) AS pinCount " +
            "FROM categories c ORDER BY c.sortOrder, c.name COLLATE NOCASE"
    )
    fun categoriesWithCounts(): Flow<List<CategoryWithCount>>

    @Query("SELECT * FROM categories WHERE showInCar = 1 ORDER BY sortOrder, name COLLATE NOCASE")
    fun carCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun category(id: Long): Category?

    @Insert suspend fun insertCategory(c: Category): Long
    @Update suspend fun updateCategory(c: Category)
    @Delete suspend fun deleteCategory(c: Category)

    @Query("SELECT * FROM pins WHERE categoryId = :categoryId ORDER BY timestamp DESC")
    fun pinsFor(categoryId: Long): Flow<List<Pin>>

    @Query("SELECT * FROM pins WHERE categoryId = :categoryId ORDER BY timestamp")
    suspend fun pinsForExport(categoryId: Long): List<Pin>

    @Query("SELECT * FROM pins ORDER BY timestamp")
    suspend fun allPins(): List<Pin>

    @Query("SELECT * FROM categories")
    suspend fun allCategories(): List<Category>

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun categoryByName(name: String): Category?

    @Query("SELECT COUNT(*) FROM pins WHERE categoryId = :categoryId AND lat = :lat AND lng = :lng AND timestamp = :timestamp")
    suspend fun countSame(categoryId: Long, lat: Double, lng: Double, timestamp: Long): Int

    @Insert suspend fun insertPin(p: Pin): Long
    @Query("UPDATE pins SET address = :address WHERE id = :id")
    suspend fun setAddress(id: Long, address: String)
    @Query("UPDATE pins SET note = :note WHERE id = :id")
    suspend fun setNote(id: Long, note: String?)
    @Query("UPDATE pins SET name = :name, note = :note WHERE id = :id")
    suspend fun setNameAndNote(id: Long, name: String?, note: String?)
    @Query("UPDATE pins SET categoryId = :categoryId WHERE id = :id")
    suspend fun movePin(id: Long, categoryId: Long)
    @Query("DELETE FROM pins WHERE id = :id")
    suspend fun deletePin(id: Long)
}

@Database(entities = [Category::class, Pin::class], version = 3, exportSchema = false)
abstract class PinnrDb : RoomDatabase() {
    abstract fun dao(): PinDao

    companion object {
        @Volatile private var instance: PinnrDb? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN tileSize INTEGER NOT NULL DEFAULT 1")
            }
        }
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pins ADD COLUMN name TEXT")
            }
        }
        fun get(context: Context): PinnrDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, PinnrDb::class.java, "pinnr.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { instance = it }
        }
    }
}
